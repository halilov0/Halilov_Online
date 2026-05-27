# Cart behavior

End-to-end reference for how the shopping cart works: where it's stored, how
it syncs, and — the part worth scrutinising — how the local cart is reconciled
with the account on login. Written so you can read the decision table and the
worked scenarios and decide whether each choice makes sense.

Code:
- Frontend store: [shop/src/cart/cartStore.ts](shop/src/cart/cartStore.ts)
- Login/logout orchestration: [shop/src/auth/authStore.ts](shop/src/auth/authStore.ts)
- Backend API: [CartController.java](backend/src/main/java/com/halilov/online/cart/CartController.java)
- Backend logic: [CartService.java](backend/src/main/java/com/halilov/online/cart/CartService.java)
- Table: [V11__cart_lines.sql](backend/src/main/resources/db/migration/V11__cart_lines.sql)

---

## 1. Model

- **Guests** keep the cart entirely in `localStorage`. No DB row, no API calls.
- **Signed-in users** are server-backed: the cart lives in the `cart_lines`
  table, one row per `(user_id, product_id)` (`UNIQUE`, `quantity > 0`,
  `ON DELETE CASCADE` on both user and product). `localStorage` is a local
  cache/mirror the server is the source of truth for.

A line carries display data (`slug`, `nameHe`, `priceAgorot`, `imageUrl`) for
rendering offline, but only `productId` + `quantity` are ever sent to the
server — prices/names are re-resolved server-side so they can't be tampered
with from the client.

### localStorage keys

| Key                     | Shape                      | Meaning |
|-------------------------|----------------------------|---------|
| `halilov.cart`          | `CartLine[]`               | The visible cart lines. |
| `halilov.cart.owner`    | `number` (userId)          | Which signed-in user this cart belongs to. Absent = guest cart. |
| `halilov.cart.baseline` | `{ [productId]: quantity }`| Snapshot of the cart the **last time it provably matched the server**. |

All three are shared across tabs of the same browser. (Guest *order* tokens
live in `sessionStorage` under `halilov.guestOrders` — that's checkout, not the
cart, and out of scope here.)

---

## 2. Sync mechanisms (while signed in)

1. **Debounced push** — every mutation (`add`/`setQty`/`remove`) optimistically
   updates local lines, then schedules one `PUT /api/cart` after **350 ms**, so
   rapid +/- clicks collapse into a single request. `PUT` replaces the server
   cart wholesale with the full local state.
2. **Cross-tab** — every mutation `save()`s to `localStorage` and posts on a
   `BroadcastChannel`. Other tabs adopt the new lines via the channel
   (low-latency) **or** the `storage` event (fallback for browsers without
   `BroadcastChannel`). The receiving tab does **not** re-broadcast or re-push.
3. **Cross-device** — on `visibilitychange` to visible (tab regains focus),
   a signed-in tab pulls the canonical cart with `GET /api/cart`, throttled to
   once per **3 s**. Picks up changes made on another device.

Quantity is clamped to **1..99** client-side; the server independently clamps
to `min(99, stock)` and drops inactive/missing products.

---

## 3. The four server operations

| Store method            | HTTP                | Server does |
|-------------------------|---------------------|-------------|
| `loadFromRemote()`      | `GET /api/cart`     | Returns active lines (inactive/zero dropped). |
| `pushToRemote()`        | `PUT /api/cart`     | **Replace**: delete all rows, persist the desired set. |
| `mergeWithRemote(items)`| `POST /api/cart/merge` | **Merge**: `existing + incoming` summed per product, then persisted. |
| `clearAll()`            | `DELETE /api/cart`  | Delete all rows. |

Server-side `persist()` (used by both replace and merge): sums duplicate
productIds, drops products that are missing/inactive or `qty <= 0`, clamps each
line to `min(99, stock)`.

Important distinction:
- **Replace** (`PUT`) makes the server exactly equal the local cart.
- **Merge** (`POST /merge`) *adds* incoming quantities on top of what's already
  in the DB. This is why login can't naively merge the whole local cart — see §5.

---

## 4. The baseline

`halilov.cart.baseline` is the crux of the login reconciliation. It records the
cart **as the server last confirmed it**, and is refreshed on every operation
that synchronises local and server:

| Event                      | baseline becomes |
|----------------------------|------------------|
| `loadFromRemote()` success | the fetched server cart |
| `mergeWithRemote()` success| the merged server cart returned |
| `pushToRemote()` success   | exactly the snapshot that was pushed |
| `clearAll()` success       | empty `{}` |
| `logout()`                 | cleared (removed) |
| guest mutation (no token)  | **unchanged** — the server never confirmed it |

So at any moment: **`local lines − baseline = changes the server hasn't seen`**.
During a normal signed-in session local ≈ baseline (continuous sync keeps them
equal). During a logged-out window the baseline freezes while local can drift.

`additionsSinceBaseline(lines)` returns the **positive** part of that delta as
merge items — new products and quantity increases. Removals and decreases are
omitted (you can't express "remove" through a summing merge).

---

## 5. Login / register reconciliation

When the user explicitly authenticates, `adoptAuth()` runs:

```
prevOwner = halilov.cart.owner   (read BEFORE setting the new token)
setToken(token); fetchMe()       (fetchMe sets owner = me.id on success)
myId = the just-authenticated user's id
```

Then, based on who the local cart belonged to:

| `prevOwner` (local cart's owner tag) | Authenticating as | Action | Why |
|--------------------------------------|-------------------|--------|-----|
| **absent** (genuine guest cart)      | N                 | `mergeAdditionsWithRemote()` — baseline empty ⇒ merges the **whole** local cart | Anonymous shopping folds into the account. |
| **N** (this same user's residue)     | N                 | `mergeAdditionsWithRemote()` — merges **only the delta** since baseline | Residue already in the DB is skipped (no doubling); items added while logged out are folded in (no loss). |
| **M** (a different user)             | N                 | `clearLocal()` then `loadFromRemote()` | Don't bleed M's cart into N's account; adopt N's canonical server cart. |

Finally `owner` is set to `myId`.

**Why the owner tag, not "is a token present?"** A silently expired token is
wiped by `fetchMe()`'s 401 handler *before* the user re-logs in. The old
heuristic ("if a token already exists, it's a user-switch") therefore saw the
expired-session cart as a fresh guest cart and merged it whole — that was the
cart-doubling bug. The owner tag survives expiry (only `logout` clears it), so
re-login can recognise residue.

**Why `fetchMe()` sets the owner on every success**, not just at login: it
tags carts for sessions that predate this code and keeps the tag fresh while
the token is valid, so the *next* silent expiry is recognised as residue. The
tag is **never** cleared on a 401/403 — it must outlive the token.

---

## 6. Logout

```
pushToRemote()      flush any debounced mutation still in flight to the DB
clearLocal()        wipe local lines (next guest on this browser starts empty)
setCartOwner(null)  drop the owner tag
clearCartBaseline() drop the baseline
setToken(null)      end the session
```

The DB cart is intentionally **left intact** — it's restored on the next login.

---

## 7. Failure handling

- **401/403** on a cart call: the token died. The cart is left untouched; the
  auth store handles the session. (`pushToRemote` does *not* roll back here.)
- **5xx / network**: transient. Local stays; the next mutation's debounced push
  retries.
- **Other 4xx** (400/404/409/422) on `pushToRemote`: the optimistic local state
  is something the server rejects. Roll back by `loadFromRemote()` (adopt the
  canonical cart).
- `loadFromRemote` / `mergeWithRemote` failures: keep local as-is.

---

## 8. Worked scenarios

Notation: `DB`, `local`, `baseline`, `owner`.

**A — Session expires, re-login (the original bug, now fixed)**
1. Signed in: `DB {X:1}`, `local [X:1]`, `baseline {X:1}`, `owner N`.
2. Close tab; token expires server-side.
3. Reopen: `fetchMe` → 401 → token + user wiped. `local`, `owner N`,
   `baseline {X:1}` all survive → UI shows a guest with `X:1`.
4. Re-login: `prevOwner N == myId N` → `mergeAdditions`. Delta of `[X:1]` vs
   `{X:1}` = **empty** → merge `[]` → DB returns `{X:1}` → adopt. **No doubling.**

**B — Expire, add an item as guest, then re-login**
1. After step 3 above, guest adds `Y:1` → `local [X:1, Y:1]`, `baseline {X:1}`.
2. Re-login: delta = `[Y:1]` → merge `Y:1` onto `DB {X:1}` → `{X:1, Y:1}`.
   X not doubled, **Y preserved.**

**C — True guest's first login**
- `local [A:2]`, no owner, baseline empty. DB (from phone) `{B:1}`.
- Login: delta = whole cart `[A:2]` → merge onto `{B:1}` → `{A:2, B:1}`.

**D — Two users, one browser**
- A signed in (`owner A`). Token expires. B logs in: `prevOwner A != B` →
  `clearLocal` + `loadFromRemote` → B sees only B's DB cart. No bleed.

**E — Another device changes the cart during the logged-out window**
- Device1 `baseline {X:1}`, expires. Device2 adds `Z:3` → `DB {X:1, Z:3}`.
- Device1 guest adds `Y:1`, re-logs in: delta `[Y:1]` → merge onto
  `{X:1, Z:3}` → `{X:1, Z:3, Y:1}`. Both devices' work preserved, X not doubled.

---

## 9. Deliberate tradeoffs / known edges

Review these specifically — they're conscious choices, not oversights:

1. **Removals during a logged-out window don't propagate.** Merge can't carry a
   negative delta, so if you remove an item (or lower its quantity) while your
   token is expired, the DB keeps the old line and it **reappears** after
   re-login. Rationale: keeping an item beats silently dropping one. Additions
   and increases — the common case — are handled.
2. **Inactive products vanish silently.** A product deactivated in the catalog
   is dropped by the server on the next load/merge; the line disappears from the
   cart with no notice.
3. **Stock clamp can shrink a line.** Every replace/merge clamps to
   `min(99, stock)`, so a quantity can drop server-side if stock fell.
4. **Stale baseline if the mount load fails.** If `loadFromRemote` fails on app
   mount (network), the baseline may lag the DB until the next successful sync.
   Bounded and self-correcting, but a re-login in that window could merge a
   slightly-off delta.
5. **Shared computers.** The token sits in `localStorage`, so *before* it
   expires the next person on that browser is logged in as you — the cart fix
   doesn't change that. Logging out is the remedy (it wipes cart, owner,
   baseline, token).

---

## 10. Review checklist

- Does the §5 decision table match what you'd want for each case?
- Is "removals during a logged-out window don't propagate" (§9.1) acceptable,
  or should logout/expiry behave differently?
- Should an expired session show the residue cart as a guest at all, or be
  emptied with a "log in to restore your cart" notice? (Current choice: keep it
  visible — see commit history / discussion.)
- Is the 350 ms push debounce / 3 s focus-refresh throttle tuned right?
