import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, canCustomerCancel, formatPrice, type OrderView, type Product, type SavedAddress, type SavedAddressUpsert } from '../api'
import { useAuth } from '../auth/authStore'
import { useCart } from '../cart/cartStore'
import { Field } from '../components/Field'
import { Autocomplete, fetchCities, fetchStreets } from '../components/Autocomplete'
import { Footer } from '../components/Footer'
import { Icon } from '../components/Icon'
import { useToast } from '../components/Toast'

const PHONE_PREFIXES = ['050', '051', '052', '053', '054', '055', '058'] as const
const HOUSE_NO_RE = /^\d+[א-תa-zA-Z]?$/
const PHONE_NUMBER_RE = /^\d{7}$/

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

type Tab = 'profile' | 'addresses' | 'orders'

const STATUS_LABEL: Record<OrderView['status'], string> = {
  PENDING:   'ממתין לתשלום',
  PAID:      'שולם',
  FULFILLED: 'בהכנה',
  SHIPPED:   'במשלוח',
  DELIVERED: 'נמסר',
  CANCELLED: 'בוטלה',
  REFUNDED:  'הוחזר תשלום',
}

export function AccountPage() {
  const { user, token, fetchMe, logout } = useAuth()
  const nav = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const raw = searchParams.get('tab')
  const tab: Tab = raw === 'addresses' ? 'addresses' : raw === 'orders' ? 'orders' : 'profile'

  // On F5 the store boots with token from localStorage but user=null until
  // fetchMe() resolves. Only redirect when there's no token at all — otherwise
  // wait for the in-flight session hydration.
  useEffect(() => { if (!token) nav('/login?next=/account') }, [token, nav])
  if (!user) return null

  return (
    <>
      <div className="hm-account-page">
        <div className="head">
          <div className="eyebrow">החשבון שלי</div>
          <h1>שלום, {user.fullName.split(' ')[0]}</h1>
        </div>

        <div className="account-grid">
          <aside className="side-nav">
            <button
              className={tab === 'profile' ? 'active' : ''}
              onClick={() => setSearchParams({ tab: 'profile' })}
            >
              <Icon name="user" size={16} />
              פרטים אישיים
            </button>
            <button
              className={tab === 'addresses' ? 'active' : ''}
              onClick={() => setSearchParams({ tab: 'addresses' })}
            >
              <Icon name="pin" size={16} />
              כתובות שמורות
            </button>
            <button
              className={tab === 'orders' ? 'active' : ''}
              onClick={() => setSearchParams({ tab: 'orders' })}
            >
              <Icon name="bag" size={16} />
              ההזמנות שלי
            </button>
            <Link to="/favorites">
              <Icon name="heart" size={16} />
              מועדפים
            </Link>
            <button className="logout" onClick={() => { logout(); nav('/') }}>
              <Icon name="arrow" size={16} />
              התנתקות
            </button>
          </aside>

          <section className="account-panel">
            {tab === 'profile' && <ProfileTab onSaved={fetchMe} />}
            {tab === 'addresses' && <AddressesTab />}
            {tab === 'orders' && <OrdersTab />}
          </section>
        </div>
      </div>
      <Footer />
    </>
  )
}

function ProfileTab({ onSaved }: { onSaved: () => void | Promise<void> }) {
  const { user } = useAuth()
  const pushToast = useToast(s => s.push)
  const seeded = splitPhone(user?.phone ?? '')
  const [fullName, setFullName] = useState(user?.fullName ?? '')
  const [phonePrefix, setPhonePrefix] = useState<string>(seeded.prefix)
  const [phoneNumber, setPhoneNumber] = useState<string>(seeded.number)
  const [marketingOptIn, setMarketingOptIn] = useState(user?.marketingOptIn ?? false)
  const [savingConsent, setSavingConsent] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function toggleMarketing(next: boolean) {
    setMarketingOptIn(next)
    setSavingConsent(true)
    try {
      await api('/api/me/marketing-consent', {
        method: 'PUT',
        body: JSON.stringify({ optIn: next }),
      })
      await onSaved()
      pushToast(next ? 'נרשמת לעדכונים' : 'בוטלה הרשמה לעדכונים')
    } catch (e) {
      setMarketingOptIn(!next)
      setError(e instanceof Error ? e.message : 'שגיאה')
    } finally {
      setSavingConsent(false)
    }
  }

  async function save(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    if (fullName.trim().length < 2) { setError('נדרש שם מלא'); return }
    if (phoneNumber && !PHONE_NUMBER_RE.test(phoneNumber)) {
      setError('מספר טלפון - 7 ספרות'); return
    }
    setSaving(true)
    try {
      await api('/api/me/profile', {
        method: 'PUT',
        body: JSON.stringify({
          fullName: fullName.trim(),
          phone: phoneNumber ? phonePrefix + phoneNumber : '',
        }),
      })
      await onSaved()
      pushToast('הפרטים נשמרו')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={save} className="account-form">
      <h2>פרטים אישיים</h2>
      <p className="hint">השם והטלפון משמשים כברירת מחדל בקופה.</p>

      <div className="cls-row-2">
        <Field
          label="שם מלא"
          required
          value={fullName}
          onChange={e => setFullName(e.target.value)}
        />
        <div className="hm-field">
          <label>טלפון</label>
          <div className="cls-phone-group">
            <select
              className="hm-input mono"
              value={phonePrefix}
              onChange={e => setPhonePrefix(e.target.value)}
              aria-label="קידומת"
            >
              {PHONE_PREFIXES.map(p => <option key={p} value={p}>{p}</option>)}
            </select>
            <input
              className="hm-input mono"
              type="tel"
              inputMode="numeric"
              placeholder="1234567"
              maxLength={7}
              value={phoneNumber}
              onChange={e => setPhoneNumber(e.target.value.replace(/\D+/g, '').slice(0, 7))}
            />
          </div>
        </div>
      </div>

      <div className="hm-field">
        <label>אימייל</label>
        <input className="hm-input" type="email" value={user?.email ?? ''} disabled />
        <div className="cls-field-hint">לא ניתן לשנות. צריך להחליף? פנו אלינו.</div>
      </div>

      <div className="marketing-row">
        <div className="info">
          <div className="t">עדכונים שיווקיים במייל</div>
          <div className="d">מבצעים, מוצרים חדשים, קופונים. אפשר להסיר בכל עת.</div>
        </div>
        <label className={`hm-switch${savingConsent ? ' busy' : ''}`}>
          <input
            type="checkbox"
            checked={marketingOptIn}
            disabled={savingConsent}
            onChange={e => toggleMarketing(e.target.checked)}
          />
          <span className="slider" />
        </label>
      </div>

      {error && <div className="hm-error" style={{ marginTop: 12 }}>{error}</div>}

      <div className="actions">
        <button type="submit" className="hm-btn-primary" disabled={saving}>
          {saving ? 'שומר…' : 'שמירה'}
        </button>
      </div>
    </form>
  )
}

function AddressesTab() {
  const pushToast = useToast(s => s.push)
  const [addresses, setAddresses] = useState<SavedAddress[]>([])
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<SavedAddress | null>(null)
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function load() {
    setLoading(true)
    api<SavedAddress[]>('/api/me/addresses')
      .then(setAddresses)
      .catch(e => setError(e instanceof Error ? e.message : 'שגיאה'))
      .finally(() => setLoading(false))
  }
  useEffect(load, [])

  async function setDefault(id: number) {
    try {
      await api(`/api/me/addresses/${id}/default`, { method: 'POST' })
      load()
      pushToast('עודכן כברירת מחדל')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    }
  }

  async function remove(a: SavedAddress) {
    if (!confirm(`למחוק את הכתובת "${a.label || a.street}"?`)) return
    try {
      await api(`/api/me/addresses/${a.id}`, { method: 'DELETE' })
      load()
      pushToast('הכתובת נמחקה')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    }
  }

  if (editing || creating) {
    return (
      <AddressForm
        initial={editing}
        onCancel={() => { setEditing(null); setCreating(false); setError(null) }}
        onSaved={(msg) => {
          setEditing(null); setCreating(false); setError(null)
          load()
          pushToast(msg)
        }}
      />
    )
  }

  return (
    <div className="account-form">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        <h2 style={{ margin: 0 }}>כתובות שמורות</h2>
        <button type="button" className="hm-btn-primary" onClick={() => setCreating(true)}>
          + הוספת כתובת
        </button>
      </div>
      <p className="hint">בקופה תוכלו לבחור מהכתובות השמורות במקום למלא הכל מחדש.</p>

      {error && <div className="hm-error" style={{ marginTop: 12 }}>{error}</div>}

      {loading ? (
        <p style={{ color: 'var(--ink-3)', marginTop: 16 }}>טוען…</p>
      ) : addresses.length === 0 ? (
        <div className="addr-empty">
          <div className="ico"><Icon name="pin" size={22} /></div>
          <h3>אין עדיין כתובות שמורות</h3>
          <p>הוסיפו כתובת אחת או יותר כדי לקצר את התשלום בפעם הבאה.</p>
        </div>
      ) : (
        <div className="addr-list">
          {addresses.map(a => (
            <article key={a.id} className={`addr-card${a.isDefault ? ' default' : ''}`}>
              <div className="top">
                <div>
                  <div className="label">
                    {a.label || 'כתובת'}
                    {a.isDefault && <span className="pill">ברירת מחדל</span>}
                  </div>
                  <div className="name">{a.fullName} · {a.phone}</div>
                </div>
              </div>
              <div className="body">
                {a.street}
                {a.houseNo ? ` ${a.houseNo}` : ''}
                {a.apartment ? `, דירה ${a.apartment}` : ''}
                <br />
                {a.city}{a.postalCode ? ` · ${a.postalCode}` : ''}
                {a.notes && <div className="notes">{a.notes}</div>}
              </div>
              <div className="actions">
                <button type="button" onClick={() => setEditing(a)}>עריכה</button>
                <button type="button" onClick={() => remove(a)} className="danger">מחיקה</button>
                {!a.isDefault && (
                  <button type="button" onClick={() => setDefault(a.id)} className="link">
                    קביעה כברירת מחדל
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  )
}

function AddressForm({
  initial,
  onCancel,
  onSaved,
}: {
  initial: SavedAddress | null
  onCancel: () => void
  onSaved: (msg: string) => void
}) {
  const { user } = useAuth()
  const seeded = splitPhone(initial?.phone ?? user?.phone ?? '')
  const [label, setLabel] = useState(initial?.label ?? '')
  const [fullName, setFullName] = useState(initial?.fullName ?? user?.fullName ?? '')
  const [phonePrefix, setPhonePrefix] = useState(seeded.prefix)
  const [phoneNumber, setPhoneNumber] = useState(seeded.number)
  const [city, setCity] = useState(initial?.city ?? '')
  const [street, setStreet] = useState(initial?.street ?? '')
  const [houseNo, setHouseNo] = useState(initial?.houseNo ?? '')
  const [apartment, setApartment] = useState(initial?.apartment ?? '')
  const [postalCode, setPostalCode] = useState(initial?.postalCode ?? '')
  const [notes, setNotes] = useState(initial?.notes ?? '')
  const [isDefault, setIsDefault] = useState(initial?.isDefault ?? false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function save(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    if (fullName.trim().length < 2) return setError('נדרש שם מלא')
    if (!PHONE_NUMBER_RE.test(phoneNumber)) return setError('מספר טלפון - 7 ספרות')
    if (!city.trim()) return setError('נדרשת עיר')
    if (!street.trim()) return setError('נדרש רחוב')
    if (!HOUSE_NO_RE.test(houseNo)) return setError('מספר בית לא תקין')
    if (postalCode.trim()) {
      const d = postalCode.replace(/\D+/g, '')
      if (!/^(\d{5}|\d{7})$/.test(d)) return setError('מיקוד 5 או 7 ספרות')
    }

    const body: SavedAddressUpsert = {
      label: label.trim() || undefined,
      fullName: fullName.trim(),
      phone: phonePrefix + phoneNumber,
      street,
      houseNo,
      apartment: apartment || undefined,
      city,
      postalCode: postalCode || undefined,
      notes: notes || undefined,
      isDefault,
    }
    setSaving(true)
    try {
      if (initial) {
        await api(`/api/me/addresses/${initial.id}`, { method: 'PUT', body: JSON.stringify(body) })
      } else {
        await api('/api/me/addresses', { method: 'POST', body: JSON.stringify(body) })
      }
      onSaved(initial ? 'הכתובת עודכנה' : 'הכתובת נוספה')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={save} className="account-form">
      <a className="back-link" onClick={onCancel}>← חזרה לרשימה</a>
      <h2 style={{ marginTop: 8 }}>{initial ? 'עריכת כתובת' : 'כתובת חדשה'}</h2>

      <Field
        label="כינוי (אופציונלי)"
        placeholder="בית / עבודה / כתובת של אמא"
        value={label}
        onChange={e => setLabel(e.target.value)}
      />

      <div className="cls-row-2">
        <Field label="שם מלא" required value={fullName} onChange={e => setFullName(e.target.value)} />
        <div className="hm-field">
          <label>טלפון</label>
          <div className="cls-phone-group">
            <select
              className="hm-input mono"
              value={phonePrefix}
              onChange={e => setPhonePrefix(e.target.value)}
              aria-label="קידומת"
            >
              {PHONE_PREFIXES.map(p => <option key={p} value={p}>{p}</option>)}
            </select>
            <input
              className="hm-input mono"
              type="tel"
              inputMode="numeric"
              placeholder="1234567"
              maxLength={7}
              value={phoneNumber}
              onChange={e => setPhoneNumber(e.target.value.replace(/\D+/g, '').slice(0, 7))}
            />
          </div>
        </div>
      </div>

      <div className="cls-row-21">
        <Autocomplete
          label="עיר"
          required
          value={city}
          onChange={setCity}
          fetchSuggestions={fetchCities}
          placeholder="לחצו לבחירה או הקלידו"
        />
        <Field
          label="מיקוד" mono value={postalCode}
          placeholder="5 או 7 ספרות"
          inputMode="numeric"
          onChange={e => setPostalCode(e.target.value)}
        />
      </div>
      <div className="cls-row-3">
        <Autocomplete
          label="רחוב"
          required
          value={street}
          onChange={setStreet}
          fetchSuggestions={q => fetchStreets(city, q)}
          resetKey={city}
          disabled={!city.trim()}
          placeholder={city.trim() ? 'לחצו לבחירה או הקלידו' : 'בחר/י עיר תחילה'}
        />
        <Field
          label="מספר" required mono value={houseNo}
          inputMode="text"
          placeholder="10 או 10א"
          onChange={e => setHouseNo(e.target.value)}
        />
        <Field label="דירה" value={apartment} onChange={e => setApartment(e.target.value)} />
      </div>
      <Field
        label="הערות לשליח (אופציונלי)"
        multiline
        rows={2}
        value={notes}
        onChange={e => setNotes(e.target.value)}
      />

      <label className="default-toggle">
        <input
          type="checkbox"
          checked={isDefault}
          onChange={e => setIsDefault(e.target.checked)}
        />
        <span>הגדר כברירת מחדל</span>
      </label>

      {error && <div className="hm-error" style={{ marginTop: 12 }}>{error}</div>}

      <div className="actions">
        <button type="button" className="hm-btn-quiet" onClick={onCancel}>ביטול</button>
        <button type="submit" className="hm-btn-primary" disabled={saving}>
          {saving ? 'שומר…' : 'שמירה'}
        </button>
      </div>
    </form>
  )
}

function OrdersTab() {
  const pushToast = useToast(s => s.push)
  const addToCart = useCart(s => s.add)
  const [orders, setOrders] = useState<OrderView[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reordering, setReordering] = useState<string | null>(null)
  const [cancelling, setCancelling] = useState<string | null>(null)
  const nav = useNavigate()

  useEffect(() => {
    api<OrderView[]>('/api/orders')
      .then(setOrders)
      .catch(e => setError(e instanceof Error ? e.message : 'שגיאה'))
  }, [])

  async function cancelOrder(o: OrderView) {
    const wasPaid = o.status === 'PAID' || o.status === 'FULFILLED'
    const msg = wasPaid
      ? `לבטל את הזמנה ${o.orderNumber}? יבוצע החזר מלא בסך ${formatPrice(o.totalAgorot)} תוך 3-7 ימי עסקים.`
      : `לבטל את הזמנה ${o.orderNumber}?`
    if (!confirm(msg)) return
    const reason = prompt('סיבת הביטול (אופציונלי)') ?? undefined
    setCancelling(o.orderNumber); setError(null)
    try {
      const updated = await api<OrderView>(`/api/orders/${o.orderNumber}/cancel`, {
        method: 'POST',
        body: JSON.stringify({ reason }),
      })
      setOrders(list => list ? list.map(x => x.orderNumber === updated.orderNumber ? updated : x) : list)
      pushToast(wasPaid ? 'ההזמנה בוטלה — ההחזר בדרך' : 'ההזמנה בוטלה')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה בביטול')
    } finally {
      setCancelling(null)
    }
  }

  async function reorder(o: OrderView) {
    setReordering(o.orderNumber); setError(null)
    try {
      const page = await api<{ content: Product[] }>('/api/products?size=500')
      const byId = new Map(page.content.map(p => [p.id, p]))
      let added = 0
      let missing = 0
      for (const it of o.items) {
        const p = byId.get(it.productId)
        if (!p || p.stockQty <= 0 || !p.active) { missing++; continue }
        addToCart(p, Math.min(it.quantity, p.stockQty))
        added++
      }
      if (added === 0) {
        pushToast('המוצרים בהזמנה אינם זמינים יותר')
      } else if (missing > 0) {
        pushToast(`נוספו לסל ${added} מוצרים (${missing} לא זמינים)`)
        nav('/cart')
      } else {
        pushToast(`נוספו לסל ${added} מוצרים`)
        nav('/cart')
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה בהזמנה חוזרת')
    } finally {
      setReordering(null)
    }
  }

  return (
    <div className="account-form">
      <h2>ההזמנות שלי</h2>
      <p className="hint">היסטוריית הקניות שלך. לחצו על הזמנה לצפייה במעקב מלא או להזמין שוב את אותם המוצרים.</p>

      {error && <div className="hm-error" style={{ marginTop: 12 }}>{error}</div>}

      {orders === null ? (
        <p style={{ color: 'var(--ink-3)', marginTop: 16 }}>טוען…</p>
      ) : orders.length === 0 ? (
        <div className="addr-empty">
          <div className="ico"><Icon name="bag" size={22} /></div>
          <h3>אין עדיין הזמנות</h3>
          <p>הזמנות שתבצעו יופיעו כאן עם סטטוס עדכני.</p>
          <Link
            to="/"
            style={{
              display: 'inline-flex', alignItems: 'center', gap: 8,
              marginTop: 14, background: 'var(--ink)', color: '#fff',
              padding: '10px 20px', borderRadius: 'var(--r-md)',
              fontWeight: 700, fontSize: 13.5,
            }}
          >
            לקטלוג
            <Icon name="arrow" size={14} stroke={2.2} />
          </Link>
        </div>
      ) : (
        <div className="orders-list">
          {orders.map(o => <OrderRow
            key={o.orderNumber} o={o}
            onReorder={() => reorder(o)}
            reordering={reordering === o.orderNumber}
            onCancel={() => cancelOrder(o)}
            cancelling={cancelling === o.orderNumber}
          />)}
        </div>
      )}
    </div>
  )
}

function OrderRow({ o, onReorder, reordering, onCancel, cancelling }: {
  o: OrderView
  onReorder: () => void
  reordering: boolean
  onCancel: () => void
  cancelling: boolean
}) {
  const itemCount = useMemo(() => o.items.reduce((s, i) => s + i.quantity, 0), [o.items])
  const date = new Date(o.createdAt)
  const dateStr = `${String(date.getDate()).padStart(2, '0')}/${String(date.getMonth() + 1).padStart(2, '0')}/${date.getFullYear()}`
  const canReorder = o.status !== 'PENDING' && o.items.length > 0
  const showCancel = canCustomerCancel(o.status)

  return (
    <article className={`order-row status-${o.status.toLowerCase()}`}>
      <div className="top">
        <div>
          <div className="num mono">{o.orderNumber}</div>
          <div className="meta">{dateStr} · {itemCount} פריטים</div>
        </div>
        <span className={`status-pill s-${o.status.toLowerCase()}`}>{STATUS_LABEL[o.status]}</span>
      </div>

      <div className="items-preview">
        {o.items.slice(0, 3).map(it => (
          <span key={it.productId} className="item-chip">
            {it.nameHe} <span className="q">×{it.quantity}</span>
          </span>
        ))}
        {o.items.length > 3 && <span className="item-chip more">+{o.items.length - 3}</span>}
      </div>

      {(o.refundedAt || (o.cancelledAt && o.status === 'CANCELLED')) && (
        <div
          style={{
            marginTop: 10,
            padding: '8px 12px',
            fontSize: 12,
            background: 'var(--paper-2, #f5f3ef)',
            borderRadius: 'var(--r-sm, 6px)',
            color: 'var(--ink-2, #555)',
            lineHeight: 1.5,
          }}
        >
          {o.refundedAt
            ? `הוחזר ${formatPrice(o.refundAmountAgorot ?? o.totalAgorot)} · ${new Date(o.refundedAt).toLocaleDateString('he-IL')}`
            : `בוטלה · ${new Date(o.cancelledAt!).toLocaleDateString('he-IL')}`}
          {o.cancellationReason ? ` · ${o.cancellationReason}` : ''}
        </div>
      )}

      <div className="row-foot">
        <div className="total">
          <span className="lbl">סה״כ</span>
          <span className="v mono">{formatPrice(o.totalAgorot)}</span>
        </div>
        <div className="actions">
          <Link to={`/track?orderNumber=${o.orderNumber}`} className="ghost">
            פרטים ומעקב
          </Link>
          {showCancel && (
            <button
              type="button"
              onClick={onCancel}
              disabled={cancelling}
              className="ghost"
              style={{ color: 'var(--berry)' }}
            >
              {cancelling ? 'מבטל…' : 'ביטול הזמנה'}
            </button>
          )}
          {canReorder && (
            <button type="button" onClick={onReorder} disabled={reordering} className="primary">
              {reordering ? 'מוסיף…' : 'הזמן שוב'}
            </button>
          )}
        </div>
      </div>
    </article>
  )
}
