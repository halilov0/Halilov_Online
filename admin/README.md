# Admin SPA — `halilov.co.il/admin`

Operator dashboard for Halilov Online. Same stack as the shop SPA,
mounted at `/admin/` behind the same nginx. Locked to users with role
`ADMIN`; backed by per-request server-side authorization on every
`/api/admin/**` route.

See [`ARCHITECTURE.md`](../ARCHITECTURE.md) for the full system diagram.

## Stack

| Concern    | Choice                          |
|------------|---------------------------------|
| Framework  | React 19 + TypeScript           |
| Bundler    | Vite                            |
| Router     | `react-router-dom` v7           |
| State      | Zustand                         |
| HTTP       | `fetch` wrapped in [`src/api.ts`](src/api.ts) |
| Styling    | Hand-rolled CSS (RTL)           |

Separate codebase from the shop SPA so it can ship a different bundle
(narrower features, no marketing pixels, different colour palette).
Shared shape: same `api()` wrapper pattern, same Zustand stores, same
Hebrew error fallbacks.

## Run locally

```
npm install
npm run dev     # http://localhost:5174 by default
npm run build
```

Backend must be reachable at `/api/**` — Vite proxies in dev, nginx
proxies in prod.

## Layout

```
src/
  api.ts                  fetch wrapper, types
  App.tsx                 router; outermost Layout + RequireAdmin gate
  main.tsx                React root
  auth/authStore.ts       Zustand: token + user; two-step login (password → TOTP)
  components/
    RequireAdmin.tsx      Route guard — bounces to /login if not ADMIN
    Layout.tsx            Sidebar + TopBar + outlet
    Sidebar.tsx           Nav links
    TopBar.tsx            Right-side actions (logout, etc.)
    Field.tsx             Form field with label + error slot
    StatusPill.tsx        Coloured order-status badge
    Toast.tsx             Toast container + helper
    Icon.tsx              Inline SVG icons
  pages/                  One per route (DashboardPage, OrdersPage, ProductsPage, …)
```

## Auth — two-step admin login

The admin login is the only place we surface the TOTP flow:

1. `useAuth.login(email, password)` POSTs `/api/auth/login`.
2. Response is either an `AuthResponse` (JWT — trusted IP, no 2FA) or a
   `ChallengeResponse` (`{ requires2FA: true, challenge: '...' }`).
3. The store returns `'done'` or `'totp-required'`. On the latter the
   challenge is stashed in `pendingChallenge`; the login page renders
   the TOTP input.
4. `submitTotp(code)` POSTs `/api/auth/login/totp` with `{ challenge,
   code }` and consumes the result the same way.

If `/me` ever returns a non-ADMIN role, the store wipes the token
locally — there's no UI for a customer in this app, and we don't want
a stale or escalated session lingering.

### `RequireAdmin` gate

The route guard:

- Reads `user` + `bootstrapped` from the auth store.
- Calls `fetchMe()` once on mount when not bootstrapped.
- Shows a loading hint until `bootstrapped` flips to true.
- Bounces to `/login` (with the intended path in state) when the user
  is unauthenticated.

`bootstrapped` matters: until the first `/me` resolves, `user` is
`null` even if the token is present, so naive `!user` guards would
bounce on every F5.

### Security page

[pages/SecurityPage.tsx](src/pages/SecurityPage.tsx) handles two
self-service flows for the logged-in admin:

- **2FA enroll / disable** — `POST /api/me/totp/{enroll,confirm,disable}`.
- **Change password** — `PATCH /api/me/password` with `{currentPassword,
  newPassword}`. The backend rotates `users.force_logout_at` (kills
  other devices) and returns a fresh JWT in the response. The page
  calls `useAuth.adoptToken(token)` to persist it via `setToken()` and
  update the store atomically, so the calling tab stays logged in.
  Same-as-current new password → 400 with Hebrew copy.

## Build & deploy

`Dockerfile` builds the static bundle and serves it from nginx
(`nginx.conf` in this folder). The infra stack mounts this image on
`/admin/`.

## Tests

No suite yet — same calculus as the shop. Floor:

```
npm run lint
npx tsc --noEmit
```
