import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, downloadFile, formatPrice, type BulkResult, type Category, type Product, type ProductUpsert } from '../api'
import { Field } from '../components/Field'
import { Icon } from '../components/Icon'
import { Checkbox } from '../components/Checkbox'
import { BulkBar } from '../components/BulkBar'
import { confirmDialog, confirmBulkDelete } from '../components/ConfirmDialog'
import { useToast } from '../components/Toast'
import { useRowSelection } from '../hooks/useRowSelection'

const emptyDraft: ProductUpsert = {
  sku: '', slug: '', nameHe: '', descriptionHe: '',
  categoryId: null, priceAgorot: 0, stockQty: 0,
  imageUrl: '', imageUrls: [], active: true,
}

export function ProductsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const urlQuery = searchParams.get('q') ?? ''
  const [products, setProducts] = useState<Product[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [editing, setEditing] = useState<Product | null>(null)
  const [creating, setCreating] = useState(false)
  const [draft, setDraft] = useState<ProductUpsert>(emptyDraft)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [query, setQuery] = useState(urlQuery)
  const [importing, setImporting] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [importSummary, setImportSummary] = useState<{ created: number; updated: number; totalRows: number; errors: { line: number; sku: string; message: string }[] } | null>(null)

  useEffect(() => { setQuery(urlQuery) }, [urlQuery])

  const push = useToast(s => s.push)

  const filteredProducts = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return products
    return products.filter(p =>
      p.nameHe.toLowerCase().includes(q) ||
      p.sku.toLowerCase().includes(q)
    )
  }, [products, query])

  const filteredIds = useMemo(() => filteredProducts.map(p => p.id), [filteredProducts])
  const sel = useRowSelection<number>(filteredIds)

  const updateQuery = (v: string) => {
    setQuery(v)
    const next = new URLSearchParams(searchParams)
    if (v.trim()) next.set('q', v.trim())
    else next.delete('q')
    setSearchParams(next, { replace: true })
  }

  function load() {
    setError(null)
    Promise.all([
      api<{ content: Product[] }>('/api/products?size=200'),
      api<Category[]>('/api/categories'),
    ])
      .then(([p, c]) => { setProducts(p.content); setCategories(c) })
      .catch(e => setError(e.message))
  }
  useEffect(load, [])

  function startCreate() {
    setEditing(null); setCreating(true); setDraft(emptyDraft); setError(null)
  }

  function startEdit(p: Product) {
    setCreating(false); setEditing(p)
    setDraft({
      sku: p.sku, slug: p.slug, nameHe: p.nameHe,
      descriptionHe: p.descriptionHe ?? '',
      categoryId: p.categoryId, priceAgorot: p.priceAgorot, stockQty: p.stockQty,
      imageUrl: p.imageUrl ?? '', imageUrls: p.imageUrls ?? [], active: p.active,
    })
    setError(null)
  }

  function cancel() { setEditing(null); setCreating(false); setError(null) }

  async function uploadImage(file: File, target: 'main' | 'extra' = 'main') {
    setError(null); setUploading(true)
    try {
      const form = new FormData()
      form.append('file', file)
      const res = await api<{ url: string }>('/api/admin/media/products', { method: 'POST', body: form })
      if (target === 'main') {
        setDraft(d => ({ ...d, imageUrl: res.url }))
      } else {
        setDraft(d => ({ ...d, imageUrls: [...d.imageUrls, res.url] }))
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה בהעלאה')
    } finally {
      setUploading(false)
    }
  }

  function removeExtraImage(url: string) {
    setDraft(d => ({ ...d, imageUrls: d.imageUrls.filter(u => u !== url) }))
  }

  async function save(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true); setError(null)
    try {
      if (editing) {
        await api(`/api/admin/catalog/products/${editing.id}`, { method: 'PUT', body: JSON.stringify(draft) })
      } else {
        await api('/api/admin/catalog/products', { method: 'POST', body: JSON.stringify(draft) })
      }
      cancel(); load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    } finally {
      setBusy(false)
    }
  }

  async function exportProductsCsv() {
    setExporting(true); setError(null)
    try {
      await downloadFile('/api/admin/catalog/products.csv', 'products.csv')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה בייצוא')
    } finally {
      setExporting(false)
    }
  }

  async function importProductsCsv(file: File) {
    setImporting(true); setError(null); setImportSummary(null)
    try {
      const form = new FormData()
      form.append('file', file)
      const res = await api<{ created: number; updated: number; totalRows: number; errors: { line: number; sku: string; message: string }[] }>(
        '/api/admin/catalog/products/import',
        { method: 'POST', body: form }
      )
      setImportSummary(res)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה בייבוא')
    } finally {
      setImporting(false)
    }
  }

  async function remove(p: Product) {
    const ok = await confirmDialog({
      title: 'מחיקת מוצר',
      message: `למחוק את "${p.nameHe}"? פעולה זו בלתי הפיכה.`,
      confirmLabel: 'מחק', danger: true,
    })
    if (!ok) return
    setError(null)
    try {
      await api(`/api/admin/catalog/products/${p.id}`, { method: 'DELETE' })
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    }
  }

  async function bulkDelete() {
    const n = sel.count
    if (n === 0) return
    const ok = await confirmBulkDelete(n, {
      title: `מחיקת ${n} מוצרים`,
      message: 'המוצרים יוסרו לצמיתות מהקטלוג. מומלץ לייצא CSV לפני המחיקה.',
    })
    if (!ok) return
    setError(null)
    try {
      const res = await api<BulkResult>('/api/admin/catalog/products/bulk-delete', {
        method: 'POST', body: JSON.stringify({ ids: sel.selectedList }),
      })
      push(`${res.affected} מוצרים נמחקו`)
      sel.clear(); load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    }
  }

  // Show (active=true) / hide (active=false) a set of products without
  // deleting. Used by the bulk bar and the single-row status toggle.
  async function setActive(ids: number[], active: boolean) {
    if (ids.length === 0) return
    setError(null)
    try {
      await api<BulkResult>('/api/admin/catalog/products/bulk-active', {
        method: 'POST', body: JSON.stringify({ ids, active }),
      })
      push(active ? 'המוצרים הופעלו' : 'המוצרים הוסתרו')
      sel.clear(); load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    }
  }

  const isEditing = creating || editing
  const lowStockCount = products.filter(p => p.stockQty < 10 && p.active).length

  if (isEditing) {
    return (
      <form onSubmit={save}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 18 }}>
          <div>
            <a onClick={cancel} className="hm-meta" style={{ fontFamily: 'var(--mono)', letterSpacing: '0.16em', textTransform: 'uppercase', color: 'var(--ink-3)' }}>
              ← חזרה למוצרים
            </a>
            <h1 style={{ marginTop: 4 }}>{editing ? 'עריכת מוצר' : 'מוצר חדש'}</h1>
            <div className="sub">{editing ? editing.nameHe : 'הוסף מוצר חדש לקטלוג'}</div>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button type="button" className="hm-btn hm-btn-quiet" onClick={cancel}>ביטול</button>
            <button type="submit" className="hm-btn hm-btn-primary" disabled={busy || uploading}>
              {busy ? 'שומר…' : 'שמירה'}
            </button>
          </div>
        </div>

        {error && <div className="hm-error" style={{ marginBottom: 14 }}>{error}</div>}

        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 14 }}>
          <div style={{ display: 'grid', gap: 14 }}>
            <div className="adm-card">
              <h3 style={{ fontFamily: 'var(--serif)', fontSize: 18, marginBottom: 14 }}>פרטים בסיסיים</h3>
              <div style={{ display: 'grid', gap: 14 }}>
                <Field label="שם המוצר" required value={draft.nameHe} onChange={e => setDraft({ ...draft, nameHe: e.target.value })} />
                <Field label="תיאור" multiline rows={3} value={draft.descriptionHe ?? ''} onChange={e => setDraft({ ...draft, descriptionHe: e.target.value })} />
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <Field label="מק״ט" required mono value={draft.sku} onChange={e => setDraft({ ...draft, sku: e.target.value })} />
                  <Field label="Slug (אנגלית)" required mono value={draft.slug} onChange={e => setDraft({ ...draft, slug: e.target.value })} />
                </div>
              </div>
            </div>

            <div className="adm-card">
              <h3 style={{ fontFamily: 'var(--serif)', fontSize: 18, marginBottom: 14 }}>תמונה ראשית</h3>
              <div style={{ display: 'flex', gap: 18, alignItems: 'flex-start' }}>
                <div style={{
                  width: 160, height: 160, borderRadius: 'var(--r-md)',
                  background: 'var(--paper-2)',
                  border: '1px solid var(--line)',
                  display: 'grid', placeItems: 'center', overflow: 'hidden',
                }}>
                  {draft.imageUrl ? (
                    <img src={draft.imageUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'contain' }} />
                  ) : (
                    <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)', letterSpacing: '0.14em', textTransform: 'uppercase' }}>
                      אין תמונה
                    </span>
                  )}
                </div>
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <label className="hm-btn hm-btn-quiet" style={{ width: 'fit-content', cursor: uploading ? 'wait' : 'pointer' }}>
                    <Icon name="upload" size={14} />
                    {uploading ? 'מעלה…' : draft.imageUrl ? 'החלפת תמונה' : '+ העלאה'}
                    <input type="file" accept="image/jpeg,image/png,image/webp" disabled={uploading}
                           onChange={e => { const f = e.target.files?.[0]; if (f) uploadImage(f, 'main'); e.target.value = '' }}
                           style={{ display: 'none' }} />
                  </label>
                  {draft.imageUrl && (
                    <button type="button" className="hm-btn hm-btn-ghost" style={{ width: 'fit-content' }}
                            onClick={() => setDraft(d => ({ ...d, imageUrl: '' }))}>
                      <Icon name="trash" size={14} /> הסרת תמונה
                    </button>
                  )}
                  <div className="hm-meta" style={{ fontSize: 11.5 }}>
                    JPG / PNG / WebP · מקסימום 10MB · מותאם אוטומטית ל-1200px
                  </div>
                </div>
              </div>
            </div>

            <div className="adm-card">
              <h3 style={{ fontFamily: 'var(--serif)', fontSize: 18, marginBottom: 14 }}>תמונות נוספות לגלריה</h3>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
                {draft.imageUrls.map(url => (
                  <div key={url} style={{
                    position: 'relative', width: 96, height: 96,
                    borderRadius: 'var(--r-sm)', overflow: 'hidden',
                    border: '1px solid var(--line)', background: 'var(--paper-2)',
                  }}>
                    <img src={url} alt="" style={{ width: '100%', height: '100%', objectFit: 'contain' }} />
                    <button type="button"
                            onClick={() => removeExtraImage(url)}
                            aria-label="הסר תמונה"
                            style={{
                              position: 'absolute', top: 4, insetInlineEnd: 4,
                              width: 22, height: 22, borderRadius: '50%',
                              border: 'none', background: 'rgba(15,16,20,0.8)', color: '#fff',
                              cursor: 'pointer', display: 'grid', placeItems: 'center',
                              fontSize: 14, lineHeight: 1,
                            }}>
                      ×
                    </button>
                  </div>
                ))}
                <label className="hm-btn hm-btn-quiet" style={{
                  width: 96, height: 96, display: 'grid', placeItems: 'center',
                  cursor: uploading ? 'wait' : 'pointer', fontSize: 12,
                }}>
                  {uploading ? 'מעלה…' : '+ הוסף'}
                  <input type="file" accept="image/jpeg,image/png,image/webp" disabled={uploading}
                         onChange={e => { const f = e.target.files?.[0]; if (f) uploadImage(f, 'extra'); e.target.value = '' }}
                         style={{ display: 'none' }} />
                </label>
              </div>
              <div className="hm-meta" style={{ fontSize: 11.5, marginTop: 10 }}>
                התמונות הנוספות יוצגו כתמונות ממוזערות בעמוד המוצר.
              </div>
            </div>
          </div>

          <div style={{ display: 'grid', gap: 14, height: 'fit-content' }}>
            <div className="adm-card">
              <div className="hm-label" style={{ marginBottom: 10 }}>סטטוס</div>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <input
                  type="checkbox"
                  checked={draft.active}
                  onChange={e => setDraft({ ...draft, active: e.target.checked })}
                />
                <span>{draft.active ? 'פעיל בקטלוג' : 'מוסתר'}</span>
              </label>
            </div>

            <div className="adm-card">
              <h3 style={{ fontFamily: 'var(--serif)', fontSize: 17, marginBottom: 12 }}>תמחור ומלאי</h3>
              <div style={{ display: 'grid', gap: 12 }}>
                <Field label="מחיר (אגורות)" type="number" min={0} required mono
                       value={draft.priceAgorot}
                       onChange={e => setDraft({ ...draft, priceAgorot: Number(e.target.value) })} />
                <Field label="מלאי" type="number" min={0} required mono
                       value={draft.stockQty}
                       onChange={e => setDraft({ ...draft, stockQty: Number(e.target.value) })} />
              </div>
            </div>

            <div className="adm-card">
              <h3 style={{ fontFamily: 'var(--serif)', fontSize: 17, marginBottom: 12 }}>ארגון</h3>
              <div className="hm-field">
                <label>קטגוריה</label>
                <select className="hm-input"
                        value={draft.categoryId ?? ''}
                        onChange={e => setDraft({ ...draft, categoryId: e.target.value ? Number(e.target.value) : null })}>
                  <option value="">— ללא —</option>
                  {categories.map(c => <option key={c.id} value={c.id}>{c.nameHe}</option>)}
                </select>
              </div>
            </div>
          </div>
        </div>
      </form>
    )
  }

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 18 }}>
        <div>
          <h1>מוצרים</h1>
          <div className="sub">
            {products.filter(p => p.active).length} פעילים
            {lowStockCount > 0 && ` · ${lowStockCount} במלאי נמוך`}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="hm-btn hm-btn-quiet" onClick={exportProductsCsv} disabled={exporting}>
            {exporting ? 'מייצא…' : 'ייצוא CSV'}
          </button>
          <label className="hm-btn hm-btn-quiet" style={{ cursor: importing ? 'wait' : 'pointer' }}>
            {importing ? 'מייבא…' : 'ייבוא CSV'}
            <input type="file" accept=".csv,text/csv" disabled={importing}
                   onChange={e => { const f = e.target.files?.[0]; if (f) importProductsCsv(f); e.target.value = '' }}
                   style={{ display: 'none' }} />
          </label>
          <button className="hm-btn hm-btn-primary" onClick={startCreate}>+ מוצר חדש</button>
        </div>
      </div>

      {error && <div className="hm-error" style={{ marginBottom: 14 }}>{error}</div>}

      {importSummary && (
        <div className="adm-card" style={{ marginBottom: 14, borderColor: importSummary.errors.length ? 'var(--terracotta)' : 'var(--leaf)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
            <div>
              <div style={{ fontWeight: 700, marginBottom: 4 }}>
                ייבוא הושלם: {importSummary.created} נוצרו, {importSummary.updated} עודכנו
                {importSummary.errors.length > 0 && ` · ${importSummary.errors.length} שגיאות`}
              </div>
              <div className="hm-meta" style={{ fontSize: 12 }}>
                סה״כ {importSummary.totalRows} שורות נקראו
              </div>
              {importSummary.errors.length > 0 && (
                <ul style={{ marginTop: 10, paddingInlineStart: 18, fontSize: 12.5, color: 'var(--ink-2)' }}>
                  {importSummary.errors.slice(0, 20).map((er, i) => (
                    <li key={i}>שורה {er.line}{er.sku ? ` (${er.sku})` : ''}: {er.message}</li>
                  ))}
                  {importSummary.errors.length > 20 && <li>… ועוד {importSummary.errors.length - 20}</li>}
                </ul>
              )}
            </div>
            <button className="hm-icon-btn" onClick={() => setImportSummary(null)} aria-label="סגור">
              <Icon name="x" size={14} />
            </button>
          </div>
        </div>
      )}

      <div style={{ display: 'flex', gap: 10, marginBottom: 14, alignItems: 'center', flexWrap: 'wrap' }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 8,
          background: 'var(--card)', border: '1px solid var(--line)',
          borderRadius: 'var(--r-pill)', padding: '6px 14px',
          width: 280, color: 'var(--ink-3)',
        }}>
          <Icon name="search" size={14} />
          <input
            type="search"
            value={query}
            onChange={e => updateQuery(e.target.value)}
            placeholder="חפש מק״ט, שם מוצר…"
            style={{
              flex: 1, border: 'none', outline: 'none', background: 'transparent',
              fontSize: 13, color: 'var(--ink)', minWidth: 0, fontFamily: 'inherit',
            }}
          />
        </div>
        {query.trim() && (
          <span className="hm-meta" style={{ fontSize: 12 }}>
            {filteredProducts.length} מתוך {products.length}
          </span>
        )}
      </div>

      <table className="adm-table">
        <thead>
          <tr>
            <th className="sel">
              <Checkbox checked={sel.allSelected} indeterminate={sel.someSelected}
                        onClick={() => sel.toggleAll()} ariaLabel="בחר הכל" />
            </th>
            <th style={{ width: 56 }}></th>
            <th>שם</th>
            <th>מק״ט</th>
            <th>קטגוריה</th>
            <th style={{ textAlign: 'start' }}>מחיר</th>
            <th>מלאי</th>
            <th>סטטוס</th>
            <th style={{ width: 140 }}></th>
          </tr>
        </thead>
        <tbody>
          {filteredProducts.map((p, index) => {
            const lowStock = p.stockQty < 10
            const outOfStock = p.stockQty <= 0
            return (
              <tr key={p.id} className={sel.isSelected(p.id) ? 'selected' : ''}>
                <td className="sel">
                  <Checkbox checked={sel.isSelected(p.id)}
                            onClick={(e) => sel.toggle(index, e.shiftKey)}
                            ariaLabel={`בחר ${p.nameHe}`} />
                </td>
                <td>
                  <div style={{
                    width: 42, height: 42, borderRadius: 'var(--r-sm)',
                    background: 'var(--paper-2)', display: 'grid', placeItems: 'center',
                    overflow: 'hidden',
                  }}>
                    {p.imageUrl
                      ? <img src={p.imageUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'contain' }} />
                      : <span className="mono" style={{ fontSize: 9, color: 'var(--ink-3)', letterSpacing: '0.1em' }}>—</span>}
                  </div>
                </td>
                <td>
                  <div style={{ fontWeight: 500 }}>{p.nameHe}</div>
                  {p.descriptionHe && <div style={{ fontSize: 11.5, color: 'var(--ink-3)' }}>{p.descriptionHe}</div>}
                </td>
                <td className="num">{p.sku}</td>
                <td>
                  <span className="hm-chip" style={{ padding: '3px 10px', fontSize: 11.5 }}>
                    {categories.find(c => c.id === p.categoryId)?.nameHe ?? '—'}
                  </span>
                </td>
                <td className="num" style={{ textAlign: 'start', fontWeight: 600 }}>{formatPrice(p.priceAgorot)}</td>
                <td className="num" style={{ color: outOfStock ? 'var(--berry)' : lowStock ? 'var(--terracotta)' : 'var(--ink)' }}>
                  {p.stockQty}{lowStock && !outOfStock && ' ⚠'}
                </td>
                <td>
                  <button
                    type="button"
                    className={`hm-status ${p.active ? 'hm-status-paid' : 'hm-status-cancelled'}`}
                    style={{ textTransform: 'none', cursor: 'pointer', border: 'none', font: 'inherit' }}
                    title={p.active ? 'לחצו כדי להסתיר מהחנות' : 'לחצו כדי להפעיל בחנות'}
                    onClick={() => setActive([p.id], !p.active)}
                  >
                    {p.active ? 'פעיל' : 'מוסתר'}
                  </button>
                </td>
                <td style={{ display: 'flex', gap: 6 }}>
                  <button className="hm-btn hm-btn-quiet" style={{ padding: '5px 12px', fontSize: 12 }} onClick={() => startEdit(p)}>עריכה</button>
                  <button className="hm-icon-btn" style={{ width: 28, height: 28 }} onClick={() => remove(p)} aria-label="מחיקה">
                    <Icon name="trash" size={14} />
                  </button>
                </td>
              </tr>
            )
          })}
          {filteredProducts.length === 0 && (
            <tr><td colSpan={9} style={{ textAlign: 'center', color: 'var(--ink-3)', padding: 30 }}>
              {query.trim() ? `אין תוצאות עבור "${query.trim()}".` : 'אין מוצרים. לחצו "מוצר חדש".'}
            </td></tr>
          )}
        </tbody>
      </table>

      <BulkBar count={sel.count} onClear={sel.clear}>
        <button className="hm-btn hm-btn-quiet" onClick={() => setActive(sel.selectedList, true)}>הפעלה</button>
        <button className="hm-btn hm-btn-quiet" onClick={() => setActive(sel.selectedList, false)}>הסתרה</button>
        <button className="hm-btn hm-btn-danger" onClick={bulkDelete}>מחיקת נבחרים</button>
      </BulkBar>
    </>
  )
}
