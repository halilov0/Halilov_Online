# Shop SPA — `halilov.co.il`

Customer-facing single-page app for Halilov Online. React 19 + Vite +
TypeScript, served as a static bundle from the same nginx that proxies
`/api/**` to the Spring backend.

See [`ARCHITECTURE.md`](../ARCHITECTURE.md) for the full system
diagram. This README covers the shop module specifically.

## Stack

| Concern         | Choice                                  |
|-----------------|-----------------------------------------|
| Framework       | React 19 (TypeScript, function-only)    |
| Bundler / dev   | Vite                                    |
| Router          | `react-router-dom` v7                   |
| State           | Zustand (per-domain stores)             |
| HTTP            | `fetch` wrapped in [`src/api.ts`](src/api.ts) |
| PDF (invoice)   | `jspdf` rendered client-side            |
| Styling         | Hand-rolled CSS in [`src/index.css`](src/index.css), RTL-first |

No CSS framework. No React Query. No global redux. Stores own
domain-specific state and call `api()` directly.

## Run locally

```
npm install
npm run dev     # http://localhost:5173 (Vite), proxies /api to backend
npm run build   # static bundle in dist/, ready for nginx
```

`vite.config.ts` proxies `/api/**` to the local backend on `:8080` for
dev; in prod nginx does the same job at the edge.

## Layout

```
src/
  api.ts                  fetch wrapper, types, formatPrice helper, guest-token plumbing
  App.tsx                 router, hydrate-user-on-token effect, per-route SEO effect
  seo.ts                  per-route canonical + robots (noindex) for the SPA shell
  main.tsx                React root mount
  auth/authStore.ts       Zustand: token + user, login/register/logout/fetchMe
  cart/cartStore.ts       Zustand: cart lines, debounced server sync, cross-tab + cross-device
  favorites/favoritesStore.ts  Zustand: wishlist set, debounced server sync, cross-tab + cross-device
  delivery/deliveryConfig.ts   Zustand: server-mirrored delivery config
  lib/invoicePdf.ts       client-side invoice PDF render
  pages/                  one component per route (CatalogPage, CartPage, …)
  components/             shared UI (Header, Footer, ProductCard, Toast, …)
```

## How auth works

1. The `api()` wrapper auto-attaches `Authorization: Bearer ...` from
   `localStorage` (`halilov.token`).
2. `App.tsx` calls `useAuth.getState().fetchMe()` on mount when a token
   exists. The store holds the token **immediately** but `user` is
   `null` until `/me` resolves.
3. Protected routes must gate on `!token`, **not** `!user` — otherwise
   they bounce to login on every F5 while `fetchMe` is in flight.

`ApiError` from `api.ts` carries the HTTP status; `403` for a
wrong-account order read is mapped to a "wrong account" hint without
ever echoing the actual owner's email.

## How the cart works

- Source of truth on the server: `/api/cart` (`PUT` to replace,
  `POST /merge` for login, `DELETE` to clear, `GET` to read).
- `cartStore` debounces local mutations into a single `PUT` (350 ms).
- **Cross-tab**: `BroadcastChannel` (modern browsers) +
  `storage` event (fallback) keep tabs in sync without re-pushing.
- **Cross-device**: `visibilitychange` on the document refetches the
  canonical cart when a tab returns to foreground (throttled to 3 s).
- Guests have no `cart_lines` row; the cart is entirely in
  `localStorage`, tagged once signed in with its owner userId
  (`halilov.cart.owner`) and a baseline (`halilov.cart.baseline`) — the
  last cart that provably matched the server, refreshed on every
  load/merge/push. On login `adoptAuth` reconciles by these:
  - **owner == another user** → drop the local cart, then
    `loadFromRemote()`.
  - **otherwise** → `mergeAdditionsWithRemote()` merges only the lines
    added/increased since the baseline via `POST /api/cart/merge`. A true
    guest cart has an empty baseline so it folds in whole; an
    expired-session residue contributes only items added while logged
    out, so lines already in the DB aren't re-summed (no doubling) and
    nothing added in the meantime is lost.
- Full behavior reference (sync, reconciliation table, scenarios, edges):
  [../CART.md](../CART.md).

## Guest checkout & order share links

- Guest orders return a one-shot `guestToken` in the create response.
  The token is stored in **`sessionStorage`** (vanishes on tab close)
  under `halilov.guestOrders`, keyed by order number.
- `api()` automatically attaches `X-Guest-Token: ...` on reads /
  payment calls that match `^/api/orders/{n}` or
  `^/api/payments/mock/{n}/complete`. No app code threads the token
  through manually.
- Registered users can mint a `shareToken` for an order they own
  (`POST /api/orders/{n}/share`). The token is reused on repeat calls,
  so existing share recipients don't have their links revoked.

## Hebrew / RTL

`index.html` sets `dir="rtl"` and `lang="he"`. All user-facing copy is
Hebrew. Error strings come from the backend where possible; `api.ts`
falls back to generic Hebrew copy for 429/503 and unparseable bodies.

## Build & deploy

`Dockerfile` builds with `npm run build`, then serves `dist/` with
nginx (`nginx.conf` in this folder). The infra stack mounts this image
on `/` of the public origin; admin lives on `/admin/`.

## Tests

No frontend test suite yet — pragmatic call given solo dev + small
surface. Type-check + lint are the floor:

```
npm run lint
npx tsc --noEmit
```
