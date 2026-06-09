import { useEffect, useMemo, useRef, useState, Fragment } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import {
  api, ApiError, formatPrice, rememberGuestOrder,
  type CreateOrderRequest,
  type DeliveryQuote, type OrderView, type SavedAddress,
} from '../api'
import { useCart } from '../cart/cartStore'
import { useAuth } from '../auth/authStore'
import { useDeliveryConfig } from '../delivery/deliveryConfig'
import { Field } from '../components/Field'
import { Autocomplete, fetchCities, fetchStreets } from '../components/Autocomplete'
import { Icon } from '../components/Icon'
import { Footer } from '../components/Footer'

const STEPS = [
  ['1', 'משלוח'],
  ['2', 'תשלום'],
  ['3', 'אישור'],
] as const

// Israeli mobile prefixes only — couriers need a phone they can reach in the field.
const PHONE_PREFIXES = ['050', '051', '052', '053', '054', '055', '058'] as const

type ErrorKey = 'fullName' | 'phone' | 'city' | 'street' | 'houseNo' | 'postalCode' | 'guestEmail'
type Errors = Partial<Record<ErrorKey, string>>

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

// House number: digits, optionally followed by one Hebrew/Latin letter (e.g. "10א", "7B").
const HOUSE_NO_RE = /^\d+[א-תa-zA-Z]?$/
const PHONE_NUMBER_RE = /^\d{7}$/

// Try to infer prefix + 7-digit local number from any pre-existing user.phone value.
function splitPhone(raw: string): { prefix: string; number: string } {
  const digits = (raw || '').replace(/\D+/g, '')
  for (const p of PHONE_PREFIXES) {
    if (digits.startsWith(p)) {
      const rest = digits.slice(p.length)
      if (rest.length === 7) return { prefix: p, number: rest }
    }
  }
  return { prefix: '050', number: '' }
}

export function CheckoutPage() {
  const {
    lines, subtotalAgorot, clearAll,
    coupon, couponCode, couponError, applyCoupon, removeCoupon, revalidateCoupon,
  } = useCart()
  const { user } = useAuth()
  const courierFlatAgorot = useDeliveryConfig(s => s.courierFlatAgorot)
  const freeAboveAgorot = useDeliveryConfig(s => s.freeAboveAgorot)
  const nav = useNavigate()

  const seeded = splitPhone(user?.phone ?? '')
  const [fullName, setFullName] = useState(user?.fullName ?? '')
  const [phonePrefix, setPhonePrefix] = useState<string>(seeded.prefix)
  const [phoneNumber, setPhoneNumber] = useState<string>(seeded.number)
  const [guestEmail, setGuestEmail] = useState('')
  const [street, setStreet] = useState('')
  const [houseNo, setHouseNo] = useState('')
  const [apartment, setApartment] = useState('')
  const [city, setCity] = useState('')
  const [postalCode, setPostalCode] = useState('')
  const [notes, setNotes] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [errors, setErrors] = useState<Errors>({})
  const [touched, setTouched] = useState<Partial<Record<ErrorKey, boolean>>>({})
  // Coupon lives in the cart store (applied in /cart, carried here). Only the
  // input box and its in-flight flag are local to this page.
  const [couponInput, setCouponInput] = useState('')
  const [applyingCoupon, setApplyingCoupon] = useState(false)

  // Loaded lookup sets for validation. Empty = not yet loaded (don't block submit on those).
  const [allCities, setAllCities] = useState<Set<string>>(new Set())
  // Streets are NOT prefetched into a set: the browse endpoint caps at 2000, and big
  // cities (Tel Aviv) overflow that — late-alef-bet streets (ש/ת) fall off and get
  // falsely rejected. Validate each street against the server instead (exact-match
  // query, same source the dropdown uses). Seq guards against stale async overwrites.
  const streetCheckSeq = useRef(0)

  // Saved address book — picker at top of form, falls back to manual entry.
  const [savedAddresses, setSavedAddresses] = useState<SavedAddress[]>([])
  const [selectedSavedId, setSelectedSavedId] = useState<number | 'new' | null>(null)

  const [deliveryQuote, setDeliveryQuote] = useState<DeliveryQuote | null>(null)

  function fillFromSaved(a: SavedAddress) {
    setFullName(a.fullName)
    const p = splitPhone(a.phone)
    setPhonePrefix(p.prefix); setPhoneNumber(p.number)
    setCity(a.city)
    setStreet(a.street)
    setHouseNo(a.houseNo ?? '')
    setApartment(a.apartment ?? '')
    setPostalCode(a.postalCode ?? '')
    setNotes(a.notes ?? '')
    setErrors({}); setTouched({})
  }

  useEffect(() => {
    if (!user) return
    api<SavedAddress[]>('/api/me/addresses').then(list => {
      setSavedAddresses(list)
      if (list.length > 0) {
        const def = list.find(a => a.isDefault) ?? list[0]
        setSelectedSavedId(def.id)
        fillFromSaved(def)
      } else {
        setSelectedSavedId('new')
      }
    }).catch(() => { /* tolerate — falls back to manual entry */ })
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user])

  // Mount-only guard: bounce to /cart if user lands empty. AFTER mount we ignore
  // lines becoming 0 — otherwise clearing the cart on submit races with the
  // payment redirect and the SPA shows a white frame until refresh.
  const emptyCartChecked = useRef(false)
  useEffect(() => {
    if (emptyCartChecked.current) return
    emptyCartChecked.current = true
    if (lines.length === 0) nav('/cart')
  }, [lines.length, nav])

  // Prefetch the full cities list once for validation + dropdown browse.
  useEffect(() => {
    fetchCities('').then(list => setAllCities(new Set(list))).catch(() => { /* tolerate */ })
  }, [])

  // Server-backed exact-match check for a typed street. Uses the same filtered
  // endpoint as the dropdown, so it finds any real street regardless of list size.
  // Tolerates API failure (returns true) — never block a paying customer on a
  // places-lookup outage.
  async function confirmStreetValid(value: string): Promise<boolean> {
    const v = value.trim()
    if (!v || !city.trim()) return false
    try {
      const list = await fetchStreets(city, v)
      return list.includes(v)
    } catch {
      return true
    }
  }

  if (lines.length === 0 && !submitting) return null

  const subtotal = subtotalAgorot()
  const discount = coupon ? Math.min(coupon.discountAgorot, subtotal) : 0
  const discountedSubtotal = Math.max(0, subtotal - discount)
  const freeShipping = coupon?.freeShipping ?? false
  const shippingAgorot = useMemo(() => {
    if (freeShipping) return 0
    const opt = deliveryQuote?.options.find(o => o.method === 'COURIER')
    if (opt) return opt.priceAgorot
    return discountedSubtotal >= freeAboveAgorot ? 0 : courierFlatAgorot
  }, [deliveryQuote, discountedSubtotal, freeAboveAgorot, courierFlatAgorot, freeShipping])
  const total = discountedSubtotal + shippingAgorot

  useEffect(() => {
    let cancelled = false
    api<DeliveryQuote>(`/api/delivery/quote?subtotalAgorot=${discountedSubtotal}`)
      .then(q => { if (!cancelled) setDeliveryQuote(q) })
      .catch(() => { /* tolerate — falls back to client default */ })
    return () => { cancelled = true }
  }, [discountedSubtotal])

  // Re-validate the carried coupon against the live subtotal — it may now fail
  // the minimum, or a previously-failing code may apply once the cart grows.
  useEffect(() => {
    if (couponCode) void revalidateCoupon()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subtotal, couponCode])

  async function onApplyCoupon() {
    const code = couponInput.trim()
    if (!code) return
    setApplyingCoupon(true)
    const ok = await applyCoupon(code)
    setApplyingCoupon(false)
    if (ok) setCouponInput('')
  }

  function onRemoveCoupon() {
    removeCoupon(); setCouponInput('')
  }

  function validate(name: ErrorKey, value: string): string | null {
    const v = value.trim()
    switch (name) {
      case 'fullName':   return v.length < 2 ? 'נדרש שם מלא' : null
      case 'phone':      return PHONE_NUMBER_RE.test(phoneNumber) ? null : 'מספר טלפון - 7 ספרות'
      case 'guestEmail': return EMAIL_RE.test(v) ? null : 'נדרשת כתובת אימייל תקינה'
      case 'city': {
        if (!v) return 'נדרשת עיר'
        if (allCities.size > 0 && !allCities.has(v)) return 'בחרו עיר מהרשימה'
        return null
      }
      case 'street': {
        // Required only — existence is confirmed against the server (async) in
        // markBlur/onSubmit, since the full street list can't be held client-side.
        return v ? null : 'נדרש רחוב'
      }
      case 'houseNo':    return HOUSE_NO_RE.test(v) ? null : 'מספר בית לא תקין'
      case 'postalCode': {
        if (!v) return null
        const digits = v.replace(/\D+/g, '')
        return /^(\d{5}|\d{7})$/.test(digits) ? null : 'מיקוד 5 או 7 ספרות'
      }
    }
  }

  function markBlur(name: ErrorKey, value: string) {
    setTouched(t => ({ ...t, [name]: true }))
    const syncErr = validate(name, value) ?? undefined
    setErrors(e => ({ ...e, [name]: syncErr }))
    // Street: required passes locally, so confirm it exists against the server.
    if (name === 'street' && !syncErr && value.trim()) {
      const seq = ++streetCheckSeq.current
      void confirmStreetValid(value).then(ok => {
        if (seq !== streetCheckSeq.current) return // a newer edit/check superseded this
        setErrors(e => ({ ...e, street: ok ? undefined : 'בחרו רחוב מהרשימה' }))
      })
    }
  }

  function patchOnChange(name: ErrorKey, value: string) {
    if (touched[name]) {
      setErrors(e => ({ ...e, [name]: validate(name, value) ?? undefined }))
    }
  }

  function validateAll(): Errors {
    return {
      fullName:   validate('fullName',   fullName)         ?? undefined,
      phone:      validate('phone',      phoneNumber)      ?? undefined,
      city:       validate('city',       city)             ?? undefined,
      street:     validate('street',     street)           ?? undefined,
      houseNo:    validate('houseNo',    houseNo)          ?? undefined,
      postalCode: validate('postalCode', postalCode)       ?? undefined,
      guestEmail: user ? undefined : (validate('guestEmail', guestEmail) ?? undefined),
    }
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)

    const validated = validateAll()
    // Authoritative street existence check — async, against the server.
    if (!validated.street && street.trim() && !(await confirmStreetValid(street))) {
      validated.street = 'בחרו רחוב מהרשימה'
    }
    const hasErrors = Object.values(validated).some(Boolean)
    if (hasErrors) {
      setErrors(validated)
      setTouched({
        fullName: true, phone: true, city: true,
        street: true, houseNo: true, postalCode: true,
        guestEmail: true,
      })
      setError('בדקו את השדות המסומנים באדום')
      return
    }

    setSubmitting(true)
    try {
      const fullPhone = phonePrefix + phoneNumber
      const body: CreateOrderRequest = {
        items: lines.map(l => ({ productId: l.productId, quantity: l.quantity })),
        shipping: {
          fullName,
          phone: fullPhone,
          street,
          houseNo,
          apartment: apartment || undefined,
          city,
          postalCode: postalCode || undefined,
          notes: notes || undefined,
        },
        deliveryMethod: 'COURIER',
        couponCode: coupon?.code,
        guestEmail: user ? undefined : guestEmail.trim(),
      }
      const order = await api<OrderView>('/api/orders', {
        method: 'POST',
        body: JSON.stringify(body),
      })
      if (!user && order.guestToken) {
        rememberGuestOrder(order.orderNumber, order.guestToken)
      }
      try {
        const pay = await api<{ provider: string; redirectUrl: string; orderNumber: string }>(
          `/api/orders/${order.orderNumber}/pay`,
          { method: 'POST' }
        )
        if (pay.redirectUrl.startsWith('http://') || pay.redirectUrl.startsWith('https://')) {
          void clearAll()
          window.location.href = pay.redirectUrl
        } else {
          nav(pay.redirectUrl)
          setTimeout(() => { void clearAll() }, 50)
        }
      } catch (payErr) {
        // Payment provider disabled (503) — order is created, just no gateway
        // to forward to. Land the customer on confirmation so they see their
        // order number and the "we'll be in touch" copy that page renders for
        // PENDING orders. Any other error: surface it on the checkout form.
        if (payErr instanceof ApiError && payErr.status === 503) {
          void clearAll()
          nav(`/confirmation/${order.orderNumber}`)
          return
        }
        throw payErr
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה ביצירת ההזמנה')
      setSubmitting(false)
    }
  }

  return (
    <>
      <form onSubmit={onSubmit} noValidate>
        <div className="cls-page">
          <div className="cls-crumb">
            <Link to="/cart">סל</Link>
            <span className="sep">›</span>
            <span className="current">קופה</span>
          </div>

          <div className="cls-sidebar-layout">
            <div>
              <div className="cls-stepper">
                {STEPS.map(([n, t], i) => {
                  const isActive = i === 0
                  return (
                    <Fragment key={n}>
                      <div className={`step${isActive ? ' active' : ''}`}>
                        <div className="dot">{n}</div>
                        <span className="lbl">{t}</span>
                      </div>
                      {n !== '3' && <div className="bar" />}
                    </Fragment>
                  )
                })}
              </div>

              {!user && (
                <>
                  <div className="cls-checkout-section-title">פרטי קשר</div>
                  <div className="cls-info-banner" style={{ marginBottom: 12 }}>
                    <span className="ico"><Icon name="user" size={16} /></span>
                    <div>
                      קונים כאורח — אישור ההזמנה יישלח למייל למטה.{' '}
                      <Link to="/login?next=/checkout" style={{ fontWeight: 700 }}>
                        כבר יש לכם חשבון? התחברו
                      </Link>
                    </div>
                  </div>
                  <div style={{ marginBottom: 14 }}>
                    <Field
                      label="אימייל" required value={guestEmail}
                      placeholder="name@example.com"
                      inputMode="email"
                      onChange={e => { setGuestEmail(e.target.value); patchOnChange('guestEmail', e.target.value) }}
                      onBlur={() => markBlur('guestEmail', guestEmail)}
                      error={errors.guestEmail}
                    />
                  </div>
                </>
              )}

              <div className="cls-checkout-section-title">פרטי משלוח</div>

              {deliveryQuote && (
                <div className="cls-info-banner" style={{ marginBottom: 14 }}>
                  <span className="ico"><Icon name="truck" size={16} /></span>
                  <div>
                    שליח עד הבית — {' '}
                    <strong>
                      {shippingAgorot === 0 ? 'חינם' : formatPrice(shippingAgorot)}
                    </strong>
                    {freeShipping
                      ? ' · הקופון כולל משלוח חינם'
                      : freeThresholdHint(deliveryQuote, discountedSubtotal)}
                  </div>
                </div>
              )}

              {savedAddresses.length > 0 && (
                <div className="cls-saved-addrs">
                  <div className="head">
                    <h4>בחירת כתובת שמורה</h4>
                    <Link to="/account?tab=addresses">ניהול כתובות</Link>
                  </div>
                  <div className="opts">
                    {savedAddresses.map(a => (
                      <button
                        type="button"
                        key={a.id}
                        className={`opt${selectedSavedId === a.id ? ' selected' : ''}`}
                        onClick={() => { setSelectedSavedId(a.id); fillFromSaved(a) }}
                      >
                        <div className="l">
                          {a.label || a.city}
                          {a.isDefault && <span className="pill">דיפולט</span>}
                        </div>
                        <div>{a.street}{a.houseNo ? ` ${a.houseNo}` : ''}, {a.city}</div>
                      </button>
                    ))}
                    <button
                      type="button"
                      className={`opt${selectedSavedId === 'new' ? ' selected' : ''}`}
                      onClick={() => {
                        setSelectedSavedId('new')
                        setFullName(user?.fullName ?? '')
                        const p = splitPhone(user?.phone ?? '')
                        setPhonePrefix(p.prefix); setPhoneNumber(p.number)
                        setStreet(''); setHouseNo(''); setApartment('')
                        setCity(''); setPostalCode(''); setNotes('')
                        setErrors({}); setTouched({})
                      }}
                    >
                      <div className="l"><span className="new-mark">+</span> כתובת חדשה</div>
                      <div>מילוי ידני של פרטי המשלוח</div>
                    </button>
                  </div>
                </div>
              )}

              <div style={{ display: 'grid', gap: 12 }}>
                <div className="cls-row-2">
                  <Field
                    label="שם מלא" required value={fullName}
                    onChange={e => { setFullName(e.target.value); patchOnChange('fullName', e.target.value) }}
                    onBlur={() => markBlur('fullName', fullName)}
                    error={errors.fullName}
                  />
                  <div className="hm-field">
                    <label>טלפון</label>
                    <div className="cls-phone-group">
                      <select
                        className={`hm-input mono ${errors.phone ? 'has-error' : ''}`}
                        value={phonePrefix}
                        onChange={e => setPhonePrefix(e.target.value)}
                        aria-label="קידומת"
                      >
                        {PHONE_PREFIXES.map(p => <option key={p} value={p}>{p}</option>)}
                      </select>
                      <input
                        className={`hm-input mono ${errors.phone ? 'has-error' : ''}`}
                        type="tel"
                        inputMode="numeric"
                        placeholder="1234567"
                        maxLength={7}
                        value={phoneNumber}
                        onChange={e => {
                          const digits = e.target.value.replace(/\D+/g, '').slice(0, 7)
                          setPhoneNumber(digits)
                          patchOnChange('phone', digits)
                        }}
                        onBlur={() => markBlur('phone', phoneNumber)}
                        required
                      />
                    </div>
                    {errors.phone && <div className="cls-field-err">{errors.phone}</div>}
                  </div>
                </div>
                <div className="cls-row-21">
                  <Autocomplete
                    label="עיר"
                    required
                    value={city}
                    onChange={v => { setCity(v); patchOnChange('city', v) }}
                    onBlur={() => markBlur('city', city)}
                    fetchSuggestions={fetchCities}
                    placeholder="לחצו לבחירה או הקלידו"
                    error={errors.city}
                  />
                  <Field
                    label="מיקוד" mono value={postalCode}
                    placeholder="5 או 7 ספרות"
                    inputMode="numeric"
                    onChange={e => { setPostalCode(e.target.value); patchOnChange('postalCode', e.target.value) }}
                    onBlur={() => markBlur('postalCode', postalCode)}
                    error={errors.postalCode}
                  />
                </div>
                <div className="cls-row-3">
                  <Autocomplete
                    label="רחוב"
                    required
                    value={street}
                    onChange={v => { streetCheckSeq.current++; setStreet(v); patchOnChange('street', v) }}
                    onBlur={() => markBlur('street', street)}
                    fetchSuggestions={q => fetchStreets(city, q)}
                    resetKey={city}
                    disabled={!city.trim()}
                    placeholder={city.trim() ? 'לחצו לבחירה או הקלידו' : 'בחר/י עיר תחילה'}
                    error={errors.street}
                  />
                  <Field
                    label="מספר" required mono value={houseNo}
                    inputMode="text"
                    placeholder="10 או 10א"
                    onChange={e => { setHouseNo(e.target.value); patchOnChange('houseNo', e.target.value) }}
                    onBlur={() => markBlur('houseNo', houseNo)}
                    error={errors.houseNo}
                  />
                  <Field label="דירה" value={apartment} onChange={e => setApartment(e.target.value)} />
                </div>
                <Field
                  label="הערות לשליח"
                  multiline
                  rows={2}
                  value={notes}
                  onChange={e => setNotes(e.target.value)}
                />
              </div>

              <div className="cls-info-banner">
                <span className="ico"><Icon name="truck" size={16} /></span>
                <div>השליח יצור איתך קשר ~30 דקות לפני הגעה.</div>
              </div>

              {error && <div className="hm-error" style={{ marginTop: 14 }}>{error}</div>}
            </div>

            <aside className="cls-summary">
              <h3>סיכום</h3>
              <div style={{ display: 'grid', gap: 10, marginBottom: 12 }}>
                {lines.map(l => (
                  <div key={l.productId} className="cls-mini-line">
                    <div className="thumb">
                      {l.imageUrl ? <img src={l.imageUrl} alt={l.nameHe} /> : <span className="ph">{l.slug.slice(0, 6)}</span>}
                    </div>
                    <div className="info">
                      <div className="n">{l.nameHe}</div>
                      <div className="q">× {l.quantity}</div>
                    </div>
                    <div className="v">{formatPrice(l.priceAgorot * l.quantity)}</div>
                  </div>
                ))}
              </div>
              <hr />
              <div className="coupon-row">
                <div className="lbl">קוד הנחה</div>
                {coupon ? (
                  <div className="applied">
                    <span className="tag">{coupon.code}</span>
                    <span className="meta">{coupon.summary || 'הנחה'}</span>
                    <button type="button" className="rm" onClick={onRemoveCoupon} aria-label="הסר קוד">
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
                      onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); void onApplyCoupon() } }}
                    />
                    <button type="button" onClick={onApplyCoupon}
                            disabled={applyingCoupon || !couponInput.trim()}>
                      {applyingCoupon ? '…' : 'החל'}
                    </button>
                  </div>
                )}
                {couponError && <div className="err">{couponError}</div>}
              </div>
              <hr />
              <div className="row">
                <span>סך ביניים</span>
                <span className="v">{formatPrice(subtotal)}</span>
              </div>
              {discount > 0 && (
                <div className="row" style={{ color: 'var(--olive, #5d7a3a)' }}>
                  <span>הנחה{coupon ? ` (${coupon.code})` : ''}</span>
                  <span className="v">-{formatPrice(discount)}</span>
                </div>
              )}
              <div className="row">
                <span>משלוח</span>
                <span className="v">{shippingAgorot === 0 ? 'חינם' : formatPrice(shippingAgorot)}</span>
              </div>
              <hr />
              <div className="total-row">
                <span className="lbl">סך הכל</span>
                <span className="v">{formatPrice(total)}</span>
              </div>
              <button type="submit" className="cta" disabled={submitting}>
                {submitting ? 'יוצר הזמנה…' : 'אישור הזמנה'}
                {!submitting && <Icon name="arrow" size={14} stroke={2.2} />}
              </button>
              <div className="secure-note">
                <Icon name="secure" size={16} />
                תשלום מאובטח · PayPal
              </div>
            </aside>
          </div>
        </div>
      </form>
      <Footer />
    </>
  )
}

function freeThresholdHint(q: DeliveryQuote | null, subtotalAgorot: number): string {
  const opt = q?.options.find(o => o.method === 'COURIER')
  if (!opt || opt.freeAboveAgorot <= 0) return ''
  if (opt.priceAgorot === 0) return ' · משלוח חינם'
  const left = opt.freeAboveAgorot - subtotalAgorot
  if (left <= 0) return ' · משלוח חינם'
  return ` · עוד ${formatPrice(left)} למשלוח חינם`
}
