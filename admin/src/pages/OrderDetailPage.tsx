import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, formatPrice, ORDER_STATUSES, type OrderStatus, type OrderView, type RefundRequest } from '../api'
import { StatusPill } from '../components/StatusPill'
import { Icon } from '../components/Icon'
import { comingSoon } from '../components/Toast'

type Step = { key: string; label: string; statuses: OrderStatus[] }

const STEPS: Step[] = [
  { key: 'placed',    label: 'הוזמן',  statuses: ['PENDING', 'PAID', 'FULFILLED', 'SHIPPED', 'DELIVERED'] },
  { key: 'paid',      label: 'שולם',   statuses: ['PAID', 'FULFILLED', 'SHIPPED', 'DELIVERED'] },
  { key: 'fulfilled', label: 'הוכן',   statuses: ['FULFILLED', 'SHIPPED', 'DELIVERED'] },
  { key: 'shipped',   label: 'נשלח',   statuses: ['SHIPPED', 'DELIVERED'] },
  { key: 'delivered', label: 'נמסר',   statuses: ['DELIVERED'] },
]

function stepState(step: Step, current: OrderStatus, idx: number): 'done' | 'active' | '' {
  if (step.statuses.includes(current)) return 'done'
  const activeIdx = STEPS.findIndex(s => s.statuses.includes(current))
  if (activeIdx >= 0 && idx === activeIdx + 1) return 'active'
  return ''
}

export function OrderDetailPage() {
  const { orderNumber } = useParams<{ orderNumber: string }>()
  const [order, setOrder] = useState<OrderView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [updating, setUpdating] = useState(false)
  const [refundOpen, setRefundOpen] = useState(false)

  function load() {
    if (!orderNumber) return
    api<OrderView>(`/api/admin/orders/${orderNumber}`)
      .then(setOrder)
      .catch(e => setError(e.message))
  }
  useEffect(load, [orderNumber])

  async function changeStatus(status: OrderStatus) {
    if (!orderNumber || !order) return
    if (status === order.status) return
    if (!confirm(`לעדכן סטטוס ל-${status}?`)) return
    setUpdating(true); setError(null)
    try {
      const updated = await api<OrderView>(`/api/admin/orders/${orderNumber}/status`, {
        method: 'PATCH', body: JSON.stringify({ status }),
      })
      setOrder(updated)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    } finally {
      setUpdating(false)
    }
  }

  if (!order) {
    return error
      ? <div><div className="hm-error">{error}</div><p style={{ marginTop: 14 }}><Link to="/orders">← חזרה לרשימה</Link></p></div>
      : <p style={{ color: 'var(--ink-3)' }}>טוען…</p>
  }

  const dateStr = new Date(order.createdAt).toLocaleString('he-IL', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 18 }}>
        <div>
          <Link to="/orders" className="hm-meta" style={{ fontFamily: 'var(--mono)', letterSpacing: '0.16em', textTransform: 'uppercase', color: 'var(--ink-3)' }}>
            ← חזרה לרשימה
          </Link>
          <h1 style={{ marginTop: 4 }}>הזמנה {order.orderNumber}</h1>
          <div className="sub" style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 0 }}>
            <span>{dateStr}</span>
            <span style={{ color: 'var(--line-2)' }}>·</span>
            <StatusPill s={order.status} />
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <select
            className="hm-input"
            value={order.status}
            onChange={e => changeStatus(e.target.value as OrderStatus)}
            disabled={updating}
            style={{ width: 180, padding: '8px 12px' }}
          >
            {ORDER_STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
          <button className="hm-btn hm-btn-quiet" onClick={() => comingSoon('הדפסת תווית')}>⎙ הדפסת תווית</button>
          <button
            className="hm-btn hm-btn-quiet"
            onClick={() => setRefundOpen(true)}
            disabled={!canRefund(order.status)}
            title={canRefund(order.status) ? undefined : 'אפשר החזר רק להזמנה ששולמה'}
          >
            החזר תשלום
          </button>
        </div>
      </div>

      {refundOpen && (
        <RefundModal
          order={order}
          onClose={() => setRefundOpen(false)}
          onDone={(updated) => { setOrder(updated); setRefundOpen(false) }}
        />
      )}

      {error && <div className="hm-error" style={{ marginBottom: 14 }}>{error}</div>}

      {(order.cancelledAt || order.refundedAt) && (
        <div className="adm-card" style={{ marginBottom: 14, borderInlineStart: '3px solid var(--terracotta, #b04a2f)' }}>
          <div className="hm-label" style={{ marginBottom: 8 }}>
            {order.status === 'REFUNDED' ? 'החזר תשלום' : 'הזמנה בוטלה'}
          </div>
          <div style={{ display: 'grid', gap: 4, fontSize: 13 }}>
            {order.refundedAt && (
              <div>
                <strong className="mono">{formatPrice(order.refundAmountAgorot ?? order.totalAgorot)}</strong>
                {order.refundAmountAgorot != null && order.refundAmountAgorot < order.totalAgorot
                  ? ' (החזר חלקי)' : ' (החזר מלא)'}
                {' · '}
                {new Date(order.refundedAt).toLocaleString('he-IL')}
              </div>
            )}
            {order.cancelledAt && !order.refundedAt && (
              <div>{new Date(order.cancelledAt).toLocaleString('he-IL')}</div>
            )}
            {order.cancelledBy && (
              <div style={{ color: 'var(--ink-3)' }}>
                בוטל על-ידי: {order.cancelledBy === 'CUSTOMER' ? 'הלקוח' : order.cancelledBy === 'ADMIN' ? 'מנהל' : 'מערכת'}
              </div>
            )}
            {order.cancellationReason && (
              <div style={{ color: 'var(--ink-2)' }}>סיבה: {order.cancellationReason}</div>
            )}
          </div>
        </div>
      )}

      {/* timeline */}
      <div className="adm-card" style={{ marginBottom: 14 }}>
        <h3 style={{ fontFamily: 'var(--serif)', fontSize: 18, marginBottom: 18 }}>מסלול הזמנה</h3>
        <div style={{ display: 'grid', gridTemplateColumns: `repeat(${STEPS.length}, 1fr)`, gap: 8, position: 'relative' }}>
          {STEPS.map((step, i) => {
            const s = stepState(step, order.status, i)
            return (
              <div key={step.key} style={{ textAlign: 'center', position: 'relative' }}>
                {i < STEPS.length - 1 && (
                  <div style={{
                    position: 'absolute', top: 14, insetInlineStart: '-50%', width: '100%', height: 2,
                    background: s === 'done' ? 'var(--olive)' : 'var(--line)', zIndex: 0,
                  }} />
                )}
                <div style={{
                  width: 30, height: 30, borderRadius: '50%',
                  background: s === 'active' ? 'var(--ink)' : s === 'done' ? 'var(--olive)' : 'var(--card)',
                  color: s ? 'var(--paper)' : 'var(--ink-3)',
                  border: s ? 'none' : '1px solid var(--line)',
                  margin: '0 auto', position: 'relative', zIndex: 1,
                  display: 'grid', placeItems: 'center', fontSize: 13, fontWeight: 600,
                }}>
                  {s === 'done' ? '✓' : i + 1}
                </div>
                <div style={{ fontSize: 13, fontWeight: s ? 600 : 400, marginTop: 8 }}>{step.label}</div>
              </div>
            )
          })}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1.7fr 1fr', gap: 14 }}>
        <div className="adm-card" style={{ padding: 0 }}>
          <div style={{ padding: '18px 22px', borderBottom: '1px solid var(--line)' }}>
            <h3 style={{ fontFamily: 'var(--serif)', fontSize: 18 }}>פריטים ({order.items.length})</h3>
          </div>
          <div>
            {order.items.map(it => (
              <div key={it.productId} style={{
                display: 'grid', gridTemplateColumns: '60px 1fr auto auto auto',
                gap: 16, alignItems: 'center',
                padding: '14px 22px', borderBottom: '1px solid var(--line)',
              }}>
                <div style={{
                  width: 60, height: 60, borderRadius: 'var(--r-md)',
                  background: 'var(--paper-2)', display: 'grid', placeItems: 'center',
                  fontFamily: 'var(--mono)', fontSize: 10, color: 'var(--ink-3)', letterSpacing: '0.1em', textAlign: 'center', padding: 4,
                }}>
                  {it.sku}
                </div>
                <div>
                  <div style={{ fontWeight: 500 }}>{it.nameHe}</div>
                  <div className="mono" style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>{it.sku}</div>
                </div>
                <div className="mono" style={{ color: 'var(--ink-3)' }}>{formatPrice(it.unitPriceAgorot)}</div>
                <div className="mono">× {it.quantity}</div>
                <div className="mono" style={{ fontWeight: 600, minWidth: 80, textAlign: 'start' }}>
                  {formatPrice(it.lineTotalAgorot)}
                </div>
              </div>
            ))}
          </div>
          <div style={{ padding: '14px 22px', display: 'grid', gap: 6 }}>
            <Row k="סך ביניים" v={formatPrice(order.subtotalAgorot)} />
            {order.discountAgorot > 0 && (
              <Row
                k={`הנחה${order.couponCode ? ` (${order.couponCode})` : ''}`}
                v={`-${formatPrice(order.discountAgorot)}`}
              />
            )}
            <Row k="משלוח" v={formatPrice(order.shippingAgorot)} />
            <div style={{
              display: 'flex', justifyContent: 'space-between', marginTop: 6,
              paddingTop: 10, borderTop: '1px solid var(--line)',
            }}>
              <strong>סך הכל</strong>
              <strong className="mono" style={{ fontSize: 18 }}>{formatPrice(order.totalAgorot)}</strong>
            </div>
          </div>
        </div>

        <div style={{ display: 'grid', gap: 14, height: 'fit-content' }}>
          {order.shipping && (
            <>
              <div className="adm-card">
                <div className="hm-label" style={{ marginBottom: 10 }}>לקוח</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <div style={{
                    width: 42, height: 42, borderRadius: '50%',
                    background: 'var(--terra-soft)', color: 'var(--terracotta)',
                    fontWeight: 700, display: 'grid', placeItems: 'center',
                  }}>{order.shipping.fullName?.[0] ?? '·'}</div>
                  <div>
                    <div style={{ fontWeight: 600 }}>{order.shipping.fullName}</div>
                    <div className="mono" style={{ fontSize: 12, color: 'var(--ink-3)' }}>{order.shipping.phone}</div>
                  </div>
                </div>
              </div>

              <div className="adm-card">
                <div className="hm-label" style={{ marginBottom: 10 }}>כתובת למשלוח</div>
                <div style={{ fontSize: 13, lineHeight: 1.6 }}>
                  {order.shipping.fullName}<br />
                  <span className="mono">{order.shipping.phone}</span><br />
                  רחוב {order.shipping.street} {order.shipping.houseNo}
                  {order.shipping.apartment && `, דירה ${order.shipping.apartment}`}<br />
                  {order.shipping.city}
                  {order.shipping.postalCode && <> <span className="mono">{order.shipping.postalCode}</span></>}
                </div>
                {order.shipping.notes && (
                  <div style={{
                    marginTop: 10, padding: 10, fontSize: 12,
                    background: 'var(--paper-2)', borderRadius: 'var(--r-sm)',
                    color: 'var(--ink-2)',
                  }}>
                    {order.shipping.notes}
                  </div>
                )}
              </div>
            </>
          )}

          <div className="adm-card">
            <div className="hm-label" style={{ marginBottom: 10 }}>תשלום</div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 13 }}>
              <span>Grow / Meshulam</span>
              <StatusPill s={order.status} />
            </div>
            <hr className="hm-rule" />
            <div className="mono" style={{ fontSize: 11.5, color: 'var(--ink-3)' }}>
              Webhook אמיתי יחובר כשנפעיל את Grow
            </div>
          </div>
        </div>
      </div>
    </>
  )
}

function Row({ k, v, muted }: { k: string; v: string; muted?: boolean }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', color: muted ? 'var(--ink-3)' : 'var(--ink)', fontSize: 14 }}>
      <span>{k}</span><span className="mono">{v}</span>
    </div>
  )
}

function canRefund(s: OrderStatus): boolean {
  return s === 'PAID' || s === 'FULFILLED' || s === 'SHIPPED' || s === 'DELIVERED'
}

function RefundModal({ order, onClose, onDone }: {
  order: OrderView
  onClose: () => void
  onDone: (updated: OrderView) => void
}) {
  const [amountStr, setAmountStr] = useState((order.totalAgorot / 100).toFixed(2))
  const [reason, setReason] = useState('')
  const [restoreStock, setRestoreStock] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setErr(null)
    const shek = Number(amountStr.replace(',', '.'))
    if (!isFinite(shek) || shek <= 0) { setErr('סכום לא תקין'); return }
    const amountAgorot = Math.round(shek * 100)
    if (amountAgorot > order.totalAgorot) {
      setErr('הסכום גבוה מסך ההזמנה'); return
    }
    const body: RefundRequest = { amountAgorot, reason: reason.trim() || undefined, restoreStock }
    setSubmitting(true)
    try {
      const updated = await api<OrderView>(`/api/admin/orders/${order.orderNumber}/refund`, {
        method: 'POST', body: JSON.stringify(body),
      })
      onDone(updated)
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'שגיאה')
      setSubmitting(false)
    }
  }

  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,.45)',
        display: 'grid', placeItems: 'center', zIndex: 100,
      }}
    >
      <form
        onClick={e => e.stopPropagation()}
        onSubmit={submit}
        style={{
          background: 'var(--card, #fff)', borderRadius: 'var(--r-md, 8px)',
          padding: 24, width: 'min(440px, 92vw)', boxShadow: '0 20px 60px rgba(0,0,0,.25)',
        }}
      >
        <h3 style={{ marginTop: 0, fontFamily: 'var(--serif)' }}>החזר תשלום</h3>
        <p style={{ fontSize: 13, color: 'var(--ink-3)', marginTop: 0 }}>
          הזמנה {order.orderNumber} · סך {formatPrice(order.totalAgorot)}
        </p>

        <label style={{ display: 'block', marginTop: 14 }}>
          <span className="hm-label">סכום החזר (₪)</span>
          <input
            className="hm-input mono"
            type="text"
            inputMode="decimal"
            value={amountStr}
            onChange={e => setAmountStr(e.target.value)}
            style={{ marginTop: 6 }}
            required
          />
        </label>

        <label style={{ display: 'block', marginTop: 12 }}>
          <span className="hm-label">סיבה (אופציונלי)</span>
          <textarea
            className="hm-input"
            rows={3}
            value={reason}
            onChange={e => setReason(e.target.value)}
            style={{ marginTop: 6, resize: 'vertical' }}
            maxLength={500}
          />
        </label>

        <label style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 14, fontSize: 13 }}>
          <input
            type="checkbox"
            checked={restoreStock}
            onChange={e => setRestoreStock(e.target.checked)}
          />
          החזרת מלאי ושחרור קופון
        </label>

        {err && <div className="hm-error" style={{ marginTop: 12 }}>{err}</div>}

        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 18 }}>
          <button type="button" className="hm-btn hm-btn-quiet" onClick={onClose} disabled={submitting}>
            ביטול
          </button>
          <button type="submit" className="hm-btn hm-btn-primary" disabled={submitting}>
            {submitting ? 'מבצע…' : 'אישור החזר'}
          </button>
        </div>
      </form>
    </div>
  )
}
