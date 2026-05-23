import { useState } from 'react'
import { useAuth } from '../auth/authStore'
import { Field } from './Field'
import { Icon } from './Icon'

type Props = {
  /** Heading above the form. Defaults to a generic "log in to view". */
  heading?: string
  /** Helper line below the heading. */
  message?: string
  /** Invoked after a successful login. */
  onSuccess?: () => void
}

/**
 * Inline login form for pages that hit a 401. Lets a returning customer
 * authenticate in place instead of bouncing through /login + a redirect —
 * the typical case is opening an order link in a new browser.
 */
export function QuickLogin({ heading, message, onSuccess }: Props) {
  const { login, loading, error, user, logout } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    try {
      await login(email, password)
      onSuccess?.()
    } catch { /* error in store */ }
  }

  return (
    <div className="cls-quick-login">
      <div className="head">
        <div className="ico"><Icon name="user" size={20} /></div>
        <div>
          <h3>{heading ?? 'נדרשת התחברות לצפייה בהזמנה'}</h3>
          <p>{message ?? 'התחברו עם המייל ששמתם בהזמנה כדי לצפות בפרטים.'}</p>
          {user && (
            <p className="current">
              מחוברים כרגע כ-<strong>{user.email}</strong>.{' '}
              <a onClick={() => logout()}>התנתק</a>
            </p>
          )}
        </div>
      </div>
      <form onSubmit={onSubmit} style={{ display: 'grid', gap: 12 }}>
        <Field
          label="אימייל"
          type="email"
          required
          autoComplete="email"
          value={email}
          onChange={e => setEmail(e.target.value)}
        />
        <Field
          label="סיסמה"
          type="password"
          required
          mono
          autoComplete="current-password"
          value={password}
          onChange={e => setPassword(e.target.value)}
        />
        {error && <div className="hm-error">{error}</div>}
        <button type="submit" className="cta" disabled={loading}>
          {loading ? 'מתחבר…' : (user ? 'התחבר עם חשבון אחר' : 'כניסה')}
          {!loading && <Icon name="arrow" size={14} stroke={2.2} />}
        </button>
      </form>
    </div>
  )
}
