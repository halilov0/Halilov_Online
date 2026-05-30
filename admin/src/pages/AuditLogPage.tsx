import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, downloadFile, type AuditLogList, type AuditLogRow, type PurgeResult } from '../api'
import { confirmDialog } from '../components/ConfirmDialog'
import { useToast } from '../components/Toast'

const PAGE_SIZE = 100

const ACTION_LABELS: Record<string, string> = {
  USER_REGISTER:             'הרשמה',
  USER_LOGIN:                'התחברות',
  USER_LOGIN_FAILED:         'התחברות נכשלה',
  ORDER_PLACED:              'הזמנה נוצרה',
  ORDER_CANCELLED:           'הזמנה בוטלה',
  ORDER_STATUS_CHANGED:      'סטטוס הזמנה',
  ORDER_REFUNDED:            'זיכוי',
  PRODUCT_CREATED:           'מוצר נוצר',
  PRODUCT_UPDATED:           'מוצר עודכן',
  PRODUCT_DELETED:           'מוצר נמחק',
  CATEGORY_CREATED:          'קטגוריה נוצרה',
  CATEGORY_UPDATED:          'קטגוריה עודכנה',
  CATEGORY_DELETED:          'קטגוריה נמחקה',
  COUPON_CREATED:            'קופון נוצר',
  COUPON_UPDATED:            'קופון עודכן',
  COUPON_DELETED:            'קופון נמחק',
  MARKETING_CAMPAIGN_SENT:   'קמפיין שיווק',
  ACCOUNT_PROFILE_UPDATED:   'עדכון פרופיל',
  ACCOUNT_PASSWORD_CHANGED:  'שינוי סיסמה',
  ACCOUNT_MARKETING_CHANGED: 'הסכמה לשיווק',
  ACCOUNT_ADDRESS_SAVED:     'כתובת נשמרה',
  ACCOUNT_ADDRESS_DELETED:   'כתובת נמחקה',
  USER_ENABLED:              'הפעלת חשבון',
  USER_DISABLED:             'השבתת חשבון',
  USER_FORCE_LOGOUT:         'ניתוק כפוי',
}

const ROW_COLOR: Record<string, string> = {
  USER_LOGIN_FAILED: '#b3261e',
  ORDER_REFUNDED:    '#b3261e',
  USER_DISABLED:     '#b3261e',
  USER_FORCE_LOGOUT: '#b3261e',
  ORDER_PLACED:      '#1f7a45',
  USER_REGISTER:     '#1f7a45',
}

export function AuditLogPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const userIdParam = searchParams.get('userId')
  const initialAction = searchParams.get('action') ?? ''
  const initialFrom = searchParams.get('from') ?? ''
  const initialTo = searchParams.get('to') ?? ''

  const [data, setData] = useState<AuditLogList | null>(null)
  const [actions, setActions] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [action, setAction] = useState(initialAction)
  const [from, setFrom] = useState(initialFrom)
  const [to, setTo] = useState(initialTo)
  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [purgeDays, setPurgeDays] = useState('90')
  const [exporting, setExporting] = useState(false)
  const [purging, setPurging] = useState(false)
  const push = useToast(s => s.push)

  /** Query string for the active filters, shared by the list, CSV export. */
  const filterParams = () => {
    const params = new URLSearchParams()
    if (userIdParam) params.set('userId', userIdParam)
    if (action) params.set('action', action)
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    return params
  }

  useEffect(() => {
    api<string[]>('/api/admin/audit-log/actions').then(setActions).catch(() => {})
  }, [])

  useEffect(() => {
    setError(null)
    const params = filterParams()
    params.set('page', String(page))
    params.set('size', String(PAGE_SIZE))
    api<AuditLogList>(`/api/admin/audit-log?${params.toString()}`)
      .then(setData)
      .catch(e => setError(e.message))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, userIdParam, action, from, to, reloadKey])

  async function exportCsv() {
    setExporting(true); setError(null)
    try {
      const qs = filterParams().toString()
      await downloadFile(`/api/admin/audit-log/export.csv${qs ? `?${qs}` : ''}`, 'audit-log.csv')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה בייצוא')
    } finally {
      setExporting(false)
    }
  }

  async function purge() {
    const days = Number(purgeDays)
    if (!Number.isInteger(days) || days < 1) { setError('מספר ימים לא תקין (מינימום 1)'); return }
    const ok = await confirmDialog({
      title: 'ניקוי רשומות ישנות',
      message: `כל רשומות הלוג הישנות מ-${days} ימים יימחקו לצמיתות. רשומות חדשות יותר יישמרו, והניקוי עצמו יתועד בלוג.`,
      confirmLabel: 'נקה',
      danger: true,
      challenge: 'מחק',
      challengeHint: 'הקלידו "מחק" לאישור',
    })
    if (!ok) return
    setPurging(true); setError(null)
    try {
      const res = await api<PurgeResult>(`/api/admin/audit-log?olderThanDays=${days}`, { method: 'DELETE' })
      push(`${res.deleted} רשומות נמחקו`)
      setPage(0); setReloadKey(k => k + 1)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה בניקוי')
    } finally {
      setPurging(false)
    }
  }

  const updateFilter = (next: Record<string, string>) => {
    const params = new URLSearchParams(searchParams)
    Object.entries(next).forEach(([k, v]) => {
      if (v) params.set(k, v); else params.delete(k)
    })
    setSearchParams(params, { replace: true })
    setPage(0)
  }

  const rows = data?.content ?? []
  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 18 }}>
        <div>
          <h1>לוג פעולות</h1>
          <div className="sub">
            {data ? `${data.total} רשומות` : 'טוען…'}
            {userIdParam && ` · מסונן למשתמש #${userIdParam}`}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {userIdParam && (
            <button className="hm-btn hm-btn-quiet" onClick={() => updateFilter({ userId: '' })}>
              הסר סינון משתמש
            </button>
          )}
          <button className="hm-btn hm-btn-quiet" onClick={exportCsv} disabled={exporting}>
            {exporting ? 'מייצא…' : 'ייצוא CSV'}
          </button>
        </div>
      </div>

      {error && <div className="hm-error" style={{ marginBottom: 14 }}>{error}</div>}

      <div className="adm-card" style={{ marginBottom: 14, display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
        <span style={{ fontWeight: 600 }}>ניקוי רשומות ישנות</span>
        <span className="hm-meta">מחיקת רשומות ישנות מ-</span>
        <input
          type="number" min={1}
          value={purgeDays}
          onChange={e => setPurgeDays(e.target.value)}
          className="hm-input mono"
          style={{ width: 80 }}
        />
        <span className="hm-meta">ימים</span>
        <button className="hm-btn hm-btn-danger" onClick={purge} disabled={purging} style={{ marginInlineStart: 'auto' }}>
          {purging ? 'מנקה…' : 'נקה רשומות ישנות'}
        </button>
      </div>

      <div style={{ display: 'flex', gap: 10, marginBottom: 14, flexWrap: 'wrap', alignItems: 'center' }}>
        <select
          value={action}
          onChange={e => { setAction(e.target.value); updateFilter({ action: e.target.value }) }}
          className="hm-select"
          style={{ padding: '8px 14px', minWidth: 180 }}
        >
          <option value="">כל הפעולות</option>
          {actions.map(a => <option key={a} value={a}>{ACTION_LABELS[a] ?? a}</option>)}
        </select>
        <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--ink-3)' }}>
          מ-
          <input
            type="date"
            value={from}
            onChange={e => { setFrom(e.target.value); updateFilter({ from: e.target.value }) }}
            style={{ padding: '6px 10px', border: '1px solid var(--line)', borderRadius: 'var(--r-md)' }}
          />
        </label>
        <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--ink-3)' }}>
          עד-
          <input
            type="date"
            value={to}
            onChange={e => { setTo(e.target.value); updateFilter({ to: e.target.value }) }}
            style={{ padding: '6px 10px', border: '1px solid var(--line)', borderRadius: 'var(--r-md)' }}
          />
        </label>
      </div>

      <table className="adm-table">
        <thead>
          <tr>
            <th style={{ width: 160 }}>תאריך</th>
            <th style={{ width: 180 }}>פעולה</th>
            <th>מבצע</th>
            <th>פרטים</th>
            <th style={{ width: 110 }}>IP</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(r => <AuditRow key={r.id} r={r} />)}
          {rows.length === 0 && data && (
            <tr><td colSpan={5} style={{ textAlign: 'center', color: 'var(--ink-3)', padding: 30 }}>אין רשומות תואמות.</td></tr>
          )}
        </tbody>
      </table>

      {data && totalPages > 1 && (
        <div style={{ display: 'flex', justifyContent: 'center', gap: 8, marginTop: 18 }}>
          <button className="hm-btn hm-btn-quiet" disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))}>
            הקודם
          </button>
          <span style={{ alignSelf: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
            דף {page + 1} מתוך {totalPages}
          </span>
          <button className="hm-btn hm-btn-quiet" disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)}>
            הבא
          </button>
        </div>
      )}
    </>
  )
}

function AuditRow({ r }: { r: AuditLogRow }) {
  const color = ROW_COLOR[r.action] ?? 'var(--ink)'
  const label = ACTION_LABELS[r.action] ?? r.action
  return (
    <tr>
      <td style={{ color: 'var(--ink-3)', whiteSpace: 'nowrap', fontSize: 12.5 }}>
        {new Date(r.createdAt).toLocaleString('he-IL', {
          day: '2-digit', month: '2-digit', year: '2-digit',
          hour: '2-digit', minute: '2-digit', second: '2-digit',
        })}
      </td>
      <td><span style={{ fontWeight: 600, color }}>{label}</span></td>
      <td>
        {r.actorEmail
          ? <span>{r.actorEmail}{r.actorRole && r.actorRole !== 'CUSTOMER' && ` · ${r.actorRole}`}</span>
          : <span style={{ color: 'var(--ink-3)' }}>אנונימי</span>}
      </td>
      <td style={{ fontSize: 13 }}>{r.message ?? '—'}</td>
      <td className="num" style={{ fontSize: 11.5, color: 'var(--ink-3)' }}>{r.actorIp ?? '—'}</td>
    </tr>
  )
}
