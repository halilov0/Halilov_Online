import { create } from 'zustand'

/**
 * Toast queue. {@link useToast.push} enqueues a string that auto-clears
 * after 2.4 s. The container component (mounted near the app root)
 * renders the current entries. Deliberately minimal — no per-toast
 * type / severity until something actually needs to distinguish them.
 */
type ToastEntry = { id: number; text: string }

type ToastState = {
  toasts: ToastEntry[]
  push: (text: string) => void
  remove: (id: number) => void
}

let nextId = 1

export const useToast = create<ToastState>((set) => ({
  toasts: [],
  push: (text) => {
    const id = nextId++
    set((s) => ({ toasts: [...s.toasts, { id, text }] }))
    setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }))
    }, 2400)
  },
  remove: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}))

export function comingSoon(label?: string) {
  useToast.getState().push(label ? `${label} — בקרוב` : 'בקרוב')
}

export function ToastHost() {
  const toasts = useToast((s) => s.toasts)
  return (
    <div className="hm-toast-wrap" aria-live="polite">
      {toasts.map((t) => (
        <div key={t.id} className="hm-toast">{t.text}</div>
      ))}
    </div>
  )
}

