import { create } from 'zustand'
import {
  api, ApiError, getToken,
  type FavoriteAdjustment, type FavoritesReplaceRequest, type FavoritesResponse,
} from '../api'
import { useToast } from '../components/Toast'

/**
 * Shop favorites (wishlist) store. Server-backed for signed-in users,
 * localStorage-only for guests — same continuous-sync model as the cart.
 *
 * Mutations (toggle / remove / clear) are optimistic on the local set
 * and debounced into a single `PUT /api/favorites` (350 ms). Cross-tab
 * updates fan out via `BroadcastChannel` (with a `storage`-event
 * fallback). On tab visibility change the set is *reconciled*
 * (throttled): unpushed local changes are flushed, otherwise the
 * canonical set is pulled — so a heart on another device propagates
 * without clobbering one this device hasn't synced yet. On `pagehide`
 * a dirty set does a final `keepalive` flush so a change made inside
 * the 350 ms debounce window survives a tab close.
 *
 * Every server read/write returns a `FavoritesResponse` carrying any
 * adjustments the server made (inactive-product drop). They're toasted
 * via {@link useToast} and kept in `adjustments` for the favorites page
 * to show inline, so the set never mutates silently.
 *
 * Failure handling: 401/403 don't touch the set (the auth store deals
 * with it). 5xx and network errors retry on the next mutation. Other
 * 4xx mean the optimistic local state diverged from what the server
 * will accept — we roll back by adopting the canonical set.
 */

const STORAGE_KEY = 'halilov.favorites'
// Records which signed-in user the persisted favorites belong to. Lets login
// reconciliation tell a genuine guest set (fold into the account) apart from
// the residue of an expired session — whose favorites already live on the
// server, so merging would be a no-op but more importantly we shouldn't carry
// a previous user's wishlist into the next user's account. Survives a silent
// token expiry because it's a separate key the auth logout path clears
// explicitly.
const OWNER_KEY = 'halilov.favorites.owner'
// Snapshot of the favorites set the last time it provably matched the server.
// Lets login reconciliation merge only what was added *since* that point:
// residue already in the DB isn't re-sent (no churn), while items added during
// a logged-out window aren't in the baseline, so they still get folded in
// (no silent loss).
const BASELINE_KEY = 'halilov.favorites.baseline'
const BROADCAST_CHANNEL = 'halilov.favorites'
// Coalesce rapid toggles into a single PUT.
const PUSH_DEBOUNCE_MS = 350

type FavoritesState = {
  ids: number[]
  /** Server-side changes (drop) the user hasn't acknowledged yet, kept
   *  for inline display on the favorites page. Cleared when the user
   *  re-acts on that product or dismisses the notice. Page-scoped — not
   *  persisted. */
  adjustments: FavoriteAdjustment[]
  has: (productId: number) => boolean
  /** Toggle a product's favorite state. Returns the new state (true =
   *  now hearted, false = removed) so callers can pick the right toast. */
  toggle: (productId: number) => boolean
  remove: (productId: number) => void
  /** Dismiss an inline adjustment notice once the user has seen it. */
  dismissAdjustment: (productId: number) => void
  /** Wipe local set only. Use clearAll() to also drop the server set. */
  clearLocal: () => void
  /** Wipe local + server. */
  clearAll: () => Promise<void>
  /** Adopt the canonical server set, clobbering local. Used for rollback
   *  and the post-login user-switch. */
  loadFromRemote: () => Promise<void>
  /** Focus/mount sync: if local has unpushed changes, flush them
   *  (they're the newer intent on the device the user is looking at);
   *  otherwise pull the canonical set. Prevents a stale GET from
   *  clobbering a local mutation. */
  reconcileWithRemote: () => Promise<void>
  /** Merge ids into the server set and adopt the result. Defaults to
   *  the whole local set; pass a subset to merge only those. */
  mergeWithRemote: (productIds?: number[]) => Promise<void>
  /** Merge only the ids added since the set last matched the server.
   *  Avoids re-sending residue already in the DB while still folding in
   *  items added during a logged-out window. */
  mergeAdditionsWithRemote: () => Promise<void>
  /** Best-effort flush of current local state to the server. */
  pushToRemote: () => Promise<void>
}

function load(): number[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter((n): n is number => typeof n === 'number')
  } catch {
    return []
  }
}

function save(ids: number[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(ids))
}

/** userId the persisted favorites belong to, or null for a guest set. */
export function getFavoritesOwner(): number | null {
  const raw = localStorage.getItem(OWNER_KEY)
  if (!raw) return null
  const n = Number(raw)
  return Number.isInteger(n) ? n : null
}

/** Tag the persisted favorites as owned by `userId`, or clear the tag. */
export function setFavoritesOwner(userId: number | null) {
  if (userId == null) localStorage.removeItem(OWNER_KEY)
  else localStorage.setItem(OWNER_KEY, String(userId))
}

/** Last server-confirmed favorites as a set of productIds (empty if none). */
function getBaseline(): Set<number> {
  try {
    const raw = localStorage.getItem(BASELINE_KEY)
    if (!raw) return new Set()
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return new Set()
    return new Set(parsed.filter((n): n is number => typeof n === 'number'))
  } catch {
    return new Set()
  }
}

/** Record the current ids as the server-confirmed baseline. */
function setBaseline(ids: number[]) {
  localStorage.setItem(BASELINE_KEY, JSON.stringify(ids))
}

/** Drop the baseline (logout — the next session starts with no history). */
export function clearFavoritesBaseline() {
  localStorage.removeItem(BASELINE_KEY)
}

/**
 * Ids added since the favorites last matched the server. For a guest set the
 * baseline is empty so every id is returned. Removals during a logged-out
 * window don't propagate (merge can't carry a removal) — same trade-off as
 * the cart: keep > silently-drop.
 */
function additionsSinceBaseline(ids: number[]): number[] {
  const base = getBaseline()
  return ids.filter(id => !base.has(id))
}

// ---------- cross-tab sync ----------

type FavoritesMessage = { type: 'favorites-update'; ids: number[] }

let channel: BroadcastChannel | null = null
try {
  channel = typeof BroadcastChannel !== 'undefined'
    ? new BroadcastChannel(BROADCAST_CHANNEL)
    : null
} catch {
  channel = null
}

function broadcastUpdate(ids: number[]) {
  if (!channel) return
  try { channel.postMessage({ type: 'favorites-update', ids } satisfies FavoritesMessage) } catch { /* ignore */ }
}

// ---------- debounced push ----------

let pushTimer: number | null = null

function cancelPendingPush() {
  if (pushTimer !== null) {
    clearTimeout(pushTimer)
    pushTimer = null
  }
}

function schedulePush(push: () => Promise<void>) {
  if (!getToken()) return
  cancelPendingPush()
  pushTimer = window.setTimeout(() => {
    pushTimer = null
    push().catch(() => { /* sync failures must not disrupt the UI */ })
  }, PUSH_DEBOUNCE_MS)
}

/** Flush a pending debounced push without firing it — used by logout so
 *  a stray timer can't push into a session we're tearing down. */
export function cancelPendingFavoritesPush() {
  cancelPendingPush()
}

// ---------- dirty tracking / server-result adoption ----------

/** True when local ids exactly equal the last server-confirmed baseline. */
function matchesBaseline(ids: number[]): boolean {
  const base = getBaseline()
  if (base.size !== ids.length) return false
  for (const id of ids) if (!base.has(id)) return false
  return true
}

/** Local has changes the server hasn't confirmed (or a push is mid-flight). */
function favoritesAreDirty(): boolean {
  return pushTimer !== null || !matchesBaseline(useFavorites.getState().ids)
}

/** Same set? Used to detect whether the user mutated while a push was in flight. */
function sameSet(a: number[], b: number[]): boolean {
  if (a.length !== b.length) return false
  const s = new Set(a)
  for (const id of b) if (!s.has(id)) return false
  return true
}

// Removals are terminal (the product is gone), so a focus refresh could
// report the same dead favorite every time — dedupe by productId for the
// page's lifetime so the user is told once.
const toastedRemovals = new Set<number>()

/** Surface server-side drops the user didn't ask for. */
function notifyAdjustments(adjustments: FavoriteAdjustment[] | undefined) {
  if (!adjustments?.length) return
  const push = useToast.getState().push
  for (const a of adjustments) {
    if (toastedRemovals.has(a.productId)) continue
    toastedRemovals.add(a.productId)
    const name = a.nameHe ?? 'פריט'
    push(`${name} הוסר מהמועדפים — אינו זמין יותר`)
  }
}

/** Drop the notice for a product the user just acted on. */
function dropAdjustment(list: FavoriteAdjustment[], productId: number): FavoriteAdjustment[] {
  return list.some(a => a.productId === productId)
    ? list.filter(a => a.productId !== productId)
    : list
}

/** Fold new server adjustments into the kept set, keyed by productId. */
function mergeAdjustmentLists(existing: FavoriteAdjustment[], incoming: FavoriteAdjustment[] | undefined): FavoriteAdjustment[] {
  if (!incoming?.length) return existing
  const byId = new Map<number, FavoriteAdjustment>()
  for (const a of existing) byId.set(a.productId, a)
  for (const a of incoming) byId.set(a.productId, a)
  return [...byId.values()]
}

/** Adopt the canonical set the server returned. */
function adoptServerResult(res: FavoritesResponse) {
  const ids = res.productIds ?? []
  const adjustments = mergeAdjustmentLists(useFavorites.getState().adjustments, res.adjustments)
  save(ids)
  useFavorites.setState({ ids, adjustments })
  broadcastUpdate(ids)
  setBaseline(ids)
  notifyAdjustments(res.adjustments)
}

export const useFavorites = create<FavoritesState>((set, get) => ({
  ids: load(),
  adjustments: [],

  has(productId) {
    return get().ids.includes(productId)
  },

  toggle(productId) {
    const cur = get().ids
    const exists = cur.includes(productId)
    const next = exists ? cur.filter(id => id !== productId) : [...cur, productId]
    save(next)
    set({ ids: next, adjustments: dropAdjustment(get().adjustments, productId) })
    broadcastUpdate(next)
    schedulePush(() => get().pushToRemote())
    return !exists
  },

  remove(productId) {
    const next = get().ids.filter(id => id !== productId)
    save(next)
    set({ ids: next, adjustments: dropAdjustment(get().adjustments, productId) })
    broadcastUpdate(next)
    schedulePush(() => get().pushToRemote())
  },

  dismissAdjustment(productId) {
    set({ adjustments: dropAdjustment(get().adjustments, productId) })
  },

  clearLocal() {
    save([])
    set({ ids: [], adjustments: [] })
    broadcastUpdate([])
  },

  async clearAll() {
    save([])
    set({ ids: [], adjustments: [] })
    broadcastUpdate([])
    cancelPendingPush()
    if (!getToken()) return
    try {
      await api('/api/favorites', { method: 'DELETE' })
      setBaseline([])
    } catch {
      // ignore — local is the user-visible truth, server will catch up next push
    }
  },

  async loadFromRemote() {
    if (!getToken()) return
    try {
      adoptServerResult(await api<FavoritesResponse>('/api/favorites'))
    } catch {
      // leave local intact on failure
    }
  },

  async reconcileWithRemote() {
    if (!getToken()) return
    if (favoritesAreDirty()) {
      cancelPendingPush()
      await get().pushToRemote()
    } else {
      await get().loadFromRemote()
    }
  },

  async mergeWithRemote(productIds) {
    if (!getToken()) return
    const payload = productIds ?? get().ids
    const body: FavoritesReplaceRequest = { productIds: payload }
    try {
      adoptServerResult(await api<FavoritesResponse>('/api/favorites/merge', {
        method: 'POST',
        body: JSON.stringify(body),
      }))
    } catch {
      // ignore — keep local as-is
    }
  },

  async mergeAdditionsWithRemote() {
    return get().mergeWithRemote(additionsSinceBaseline(get().ids))
  },

  async pushToRemote() {
    if (!getToken()) return
    const snapshot = get().ids
    const body: FavoritesReplaceRequest = { productIds: snapshot }
    try {
      const res = await api<FavoritesResponse>('/api/favorites', {
        method: 'PUT',
        body: JSON.stringify(body),
      })
      // Adopt only if the user hasn't mutated since we snapshotted.
      if (sameSet(get().ids, snapshot)) {
        const serverIds = res.productIds ?? []
        const changed = (res.adjustments?.length ?? 0) > 0 || !sameSet(serverIds, snapshot)
        if (changed) adoptServerResult(res)
        else setBaseline(snapshot)
      }
    } catch (e) {
      // 401/403: token died — caller (authStore) handles auth state, keep set.
      // 5xx/network: transient, next mutation retries via debounce.
      // Other 4xx: optimistic state diverged — roll back by pulling.
      if (e instanceof ApiError
          && e.status >= 400 && e.status < 500
          && e.status !== 401 && e.status !== 403) {
        await get().loadFromRemote()
      }
    }
  },
}))

// Receive updates from other tabs and adopt without re-broadcasting (loop)
// or re-pushing (the originating tab already does it).
let lastExternalSignature: string | null = null

function applyIdsFromExternal(rawJson: string) {
  if (rawJson === lastExternalSignature) return
  let parsed: unknown
  try { parsed = JSON.parse(rawJson) } catch { return }
  if (!Array.isArray(parsed)) return
  lastExternalSignature = rawJson
  const ids = parsed.filter((n): n is number => typeof n === 'number')
  localStorage.setItem(STORAGE_KEY, rawJson)
  useFavorites.setState({ ids })
  cancelPendingPush()
}

if (channel) {
  channel.onmessage = (e: MessageEvent<FavoritesMessage>) => {
    const msg = e.data
    if (!msg || msg.type !== 'favorites-update' || !Array.isArray(msg.ids)) return
    applyIdsFromExternal(JSON.stringify(msg.ids))
  }
}

window.addEventListener('storage', (e) => {
  if (e.key !== STORAGE_KEY) return
  if (e.newValue === null) {
    if (lastExternalSignature === '[]') return
    lastExternalSignature = '[]'
    useFavorites.setState({ ids: [] })
    cancelPendingPush()
    return
  }
  applyIdsFromExternal(e.newValue)
})

// Cross-device sync: when the user returns to the tab, pull the canonical
// set from the server. Throttled so quick tab-flipping doesn't spam it.
const FOCUS_REFRESH_MIN_MS = 3000
let lastFocusRefreshAt = 0

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState !== 'visible') return
  if (!getToken()) return
  const now = Date.now()
  if (now - lastFocusRefreshAt < FOCUS_REFRESH_MIN_MS) return
  lastFocusRefreshAt = now
  void useFavorites.getState().reconcileWithRemote()
})

// Flush unpushed changes when the page goes away. Without this, a toggle
// made inside the 350 ms debounce window is lost if the user closes the
// tab before it fires. `keepalive` lets the PUT outlive the unloading
// document; `sendBeacon` can't because it can't set the Authorization
// header.
window.addEventListener('pagehide', () => {
  if (!getToken()) return
  if (!favoritesAreDirty()) return
  const body: FavoritesReplaceRequest = { productIds: useFavorites.getState().ids }
  void api('/api/favorites', { method: 'PUT', body: JSON.stringify(body), keepalive: true })
    .catch(() => { /* page is unloading; nothing to recover */ })
})
