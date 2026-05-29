# Favorites (wishlist) behavior

The favorites set is a direct port of the cart sync model to a quantity-less
per-user set of product ids. **Read [CART.md](CART.md) first** — every primitive
(debounced PUT, BroadcastChannel cross-tab fan-out, visibilitychange reconcile,
pagehide keepalive flush, owner + baseline localStorage keys, login
reconciliation table, no-clobber rule) carries over verbatim. This doc only
notes what differs.

Code:
- Frontend store: [shop/src/favorites/favoritesStore.ts](shop/src/favorites/favoritesStore.ts)
- Login/logout orchestration: [shop/src/auth/authStore.ts](shop/src/auth/authStore.ts) (same `adoptAuth` reconciles cart + favorites)
- Backend API: [FavoriteController.java](backend/src/main/java/com/halilov/online/favorites/FavoriteController.java)
- Backend logic: [FavoriteService.java](backend/src/main/java/com/halilov/online/favorites/FavoriteService.java)
- Table: [V19__favorites.sql](backend/src/main/resources/db/migration/V19__favorites.sql)

---

## What differs from the cart

| Dimension                | Cart                                             | Favorites                                            |
|--------------------------|--------------------------------------------------|------------------------------------------------------|
| Row shape                | `(user_id, product_id, quantity, …)`             | `(user_id, product_id, created_at)` — no quantity    |
| Local state              | `CartLine[]` with display fields                 | `number[]` (productIds only)                         |
| localStorage keys        | `halilov.cart` / `.owner` / `.baseline`          | `halilov.favorites` / `.owner` / `.baseline`         |
| BroadcastChannel         | `halilov.cart`                                   | `halilov.favorites`                                  |
| Mutators                 | `add` / `setQty` / `remove`                      | `toggle` / `remove`                                  |
| Endpoint                 | `/api/cart` (`PUT` `{ items: [{productId, quantity}] }`) | `/api/favorites` (`PUT` `{ productIds: number[] }`)  |
| Merge semantics          | sum quantities per product (`existing + incoming`) | set union (idempotent — a duplicate is a no-op)      |
| Payload cap              | 200 items                                        | 500 ids                                              |
| Adjustment types         | `CLAMPED` *and* `REMOVED`                        | only `REMOVED` (no quantity ⇒ nothing to clamp)      |
| Inline notice            | dismissible drop banner + per-line clamp note    | dismissible drop banner only                         |

The store/server names mirror cart 1:1 (`pushToRemote`, `loadFromRemote`,
`reconcileWithRemote`, `mergeAdditionsWithRemote`, `cancelPendingFavoritesPush`,
`getFavoritesOwner`, `setFavoritesOwner`, `clearFavoritesBaseline`,
`FavoritesResponse`, `FavoriteAdjustment`) so anyone fluent in the cart store
can read the favorites store cold.

---

## Login reconciliation

Same three-row table as `CART.md §5`, just for favorites:

| `prevOwner`              | Authenticating as | Action |
|--------------------------|-------------------|--------|
| absent (guest set)       | N                 | `mergeAdditionsWithRemote()` — empty baseline ⇒ merge the whole local set |
| **N** (residue)          | N                 | `mergeAdditionsWithRemote()` — merge only ids added since baseline |
| **M** (different user)   | N                 | `clearLocal()` + `loadFromRemote()` |

`adoptAuth` in `authStore.ts` runs the cart reconciliation and the favorites
reconciliation in sequence; logout flushes both, clears both owner tags, and
drops both baselines. The owner tag survives a silent token expiry (only
explicit `logout` clears it) so a re-login can tell residue from a guest set —
same rationale as the cart.

---

## Deliberate tradeoffs (shared with cart)

1. **Removals during a logged-out window don't propagate.** Merge is set union,
   so unfavoriting an item while your token is expired leaves it in the DB and
   it reappears after re-login. Keeping a heart beats silently dropping one.
2. **Inactive/deleted products are dropped — with a notice.** The server drops
   them on load/merge/push and returns a `REMOVED` adjustment. The store toasts
   it once per `productId` (deduped for the page's lifetime so focus refresh
   doesn't re-toast a dead favorite) and keeps it in `useFavorites().adjustments`
   for an inline dismissible banner on the favorites page.
3. **Shared computers.** Same as cart — the token sits in `localStorage`, so
   the next person on the browser is logged in as you until the token expires.
   Logging out is the remedy (wipes favorites + owner + baseline + token).
