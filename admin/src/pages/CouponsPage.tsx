import { useEffect, useMemo, useState } from 'react'
import { api, formatPrice, type BulkResult, type Coupon, type CouponType, type CouponUpsert } from '../api'
import { Field } from '../components/Field'
import { Icon } from '../components/Icon'
import { Checkbox } from '../components/Checkbox'
import { BulkBar } from '../components/BulkBar'
import { confirmDialog, confirmBulkDelete } from '../components/ConfirmDialog'
import { useToast } from '../components/Toast'
import { useRowSelection } from '../hooks/useRowSelection'

type DraftState = {
  code: string
  type: CouponType
  value: string
  minSubtotalShekel: string
  maxUses: string
  expiresAt: string
  active: boolean
}

const emptyDraft: DraftState = {
  code: '', type: 'PERCENT', value: '10',
  minSubtotalShekel: '', maxUses: '', expiresAt: '', active: true,
}

function draftFromCoupon(c: Coupon): DraftState {
  return {
    code: c.code,
    type: c.type,
    value: String(c.value),
    minSubtotalShekel: c.minSubtotalAgorot > 0 ? String(c.minSubtotalAgorot / 100) : '',
    maxUses: c.maxUses != null ? String(c.maxUses) : '',
    expiresAt: c.expiresAt ? c.expiresAt.slice(0, 16) : '',
    active: c.active,
  }
}

function buildUpsert(d: DraftState): CouponUpsert {
  return {
    code: d.code.trim().toUpperCase(),
    type: d.type,
    value: Number(d.value),
    minSubtotalAgorot: d.minSubtotalShekel ? Math.round(Number(d.minSubtotalShekel) * 100) : 0,
    maxUses: d.maxUses ? Number(d.maxUses) : null,
    expiresAt: d.expiresAt ? new Date(d.expiresAt).toISOString() : null,
    active: d.active,
  }
}

export function CouponsPage() {
  const [coupons, setCoupons] = useState<Coupon[]>([])
  const [editing, setEditing] = useState<Coupon | null>(null)
  const [creating, setCreating] = useState(false)
  const [draft, setDraft] = useState<DraftState>(emptyDraft)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const push = useToast(s => s.push)

  const couponIds = useMemo(() => coupons.map(c => c.id), [coupons])
  const sel = useRowSelection<number>(couponIds)

  function load() {
    setError(null)
    api<Coupon[]>('/api/admin/coupons').then(setCoupons).catch(e => setError(e.message))
  }
  useEffect(load, [])

  function startCreate() {
    setEditing(null); setCreating(true); setDraft(emptyDraft); setError(null)
  }
  function startEdit(c: Coupon) {
    setCreating(false); setEditing(c); setDraft(draftFromCoupon(c)); setError(null)
  }
  function cancel() { setEditing(null); setCreating(false); setError(null) }

  async function save(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true); setError(null)
    try {
      const body = buildUpsert(draft)
      if (editing) {
        await api(`/api/admin/coupons/${editing.id}`, { method: 'PUT', body: JSON.stringify(body) })
      } else {
        await api('/api/admin/coupons', { method: 'POST', body: JSON.stringify(body) })
      }
      cancel(); load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    } finally {
      setBusy(false)
    }
  }

  async function toggleActive(c: Coupon) {
    try {
      await api(`/api/admin/coupons/${c.id}`, {
        method: 'PUT',
        body: JSON.stringify(buildUpsert({ ...draftFromCoupon(c), active: !c.active })),
      })
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    }
  }

  async function remove(c: Coupon) {
    const ok = await confirmDialog({
      title: 'מחיקת קופון',
      message: `למחוק את ${c.code}?`,
      confirmLabel: 'מחק', danger: true,
    })
    if (!ok) return
    try {
      await api(`/api/admin/coupons/${c.id}`, { method: 'DELETE' })
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    }
  }

  async function bulkDelete() {
    const n = sel.count
    if (n === 0) return
    const ok = await confirmBulkDelete(n, {
      title: `מחיקת ${n} קופונים`,
      message: 'הקודים יימחקו לצמיתות.',
    })
    if (!ok) return
    try {
      const res = await api<BulkResult>('/api/admin/coupons/bulk-delete', {
        method: 'POST', body: JSON.stringify({ ids: sel.selectedList }),
      })
      push(`${res.affected} קופונים נמחקו`)
      sel.clear(); load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    }
  }

  function valueLabel(c: Coupon) {
    return c.type === 'PERCENT' ? `${c.value}%` : formatPrice(c.value)
  }

  function statusOf(c: Coupon): { lbl: string; cls: string } {
    if (!c.active) return { lbl: 'מושבת', cls: 'hm-status-cancelled' }
    if (c.expiresAt && new Date(c.expiresAt) < new Date()) return { lbl: 'פג תוקף', cls: 'hm-status-cancelled' }
    if (c.maxUses != null && c.usedCount >= c.maxUses) return { lbl: 'מוצה', cls: 'hm-status-cancelled' }
    return { lbl: 'פעיל', cls: 'hm-status-paid' }
  }

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 18 }}>
        <div>
          <h1>קופונים</h1>
          <div className="sub">{coupons.length} קודים</div>
        </div>
        {!creating && !editing && (
          <button className="hm-btn hm-btn-primary" onClick={startCreate}>+ קוד חדש</button>
        )}
      </div>

      {error && <div className="hm-error" style={{ marginBottom: 14 }}>{error}</div>}

      {(creating || editing) && (
        <form onSubmit={save} className="adm-card" style={{ marginBottom: 14, display: 'grid', gap: 14 }}>
          <h3 style={{ fontFamily: 'var(--serif)', fontSize: 18 }}>
            {editing ? `עריכת ${editing.code}` : 'קוד חדש'}
          </h3>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
            <Field label="קוד" required mono value={draft.code}
                   onChange={e => setDraft({ ...draft, code: e.target.value })} />
            <div className="hm-field">
              <label>סוג הנחה</label>
              <select className="hm-input" value={draft.type}
                      onChange={e => setDraft({ ...draft, type: e.target.value as CouponType })}>
                <option value="PERCENT">אחוז</option>
                <option value="FIXED">סכום (₪)</option>
              </select>
            </div>
            <Field
              label={draft.type === 'PERCENT' ? 'אחוז (1-100)' : 'סכום באגורות'}
              type="number" min={1} max={draft.type === 'PERCENT' ? 100 : undefined} required mono
              value={draft.value}
              onChange={e => setDraft({ ...draft, value: e.target.value })}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
            <Field label="מינימום הזמנה (₪, אופציונלי)" type="number" min={0} mono
                   value={draft.minSubtotalShekel}
                   onChange={e => setDraft({ ...draft, minSubtotalShekel: e.target.value })} />
            <Field label="מקסימום שימושים (ריק = ללא הגבלה)" type="number" min={1} mono
                   value={draft.maxUses}
                   onChange={e => setDraft({ ...draft, maxUses: e.target.value })} />
            <Field label="פג תוקף (אופציונלי)" type="datetime-local" mono
                   value={draft.expiresAt}
                   onChange={e => setDraft({ ...draft, expiresAt: e.target.value })} />
          </div>

          <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <input type="checkbox" checked={draft.active}
                   onChange={e => setDraft({ ...draft, active: e.target.checked })} />
            <span>{draft.active ? 'פעיל' : 'מושבת'}</span>
          </label>

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
            <th>קוד</th>
            <th>הנחה</th>
            <th>מינימום</th>
            <th>שימושים</th>
            <th>פג תוקף</th>
            <th>סטטוס</th>
            <th style={{ width: 180 }}></th>
          </tr>
        </thead>
        <tbody>
          {coupons.map((c, index) => {
            const st = statusOf(c)
            return (
              <tr key={c.id} className={sel.isSelected(c.id) ? 'selected' : ''}>
                <td className="sel">
                  <Checkbox checked={sel.isSelected(c.id)}
                            onClick={(e) => sel.toggle(index, e.shiftKey)}
                            ariaLabel={`בחר ${c.code}`} />
                </td>
                <td className="num" style={{ fontWeight: 600 }}>{c.code}</td>
                <td className="num">{valueLabel(c)}</td>
                <td className="num" style={{ color: 'var(--ink-3)' }}>
                  {c.minSubtotalAgorot > 0 ? formatPrice(c.minSubtotalAgorot) : '—'}
                </td>
                <td className="num">
                  {c.usedCount}{c.maxUses != null ? ` / ${c.maxUses}` : ''}
                </td>
                <td className="num" style={{ color: 'var(--ink-3)' }}>
                  {c.expiresAt
                    ? new Date(c.expiresAt).toLocaleDateString('he-IL', { day: '2-digit', month: '2-digit', year: 'numeric' })
                    : '—'}
                </td>
                <td>
                  <span className={`hm-status ${st.cls}`} style={{ textTransform: 'none' }}>{st.lbl}</span>
                </td>
                <td style={{ display: 'flex', gap: 6 }}>
                  <button className="hm-btn hm-btn-quiet" style={{ padding: '5px 10px', fontSize: 12 }}
                          onClick={() => toggleActive(c)}>
                    {c.active ? 'השבת' : 'הפעל'}
                  </button>
                  <button className="hm-btn hm-btn-quiet" style={{ padding: '5px 10px', fontSize: 12 }}
                          onClick={() => startEdit(c)}>עריכה</button>
                  <button className="hm-icon-btn" style={{ width: 28, height: 28 }}
                          onClick={() => remove(c)} aria-label="מחיקה">
                    <Icon name="trash" size={14} />
                  </button>
                </td>
              </tr>
            )
          })}
          {coupons.length === 0 && (
            <tr><td colSpan={8} style={{ textAlign: 'center', color: 'var(--ink-3)', padding: 30 }}>
              אין קודים. לחצו "קוד חדש".
            </td></tr>
          )}
        </tbody>
      </table>

      <BulkBar count={sel.count} onClear={sel.clear}>
        <button className="hm-btn hm-btn-danger" onClick={bulkDelete}>מחיקת נבחרים</button>
      </BulkBar>
    </>
  )
}
