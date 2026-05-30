import { useEffect, useRef, useState } from 'react'
import { create } from 'zustand'

/**
 * Options for a single confirmation prompt. When {@link ConfirmOptions.challenge}
 * is set the confirm button stays disabled until the operator types that exact
 * string — the "type-to-confirm" safety net for large/irreversible actions.
 */
export type ConfirmOptions = {
  title: string
  message?: string
  confirmLabel?: string
  cancelLabel?: string
  danger?: boolean
  /** If set, the user must type this exactly to arm the confirm button. */
  challenge?: string
  /** Hint shown above the challenge input (defaults to a generic prompt). */
  challengeHint?: string
}

type Pending = ConfirmOptions & { resolve: (ok: boolean) => void }

type ConfirmState = {
  current: Pending | null
  ask: (opts: ConfirmOptions) => Promise<boolean>
  settle: (ok: boolean) => void
}

const useConfirmStore = create<ConfirmState>((set, get) => ({
  current: null,
  ask: (opts) =>
    new Promise<boolean>((resolve) => {
      // If a prompt is already open, reject the previous one so its caller
      // unblocks rather than hanging forever.
      const prev = get().current
      if (prev) prev.resolve(false)
      set({ current: { ...opts, resolve } })
    }),
  settle: (ok) => {
    const cur = get().current
    if (!cur) return
    cur.resolve(ok)
    set({ current: null })
  },
}))

/**
 * Imperative confirm — returns a promise that resolves `true` on confirm,
 * `false` on cancel/dismiss. Drop-in replacement for the native `confirm()`
 * with consistent styling and an optional type-to-confirm challenge.
 *
 * ```ts
 * if (await confirmDialog({ title: 'למחוק?', danger: true })) { ... }
 * ```
 */
export function confirmDialog(opts: ConfirmOptions): Promise<boolean> {
  return useConfirmStore.getState().ask(opts)
}

/** Above this many rows, a bulk delete demands type-to-confirm. */
export const BULK_TYPE_CONFIRM_THRESHOLD = 5

/**
 * Standard danger confirm for a bulk delete of {@code count} rows. Small
 * selections get a one-click confirm; selections past
 * {@link BULK_TYPE_CONFIRM_THRESHOLD} require typing the count to arm the
 * button. The caller supplies the localized title/message.
 */
export function confirmBulkDelete(
  count: number,
  opts: { title: string; message?: string },
): Promise<boolean> {
  return confirmDialog({
    title: opts.title,
    message: opts.message,
    confirmLabel: `מחק ${count}`,
    danger: true,
    challenge: count > BULK_TYPE_CONFIRM_THRESHOLD ? String(count) : undefined,
    challengeHint: `הקלידו ${count} לאישור מחיקה`,
  })
}

/** Mounts the single confirm modal. Render once near the app root. */
export function ConfirmHost() {
  const current = useConfirmStore((s) => s.current)
  const settle = useConfirmStore((s) => s.settle)
  const [typed, setTyped] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  // Reset the challenge field whenever a new prompt opens, and move focus to
  // the most relevant control.
  useEffect(() => {
    setTyped('')
    if (current) {
      const t = setTimeout(() => inputRef.current?.focus(), 30)
      return () => clearTimeout(t)
    }
  }, [current])

  // Esc cancels, Enter confirms (when armed).
  useEffect(() => {
    if (!current) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') settle(false)
      if (e.key === 'Enter' && armed) settle(true)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [current, typed])

  if (!current) return null

  const armed = !current.challenge || typed.trim() === current.challenge
  const confirmCls = `hm-btn ${current.danger ? 'hm-btn-danger' : 'hm-btn-primary'}`

  return (
    <div className="hm-modal-overlay" onMouseDown={() => settle(false)}>
      <div className="hm-modal" onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <h3 className="hm-modal-title">{current.title}</h3>
        {current.message && <p className="hm-modal-body">{current.message}</p>}

        {current.challenge && (
          <div className="hm-field" style={{ marginTop: 6 }}>
            <label>{current.challengeHint ?? `הקלידו "${current.challenge}" לאישור`}</label>
            <input
              ref={inputRef}
              className="hm-input mono"
              value={typed}
              onChange={(e) => setTyped(e.target.value)}
              placeholder={current.challenge}
              autoComplete="off"
              spellCheck={false}
            />
          </div>
        )}

        <div className="hm-modal-actions">
          <button className="hm-btn hm-btn-quiet" onClick={() => settle(false)}>
            {current.cancelLabel ?? 'ביטול'}
          </button>
          <button className={confirmCls} disabled={!armed} onClick={() => settle(true)}>
            {current.confirmLabel ?? 'אישור'}
          </button>
        </div>
      </div>
    </div>
  )
}
