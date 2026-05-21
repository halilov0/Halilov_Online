import { create } from 'zustand'
import {
  api, ApiError, getToken,
  type CartLineView, type CartReplaceRequest, type Product,
} from '../api'

const STORAGE_KEY = 'halilov.cart'
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
  add: (p: Product, quantity?: number) => void
  setQty: (productId: number, quantity: number) => void
  remove: (productId: number) => void
  /** Wipe local lines only. Use clearAll() to also drop the server cart. */
  clearLocal: () => void
  /** Wipe local + server cart. Use after a successful checkout. */
  clearAll: () => Promise<void>
  /** Pull canonical cart from the server (call on App mount with valid token). */
  loadFromRemote: () => Promise<void>
  /** Merge local lines into the server cart and adopt the merged result. */
  mergeWithRemote: () => Promise<void>
  /** Best-effort flush of current local state to the server. */
  pushToRemote: () => Promise<void>
  totalItems: () => number
  subtotalAgorot: () => number
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

export const useCart = create<CartState>((set, get) => ({
  lines: load(),

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
    set({ lines })
    broadcastUpdate(lines)
    schedulePush(() => get().pushToRemote())
  },

  setQty(productId, quantity) {
    const q = Math.max(1, Math.min(99, quantity))
    const lines = get().lines.map(l =>
      l.productId === productId ? { ...l, quantity: q } : l
    )
    save(lines)
    set({ lines })
    broadcastUpdate(lines)
    schedulePush(() => get().pushToRemote())
  },

  remove(productId) {
    const lines = get().lines.filter(l => l.productId !== productId)
    save(lines)
    set({ lines })
    broadcastUpdate(lines)
    schedulePush(() => get().pushToRemote())
  },

  clearLocal() {
    save([])
    set({ lines: [] })
    broadcastUpdate([])
  },

  async clearAll() {
    save([])
    set({ lines: [] })
    broadcastUpdate([])
    cancelPendingPush()
    if (!getToken()) return
    try {
      await api('/api/cart', { method: 'DELETE' })
    } catch {
      // ignore — local is the user-visible truth, server will catch up next push
    }
  },

  async loadFromRemote() {
    if (!getToken()) return
    try {
      const remote = await api<CartLineView[]>('/api/cart')
      const lines = remote.map(toCartLine)
      save(lines)
      set({ lines })
      broadcastUpdate(lines)
    } catch {
      // leave local intact on failure
    }
  },

  async mergeWithRemote() {
    if (!getToken()) return
    const items = get().lines.map(l => ({ productId: l.productId, quantity: l.quantity }))
    const body: CartReplaceRequest = { items }
    try {
      const merged = await api<CartLineView[]>('/api/cart/merge', {
        method: 'POST',
        body: JSON.stringify(body),
      })
      const lines = merged.map(toCartLine)
      save(lines)
      set({ lines })
      broadcastUpdate(lines)
    } catch {
      // ignore — keep local cart as-is
    }
  },

  async pushToRemote() {
    if (!getToken()) return
    const items = get().lines.map(l => ({ productId: l.productId, quantity: l.quantity }))
    const body: CartReplaceRequest = { items }
    try {
      await api('/api/cart', {
        method: 'PUT',
        body: JSON.stringify(body),
      })
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
  useCart.getState().loadFromRemote()
})
