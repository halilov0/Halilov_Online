import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../api'
import { Field } from '../components/Field'
import { Icon } from '../components/Icon'

type TokenInfo = { email: string; expiresAt: string }

export function PasswordResetPage() {
  const { token = '' } = useParams<{ token: string }>()
  const nav = useNavigate()
  const [info, setInfo] = useState<TokenInfo | null>(null)
  const [tokenError, setTokenError] = useState<string | null>(null)
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  useEffect(() => {
    if (!token) {
      setTokenError('חסר אסימון איפוס בקישור.')
      return
    }
    api<TokenInfo>(`/api/auth/password-reset/${encodeURIComponent(token)}`)
      .then(setInfo)
      .catch((e: ApiError) => {
        if (e.status === 410) setTokenError('הקישור פג תוקף או כבר נוצל. בקשו קישור חדש.')
        else if (e.status === 404) setTokenError('קישור לא קיים. בקשו קישור חדש.')
        else setTokenError(e.message ?? 'שגיאה בטעינת הקישור.')
      })
  }, [token])

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (password.length < 8) { setSubmitError('הסיסמה חייבת להיות לפחות 8 תווים.'); return }
    if (password !== confirm) { setSubmitError('הסיסמאות לא תואמות.'); return }
    setSubmitting(true)
    setSubmitError(null)
    try {
      await api(`/api/auth/password-reset/${encodeURIComponent(token)}`, {
        method: 'POST',
        body: JSON.stringify({ newPassword: password }),
      })
      setDone(true)
      setTimeout(() => nav('/login'), 1800)
    } catch (err: any) {
      setSubmitError(err.message ?? 'שגיאה')
    } finally {
      setSubmitting(false)
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
        <h1>בחרו סיסמה חדשה.</h1>

        {tokenError && (
          <>
            <div className="hm-error" style={{ marginTop: 12 }}>{tokenError}</div>
            <p className="switch-line" style={{ marginTop: 16 }}>
              <Link to="/forgot-password">בקשת קישור חדש</Link>
            </p>
          </>
        )}

        {done && (
          <div className="hm-success" style={{ padding: 14, borderRadius: 8, background: '#e7f5ec', color: '#1f7a45', marginTop: 18 }}>
            הסיסמה הוחלפה בהצלחה. מעביר להתחברות…
          </div>
        )}

        {!tokenError && !done && info && (
          <>
            <p className="lede">
              מאפסים סיסמה לחשבון <b>{info.email}</b>. הסיסמה הקודמת כבר לא תעבוד.
            </p>
            <form onSubmit={onSubmit}>
              <Field
                label="סיסמה חדשה (לפחות 8 תווים)"
                type="password"
                required
                mono
                minLength={8}
                value={password}
                onChange={e => setPassword(e.target.value)}
              />
              <Field
                label="אישור סיסמה"
                type="password"
                required
                mono
                value={confirm}
                onChange={e => setConfirm(e.target.value)}
              />
              {submitError && <div className="hm-error">{submitError}</div>}
              <button type="submit" className="submit-cta" disabled={submitting}>
                {submitting ? 'מחליף…' : 'החלף סיסמה'}
                {!submitting && <Icon name="arrow" size={14} stroke={2.2} />}
              </button>
            </form>
          </>
        )}
      </div>

      <div className="cls-auth-decor">
        <div className="inner">
          <div className="eyebrow">חד-פעמי</div>
          <div className="quote">
            "אחרי האיפוס כל הסשנים הקודמים<br />יסיימו אוטומטית."
          </div>
          <div className="who">— מערכת חלילוב אונליין</div>
        </div>
      </div>
    </div>
  )
}
