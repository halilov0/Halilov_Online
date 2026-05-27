import { create } from 'zustand'

/**
 * Favorites store. Pure-client, `localStorage`-only — no server
 * round-trip. Just a set of product IDs the user has hearted, with the
 * usual `has` / `toggle` / `remove` shape. Survives reload but not
 * across browsers; favorites graduate to the server only if a real
 * "wishlist" feature lands.
 */

const STORAGE_KEY = 'halilov.favorites'

type FavoritesState = {
  ids: number[]
  has: (productId: number) => boolean
  toggle: (productId: number) => boolean
  remove: (productId: number) => void
  clear: () => void
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

export const useFavorites = create<FavoritesState>((set, get) => ({
  ids: load(),

  has(productId) {
    return get().ids.includes(productId)
  },

  toggle(productId) {
    const cur = get().ids
    const exists = cur.includes(productId)
    const next = exists ? cur.filter(id => id !== productId) : [...cur, productId]
    save(next)
    set({ ids: next })
    return !exists
  },

  remove(productId) {
    const next = get().ids.filter(id => id !== productId)
    save(next)
    set({ ids: next })
  },

  clear() {
    save([])
    set({ ids: [] })
  },
}))
