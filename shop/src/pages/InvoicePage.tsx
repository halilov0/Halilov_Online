import { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import { api, ApiError, formatPrice, getGuestOrderToken, rememberGuestOrder, type OrderView } from '../api'
import { Icon } from '../components/Icon'
import { QuickLogin } from '../components/QuickLogin'
import { ShareMenu } from '../components/ShareMenu'
import { useToast } from '../components/Toast'
import { useAuth } from '../auth/authStore'
import { generateInvoicePdfBlob } from '../lib/invoicePdf'

export function InvoicePage() {
  const { orderNumber } = useParams<{ orderNumber: string }>()
  const [searchParams] = useSearchParams()
  const nav = useNavigate()
  const [order, setOrder] = useState<OrderView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [needsLogin, setNeedsLogin] = useState<'unauth' | 'wrong-account' | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [shareOpen, setShareOpen] = useState(false)
  const [shareUrl, setShareUrl] = useState<string | null>(null)
  const [sharing, setSharing] = useState(false)
  const { user } = useAuth()
  const invoiceRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    if (!orderNumber) return
    const urlToken = searchParams.get('t')
    if (urlToken) rememberGuestOrder(orderNumber, urlToken)
    setError(null); setNeedsLogin(null)
    api<OrderView>(`/api/orders/${orderNumber}`)
      .then(setOrder)
      .catch(e => {
        if (e instanceof ApiError && e.status === 401) {
          setNeedsLogin('unauth')
        } else if (e instanceof ApiError && e.status === 403) {
          setNeedsLogin('wrong-account')
        } else {
          setError(e.message)
        }
      })
  }, [orderNumber, searchParams, reloadKey])

  // Auto-open print dialog once the order data is rendered.
  // Small timeout lets the layout settle before print preview captures it.
  useEffect(() => {
    if (!order) return
    const t = setTimeout(() => window.print(), 250)
    return () => clearTimeout(t)
  }, [order])

  async function openShare() {
    if (!order) return
    // Guest orders already have a token in their URL; reuse it. Registered
    // owners need to mint a one-time share token via the backend so the
    // recipient can view the invoice without our password.
    let token = getGuestOrderToken(order.orderNumber)
    if (!token && user) {
      try {
        setSharing(true)
        const res = await api<{ shareToken: string }>(`/api/orders/${order.orderNumber}/share`, { method: 'POST' })
        token = res.shareToken
      } catch (e) {
        useToast.getState().push(e instanceof Error ? e.message : 'שגיאה ביצירת קישור שיתוף')
        setSharing(false)
        return
      } finally {
        setSharing(false)
      }
    }
    const base = `${window.location.origin}/invoice/${order.orderNumber}`
    setShareUrl(token ? `${base}?t=${encodeURIComponent(token)}` : base)
    setShareOpen(true)
  }

  if (needsLogin) {
    const isMismatch = needsLogin === 'wrong-account'
    return (
      <div className="cls-page-narrow">
        <QuickLogin
          heading={isMismatch ? 'החשבונית משויכת לחשבון אחר' : undefined}
          message={isMismatch
            ? 'ההזמנה הזו לא משויכת לחשבון שאיתו אתם מחוברים. התחברו עם החשבון שביצע את ההזמנה.'
            : 'ההזמנה משויכת לחשבון. התחברו כדי להוריד את החשבונית.'}
          onSuccess={() => { setNeedsLogin(null); setReloadKey(k => k + 1) }}
        />
      </div>
    )
  }
  if (error) {
    return (
      <div className="cls-page-narrow">
        <div className="hm-error">{error}</div>
        <button onClick={() => nav(-1)} className="cls-back-btn">חזרה</button>
      </div>
    )
  }
  if (!order) {
    return <div className="cls-page-narrow"><p style={{ color: 'var(--ink-3)' }}>טוען…</p></div>
  }

  const issuedAt = new Date(order.createdAt)

  return (
    <div className="cls-invoice-wrap">
      <div className="cls-invoice-actions no-print">
        <button onClick={() => window.print()} className="primary">
          <Icon name="pkg" size={14} stroke={2.2} />
          הדפס / שמור כ-PDF
        </button>
        <button onClick={openShare} disabled={sharing}>
          <Icon name="share" size={14} stroke={2.2} />
          {sharing ? 'יוצר קישור…' : 'שיתוף'}
        </button>
        <button onClick={() => nav(-1)}>חזרה</button>
      </div>
      {shareOpen && shareUrl && (
        <ShareMenu
          open={shareOpen}
          onClose={() => setShareOpen(false)}
          url={shareUrl}
          title={`חשבונית ${order.orderNumber} · חלילוב אונליין`}
          message={`חשבונית מהזמנה ${order.orderNumber} בחלילוב אונליין`}
          getPdf={async () => {
            if (!invoiceRef.current) throw new Error('החשבונית עוד לא נטענה')
            return generateInvoicePdfBlob(invoiceRef.current, order.orderNumber)
          }}
        />
      )}

      <article className="cls-invoice" lang="he" dir="rtl" ref={invoiceRef}>
        <header className="hdr">
          <div className="brand">
            <span className="mark">ח</span>
            <div>
              <div className="name">חלילוב אונליין</div>
              <div className="sub">כל מה שצריך · במקום אחד</div>
            </div>
          </div>
          <div className="biz">
            <div>חלילוב אונליין</div>
            <div>עוסק פטור 325350643</div>
            <div>halilov.store@gmail.com</div>
          </div>
        </header>

        <div className="title-row">
          <h1>קבלה / חשבונית</h1>
          <div className="meta">
            <div><span>מספר</span> <strong className="mono">#{order.orderNumber}</strong></div>
            <div>
              <span>תאריך</span>{' '}
              <strong>
                {issuedAt.toLocaleDateString('he-IL', { day: '2-digit', month: '2-digit', year: 'numeric' })}
              </strong>
            </div>
            <div><span>סטטוס</span> <strong>{statusLabel(order.status)}</strong></div>
          </div>
        </div>

        {order.shipping && (
          <section className="ship">
            <div className="block">
              <div className="lbl">לכבוד</div>
              <div className="val">
                {order.shipping.fullName}<br />
                רחוב {order.shipping.street} {order.shipping.houseNo}
                {order.shipping.apartment && `, דירה ${order.shipping.apartment}`}<br />
                {order.shipping.city}
                {order.shipping.postalCode && ` ${order.shipping.postalCode}`}<br />
                <span className="mono">{order.shipping.phone}</span>
              </div>
            </div>
          </section>
        )}

        <table className="items">
          <thead>
            <tr>
              <th className="right">פריט</th>
              <th>מק"ט</th>
              <th>כמות</th>
              <th>מחיר יחידה</th>
              <th className="left">סכום</th>
            </tr>
          </thead>
          <tbody>
            {order.items.map(it => (
              <tr key={it.productId}>
                <td className="right">{it.nameHe}</td>
                <td className="mono">{it.sku}</td>
                <td className="center">{it.quantity}</td>
                <td className="mono">{formatPrice(it.unitPriceAgorot)}</td>
                <td className="left mono">{formatPrice(it.lineTotalAgorot)}</td>
              </tr>
            ))}
          </tbody>
        </table>

        <section className="totals">
          <div className="row"><span>סך ביניים</span><span className="mono">{formatPrice(order.subtotalAgorot)}</span></div>
          {order.discountAgorot > 0 && (
            <div className="row"><span>הנחה{order.couponCode ? ` (${order.couponCode})` : ''}</span><span className="mono">-{formatPrice(order.discountAgorot)}</span></div>
          )}
          <div className="row"><span>משלוח</span><span className="mono">{order.shippingAgorot === 0 ? 'חינם' : formatPrice(order.shippingAgorot)}</span></div>
          <div className="row sum"><span>סך הכל לתשלום</span><span className="mono">{formatPrice(order.totalAgorot)}</span></div>
        </section>

        <footer className="ftr">
          <div>תודה שקנית בחלילוב אונליין.</div>
          <div className="fine">
            מסמך זה הופק אוטומטית ואינו דורש חתימה. בשאלות פנו ל-halilov.store@gmail.com.
          </div>
        </footer>
      </article>
    </div>
  )
}

function statusLabel(s: OrderView['status']): string {
  switch (s) {
    case 'PENDING':   return 'ממתין לתשלום'
    case 'PAID':      return 'שולם'
    case 'FULFILLED': return 'בהכנה'
    case 'SHIPPED':   return 'נשלח'
    case 'DELIVERED': return 'נמסר'
    case 'CANCELLED': return 'בוטל'
    case 'REFUNDED':  return 'הוחזר'
  }
}
