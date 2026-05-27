import { create } from 'zustand'
import { api, type DeliveryConfig } from '../api'

/**
 * Server-mirrored delivery config (flat courier rate, free-above
 * threshold). Single source of truth lives on the backend; this store
 * caches it for the session. Defaults match the backend `app.delivery.*`
 * values so the first paint is correct before `load()` resolves.
 */
type DeliveryConfigState = DeliveryConfig & {
  loaded: boolean
  load: () => Promise<void>
}

// Defaults mirror backend `app.delivery.{courierFlatAgorot,freeAboveAgorot}`
// so first paint is correct before the boot fetch resolves. The store
// snaps to server values on load() — the server is authoritative.
export const useDeliveryConfig = create<DeliveryConfigState>((set, get) => ({
  courierFlatAgorot: 1990,
  freeAboveAgorot: 30000,
  loaded: false,
  async load() {
    if (get().loaded) return
    try {
      const cfg = await api<DeliveryConfig>('/api/delivery/config')
      set({ ...cfg, loaded: true })
    } catch {
      // Keep defaults; the next page interaction can retry implicitly.
    }
  },
}))
