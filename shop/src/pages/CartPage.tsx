import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useCart } from '../cart/cartStore'
import { formatPrice } from '../api'
import { useDeliveryConfig } from '../delivery/deliveryConfig'
import { Icon } from '../components/Icon'
import { Footer } from '../components/Footer'

export function CartPage() {
  const {
    lines, setQty, remove, subtotalAgorot, adjustments, dismissAdjustment,
    coupon, couponCode, couponError, applyCoupon, removeCoupon, revalidateCoupon,
  } = useCart()
  const courierFlatAgorot = useDeliveryConfig(s => s.courierFlatAgorot)
  const freeAboveAgorot = useDeliveryConfig(s => s.freeAboveAgorot)
  const nav = useNavigate()

  const [couponInput, setCouponInput] = useState('')
  const [applying, setApplying] = useState(false)

  const subtotal = subtotalAgorot()
  const discount = coupon ? Math.min(coupon.discountAgorot, subtotal) : 0
  const discountedSubtotal = Math.max(0, subtotal - discount)
  const freeShipping = coupon?.freeShipping ?? false
  const shipping = freeShipping || discountedSubtotal >= freeAboveAgorot ? 0 : courierFlatAgorot
  const total = discountedSubtotal + shipping
  const toFree = freeShipping ? 0 : Math.max(0, freeAboveAgorot - discountedSubtotal)
  const totalItems = lines.reduce((s, l) => s + l.quantity, 0)

  // Re-validate a persisted coupon against the live subtotal whenever it
  // changes (qty +/-, line removed) — it may now clear or fail the minimum.
  useEffect(() => {
    if (couponCode) void revalidateCoupon()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subtotal, couponCode])

  async function onApply() {
    const code = couponInput.trim()
    if (!code) return
    setApplying(true)
    const ok = await applyCoupon(code)
    setApplying(false)
    if (ok) setCouponInput('')
  }

  // Inline notices for changes the server made (so the cart never mutates
  // silently): removed lines surface as a banner, clamps as a per-line note.
  const removed = adjustments.filter(a => a.type === 'REMOVED')
  const clampByProduct = new Map(
    adjustments.filter(a => a.type === 'CLAMPED').map(a => [a.productId, a]),
  )
  const removedBanner = removed.length > 0 && (
    <div className="cls-cart-alerts">
      {removed.map(a => (
        <div key={a.productId} className="cls-cart-alert removed">
          <span className="txt">{a.nameHe ?? 'פריט'} הוסר מהעגלה — אינו זמין במלאי</span>
          <button
            type="button"
            className="dismiss"
            onClick={() => dismissAdjustment(a.productId)}
            aria-label="סגירת ההודעה"
          >
            <Icon name="x" size={14} stroke={2.2} />
          </button>
        </div>
      ))}
    </div>
  )

  // The applied-coupon chip / code-entry box, shared between empty-cart guard
  // and the normal summary.
  const couponBox = (
    <div className="coupon-row">
      <div className="lbl">קוד הטבה</div>
      {coupon ? (
        <div className="applied">
          <span className="tag">{coupon.code}</span>
          <span className="meta">{coupon.summary || 'הנחה'}</span>
          <button type="button" className="rm" onClick={removeCoupon} aria-label="הסר קוד">
            <Icon name="x" size={12} />
          </button>
        </div>
      ) : (
        <div className="inputs">
          <input
            type="text"
            placeholder="הזן קוד"
            value={couponInput}
            onChange={e => setCouponInput(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); void onApply() } }}
          />
          <button onClick={onApply} disabled={applying || !couponInput.trim()}>
            {applying ? '…' : 'החל'}
          </button>
        </div>
      )}
      {couponError && <div className="err">{couponError}</div>}
    </div>
  )

  if (lines.length === 0) {
    return (
      <>
        <div className="cls-page">
          {removedBanner}
          <div className="cls-empty">
            <div className="ico-circle"><Icon name="bag" size={30} /></div>
            <h1>הסל ריק</h1>
            <p>הוסיפו מוצרים מהקטלוג ונחזור לכאן כשתסיימו.</p>
            <Link to="/" className="cta">
              <Icon name="arrow" size={14} stroke={2.2} />
              חזרה לקטלוג
            </Link>
          </div>
        </div>
        <Footer />
      </>
    )
  }

  return (
    <>
      <div className="cls-page">
        <div className="cls-crumb">
          <Link to="/">קטלוג</Link>
          <span className="sep">›</span>
          <span className="current">סל קניות</span>
        </div>

        <div className="cls-sidebar-layout">
          <div>
            <div className="cls-section-head" style={{ marginTop: 0 }}>
              <div className="title">
                <h2>סל קניות</h2>
                <span className="meta">{totalItems} פריטים · משלוח 3-5 ימי עסקים</span>
              </div>
            </div>

            {removedBanner}

            <div style={{ display: 'grid', gap: 10 }}>
              {lines.map(line => {
                const clamp = clampByProduct.get(line.productId)
                return (
                <div key={line.productId} className="cls-cart-line-wrap">
                <div className="cls-cart-line">
                  <div className="thumb">
                    {line.imageUrl ? (
                      <img src={line.imageUrl} alt={line.nameHe} />
                    ) : (
                      <span className="ph">{line.slug.slice(0, 8)}</span>
                    )}
                  </div>
                  <div className="info-wrap">
                    <Link to={`/p/${line.slug}`} className="name">{line.nameHe}</Link>
                    <div className="unit">{formatPrice(line.priceAgorot)} ליחידה</div>
                  </div>
                  <div className="qty-wrap">
                    <div className="cls-qty">
                      <button
                        type="button"
                        onClick={() => setQty(line.productId, line.quantity - 1)}
                        disabled={line.quantity <= 1}
                        aria-label="פחות"
                      >
                        <Icon name="minus" size={14} stroke={2.2} />
                      </button>
                      <span className="val">{line.quantity}</span>
                      <button
                        type="button"
                        onClick={() => setQty(line.productId, line.quantity + 1)}
                        disabled={line.quantity >= 99}
                        aria-label="עוד"
                      >
                        <Icon name="plus" size={14} stroke={2.2} />
                      </button>
                    </div>
                    <button
                      type="button"
                      className="rm-btn"
                      onClick={() => remove(line.productId)}
                      aria-label="הסר"
                      title="הסר"
                    >
                      <Icon name="trash" size={16} stroke={1.8} />
                    </button>
                  </div>
                  <div className="line-total">
                    {formatPrice(line.priceAgorot * line.quantity)}
                  </div>
                </div>
                {clamp && (
                  <div className="cls-cart-line-note">
                    הכמות עודכנה ל-{clamp.finalQty} — זה המלאי שנותר
                  </div>
                )}
                </div>
                )
              })}
            </div>

            {toFree > 0 && (
              <div className="cls-free-ship-nudge">
                <span className="ico"><Icon name="truck" size={18} /></span>
                <div>
                  עוד <strong>{formatPrice(toFree)}</strong> ותקבלו משלוח חינם.
                </div>
              </div>
            )}
          </div>

          <aside className="cls-summary">
            <h3>סיכום הזמנה</h3>
            <div className="row">
              <span>סך ביניים</span>
              <span className="v">{formatPrice(subtotal)}</span>
            </div>
            {discount > 0 && (
              <div className="row" style={{ color: 'var(--olive, #5d7a3a)' }}>
                <span>הנחה ({coupon!.code})</span>
                <span className="v">-{formatPrice(discount)}</span>
              </div>
            )}
            <div className="row">
              <span>משלוח</span>
              <span className="v">{shipping === 0 ? 'חינם' : formatPrice(shipping)}</span>
            </div>
            <hr />
            <div className="total-row">
              <span className="lbl">סך הכל לתשלום</span>
              <span className="v">{formatPrice(total)}</span>
            </div>
            <button className="cta" onClick={() => nav('/checkout')}>
              למעבר לקופה
              <Icon name="arrow" size={14} stroke={2.2} />
            </button>
            <div className="secure-note">
              <Icon name="secure" size={16} />
              תשלום מאובטח · PayPal
            </div>
            {couponBox}
          </aside>
        </div>
      </div>
      <Footer />
    </>
  )
}
