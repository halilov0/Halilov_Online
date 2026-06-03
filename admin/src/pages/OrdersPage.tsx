import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, formatPrice, type OrderStatus, type OrderView } from '../api'
import { StatusPill } from '../components/StatusPill'
import { Icon } from '../components/Icon'
import { comingSoon } from '../components/Toast'

type Tab = 'all' | OrderStatus

const TABS: { id: Tab; label: string }[] = [
  { id: 'all',       label: 'הכול' },
  { id: 'PENDING',   label: 'ממתינות' },
  { id: 'PAID',      label: 'שולמו' },
  { id: 'SHIPPED',   label: 'נשלחו' },
  { id: 'DELIVERED', label: 'נמסרו' },
  { id: 'CANCELLED', label: 'בוטלו' },
]

// Paid states that should carry a קבלה (receipt). Used to flag orders still
// awaiting the manual קבלה during the lean launch.
const RECEIPTABLE: OrderStatus[] = ['PAID', 'FULFILLED', 'SHIPPED', 'DELIVERED']

export function OrdersPage() {
  const [orders, setOrders] = useState<OrderView[]>([])
  const [error, setError] = useState<string | null>(null)
  const [tab, setTab] = useState<Tab>('all')
  const nav = useNavigate()

  useEffect(() => {
    api<OrderView[]>('/api/admin/orders')
      .then(setOrders)
      .catch(e => setError(e.message))
  }, [])

  const counts = useMemo(() => {
    const c: Record<string, number> = { all: orders.length }
    orders.forEach(o => { c[o.status] = (c[o.status] || 0) + 1 })
    return c
  }, [orders])

  const awaitingReceipt = useMemo(
    () => orders.filter(o => !o.invoiceNumber && RECEIPTABLE.includes(o.status)).length,
    [orders],
  )

  const filtered = useMemo(() => {
    const list = tab === 'all' ? orders : orders.filter(o => o.status === tab)
    return [...list].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  }, [orders, tab])

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 18 }}>
        <div>
          <h1>הזמנות</h1>
          <div className="sub">
            {orders.length} הזמנות
            {counts.PENDING > 0 && ` · ${counts.PENDING} ממתינות לטיפול`}
            {awaitingReceipt > 0 && ` · ${awaitingReceipt} ממתינות לקבלה`}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="hm-btn hm-btn-quiet" onClick={() => comingSoon('ייצוא')}>ייצוא</button>
          <button className="hm-btn hm-btn-primary" onClick={() => comingSoon('הזמנה ידנית')}>+ הזמנה ידנית</button>
        </div>
      </div>

      {error && <div className="hm-error" style={{ marginBottom: 14 }}>{error}</div>}

      {/* tabs */}
      <div style={{ display: 'flex', gap: 24, borderBottom: '1px solid var(--line)', marginBottom: 18 }}>
        {TABS.map(t => {
          const active = tab === t.id
          const count = counts[t.id] ?? 0
          return (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              style={{
                padding: '12px 2px', fontSize: 14,
                color: active ? 'var(--ink)' : 'var(--ink-3)',
                fontWeight: active ? 600 : 400,
                borderBottom: active ? '2px solid var(--ink)' : '2px solid transparent',
                marginBottom: -1,
                background: 'none', border: 'none',
                display: 'flex', alignItems: 'center', gap: 8,
              }}
            >
              {t.label}
              <span className="mono" style={{
                fontSize: 11, padding: '1px 7px',
                background: active ? 'var(--ink)' : 'var(--paper-2)',
                color: active ? 'var(--paper)' : 'var(--ink-3)',
                borderRadius: 'var(--r-pill)',
              }}>{count}</span>
            </button>
          )
        })}
      </div>

      {/* filter row (stubs) */}
      <div style={{ display: 'flex', gap: 10, marginBottom: 14, flexWrap: 'wrap', alignItems: 'center' }}>
        <div onClick={() => comingSoon('חיפוש')} style={{
          display: 'flex', alignItems: 'center', gap: 8,
          background: 'var(--card)', border: '1px solid var(--line)',
          borderRadius: 'var(--r-pill)', padding: '8px 14px',
          width: 280, color: 'var(--ink-3)', fontSize: 13, cursor: 'pointer',
        }}>
          <Icon name="search" size={14} />
          <span>מספר הזמנה, שם לקוח…</span>
        </div>
        {(['טווח תאריכים', 'עיר', 'מיון: חדש קודם'] as const).map(f => (
          <button key={f} className="hm-chip" onClick={() => comingSoon(f)}
                  style={{ padding: '8px 14px', display: 'flex', alignItems: 'center', gap: 8 }}>
            {f}<Icon name="chevDown" size={12} />
          </button>
        ))}
      </div>

      <table className="adm-table">
        <thead>
          <tr>
            <th>הזמנה</th><th>תאריך</th><th>לקוח</th>
            <th>פריטים</th><th>סטטוס</th>
            <th style={{ textAlign: 'start' }}>סכום</th>
            <th style={{ width: 80 }}></th>
          </tr>
        </thead>
        <tbody>
          {filtered.map(o => {
            const initial = o.shipping?.fullName?.[0] ?? '·'
            return (
              <tr key={o.orderNumber} onClick={() => nav(`/orders/${o.orderNumber}`)} style={{ cursor: 'pointer' }}>
                <td className="num"><b>{o.orderNumber}</b></td>
                <td style={{ color: 'var(--ink-3)', whiteSpace: 'nowrap' }}>
                  {new Date(o.createdAt).toLocaleString('he-IL', {
                    day: '2-digit', month: '2-digit', year: '2-digit',
                    hour: '2-digit', minute: '2-digit',
                  })}
                </td>
                <td>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <div style={{
                      width: 30, height: 30, borderRadius: '50%',
                      background: 'var(--terra-soft)', color: 'var(--terracotta)',
                      fontSize: 11, fontWeight: 700, display: 'grid', placeItems: 'center',
                    }}>{initial}</div>
                    <div>
                      <div style={{ fontWeight: 500 }}>{o.shipping?.fullName ?? '—'}</div>
                      {o.shipping?.city && (
                        <div style={{ fontSize: 11.5, color: 'var(--ink-3)' }}>{o.shipping.city}</div>
                      )}
                    </div>
                  </div>
                </td>
                <td className="num">{o.items.reduce((s, i) => s + i.quantity, 0)}</td>
                <td>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <StatusPill s={o.status} />
                    {!o.invoiceNumber && RECEIPTABLE.includes(o.status) && (
                      <span title="ממתין לקבלה ידנית" style={{
                        fontSize: 10, padding: '1px 6px', borderRadius: 'var(--r-pill)',
                        background: 'var(--terra-soft)', color: 'var(--terracotta)', whiteSpace: 'nowrap',
                      }}>קבלה</span>
                    )}
                  </div>
                </td>
                <td className="num" style={{ textAlign: 'start', fontWeight: 600 }}>{formatPrice(o.totalAgorot)}</td>
                <td onClick={e => e.stopPropagation()}>
                  <Link to={`/orders/${o.orderNumber}`} className="hm-btn hm-btn-quiet"
                        style={{ padding: '5px 12px', fontSize: 12 }}>פתח</Link>
                </td>
              </tr>
            )
          })}
          {filtered.length === 0 && (
            <tr><td colSpan={7} style={{ textAlign: 'center', color: 'var(--ink-3)', padding: 30 }}>אין הזמנות בטאב הזה.</td></tr>
          )}
        </tbody>
      </table>
    </>
  )
}
