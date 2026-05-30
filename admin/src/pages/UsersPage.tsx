import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, formatPrice, type AdminUserList, type AdminUserRow, type BulkResult } from '../api'
import { Icon } from '../components/Icon'
import { useToast } from '../components/Toast'
import { Checkbox } from '../components/Checkbox'
import { BulkBar } from '../components/BulkBar'
import { confirmDialog, BULK_TYPE_CONFIRM_THRESHOLD } from '../components/ConfirmDialog'
import { useRowSelection } from '../hooks/useRowSelection'

const PAGE_SIZE = 50

export function UsersPage() {
  const [data, setData] = useState<AdminUserList | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [busy, setBusy] = useState<number | null>(null)
  const push = useToast(s => s.push)

  const rowIds = useMemo(() => (data?.content ?? []).map(u => u.id), [data])
  const sel = useRowSelection<number>(rowIds)

  const load = () => {
    setError(null)
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) })
    if (q.trim()) params.set('q', q.trim())
    api<AdminUserList>(`/api/admin/users?${params.toString()}`)
      .then(setData)
      .catch(e => setError(e.message))
  }

  useEffect(() => { load() }, [page])

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault()
    setPage(0)
    load()
  }

  const totalSpend = useMemo(() => (data?.content ?? []).reduce((s, u) => s + u.spendAgorot, 0), [data])

  const togglEnabled = async (u: AdminUserRow) => {
    if (busy) return
    setBusy(u.id)
    try {
      const updated = await api<AdminUserRow>(`/api/admin/users/${u.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ enabled: !u.enabled }),
      })
      setData(d => d ? { ...d, content: d.content.map(x => x.id === u.id ? { ...x, ...updated } : x) } : d)
      push(updated.enabled ? 'החשבון הופעל' : 'החשבון הושבת')
    } catch (e: any) {
      push(e.message ?? 'שגיאה')
    } finally {
      setBusy(null)
    }
  }

  const forceLogout = async (u: AdminUserRow) => {
    if (busy) return
    if (!confirm(`לנתק את ${u.email} מכל המכשירים?`)) return
    setBusy(u.id)
    try {
      const updated = await api<AdminUserRow>(`/api/admin/users/${u.id}/force-logout`, { method: 'POST' })
      setData(d => d ? { ...d, content: d.content.map(x => x.id === u.id ? { ...x, ...updated } : x) } : d)
      push('המשתמש נותק מכל המכשירים')
    } catch (e: any) {
      push(e.message ?? 'שגיאה')
    } finally {
      setBusy(null)
    }
  }

  const sendPasswordReset = async (u: AdminUserRow) => {
    if (busy) return
    if (!confirm(`לשלוח אימייל איפוס סיסמה אל ${u.email}?`)) return
    setBusy(u.id)
    try {
      await api(`/api/admin/users/${u.id}/password-reset`, { method: 'POST' })
      push('נשלח אימייל איפוס סיסמה')
    } catch (e: any) {
      push(e.message ?? 'שגיאה')
    } finally {
      setBusy(null)
    }
  }

  const bulkSetEnabled = async (enabled: boolean) => {
    const n = sel.count
    if (n === 0) return
    const ok = await confirmDialog({
      title: enabled ? `הפעלת ${n} חשבונות` : `השבתת ${n} חשבונות`,
      message: enabled
        ? 'החשבונות הנבחרים יופעלו. חשבונות מנהל ידולגו.'
        : 'החשבונות הנבחרים יושבתו וינותקו מכל המכשירים. חשבונות מנהל ידולגו.',
      confirmLabel: enabled ? 'הפעל' : 'השבת',
      danger: !enabled,
      challenge: !enabled && n > BULK_TYPE_CONFIRM_THRESHOLD ? String(n) : undefined,
    })
    if (!ok) return
    try {
      const res = await api<BulkResult>('/api/admin/users/bulk-status', {
        method: 'POST', body: JSON.stringify({ ids: sel.selectedList, enabled }),
      })
      push(`${res.affected} ${enabled ? 'הופעלו' : 'הושבתו'}${res.skipped ? ` · ${res.skipped} דולגו` : ''}`)
      sel.clear(); load()
    } catch (e: any) {
      push(e.message ?? 'שגיאה')
    }
  }

  const rows = data?.content ?? []
  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 18 }}>
        <div>
          <h1>לקוחות</h1>
          <div className="sub">
            {data ? `${data.total} משתמשים` : 'טוען…'}
            {data && ` · סך רכישות ${formatPrice(totalSpend)} בדף הנוכחי`}
          </div>
        </div>
      </div>

      {error && <div className="hm-error" style={{ marginBottom: 14 }}>{error}</div>}

      <form onSubmit={onSearch} style={{ display: 'flex', gap: 10, marginBottom: 14, flexWrap: 'wrap', alignItems: 'center' }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 8,
          background: 'var(--card)', border: '1px solid var(--line)',
          borderRadius: 'var(--r-pill)', padding: '8px 14px',
          width: 320,
        }}>
          <Icon name="search" size={14} />
          <input
            value={q}
            onChange={e => setQ(e.target.value)}
            placeholder="חיפוש לפי אימייל, שם או טלפון…"
            style={{ border: 'none', outline: 'none', background: 'transparent', flex: 1, font: 'inherit' }}
          />
        </div>
        <button type="submit" className="hm-btn hm-btn-quiet" style={{ padding: '8px 16px' }}>חפש</button>
      </form>

      <table className="adm-table">
        <thead>
          <tr>
            <th className="sel">
              <Checkbox checked={sel.allSelected} indeterminate={sel.someSelected}
                        onClick={() => sel.toggleAll()} ariaLabel="בחר הכל" />
            </th>
            <th>אימייל</th><th>שם</th><th>טלפון</th>
            <th>הצטרף</th><th>הזמנות</th><th>סה״כ קניות</th>
            <th>סטטוס</th>
            <th style={{ width: 240 }}>פעולות</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((u, index) => (
            <tr key={u.id} className={sel.isSelected(u.id) ? 'selected' : ''}>
              <td className="sel">
                <Checkbox checked={sel.isSelected(u.id)}
                          onClick={(e) => sel.toggle(index, e.shiftKey)}
                          ariaLabel={`בחר ${u.email}`} />
              </td>
              <td>
                <b>{u.email}</b>
                {u.role === 'ADMIN' && (
                  <span style={{
                    marginInlineStart: 8, padding: '1px 7px', borderRadius: 'var(--r-pill)',
                    background: 'var(--terra-soft)', color: 'var(--terracotta)',
                    fontSize: 10.5, fontWeight: 700, letterSpacing: 0.5,
                  }}>ADMIN</span>
                )}
              </td>
              <td>{u.fullName}</td>
              <td className="num" style={{ color: 'var(--ink-3)' }}>{u.phone ?? '—'}</td>
              <td style={{ color: 'var(--ink-3)', whiteSpace: 'nowrap' }}>
                {new Date(u.createdAt).toLocaleDateString('he-IL', { day: '2-digit', month: '2-digit', year: '2-digit' })}
              </td>
              <td className="num">{u.orderCount}</td>
              <td className="num" style={{ fontWeight: 600 }}>{formatPrice(u.spendAgorot)}</td>
              <td>
                {u.enabled
                  ? <span style={{ color: '#1f7a45', fontWeight: 600 }}>פעיל</span>
                  : <span style={{ color: 'var(--terracotta)', fontWeight: 600 }}>מושבת</span>}
              </td>
              <td>
                <div style={{ display: 'flex', gap: 6 }}>
                  <Link
                    to={`/audit-log?userId=${u.id}`}
                    className="hm-btn hm-btn-quiet"
                    style={{ padding: '4px 10px', fontSize: 12 }}
                    title="הצג פעולות בלוג"
                  >
                    <Icon name="history" size={12} /> לוג
                  </Link>
                  <button
                    className="hm-btn hm-btn-quiet"
                    style={{ padding: '4px 10px', fontSize: 12 }}
                    disabled={busy === u.id || u.role === 'ADMIN'}
                    onClick={() => togglEnabled(u)}
                  >
                    {u.enabled ? 'השבת' : 'הפעל'}
                  </button>
                  <button
                    className="hm-btn hm-btn-quiet"
                    style={{ padding: '4px 10px', fontSize: 12 }}
                    disabled={busy === u.id}
                    onClick={() => forceLogout(u)}
                    title="ניתוק מכל המכשירים"
                  >
                    <Icon name="logout" size={12} />
                  </button>
                  <button
                    className="hm-btn hm-btn-quiet"
                    style={{ padding: '4px 10px', fontSize: 12 }}
                    disabled={busy === u.id}
                    onClick={() => sendPasswordReset(u)}
                    title="שלח אימייל איפוס סיסמה"
                  >
                    איפוס סיסמה
                  </button>
                </div>
              </td>
            </tr>
          ))}
          {rows.length === 0 && data && (
            <tr><td colSpan={9} style={{ textAlign: 'center', color: 'var(--ink-3)', padding: 30 }}>אין משתמשים תואמים.</td></tr>
          )}
        </tbody>
      </table>

      <BulkBar count={sel.count} onClear={sel.clear}>
        <button className="hm-btn hm-btn-quiet" onClick={() => bulkSetEnabled(true)}>הפעלת נבחרים</button>
        <button className="hm-btn hm-btn-danger" onClick={() => bulkSetEnabled(false)}>השבתת נבחרים</button>
      </BulkBar>

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
