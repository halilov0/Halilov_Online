import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { Field } from '../components/Field'
import { Icon } from '../components/Icon'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      await api('/api/auth/forgot-password', {
        method: 'POST',
        body: JSON.stringify({ email }),
      })
      setSent(true)
    } catch (err: any) {
      setError(err.message ?? 'שגיאה')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="cls-auth-split">
      <div className="cls-auth-form">
        <Link to="/" className="brand-link">
          <span className="mark">ח</span>
          <span>חלילוב אונליין</span>
        </Link>

        <div className="eyebrow">איפוס סיסמה</div>
        <h1>שכחתם סיסמה.</h1>
        <p className="lede">
          נשלח לכם קישור איפוס למייל. הקישור תקף שעה אחת.
        </p>

        {sent ? (
          <div style={{ marginTop: 18 }}>
            <div className="hm-success" style={{ padding: 14, borderRadius: 8, background: '#e7f5ec', color: '#1f7a45' }}>
              אם החשבון קיים, שלחנו אליו אימייל עם קישור איפוס. בדקו את תיבת הדואר (כולל ספאם).
            </div>
            <p className="switch-line" style={{ marginTop: 16 }}>
              <Link to="/login">חזרה להתחברות</Link>
            </p>
          </div>
        ) : (
          <form onSubmit={onSubmit}>
            <Field
              label="אימייל"
              type="email"
              required
              value={email}
              onChange={e => setEmail(e.target.value)}
            />
            {error && <div className="hm-error">{error}</div>}
            <button type="submit" className="submit-cta" disabled={loading}>
              {loading ? 'שולח…' : 'שלח קישור איפוס'}
              {!loading && <Icon name="arrow" size={14} stroke={2.2} />}
            </button>
            <p className="switch-line">
              נזכרתם? <Link to="/login">חזרה להתחברות</Link>
            </p>
          </form>
        )}
      </div>

      <div className="cls-auth-decor">
        <div className="inner">
          <div className="eyebrow">בטוח ופשוט</div>
          <div className="quote">
            "קישור חד-פעמי שתקף<br />שעה — בלי טפסים, בלי מעבר.״
          </div>
          <div className="who">— מערכת חלילוב אונליין</div>
        </div>
      </div>
    </div>
  )
}
