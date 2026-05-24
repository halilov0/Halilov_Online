import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/authStore'
import { Field } from '../components/Field'
import { Icon } from '../components/Icon'
import { comingSoon } from '../components/Toast'

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { login, loading, error } = useAuth()
  const nav = useNavigate()
  const [params] = useSearchParams()
  const next = params.get('next') || '/'

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    try {
      await login(email, password)
      nav(next)
    } catch { /* error in store */ }
  }

  return (
    <div className="cls-auth-split">
      <div className="cls-auth-form">
        <Link to="/" className="brand-link">
          <span className="mark">ח</span>
          <span>חלילוב אונליין</span>
        </Link>

        <div className="eyebrow">ברוכים הבאים</div>
        <h1>להתחבר לחשבון.</h1>
        <p className="lede">
          עקבו אחרי ההזמנות שלכם, שמרו כתובות, וקבלו התראה כשמוצר חוזר למלאי.
        </p>

        <form onSubmit={onSubmit}>
          <Field
            label="אימייל"
            type="email"
            required
            value={email}
            onChange={e => setEmail(e.target.value)}
          />
          <div>
            <Field
              label="סיסמה"
              type="password"
              required
              mono
              value={password}
              onChange={e => setPassword(e.target.value)}
            />
            <a className="forgot" onClick={() => comingSoon('שחזור סיסמה')}>שכחתי סיסמה</a>
          </div>
          {error && <div className="hm-error">{error}</div>}
          <button type="submit" className="submit-cta" disabled={loading}>
            {loading ? 'מתחבר…' : 'כניסה לחשבון'}
            {!loading && <Icon name="arrow" size={14} stroke={2.2} />}
          </button>
          <p className="switch-line">
            חדש אצלנו? <Link to="/register">פתיחת חשבון</Link>
          </p>
        </form>
      </div>

      <div className="cls-auth-decor">
        <div className="inner">
          <div className="eyebrow">קהילת הלקוחות</div>
          <div className="quote">
            "האתר נקי, נדיב,<br />והמשלוחים מגיעים מהר."
          </div>
          <div className="who">— מירה ר., לקוחה</div>
          <div className="features">
            <div className="feat">
              <span className="ico"><Icon name="truck" size={14} /></span>
              <span>משלוח חינם מעל ₪300 לכל הארץ.</span>
            </div>
            <div className="feat">
              <span className="ico"><Icon name="secure" size={14} /></span>
              <span>תשלום מאובטח · Grow / Meshulam.</span>
            </div>
            <div className="feat">
              <span className="ico"><Icon name="pkg" size={14} /></span>
              <span>החזרות חינם עד 14 יום.</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
