import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams, useLocation, Link } from 'react-router-dom'
import { api, rememberGuestOrder, getGuestOrderToken } from '../api'
import { Footer } from '../components/Footer'

// Landing page for the PayPal redirect. On /payment/return it captures the
// approved order server-side and forwards to the confirmation page; on
// /payment/cancel it just bounces back to the (still-PENDING) order.
export function PaymentReturnPage() {
  const [params] = useSearchParams()
  const loc = useLocation()
  const nav = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const ran = useRef(false)

  const orderNumber = params.get('order') ?? ''
  const isCancel = loc.pathname === '/payment/cancel'
  // The guest token may already be in sessionStorage; the return URL also
  // carries it as ?t= so a cross-device return still authorises.
  const guestToken = getGuestOrderToken(orderNumber) ?? params.get('t')
  const suffix = guestToken ? `&t=${encodeURIComponent(guestToken)}` : ''

  useEffect(() => {
    if (ran.current) return
    ran.current = true
    if (!orderNumber) { nav('/'); return }

    const urlToken = params.get('t')
    if (urlToken) rememberGuestOrder(orderNumber, urlToken)

    if (isCancel) {
      nav(`/orders/${orderNumber}?cancelled=1${suffix}`, { replace: true })
      return
    }

    const paypalOrderId = params.get('token') // PayPal's order id on return

    // Don't trust the capture response to always arrive: PayPal also fires a
    // webhook that marks the order PAID server-side, so a lost/slow capture
    // response shouldn't strand the buyer on the spinner forever. After 15s,
    // bounce to the order page (no ?paid flag — it renders the real status,
    // which the webhook has likely already flipped to PAID).
    const orderHref = `/orders/${orderNumber}${guestToken ? `?t=${encodeURIComponent(guestToken)}` : ''}`
    let settled = false
    const fallback = setTimeout(() => {
      if (settled) return
      settled = true
      nav(orderHref, { replace: true })
    }, 15000)

    ;(async () => {
      try {
        const res = await api<{ orderNumber: string; status: string }>(
          `/api/payments/paypal/${orderNumber}/capture`,
          { method: 'POST', body: JSON.stringify({ paypalOrderId }) },
        )
        if (settled) return
        settled = true
        clearTimeout(fallback)
        if (res.status === 'PAID') {
          nav(`/orders/${orderNumber}?paid=1${suffix}`, { replace: true })
        } else {
          setError('התשלום לא הושלם. אפשר לנסות שוב מעמוד ההזמנה.')
        }
      } catch (e) {
        if (settled) return
        settled = true
        clearTimeout(fallback)
        setError(e instanceof Error ? e.message : 'שגיאה באימות התשלום')
      }
    })()

    return () => { settled = true; clearTimeout(fallback) }
  }, [orderNumber, isCancel, nav, params, suffix, guestToken])

  return (
    <>
      <div className="hm-page-narrow" style={{ textAlign: 'center', paddingTop: 60 }}>
        {error ? (
          <>
            <div className="hm-error">{error}</div>
            <Link
              to={`/orders/${orderNumber}${guestToken ? `?t=${encodeURIComponent(guestToken)}` : ''}`}
              className="hm-btn hm-btn-quiet"
              style={{ marginTop: 14 }}
            >
              חזרה להזמנה
            </Link>
          </>
        ) : (
          <p style={{ color: 'var(--ink-3)' }}>{isCancel ? 'חוזרים…' : 'מאמתים את התשלום…'}</p>
        )}
      </div>
      <Footer />
    </>
  )
}
