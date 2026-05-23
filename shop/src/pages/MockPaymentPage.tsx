import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams, Link } from 'react-router-dom'
import { api, formatPrice, getGuestOrderToken, rememberGuestOrder, type OrderView } from '../api'
import { Footer } from '../components/Footer'
import { Icon } from '../components/Icon'

// Simulated hosted-checkout page. Replace the entire page with a window.location
// redirect to Grow's hosted URL when wiring the real provider.
export function MockPaymentPage() {
  const [params] = useSearchParams()
  const orderNumber = params.get('order') ?? ''
  const nav = useNavigate()
  const [order, setOrder] = useState<OrderView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!orderNumber) { nav('/'); return }
    // The pay-redirect URL carries ?t={guestToken} for anonymous checkouts —
    // stash it before fetching so the order load + complete calls authorise.
    const urlToken = params.get('t')
    if (urlToken) rememberGuestOrder(orderNumber, urlToken)
    api<OrderView>(`/api/orders/${orderNumber}`)
      .then(setOrder)
      .catch(e => setError(e.message))
  }, [orderNumber, nav, params])

  async function complete(outcome: 'success' | 'cancel') {
    setBusy(true)
    setError(null)
    try {
      await api(`/api/payments/mock/${orderNumber}/complete`, {
        method: 'POST',
        body: JSON.stringify({ outcome }),
      })
      const guestToken = getGuestOrderToken(orderNumber)
      const qs = new URLSearchParams()
      if (outcome === 'success') qs.set('paid', '1')
      else qs.set('cancelled', '1')
      if (guestToken) qs.set('t', guestToken)
      nav(`/orders/${orderNumber}?${qs.toString()}`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
      setBusy(false)
    }
  }

  if (error) {
    return (
      <div className="hm-page-narrow">
        <div className="hm-error">{error}</div>
        <Link to="/" className="hm-btn hm-btn-quiet" style={{ marginTop: 14 }}>חזרה לחנות</Link>
      </div>
    )
  }
  if (!order) return <div className="hm-page-narrow"><p style={{ color: 'var(--ink-3)' }}>טוען…</p></div>

  return (
    <>
      <div className="hm-page-narrow">
        <div className="hm-crumb">
          <span style={{ color: 'var(--ink-3)' }}>שער תשלום מאובטח · סביבת בדיקה</span>
        </div>
        <h1 style={{ fontSize: 36, marginBottom: 6 }}>אישור תשלום</h1>
        <p className="hm-meta" style={{ marginBottom: 22 }}>
          הזמנה <span className="mono">{order.orderNumber}</span>
        </p>

        <div className="hm-card" style={{ marginBottom: 18 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14 }}>
            <div style={{
              width: 40, height: 40, borderRadius: '50%',
              background: 'var(--olive-soft)', color: 'var(--olive-2)',
              display: 'grid', placeItems: 'center',
            }}>
              <Icon name="secure" size={20} />
            </div>
            <div>
              <div style={{ fontWeight: 600 }}>Grow · Meshulam</div>
              <div className="hm-meta">סביבת sandbox · אין חיוב אמיתי</div>
            </div>
          </div>

          <hr className="hm-rule" />

          {order.items.map(i => (
            <div key={i.productId} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13.5, padding: '6px 0' }}>
              <span>{i.nameHe} <span className="hm-meta">× {i.quantity}</span></span>
              <span className="mono">{formatPrice(i.lineTotalAgorot)}</span>
            </div>
          ))}

          <hr className="hm-rule" />

          <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700, fontSize: 17 }}>
            <span>סה״כ לתשלום</span>
            <span className="mono">{formatPrice(order.totalAgorot)}</span>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 10, flexDirection: 'column' }}>
          <button
            type="button"
            className="hm-btn hm-btn-primary hm-btn-lg"
            disabled={busy}
            onClick={() => complete('success')}
            style={{ justifyContent: 'center' }}
          >
            {busy ? 'מעבד…' : `שלם ${formatPrice(order.totalAgorot)}`}
          </button>
          <button
            type="button"
            className="hm-btn hm-btn-ghost"
            disabled={busy}
            onClick={() => complete('cancel')}
            style={{ justifyContent: 'center' }}
          >
            ביטול
          </button>
        </div>

        <p className="hm-meta" style={{ marginTop: 18, textAlign: 'center' }}>
          זוהי סביבת בדיקה. בפרודקשן הלחיצה תפתח את שער התשלום של Grow ותחזיר אותך לחנות.
        </p>
      </div>
      <Footer />
    </>
  )
}
