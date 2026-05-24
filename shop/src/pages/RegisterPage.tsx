import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/authStore'
import { useDeliveryConfig } from '../delivery/deliveryConfig'
import { Field } from '../components/Field'
import { Icon } from '../components/Icon'

export function RegisterPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [marketingOptIn, setMarketingOptIn] = useState(false)
  const freeAboveShekels = Math.round(useDeliveryConfig(s => s.freeAboveAgorot) / 100)
  const { register, loading, error } = useAuth()
  const nav = useNavigate()

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    try {
      await register({ email, password, fullName, phone: phone || undefined, marketingOptIn })
      nav('/')
    } catch { /* error in store */ }
  }

  return (
    <div className="cls-auth-split">
      <div className="cls-auth-form">
        <Link to="/" className="brand-link">
          <span className="mark">ח</span>
          <span>חלילוב אונליין</span>
        </Link>

        <div className="eyebrow">הצטרפו אלינו</div>
        <h1>פתיחת חשבון.</h1>
        <p className="lede">
          רישום מהיר. שמרו פרטי משלוח, עקבו אחרי הזמנות,
          וקבלו עדכונים על מבצעים חמים.
        </p>

        <form onSubmit={onSubmit}>
          <Field label="שם מלא" required value={fullName} onChange={e => setFullName(e.target.value)} />
          <Field label="אימייל" type="email" required value={email} onChange={e => setEmail(e.target.value)} />
          <Field label="טלפון (לא חובה)" mono value={phone} onChange={e => setPhone(e.target.value)} />
          <Field
            label="סיסמה (לפחות 8 תווים)"
            type="password"
            required
            minLength={8}
            mono
            value={password}
            onChange={e => setPassword(e.target.value)}
          />
          <label className="cls-consent">
            <input
              type="checkbox"
              checked={marketingOptIn}
              onChange={e => setMarketingOptIn(e.target.checked)}
            />
            <span>
              אני מאשר/ת שמירת האימייל וקבלת מבצעים ועדכונים מחלילוב אונליין.
              ניתן להסיר בכל עת מתוך החשבון או דרך הקישור בכל מייל.
            </span>
          </label>
          {error && <div className="hm-error">{error}</div>}
          <button type="submit" className="submit-cta" disabled={loading}>
            {loading ? 'נרשם…' : 'יצירת חשבון'}
            {!loading && <Icon name="arrow" size={14} stroke={2.2} />}
          </button>
          <p className="switch-line">
            כבר רשום? <Link to="/login">התחברות</Link>
          </p>
        </form>
      </div>

      <div className="cls-auth-decor">
        <div className="inner">
          <div className="eyebrow">למה חלילוב</div>
          <div className="quote">
            "המרקט הישראלי<br />שעובד בשבילך."
          </div>
          <div className="who">— צוות חלילוב</div>
          <div className="features">
            <div className="feat">
              <span className="ico"><Icon name="bolt" size={14} /></span>
              <span>10% הנחה בהזמנה הראשונה.</span>
            </div>
            <div className="feat">
              <span className="ico"><Icon name="truck" size={14} /></span>
              <span>משלוח לכל הארץ · חינם מעל ₪{freeAboveShekels}.</span>
            </div>
            <div className="feat">
              <span className="ico"><Icon name="phone" size={14} /></span>
              <span>שירות לקוחות · 054-6770020.</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
