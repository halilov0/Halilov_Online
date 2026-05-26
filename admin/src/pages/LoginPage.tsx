import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/authStore'
import { Field } from '../components/Field'

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [totpCode, setTotpCode] = useState('')
  const { login, submitTotp, cancelTotp, pendingChallenge, loading, error } = useAuth()
  const nav = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    try {
      const result = await login(email, password)
      if (result === 'done') nav(from, { replace: true })
      // 'totp-required' → component re-renders with pendingChallenge set
      // and the TOTP form takes over.
    } catch { /* error in store */ }
  }

  async function onTotpSubmit(e: React.FormEvent) {
    e.preventDefault()
    try {
      await submitTotp(totpCode)
      nav(from, { replace: true })
    } catch { /* error in store */ }
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', minHeight: '100vh' }}>
      {/* form panel */}
      <div style={{ padding: '56px 64px', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 36, fontFamily: 'var(--serif)', fontSize: 22 }}>
          <span style={{
            width: 32, height: 32, borderRadius: '50%',
            background: 'var(--olive)', color: 'var(--paper)',
            display: 'grid', placeItems: 'center', fontWeight: 700, fontSize: 16, fontFamily: 'var(--sans)',
          }}>ח</span>
          <span>חלילוב · ניהול</span>
        </div>

        <div className="hm-meta" style={{
          fontFamily: 'var(--mono)', letterSpacing: '0.18em',
          color: 'var(--terracotta)', textTransform: 'uppercase',
        }}>פאנל מנהל</div>
        <h1 style={{ fontSize: 50, marginTop: 10 }}>כניסה למערכת.</h1>
        <p style={{ color: 'var(--ink-2)', marginTop: 14, lineHeight: 1.6, maxWidth: '42ch' }}>
          ניהול מוצרים, קטגוריות, ומעקב אחר הזמנות. גישה למורשים בלבד.
        </p>

        {!pendingChallenge ? (
          <form onSubmit={onSubmit} style={{ maxWidth: 380, marginTop: 30, display: 'grid', gap: 14 }}>
            <Field label="אימייל" type="email" required value={email} onChange={e => setEmail(e.target.value)} />
            <Field label="סיסמה" type="password" required mono value={password} onChange={e => setPassword(e.target.value)} />
            {error && <div className="hm-error">{error}</div>}
            <button type="submit" className="hm-btn hm-btn-primary" style={{ justifyContent: 'center', padding: '14px 26px', fontSize: 15 }} disabled={loading}>
              {loading ? 'מתחבר…' : 'כניסה'}
            </button>
          </form>
        ) : (
          <form onSubmit={onTotpSubmit} style={{ maxWidth: 380, marginTop: 30, display: 'grid', gap: 14 }}>
            <div style={{ fontSize: 14, color: 'var(--ink-2)', lineHeight: 1.55 }}>
              חתימה דו-שלבית נדרשת. הקלידו את הקוד מאפליקציית האימות,
              או אחד מקודי השחזור ששמרתם.
            </div>
            <Field
              label="קוד 6 ספרות / קוד שחזור"
              type="text"
              required
              mono
              value={totpCode}
              onChange={e => setTotpCode(e.target.value)}
            />
            {error && <div className="hm-error">{error}</div>}
            <button type="submit" className="hm-btn hm-btn-primary" style={{ justifyContent: 'center', padding: '14px 26px', fontSize: 15 }} disabled={loading || !totpCode}>
              {loading ? 'מאמת…' : 'אישור'}
            </button>
            <button
              type="button"
              onClick={() => { cancelTotp(); setTotpCode('') }}
              style={{
                background: 'transparent', border: 'none', color: 'var(--ink-3)',
                fontSize: 12.5, cursor: 'pointer', textDecoration: 'underline',
              }}
            >
              ביטול והתחברות מחדש
            </button>
          </form>
        )}
      </div>

      {/* brand panel (dark, admin variant) */}
      <div style={{
        background: 'var(--ink)', color: 'var(--paper)',
        padding: '56px 64px', position: 'relative', overflow: 'hidden',
        display: 'flex', flexDirection: 'column', justifyContent: 'flex-end',
      }}>
        <div style={{
          position: 'absolute', inset: 0, opacity: .25,
          background: `radial-gradient(circle at 25% 35%, var(--olive) 0 22%, transparent 23%),
                       radial-gradient(circle at 75% 70%, var(--terracotta) 0 16%, transparent 17%),
                       radial-gradient(circle at 35% 80%, var(--gold) 0 12%, transparent 13%)`,
          filter: 'blur(2px)',
        }} />
        <div style={{ position: 'relative' }}>
          <div className="hm-meta" style={{
            fontFamily: 'var(--mono)', color: 'oklch(0.7 0.04 85)',
            letterSpacing: '0.18em', textTransform: 'uppercase',
          }}>חלילוב אונליין</div>
          <div style={{
            fontFamily: 'var(--serif)', fontSize: 42, lineHeight: 1.1,
            marginTop: 14, color: 'var(--paper)',
          }}>
            ניהול שקט,<br />חנות שעובדת.
          </div>
        </div>
      </div>
    </div>
  )
}
