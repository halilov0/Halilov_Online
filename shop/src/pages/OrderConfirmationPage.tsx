import { useEffect, useState } from 'react'
import { Link, useParams, useNavigate } from 'react-router-dom'
import { api, formatPrice, type OrderView } from '../api'
import { Icon } from '../components/Icon'
import { Footer } from '../components/Footer'
import { useAuth } from '../auth/authStore'

export function OrderConfirmationPage() {
  const { orderNumber } = useParams<{ orderNumber: string }>()
  const [order, setOrder] = useState<OrderView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const { user } = useAuth()
  const nav = useNavigate()

  useEffect(() => {
    if (!orderNumber) return
    api<OrderView>(`/api/orders/${orderNumber}`)
      .then(setOrder)
      .catch(e => setError(e.message))
  }, [orderNumber])

  if (error) return <div className="cls-page"><div className="hm-error">{error}</div></div>
  if (!order) return <div className="cls-page"><p style={{ color: 'var(--ink-3)' }}>טוען…</p></div>

  const greetingName = user?.fullName?.split(' ')[0] ?? ''
  const statusLower = order.status.toLowerCase()
  const isCancelled = order.status === 'CANCELLED'

  return (
    <>
      <div className="cls-page-narrow">
        <div className={`cls-confirm-hero${isCancelled ? ' cancelled' : ''}`}>
          <div className="check">
            <Icon name={isCancelled ? 'x' : 'check'} size={32} stroke={2.6} />
          </div>
          <div className="eyebrow">{isCancelled ? 'ההזמנה בוטלה' : 'הזמנה התקבלה'}</div>
          <h1>{isCancelled
            ? 'ההזמנה בוטלה'
            : `תודה${greetingName ? `, ${greetingName}` : ''}!`}</h1>
          <p>
            הזמנה <span className="mono" style={{ color: 'var(--ink)', fontWeight: 700 }}>#{order.orderNumber}</span>
            {isCancelled
              ? <> · החיוב לא בוצע. ניתן לחזור לקטלוג ולנסות שוב.</>
              : (user && <> · נשלח אישור אל {user.email}</>)}
          </p>
        </div>

        <div className="cls-confirm-card">
          <div className="head">
            <div>
              <h3>סטטוס הזמנה</h3>
              <div className="when">
                נוצר ב-{new Date(order.createdAt).toLocaleString('he-IL', {
                  day: '2-digit', month: '2-digit', year: 'numeric',
                  hour: '2-digit', minute: '2-digit',
                })}
              </div>
            </div>
            <span className={`cls-confirm-status ${statusLower}`}>{order.status}</span>
          </div>

          <div style={{ display: 'grid', gap: 12, marginTop: 18 }}>
            {order.items.map(it => (
              <div key={it.productId} className="cls-mini-line">
                <div className="thumb">
                  <span className="ph">{it.sku}</span>
                </div>
                <div className="info">
                  <div className="n">{it.nameHe}</div>
                  <div className="q">× {it.quantity} · {formatPrice(it.unitPriceAgorot)} ליחידה</div>
                </div>
                <div className="v">{formatPrice(it.lineTotalAgorot)}</div>
              </div>
            ))}
          </div>

          <hr style={{ border: 'none', borderTop: '1px solid var(--line)', margin: '14px 0' }} />
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', fontSize: 13.5 }}>
            <span>סך ביניים</span>
            <span className="mono" style={{ fontWeight: 700 }}>{formatPrice(order.subtotalAgorot)}</span>
          </div>
          {order.discountAgorot > 0 && (
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', fontSize: 13.5, color: 'var(--olive, #5d7a3a)' }}>
              <span>הנחה{order.couponCode ? ` (${order.couponCode})` : ''}</span>
              <span className="mono" style={{ fontWeight: 700 }}>-{formatPrice(order.discountAgorot)}</span>
            </div>
          )}
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', fontSize: 13.5 }}>
            <span>משלוח</span>
            <span className="mono" style={{ fontWeight: 700 }}>{formatPrice(order.shippingAgorot)}</span>
          </div>
          <hr style={{ border: 'none', borderTop: '1px solid var(--line)', margin: '10px 0' }} />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginTop: 6 }}>
            <strong style={{ fontSize: 15 }}>סך הכל</strong>
            <strong className="mono" style={{ fontSize: 24, fontWeight: 800 }}>{formatPrice(order.totalAgorot)}</strong>
          </div>
        </div>

        {order.shipping && (
          <div className="cls-ship-card">
            <div className="ico-circle"><Icon name="truck" size={20} /></div>
            <div style={{ flex: 1 }}>
              <h4 style={{ marginBottom: 4 }}>כתובת למשלוח</h4>
              <div style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6 }}>
                {order.shipping.fullName} · <span className="mono">{order.shipping.phone}</span><br />
                רחוב {order.shipping.street} {order.shipping.houseNo}
                {order.shipping.apartment && `, דירה ${order.shipping.apartment}`}<br />
                {order.shipping.city}
                {order.shipping.postalCode && ` · ${order.shipping.postalCode}`}
                {order.shipping.notes && (
                  <><br /><em style={{ color: 'var(--ink-3)' }}>"{order.shipping.notes}"</em></>
                )}
              </div>
            </div>
            {!isCancelled && (
              <button
                onClick={() => nav(`/track?orderNumber=${encodeURIComponent(order.orderNumber)}`)}
                style={{
                  background: '#fff', border: '1px solid var(--line-2)',
                  borderRadius: 'var(--r-md)', padding: '9px 16px',
                  fontWeight: 700, fontSize: 12.5, cursor: 'pointer',
                  color: 'var(--ink)',
                }}
              >
                עקוב
              </button>
            )}
          </div>
        )}

        <div style={{ display: 'flex', gap: 10, marginTop: 28, justifyContent: 'center', flexWrap: 'wrap' }}>
          <Link
            to="/"
            style={{
              background: 'var(--ink)', color: '#fff',
              borderRadius: 'var(--r-md)', padding: '12px 24px',
              fontWeight: 700, fontSize: 14,
              display: 'inline-flex', alignItems: 'center', gap: 8,
            }}
          >
            המשך קניות
            <Icon name="arrow" size={14} stroke={2.2} />
          </Link>
          {order.status !== 'PENDING' && order.status !== 'CANCELLED' && (
            <Link
              to={`/invoice/${order.orderNumber}`}
              target="_blank"
              rel="noopener noreferrer"
              style={{
                background: '#fff', color: 'var(--ink)',
                border: '1px solid var(--line-2)',
                borderRadius: 'var(--r-md)', padding: '12px 24px',
                fontWeight: 700, fontSize: 14,
              }}
            >
              הורד חשבונית
            </Link>
          )}
        </div>
      </div>
      <Footer />
    </>
  )
}
