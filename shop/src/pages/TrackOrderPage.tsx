import { useEffect, useRef, useState } from 'react'
import { useSearchParams, Link } from 'react-router-dom'
import { api, formatPrice, type OrderView } from '../api'
import { Icon } from '../components/Icon'
import { Footer } from '../components/Footer'
import { useAuth } from '../auth/authStore'

const FLOW: { key: OrderView['status']; label: string }[] = [
  { key: 'PENDING',   label: 'ממתין לתשלום' },
  { key: 'PAID',      label: 'שולם' },
  { key: 'FULFILLED', label: 'בהכנה' },
  { key: 'SHIPPED',   label: 'במשלוח' },
  { key: 'DELIVERED', label: 'נמסר' },
]
const TERMINAL_LABELS: Partial<Record<OrderView['status'], string>> = {
  CANCELLED: 'ההזמנה בוטלה',
  REFUNDED:  'בוצע החזר תשלום',
}

export function TrackOrderPage() {
  const { user } = useAuth()
  const [params] = useSearchParams()
  const focusOrder = params.get('orderNumber')
  const [orders, setOrders] = useState<OrderView[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const focusRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (!user) return
    api<OrderView[]>('/api/orders')
      .then(setOrders)
      .catch(e => setError(e.message))
  }, [user])

  useEffect(() => {
    if (orders && focusOrder && focusRef.current) {
      focusRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }, [orders, focusOrder])

  if (!user) {
    return (
      <>
        <div className="cls-page-narrow">
          <div className="cls-track-hero">
            <div className="eyebrow">מעקב הזמנה</div>
            <h1>נדרשת התחברות</h1>
            <p>כדי לראות את ההזמנות שלך והסטטוס העדכני, התחבר לחשבון.</p>
          </div>
          <div style={{ display: 'flex', gap: 10, justifyContent: 'center', marginTop: 18 }}>
            <Link
              to="/login?next=/track"
              style={{
                background: 'var(--ink)', color: '#fff',
                borderRadius: 'var(--r-md)', padding: '12px 26px',
                fontWeight: 700, fontSize: 14,
                display: 'inline-flex', alignItems: 'center', gap: 8,
              }}
            >
              התחברות
              <Icon name="arrow" size={14} stroke={2.2} />
            </Link>
            <Link
              to="/register"
              style={{
                background: '#fff', color: 'var(--ink)',
                border: '1px solid var(--line-2)',
                borderRadius: 'var(--r-md)', padding: '12px 26px',
                fontWeight: 700, fontSize: 14,
              }}
            >
              פתיחת חשבון
            </Link>
          </div>
        </div>
        <Footer />
      </>
    )
  }

  return (
    <>
      <div className="cls-page-narrow">
        <div className="cls-track-hero">
          <div className="eyebrow">החשבון שלי</div>
          <h1>ההזמנות שלי</h1>
          <p>סטטוס עדכני של כל הזמנה. לחיצה על מספר הזמנה מציגה את פרטיה המלאים.</p>
        </div>

        {error && <div className="cls-track-form err" style={{ marginBottom: 16 }}>{error}</div>}

        {!orders && !error && (
          <p style={{ textAlign: 'center', color: 'var(--ink-3)' }}>טוען…</p>
        )}

        {orders && orders.length === 0 && (
          <div className="cls-confirm-card" style={{ textAlign: 'center', padding: 40 }}>
            <h3 style={{ fontSize: 18, marginBottom: 8 }}>עדיין אין כאן הזמנות</h3>
            <p style={{ color: 'var(--ink-3)', marginBottom: 18 }}>
              אחרי הקנייה הראשונה ההזמנות יופיעו כאן.
            </p>
            <Link
              to="/"
              style={{
                background: 'var(--ink)', color: '#fff',
                borderRadius: 'var(--r-md)', padding: '12px 24px',
                fontWeight: 700, fontSize: 14,
                display: 'inline-flex', alignItems: 'center', gap: 8,
              }}
            >
              לקטלוג
              <Icon name="arrow" size={14} stroke={2.2} />
            </Link>
          </div>
        )}

        {orders && orders.length > 0 && (
          <div style={{ display: 'grid', gap: 22 }}>
            {orders.map(o => (
              <OrderCard
                key={o.orderNumber}
                order={o}
                highlight={o.orderNumber === focusOrder}
                cardRef={o.orderNumber === focusOrder ? focusRef : null}
              />
            ))}
          </div>
        )}
      </div>
      <Footer />
    </>
  )
}

function OrderCard({
  order,
  highlight,
  cardRef,
}: {
  order: OrderView
  highlight: boolean
  cardRef: React.RefObject<HTMLDivElement> | null
}) {
  const [expanded, setExpanded] = useState(highlight)

  return (
    <div
      ref={cardRef}
      style={{
        outline: highlight ? '2px solid var(--accent)' : 'none',
        outlineOffset: 0,
        borderRadius: 'var(--r-lg)',
      }}
    >
      <div className="cls-confirm-card">
        <div className="head">
          <div>
            <h3>
              הזמנה <span className="mono">#{order.orderNumber}</span>
            </h3>
            <div className="when">
              {new Date(order.createdAt).toLocaleString('he-IL', {
                day: '2-digit', month: '2-digit', year: 'numeric',
                hour: '2-digit', minute: '2-digit',
              })}
              {' · '}
              <span className="mono" style={{ fontWeight: 700, color: 'var(--ink-2)' }}>
                {formatPrice(order.totalAgorot)}
              </span>
              {' · '}
              {order.items.reduce((s, i) => s + i.quantity, 0)} פריטים
            </div>
          </div>
          <span className={`cls-confirm-status ${order.status.toLowerCase()}`}>{order.status}</span>
        </div>

        <div style={{ marginTop: 16 }}>
          <Timeline status={order.status} />
        </div>

        <button
          onClick={() => setExpanded(e => !e)}
          style={{
            marginTop: 16,
            background: 'transparent',
            border: 'none',
            color: 'var(--ink-2)',
            fontSize: 13,
            fontWeight: 700,
            cursor: 'pointer',
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          {expanded ? 'הסתר פרטים' : 'הצג פרטים'}
          <span style={{ display: 'inline-flex', transform: expanded ? 'rotate(180deg)' : 'none', transition: 'transform .2s' }}>
            <Icon name="chev-d" size={14} stroke={2.2} />
          </span>
        </button>

        {expanded && (
          <>
            <hr style={{ border: 'none', borderTop: '1px solid var(--line)', margin: '14px 0' }} />
            <div style={{ display: 'grid', gap: 10 }}>
              {order.items.map(it => (
                <div key={it.productId} className="cls-mini-line">
                  <div className="thumb"><span className="ph">{it.sku}</span></div>
                  <div className="info">
                    <div className="n">{it.nameHe}</div>
                    <div className="q">× {it.quantity} · {formatPrice(it.unitPriceAgorot)} ליחידה</div>
                  </div>
                  <div className="v">{formatPrice(it.lineTotalAgorot)}</div>
                </div>
              ))}
            </div>
            <hr style={{ border: 'none', borderTop: '1px solid var(--line)', margin: '12px 0' }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', fontSize: 13 }}>
              <span>סך ביניים</span>
              <span className="mono" style={{ fontWeight: 700 }}>{formatPrice(order.subtotalAgorot)}</span>
            </div>
            {order.discountAgorot > 0 && (
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', fontSize: 13, color: 'var(--olive, #5d7a3a)' }}>
                <span>הנחה{order.couponCode ? ` (${order.couponCode})` : ''}</span>
                <span className="mono" style={{ fontWeight: 700 }}>-{formatPrice(order.discountAgorot)}</span>
              </div>
            )}
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', fontSize: 13 }}>
              <span>משלוח</span>
              <span className="mono" style={{ fontWeight: 700 }}>{formatPrice(order.shippingAgorot)}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginTop: 8 }}>
              <strong style={{ fontSize: 14 }}>סך הכל</strong>
              <strong className="mono" style={{ fontSize: 20, fontWeight: 800 }}>
                {formatPrice(order.totalAgorot)}
              </strong>
            </div>
            {order.shipping && (
              <div style={{
                marginTop: 14, padding: '12px 14px',
                background: 'var(--surface)', borderRadius: 'var(--r-md)',
                fontSize: 12.5, color: 'var(--ink-2)', lineHeight: 1.6,
              }}>
                <div style={{ fontWeight: 700, color: 'var(--ink)', marginBottom: 4 }}>כתובת למשלוח</div>
                {order.shipping.fullName} · <span className="mono">{order.shipping.phone}</span><br />
                רחוב {order.shipping.street} {order.shipping.houseNo}
                {order.shipping.apartment && `, דירה ${order.shipping.apartment}`}<br />
                {order.shipping.city}
                {order.shipping.postalCode && ` · ${order.shipping.postalCode}`}
              </div>
            )}
            {order.status !== 'PENDING' && order.status !== 'CANCELLED' && (
              <div style={{ marginTop: 14, textAlign: 'center' }}>
                <Link
                  to={`/invoice/${order.orderNumber}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{
                    background: '#fff', color: 'var(--ink)',
                    border: '1px solid var(--line-2)',
                    borderRadius: 'var(--r-md)', padding: '10px 18px',
                    fontWeight: 700, fontSize: 13,
                    display: 'inline-flex', alignItems: 'center', gap: 8,
                  }}
                >
                  <Icon name="pkg" size={14} stroke={2.2} />
                  הורד חשבונית
                </Link>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}

function Timeline({ status }: { status: OrderView['status'] }) {
  if (status === 'CANCELLED' || status === 'REFUNDED') {
    return <div className="cls-timeline-terminal">{TERMINAL_LABELS[status]}</div>
  }
  const idx = FLOW.findIndex(s => s.key === status)
  const safeIdx = idx < 0 ? 0 : idx
  const barWidth = safeIdx === 0 ? 0 : (safeIdx / (FLOW.length - 1)) * 100

  return (
    <div className="cls-timeline">
      <div className="row">
        <span className="bar" style={{ insetInlineStart: '8%', width: `calc(${barWidth}% * 0.84)` }} />
        {FLOW.map((step, i) => {
          const cls = i < safeIdx ? 'done' : i === safeIdx ? 'current' : ''
          return (
            <div key={step.key} className={`node ${cls}`}>
              <div className="dot">
                {i < safeIdx ? <Icon name="check" size={14} stroke={2.6} /> : i + 1}
              </div>
              <div className="label">{step.label}</div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
