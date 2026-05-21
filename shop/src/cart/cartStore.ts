import { create } from 'zustand'
import {
  api, getToken,
  type CartLineView, type CartReplaceRequest, type Product,
} from '../api'

const STORAGE_KEY = 'halilov.cart'
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

let pushTimer: number | null = null

function schedulePush(push: () => Promise<void>) {
  if (!getToken()) return
  if (pushTimer !== null) clearTimeout(pushTimer)
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
    schedulePush(() => get().pushToRemote())
  },

  setQty(productId, quantity) {
    const q = Math.max(1, Math.min(99, quantity))
    const lines = get().lines.map(l =>
      l.productId === productId ? { ...l, quantity: q } : l
    )
    save(lines)
    set({ lines })
    schedulePush(() => get().pushToRemote())
  },

  remove(productId) {
    const lines = get().lines.filter(l => l.productId !== productId)
    save(lines)
    set({ lines })
    schedulePush(() => get().pushToRemote())
  },

  clearLocal() {
    save([])
    set({ lines: [] })
  },

  async clearAll() {
    save([])
    set({ lines: [] })
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
    } catch {
      // ignore — debounced retry happens on next mutation
    }
  },

  totalItems() {
    return get().lines.reduce((sum, l) => sum + l.quantity, 0)
  },

  subtotalAgorot() {
    return get().lines.reduce((sum, l) => sum + l.priceAgorot * l.quantity, 0)
  },
}))
