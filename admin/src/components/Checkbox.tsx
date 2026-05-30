import { useEffect, useRef } from 'react'

/**
 * Controlled checkbox used for table-row selection.
 *
 * Selection logic lives in {@link import('../hooks/useRowSelection')}, so the
 * actual toggle happens in `onClick` — that's the only handler that carries
 * `shiftKey` for range-select. `checked` is fully controlled, and `onChange`
 * is a no-op purely to keep React from warning about a controlled input.
 */
export function Checkbox({
  checked,
  indeterminate,
  onClick,
  ariaLabel,
}: {
  checked: boolean
  indeterminate?: boolean
  onClick?: (e: React.MouseEvent<HTMLInputElement>) => void
  ariaLabel?: string
}) {
  const ref = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (ref.current) ref.current.indeterminate = !!indeterminate && !checked
  }, [indeterminate, checked])

  return (
    <input
      ref={ref}
      type="checkbox"
      className="hm-check"
      checked={checked}
      onChange={() => {}}
      onClick={onClick}
      aria-label={ariaLabel}
    />
  )
}
