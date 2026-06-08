import { create } from 'zustand'
import {
  api, ApiError, getToken,
  type CartAdjustment, type CartLineView, type CartReplaceRequest,
  type CartResponse, type CartUpsertItem, type CouponValidateResponse, type Product,
} from '../api'
import { useToast } from '../components/Toast'

/**
 * Shop cart store. Server-backed for signed-in users, localStorage-only
 * for guests.
 *
 * Mutations are optimistic on the local lines and debounced into a
 * single `PUT /api/cart` (350 ms). Cross-tab updates fan out via
 * `BroadcastChannel` (with a `storage`-event fallback). On tab
 * visibility change the cart is *reconciled* (throttled): unpushed
 * local changes are flushed, otherwise the canonical cart is pulled —
 * so a mutation on another device propagates without clobbering one
 * this device hasn't synced yet. On `pagehide` a dirty cart does a
 * final `keepalive` flush so a change made inside the debounce window
 * survives a tab close.
 *
 * Every server read/write returns a `CartResponse` carrying any
 * adjustments the server made (stock clamp / inactive-product drop).
 * They're toasted via {@link useToast} and kept in `adjustments` for
 * the cart page to show inline (banner for drops, per-line note for
 * clamps), so the cart never mutates silently.
 *
 * Failure handling: 401/403 don't touch the cart (the auth store
 * deals with it). 5xx and network errors retry on the next mutation.
 * Other 4xx mean the optimistic local state diverged from what the
 * server will accept — we roll back by adopting the canonical cart.
 */

const STORAGE_KEY = 'halilov.cart'
// The applied coupon code, persisted so it survives the cart → checkout
// navigation (and a refresh). Only the code is stored; the validated discount
// is re-fetched against the live subtotal, never trusted from localStorage.
const COUPON_KEY = 'halilov.cart.coupon'
// Records which signed-in user the persisted cart belongs to. Lets login
// reconciliation tell a genuine guest cart (fold into the account) apart from
// the residue of an expired session — whose cart already lives on the server,
// so merging it would sum the local copy into its own source and double every
// line. Survives a silent token expiry because it's a separate key the auth
// logout path clears explicitly.
const OWNER_KEY = 'halilov.cart.owner'
// Snapshot (productId → quantity) of the cart the last time it provably matched
// the server. Lets login reconciliation merge only what changed *since* that
// point: residue already in the DB isn't re-summed (no doubling), while items
// added during a logged-out window (token expired but not yet re-logged-in)
// aren't in the baseline, so they still get folded in (no silent loss).
const BASELINE_KEY = 'halilov.cart.baseline'
const BROADCAST_CHANNEL = 'halilov.cart'
// Coalesce rapid +/- clicks into a single PUT to avoid spamming the backend.
const PUSH_DEBOUNCE_MS = 350

export type CartLine = {
  productId: number
  slug: string
  nameHe: string
  priceAgorot: number
  quantity: number
  imageUrl: string | null
}

type CartState = {
  lines: CartLine[]
  /** Server-side changes (clamp / drop) the user hasn't acknowledged yet, kept
   *  for inline display on the cart page. Cleared when the user mutates that
   *  line or dismisses the notice. Page-scoped — not persisted. */
  adjustments: CartAdjustment[]
  add: (p: Product, quantity?: number) => void
  setQty: (productId: number, quantity: number) => void
  remove: (productId: number) => void
  /** Dismiss an inline adjustment notice once the user has seen it. */
  dismissAdjustment: (productId: number) => void
  /** Wipe local lines only. Use clearAll() to also drop the server cart. */
  clearLocal: () => void
  /** Wipe local + server cart. Use after a successful checkout. */
  clearAll: () => Promise<void>
  /** Adopt the canonical server cart, clobbering local. Used for rollback and
   *  the post-login user-switch — callers that may hold unpushed local changes
   *  should use {@link reconcileWithRemote} instead. */
  loadFromRemote: () => Promise<void>
  /** Focus/mount sync: if local has unpushed changes, flush them (they're the
   *  newer intent on the device the user is looking at); otherwise pull the
   *  canonical cart. Prevents a stale GET from clobbering a local mutation. */
  reconcileWithRemote: () => Promise<void>
  /** Merge items into the server cart and adopt the result. Defaults to the
   *  whole local cart; pass a subset to merge only those lines. */
  mergeWithRemote: (items?: CartUpsertItem[]) => Promise<void>
  /** Merge only the lines added/increased since the cart last matched the
   *  server. Avoids re-summing residue already in the DB while still folding
   *  in items added during a logged-out window. */
  mergeAdditionsWithRemote: () => Promise<void>
  /** Best-effort flush of current local state to the server. */
  pushToRemote: () => Promise<void>
  totalItems: () => number
  subtotalAgorot: () => number

  // ---- coupon ----
  /** The applied coupon code (persisted), or null. */
  couponCode: string | null
  /** Last successful validation against the live subtotal, or null when the
   *  code currently doesn't apply (e.g. min-subtotal not met). */
  coupon: CouponValidateResponse | null
  /** Why the last apply/revalidate failed, for inline display. */
  couponError: string | null
  /** Validate `code` against the current subtotal and apply it. Returns whether
   *  it stuck. */
  applyCoupon: (code: string) => Promise<boolean>
  /** Drop the applied coupon entirely. */
  removeCoupon: () => void
  /** Re-validate the persisted code against the live subtotal. Keeps the code
   *  but clears the active discount when it no longer applies, so it re-applies
   *  if the cart grows. No-op when no code is set. */
  revalidateCoupon: () => Promise<void>
}

function load(): CartLine[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.map((l: Partial<CartLine>) => ({
      productId: l.productId!,
      slug: l.slug!,
      nameHe: l.nameHe!,
      priceAgorot: l.priceAgorot!,
      quantity: l.quantity!,
      imageUrl: l.imageUrl ?? null,
    }))
  } catch {
    return []
  }
}

function save(lines: CartLine[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(lines))
}

function loadCouponCode(): string | null {
  return localStorage.getItem(COUPON_KEY)
}

function saveCouponCode(code: string | null) {
  if (code) localStorage.setItem(COUPON_KEY, code)
  else localStorage.removeItem(COUPON_KEY)
}

/** POST /api/coupons/validate for `code` at `subtotalAgorot`. */
function validateCoupon(code: string, subtotalAgorot: number): Promise<CouponValidateResponse> {
  return api<CouponValidateResponse>('/api/coupons/validate', {
    method: 'POST',
    body: JSON.stringify({ code, subtotalAgorot }),
  })
}

/** userId the persisted cart is synced with, or null for a guest cart. */
export function getCartOwner(): number | null {
  const raw = localStorage.getItem(OWNER_KEY)
  if (!raw) return null
  const n = Number(raw)
  return Number.isInteger(n) ? n : null
}

/** Tag the persisted cart as owned by `userId`, or clear the tag (null). */
export function setCartOwner(userId: number | null) {
  if (userId == null) localStorage.removeItem(OWNER_KEY)
  else localStorage.setItem(OWNER_KEY, String(userId))
}

/** Last server-confirmed cart as a productId → quantity map (empty if none). */
function getBaseline(): Record<number, number> {
  try {
    const raw = localStorage.getItem(BASELINE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed as Record<number, number> : {}
  } catch {
    return {}
  }
}

/** Record the current lines as the server-confirmed baseline. */
function setBaseline(lines: CartLine[]) {
  const map: Record<number, number> = {}
  for (const l of lines) map[l.productId] = l.quantity
  localStorage.setItem(BASELINE_KEY, JSON.stringify(map))
}

/** Drop the baseline (logout — the next session starts with no history). */
export function clearCartBaseline() {
  localStorage.removeItem(BASELINE_KEY)
}

/**
 * Lines added or increased since the cart last matched the server, as merge
 * items. New products and quantity bumps yield a positive delta; removals and
 * decrements are omitted (we never push a removal through /merge, so the DB
 * keeps the line — losing an item is worse than keeping a stale one). For a
 * guest cart the baseline is empty, so every line is returned.
 */
function additionsSinceBaseline(lines: CartLine[]): CartUpsertItem[] {
  const base = getBaseline()
  const out: CartUpsertItem[] = []
  for (const l of lines) {
    const diff = l.quantity - (base[l.productId] ?? 0)
    if (diff > 0) out.push({ productId: l.productId, quantity: diff })
  }
  return out
}

function toCartLine(v: CartLineView): CartLine {
  return {
    productId: v.productId,
    slug: v.slug,
    nameHe: v.nameHe,
    priceAgorot: v.priceAgorot,
    quantity: v.quantity,
    imageUrl: v.imageUrl,
  }
}

// ---------- cross-tab sync ----------

type CartMessage = { type: 'cart-update'; lines: CartLine[] }

let channel: BroadcastChannel | null = null
try {
  // Older Safari versions lack BroadcastChannel; degrade silently.
  channel = typeof BroadcastChannel !== 'undefined'
    ? new BroadcastChannel(BROADCAST_CHANNEL)
    : null
} catch {
  channel = null
}

function broadcastUpdate(lines: CartLine[]) {
  if (!channel) return
  try { channel.postMessage({ type: 'cart-update', lines } satisfies CartMessage) } catch { /* ignore */ }
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

/** Flush a pending debounced push without firing it — used by logout so a
 *  stray timer can't push into a session we're tearing down. */
export function cancelPendingCartPush() {
  cancelPendingPush()
}

// ---------- dirty tracking / server-result adoption ----------

/** True when local lines exactly equal the last server-confirmed baseline. */
function matchesBaseline(lines: CartLine[]): boolean {
  const base = getBaseline()
  if (Object.keys(base).length !== lines.length) return false
  for (const l of lines) {
    if (base[l.productId] !== l.quantity) return false
  }
  return true
}

/** Local has changes the server hasn't confirmed (or a push is mid-flight). */
function cartIsDirty(): boolean {
  return pushTimer !== null || !matchesBaseline(useCart.getState().lines)
}

/** Same set of (productId → quantity)? Used to detect whether the user mutated
 *  the cart while a push was in flight. */
function sameQuantities(a: CartLine[], b: CartLine[]): boolean {
  if (a.length !== b.length) return false
  const m = new Map(a.map(l => [l.productId, l.quantity]))
  for (const l of b) {
    if (m.get(l.productId) !== l.quantity) return false
  }
  return true
}

// Removals are terminal (the product is gone), so a focus refresh could report
// the same dead line every time — dedupe by productId for the page's lifetime
// so the user is told once, not on every tab focus.
const toastedRemovals = new Set<number>()

/** Surface server-side cart changes the user didn't ask for, so nothing
 *  mutates silently. Clamps always toast (they follow a user action);
 *  removals toast once each. */
function notifyAdjustments(adjustments: CartAdjustment[] | undefined) {
  if (!adjustments?.length) return
  const push = useToast.getState().push
  for (const a of adjustments) {
    const name = a.nameHe ?? 'פריט'
    if (a.type === 'REMOVED') {
      if (toastedRemovals.has(a.productId)) continue
      toastedRemovals.add(a.productId)
      push(`${name} הוסר מהעגלה — אינו זמין במלאי`)
    } else {
      push(`${name}: הכמות עודכנה ל-${a.finalQty} (זה המלאי שנותר)`)
    }
  }
}

/** Drop the notice for a product the user just acted on (returns the same ref
 *  when there's nothing to drop, so we don't churn a new array needlessly). */
function dropAdjustment(list: CartAdjustment[], productId: number): CartAdjustment[] {
  return list.some(a => a.productId === productId)
    ? list.filter(a => a.productId !== productId)
    : list
}

/** Fold new server adjustments into the kept set, keyed by productId (a fresh
 *  notice for a product replaces the previous one). */
function mergeAdjustmentLists(existing: CartAdjustment[], incoming: CartAdjustment[] | undefined): CartAdjustment[] {
  if (!incoming?.length) return existing
  const byId = new Map<number, CartAdjustment>()
  for (const a of existing) byId.set(a.productId, a)
  for (const a of incoming) byId.set(a.productId, a)
  return [...byId.values()]
}

/** Adopt the canonical cart the server returned: mirror it locally, fan out to
 *  other tabs, refresh the baseline, keep the adjustments for inline display,
 *  and toast them. */
function adoptServerResult(res: CartResponse) {
  const lines = (res.lines ?? []).map(toCartLine)
  const adjustments = mergeAdjustmentLists(useCart.getState().adjustments, res.adjustments)
  save(lines)
  useCart.setState({ lines, adjustments })
  broadcastUpdate(lines)
  setBaseline(lines)
  notifyAdjustments(res.adjustments)
}

export const useCart = create<CartState>((set, get) => ({
  lines: load(),
  adjustments: [],
  couponCode: loadCouponCode(),
  coupon: null,
  couponError: null,

  add(p, quantity = 1) {
    const lines = [...get().lines]
    const existing = lines.find(l => l.productId === p.id)
    if (existing) {
      existing.quantity = Math.min(99, existing.quantity + quantity)
    } else {
      lines.push({
        productId: p.id,
        slug: p.slug,
        nameHe: p.nameHe,
        priceAgorot: p.priceAgorot,
        quantity: Math.min(99, quantity),
        imageUrl: p.imageUrl,
      })
    }
    save(lines)
    // Acting on a line clears its stale notice (the user has re-engaged).
    set({ lines, adjustments: dropAdjustment(get().adjustments, p.id) })
    broadcastUpdate(lines)
    schedulePush(() => get().pushToRemote())
  },

  setQty(productId, quantity) {
    const q = Math.max(1, Math.min(99, quantity))
    const lines = get().lines.map(l =>
      l.productId === productId ? { ...l, quantity: q } : l
    )
    save(lines)
    set({ lines, adjustments: dropAdjustment(get().adjustments, productId) })
    broadcastUpdate(lines)
    schedulePush(() => get().pushToRemote())
  },

  remove(productId) {
    const lines = get().lines.filter(l => l.productId !== productId)
    save(lines)
    set({ lines, adjustments: dropAdjustment(get().adjustments, productId) })
    broadcastUpdate(lines)
    schedulePush(() => get().pushToRemote())
  },

  dismissAdjustment(productId) {
    set({ adjustments: dropAdjustment(get().adjustments, productId) })
  },

  clearLocal() {
    save([])
    saveCouponCode(null)
    set({ lines: [], adjustments: [], couponCode: null, coupon: null, couponError: null })
    broadcastUpdate([])
  },

  async clearAll() {
    save([])
    saveCouponCode(null)
    set({ lines: [], adjustments: [], couponCode: null, coupon: null, couponError: null })
    broadcastUpdate([])
    cancelPendingPush()
    if (!getToken()) return
    try {
      await api('/api/cart', { method: 'DELETE' })
      setBaseline([]) // server cart is now empty — keep the baseline in step
    } catch {
      // ignore — local is the user-visible truth, server will catch up next push
    }
  },

  async loadFromRemote() {
    if (!getToken()) return
    try {
      adoptServerResult(await api<CartResponse>('/api/cart'))
    } catch {
      // leave local intact on failure
    }
  },

  async reconcileWithRemote() {
    if (!getToken()) return
    if (cartIsDirty()) {
      // Local holds unpushed changes (or a debounced push is queued). Flush
      // them rather than pulling — a stale GET would clobber the user's work.
      cancelPendingPush()
      await get().pushToRemote()
    } else {
      await get().loadFromRemote()
    }
  },

  async mergeWithRemote(items) {
    if (!getToken()) return
    const payload = items ?? get().lines.map(l => ({ productId: l.productId, quantity: l.quantity }))
    const body: CartReplaceRequest = { items: payload }
    try {
      adoptServerResult(await api<CartResponse>('/api/cart/merge', {
        method: 'POST',
        body: JSON.stringify(body),
      }))
    } catch {
      // ignore — keep local cart as-is
    }
  },

  async mergeAdditionsWithRemote() {
    return get().mergeWithRemote(additionsSinceBaseline(get().lines))
  },

  async pushToRemote() {
    if (!getToken()) return
    const snapshot = get().lines
    const items = snapshot.map(l => ({ productId: l.productId, quantity: l.quantity }))
    const body: CartReplaceRequest = { items }
    try {
      const res = await api<CartResponse>('/api/cart', {
        method: 'PUT',
        body: JSON.stringify(body),
      })
      // Adopt the canonical result only if the user hasn't mutated since we
      // snapshotted. If they have, a newer debounced push is already queued and
      // will reconcile (and report its own adjustments) — adopting this stale
      // response would clobber the newer local state.
      if (sameQuantities(get().lines, snapshot)) {
        const serverLines = (res.lines ?? []).map(toCartLine)
        const changed = (res.adjustments?.length ?? 0) > 0 || !sameQuantities(serverLines, snapshot)
        // Common case: server accepted our cart verbatim — just refresh the
        // baseline, no re-render/broadcast churn. Only adopt (and toast) when
        // the server actually changed something.
        if (changed) adoptServerResult(res)
        else setBaseline(snapshot)
      }
    } catch (e) {
      // 401/403: token died — caller (authStore) handles auth state, keep cart.
      // 5xx/network: transient, next mutation will retry via debounce.
      // Other 4xx (400/404/409/422): request is wrong; the optimistic local
      // state diverged from what the server will accept. Roll back by pulling
      // the canonical cart and adopting it.
      if (e instanceof ApiError
          && e.status >= 400 && e.status < 500
          && e.status !== 401 && e.status !== 403) {
        await get().loadFromRemote()
      }
    }
  },

  totalItems() {
    return get().lines.reduce((sum, l) => sum + l.quantity, 0)
  },

  subtotalAgorot() {
    return get().lines.reduce((sum, l) => sum + l.priceAgorot * l.quantity, 0)
  },

  async applyCoupon(code) {
    const c = code.trim()
    if (!c) return false
    try {
      const res = await validateCoupon(c, get().subtotalAgorot())
      saveCouponCode(res.code)
      set({ couponCode: res.code, coupon: res, couponError: null })
      return true
    } catch (e) {
      set({ couponError: e instanceof Error ? e.message : 'קוד לא תקין' })
      return false
    }
  },

  removeCoupon() {
    saveCouponCode(null)
    set({ couponCode: null, coupon: null, couponError: null })
  },

  async revalidateCoupon() {
    const code = get().couponCode
    if (!code) return
    const subtotal = get().subtotalAgorot()
    if (subtotal === 0) { set({ coupon: null }); return }
    try {
      set({ coupon: await validateCoupon(code, subtotal), couponError: null })
    } catch (e) {
      // The code no longer applies at this subtotal (or expired/exhausted).
      // Keep the code so it re-applies if the cart grows, but drop the discount.
      set({ coupon: null, couponError: e instanceof Error ? e.message : 'הקוד אינו תקף יותר' })
    }
  },
}))

// Receive cart updates from other tabs and adopt their state without
// re-broadcasting (loop) or re-pushing (the originating tab already does it).
//
// Two listeners for redundancy:
//   - BroadcastChannel: low-latency, modern browsers.
//   - StorageEvent: free fallback — fires in every other same-origin tab
//     whenever localStorage changes (we save() on every mutation anyway).
//
// applyLinesFromExternal() dedupes via a stringified signature so a second
// listener firing with the same payload is a no-op.
let lastExternalSignature: string | null = null

function applyLinesFromExternal(rawJson: string) {
  if (rawJson === lastExternalSignature) return
  let parsed: unknown
  try { parsed = JSON.parse(rawJson) } catch { return }
  if (!Array.isArray(parsed)) return
  lastExternalSignature = rawJson
  const lines = parsed as CartLine[]
  // Sync localStorage so a subsequent reload reflects the latest state even
  // if BroadcastChannel was the path that got us here.
  localStorage.setItem(STORAGE_KEY, rawJson)
  useCart.setState({ lines })
  cancelPendingPush()
}

if (channel) {
  channel.onmessage = (e: MessageEvent<CartMessage>) => {
    const msg = e.data
    if (!msg || msg.type !== 'cart-update' || !Array.isArray(msg.lines)) return
    applyLinesFromExternal(JSON.stringify(msg.lines))
  }
}

window.addEventListener('storage', (e) => {
  if (e.key !== STORAGE_KEY) return
  if (e.newValue === null) {
    // Other tab cleared the cart (logout / checkout).
    if (lastExternalSignature === '[]') return
    lastExternalSignature = '[]'
    useCart.setState({ lines: [] })
    cancelPendingPush()
    return
  }
  applyLinesFromExternal(e.newValue)
})

// Cross-device sync: when the user returns to the tab (switching from
// phone → PC or vice versa), pull the canonical cart from the server.
// Throttled so quick tab-flipping doesn't spam the backend.
const FOCUS_REFRESH_MIN_MS = 3000
let lastFocusRefreshAt = 0

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState !== 'visible') return
  if (!getToken()) return
  const now = Date.now()
  if (now - lastFocusRefreshAt < FOCUS_REFRESH_MIN_MS) return
  lastFocusRefreshAt = now
  void useCart.getState().reconcileWithRemote()
})

// Flush unpushed changes when the page is going away (tab close, navigation,
// mobile backgrounding). Without this, a mutation made inside the 350 ms
// debounce window is lost if the user closes the tab before it fires — and on
// the next visit the mount GET would adopt the stale server cart over it.
//
// `keepalive` lets the PUT outlive the unloading document; `sendBeacon` can't
// because it can't set the Authorization header. Fire-and-forget: once we're
// leaving, the response is irrelevant.
window.addEventListener('pagehide', () => {
  if (!getToken()) return
  if (!cartIsDirty()) return
  const items = useCart.getState().lines.map(l => ({ productId: l.productId, quantity: l.quantity }))
  const body: CartReplaceRequest = { items }
  void api('/api/cart', { method: 'PUT', body: JSON.stringify(body), keepalive: true })
    .catch(() => { /* page is unloading; nothing to recover */ })
})
