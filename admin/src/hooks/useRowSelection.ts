import { useCallback, useMemo, useRef, useState } from 'react'

/**
 * Table row multi-selection with Gmail-style shift-click range select.
 *
 * Pass the ids of the *currently visible* rows, in display order (after any
 * filtering/sorting). Range selection and "select all" operate over exactly
 * that order, so they always match what the operator sees.
 *
 * The clicked checkbox decides the direction: if it's becoming selected, a
 * shift-click selects the whole span to the previous anchor; if it's becoming
 * unselected, the span is cleared. The anchor is the last row toggled.
 *
 * @typeParam K - id type (usually `number`)
 */
export type RowSelection<K> = {
  /** Set of currently selected ids (only meaningful within `ids`). */
  selected: Set<K>
  /** Ordered array of selected ids that still exist in the visible set. */
  selectedList: K[]
  count: number
  isSelected: (id: K) => boolean
  /** Toggle a row. Pass `shiftKey` from the click event for range select. */
  toggle: (index: number, shiftKey: boolean) => void
  /** Header checkbox: select all visible if any are unselected, else clear. */
  toggleAll: () => void
  allSelected: boolean
  /** Some-but-not-all selected — drives the checkbox `indeterminate` flag. */
  someSelected: boolean
  clear: () => void
}

export function useRowSelection<K>(ids: K[]): RowSelection<K> {
  const [selected, setSelected] = useState<Set<K>>(new Set())
  const anchor = useRef<number | null>(null)

  const toggle = useCallback((index: number, shiftKey: boolean) => {
    const id = ids[index]
    if (id === undefined) return
    setSelected((prev) => {
      const next = new Set(prev)
      const willSelect = !prev.has(id)
      if (shiftKey && anchor.current !== null) {
        const lo = Math.min(anchor.current, index)
        const hi = Math.max(anchor.current, index)
        for (let i = lo; i <= hi; i++) {
          const rid = ids[i]
          if (rid === undefined) continue
          if (willSelect) next.add(rid)
          else next.delete(rid)
        }
      } else if (willSelect) {
        next.add(id)
      } else {
        next.delete(id)
      }
      return next
    })
    anchor.current = index
  }, [ids])

  const toggleAll = useCallback(() => {
    setSelected((prev) => {
      const allOn = ids.length > 0 && ids.every((id) => prev.has(id))
      if (allOn) return new Set<K>()
      return new Set(ids)
    })
    anchor.current = null
  }, [ids])

  const clear = useCallback(() => {
    setSelected(new Set<K>())
    anchor.current = null
  }, [])

  // Derive everything against the visible ids so deletions/filtering can't
  // leave phantom selections in the count or the bulk payload.
  const selectedList = useMemo(() => ids.filter((id) => selected.has(id)), [ids, selected])
  const count = selectedList.length
  const allSelected = ids.length > 0 && count === ids.length
  const someSelected = count > 0 && !allSelected

  return {
    selected,
    selectedList,
    count,
    isSelected: (id: K) => selected.has(id),
    toggle,
    toggleAll,
    allSelected,
    someSelected,
    clear,
  }
}
