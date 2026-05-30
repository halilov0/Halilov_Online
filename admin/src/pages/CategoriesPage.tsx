import { useEffect, useMemo, useState } from 'react'
import { api, type BulkResult, type Category, type CategoryUpsert } from '../api'
import { Field } from '../components/Field'
import { Icon } from '../components/Icon'
import { Checkbox } from '../components/Checkbox'
import { BulkBar } from '../components/BulkBar'
import { confirmDialog, confirmBulkDelete } from '../components/ConfirmDialog'
import { useToast } from '../components/Toast'
import { useRowSelection } from '../hooks/useRowSelection'

const emptyDraft: CategoryUpsert = { slug: '', nameHe: '', parentId: null, sortOrder: 0 }

export function CategoriesPage() {
  const [categories, setCategories] = useState<Category[]>([])
  const [editing, setEditing] = useState<Category | null>(null)
  const [creating, setCreating] = useState(false)
  const [draft, setDraft] = useState<CategoryUpsert>(emptyDraft)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const push = useToast(s => s.push)

  const categoryIds = useMemo(() => categories.map(c => c.id), [categories])
  const sel = useRowSelection<number>(categoryIds)

  function load() {
    setError(null)
    api<Category[]>('/api/categories').then(setCategories).catch(e => setError(e.message))
  }
  useEffect(load, [])

  function startCreate() { setEditing(null); setCreating(true); setDraft(emptyDraft); setError(null) }
  function startEdit(c: Category) {
    setCreating(false); setEditing(c)
    setDraft({ slug: c.slug, nameHe: c.nameHe, parentId: c.parentId, sortOrder: c.sortOrder })
    setError(null)
  }
  function cancel() { setEditing(null); setCreating(false); setError(null) }

  async function save(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true); setError(null)
    try {
      if (editing) {
        await api(`/api/admin/catalog/categories/${editing.id}`, { method: 'PUT', body: JSON.stringify(draft) })
      } else {
        await api('/api/admin/catalog/categories', { method: 'POST', body: JSON.stringify(draft) })
      }
      cancel(); load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    } finally {
      setBusy(false)
    }
  }

  async function remove(c: Category) {
    const ok = await confirmDialog({
      title: 'מחיקת קטגוריה',
      message: `למחוק "${c.nameHe}"? מוצרים שמשויכים יישארו ללא קטגוריה.`,
      confirmLabel: 'מחק', danger: true,
    })
    if (!ok) return
    try {
      await api(`/api/admin/catalog/categories/${c.id}`, { method: 'DELETE' })
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    }
  }

  async function bulkDelete() {
    const n = sel.count
    if (n === 0) return
    const ok = await confirmBulkDelete(n, {
      title: `מחיקת ${n} קטגוריות`,
      message: 'מוצרים שמשויכים לקטגוריות אלו יישארו ללא קטגוריה.',
    })
    if (!ok) return
    try {
      const res = await api<BulkResult>('/api/admin/catalog/categories/bulk-delete', {
        method: 'POST', body: JSON.stringify({ ids: sel.selectedList }),
      })
      push(`${res.affected} קטגוריות נמחקו`)
      sel.clear(); load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    }
  }

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 18 }}>
        <div>
          <h1>קטגוריות</h1>
          <div className="sub">{categories.length} קטגוריות</div>
        </div>
        {!creating && !editing && (
          <button className="hm-btn hm-btn-primary" onClick={startCreate}>+ קטגוריה חדשה</button>
        )}
      </div>

      {error && <div className="hm-error" style={{ marginBottom: 14 }}>{error}</div>}

      {(creating || editing) && (
        <form onSubmit={save} className="adm-card" style={{ marginBottom: 14, display: 'grid', gap: 14 }}>
          <h3 style={{ fontFamily: 'var(--serif)', fontSize: 18 }}>
            {editing ? `עריכת ${editing.nameHe}` : 'קטגוריה חדשה'}
          </h3>
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr', gap: 12 }}>
            <Field label="שם בעברית" required value={draft.nameHe} onChange={e => setDraft({ ...draft, nameHe: e.target.value })} />
            <Field label="Slug" required mono value={draft.slug} onChange={e => setDraft({ ...draft, slug: e.target.value })} />
            <Field label="סדר" type="number" min={0} mono value={draft.sortOrder}
                   onChange={e => setDraft({ ...draft, sortOrder: Number(e.target.value) })} />
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button type="submit" className="hm-btn hm-btn-primary" disabled={busy}>
              {busy ? 'שומר…' : 'שמירה'}
            </button>
            <button type="button" className="hm-btn hm-btn-quiet" onClick={cancel}>ביטול</button>
          </div>
        </form>
      )}

      <table className="adm-table">
        <thead>
          <tr>
            <th className="sel">
              <Checkbox checked={sel.allSelected} indeterminate={sel.someSelected}
                        onClick={() => sel.toggleAll()} ariaLabel="בחר הכל" />
            </th>
            <th style={{ width: 80 }}>סדר</th>
            <th>שם</th>
            <th>Slug</th>
            <th style={{ width: 140 }}></th>
          </tr>
        </thead>
        <tbody>
          {categories.map((c, index) => (
            <tr key={c.id} className={sel.isSelected(c.id) ? 'selected' : ''}>
              <td className="sel">
                <Checkbox checked={sel.isSelected(c.id)}
                          onClick={(e) => sel.toggle(index, e.shiftKey)}
                          ariaLabel={`בחר ${c.nameHe}`} />
              </td>
              <td className="num">{c.sortOrder}</td>
              <td style={{ fontWeight: 500 }}>{c.nameHe}</td>
              <td className="num" style={{ color: 'var(--ink-3)' }}>{c.slug}</td>
              <td style={{ display: 'flex', gap: 6 }}>
                <button className="hm-btn hm-btn-quiet" style={{ padding: '5px 12px', fontSize: 12 }} onClick={() => startEdit(c)}>עריכה</button>
                <button className="hm-icon-btn" style={{ width: 28, height: 28 }} onClick={() => remove(c)} aria-label="מחיקה">
                  <Icon name="trash" size={14} />
                </button>
              </td>
            </tr>
          ))}
          {categories.length === 0 && (
            <tr><td colSpan={5} style={{ textAlign: 'center', color: 'var(--ink-3)', padding: 30 }}>אין קטגוריות.</td></tr>
          )}
        </tbody>
      </table>

      <BulkBar count={sel.count} onClear={sel.clear}>
        <button className="hm-btn hm-btn-danger" onClick={bulkDelete}>מחיקת נבחרים</button>
      </BulkBar>
    </>
  )
}
