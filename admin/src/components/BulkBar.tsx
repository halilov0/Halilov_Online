import type { ReactNode } from 'react'

/**
 * Floating action bar shown while one or more table rows are selected.
 * Renders nothing when {@code count} is 0, so a page can mount it
 * unconditionally. Action buttons are passed as children.
 */
export function BulkBar({
  count,
  onClear,
  children,
}: {
  count: number
  onClear: () => void
  children: ReactNode
}) {
  if (count === 0) return null
  return (
    <div className="hm-bulkbar" role="region" aria-label="פעולות על בחירה">
      <span className="hm-bulkbar-count">{count} נבחרו</span>
      <div className="hm-bulkbar-actions">{children}</div>
      <button className="hm-btn hm-btn-ghost" onClick={onClear} style={{ marginInlineStart: 'auto' }}>
        נקה בחירה
      </button>
    </div>
  )
}
