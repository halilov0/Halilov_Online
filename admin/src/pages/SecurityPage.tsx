import { useEffect, useState } from 'react'
import QRCode from 'qrcode'
import { api } from '../api'
import { Field } from '../components/Field'
import { useToast } from '../components/Toast'

type Status = { enrolled: boolean }
type EnrollResponse = { secret: string; qrUri: string; recoveryCodes: string[] }

export function SecurityPage() {
  const pushToast = useToast(s => s.push)
  const [status, setStatus] = useState<Status | null>(null)
  const [enrollment, setEnrollment] = useState<EnrollResponse | null>(null)
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null)
  const [confirmCode, setConfirmCode] = useState('')
  const [disableCode, setDisableCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => { loadStatus() }, [])

  function loadStatus() {
    api<Status>('/api/me/totp/status')
      .then(setStatus)
      .catch(e => setError(e instanceof Error ? e.message : 'שגיאה'))
  }

  // Render the QR image whenever an enrollment is in progress.
  useEffect(() => {
    if (!enrollment) { setQrDataUrl(null); return }
    QRCode.toDataURL(enrollment.qrUri, { margin: 1, width: 220 })
      .then(setQrDataUrl)
      .catch(() => setQrDataUrl(null))
  }, [enrollment])

  async function startEnroll() {
    setError(null); setBusy(true)
    try {
      const res = await api<EnrollResponse>('/api/me/totp/enroll', { method: 'POST' })
      setEnrollment(res)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    } finally { setBusy(false) }
  }

  async function confirmEnroll() {
    if (!confirmCode.trim()) { setError('נדרש קוד'); return }
    setError(null); setBusy(true)
    try {
      await api('/api/me/totp/confirm', {
        method: 'POST',
        body: JSON.stringify({ code: confirmCode.trim() }),
      })
      pushToast('2FA הופעל בהצלחה')
      setEnrollment(null)
      setConfirmCode('')
      loadStatus()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    } finally { setBusy(false) }
  }

  async function disableTotp() {
    if (!disableCode.trim()) { setError('נדרש קוד'); return }
    if (!confirm('בטוח שברצונך להשבית את 2FA?')) return
    setError(null); setBusy(true)
    try {
      await api('/api/me/totp/disable', {
        method: 'POST',
        body: JSON.stringify({ code: disableCode.trim() }),
      })
      pushToast('2FA הושבת')
      setDisableCode('')
      loadStatus()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה')
    } finally { setBusy(false) }
  }

  return (
    <>
      <div style={{ marginBottom: 18 }}>
        <h1>אבטחת חשבון</h1>
        <div className="sub">אימות דו-שלבי (TOTP) להגנה על כניסות מ-IP לא מוכר.</div>
      </div>

      {error && <div className="hm-error" style={{ marginBottom: 14 }}>{error}</div>}

      <div className="adm-card" style={{ maxWidth: 640, display: 'grid', gap: 14 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <strong style={{ fontSize: 15 }}>אימות דו-שלבי</strong>
            <div style={{ fontSize: 13, color: 'var(--ink-3)', marginTop: 4 }}>
              {status === null
                ? 'טוען…'
                : status.enrolled
                  ? 'פעיל. נדרש קוד בכל התחברות מ-IP שאינו ברשימה המהימנה.'
                  : 'לא פעיל.'}
            </div>
          </div>
          <span style={{
            padding: '4px 12px', borderRadius: 12, fontSize: 12, fontWeight: 700,
            background: status?.enrolled ? '#cbe2cb' : '#eee',
            color: status?.enrolled ? '#2d5a2d' : '#666',
          }}>
            {status?.enrolled ? 'ON' : 'OFF'}
          </span>
        </div>

        {/* Not enrolled: show "enable" button */}
        {status && !status.enrolled && !enrollment && (
          <button
            type="button"
            className="hm-btn hm-btn-primary"
            onClick={startEnroll}
            disabled={busy}
            style={{ alignSelf: 'flex-start' }}
          >
            {busy ? 'מכין…' : 'הפעלת 2FA'}
          </button>
        )}

        {/* Enrollment in flight: QR + recovery codes + confirm form */}
        {enrollment && (
          <div style={{ display: 'grid', gap: 14, padding: 16, background: '#f7f5f0', borderRadius: 8 }}>
            <div>
              <strong>1. סרקו עם אפליקציית האימות</strong>
              <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 4 }}>
                Google Authenticator / 1Password / Authy
              </div>
              <div style={{ marginTop: 12, textAlign: 'center' }}>
                {qrDataUrl
                  ? <img src={qrDataUrl} alt="QR code" style={{ display: 'inline-block' }} />
                  : <div style={{ color: 'var(--ink-3)', fontSize: 13 }}>טוען QR…</div>}
              </div>
              <details style={{ marginTop: 8, fontSize: 12 }}>
                <summary style={{ cursor: 'pointer', color: 'var(--ink-3)' }}>או הזינו ידנית</summary>
                <div className="mono" style={{ marginTop: 6, padding: 8, background: '#fff', borderRadius: 6, wordBreak: 'break-all' }}>
                  {enrollment.secret}
                </div>
              </details>
            </div>

            <div>
              <strong>2. שמרו את קודי השחזור</strong>
              <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 4 }}>
                כל קוד תקף לשימוש אחד. בלי הקודים האלה, אובדן הטלפון = שיחה ל-SSH.
              </div>
              <div className="mono" style={{
                marginTop: 8, padding: 12, background: '#fff', borderRadius: 6,
                display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 4, fontSize: 13,
              }}>
                {enrollment.recoveryCodes.map(c => <div key={c}>{c}</div>)}
              </div>
            </div>

            <div>
              <strong>3. אשרו עם קוד מהאפליקציה</strong>
              <Field
                label="קוד 6 ספרות"
                type="text"
                value={confirmCode}
                onChange={e => setConfirmCode(e.target.value)}
                mono
              />
            </div>

            <div style={{ display: 'flex', gap: 10 }}>
              <button
                type="button"
                className="hm-btn hm-btn-primary"
                onClick={confirmEnroll}
                disabled={busy || !confirmCode.trim()}
              >
                {busy ? 'מאמת…' : 'הפעל'}
              </button>
              <button
                type="button"
                onClick={() => { setEnrollment(null); setConfirmCode('') }}
                style={{
                  background: 'transparent', border: '1px solid var(--line-2)',
                  borderRadius: 8, padding: '10px 18px', cursor: 'pointer',
                }}
              >
                ביטול
              </button>
            </div>
          </div>
        )}

        {/* Enrolled: show disable form */}
        {status?.enrolled && (
          <div style={{ display: 'grid', gap: 10 }}>
            <strong style={{ fontSize: 13 }}>השבתת 2FA</strong>
            <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>
              נדרש קוד 6 ספרות מהאפליקציה (או קוד שחזור) כדי להשבית.
            </div>
            <Field
              label="קוד נוכחי"
              type="text"
              value={disableCode}
              onChange={e => setDisableCode(e.target.value)}
              mono
            />
            <button
              type="button"
              onClick={disableTotp}
              disabled={busy || !disableCode.trim()}
              style={{
                background: '#fff', border: '1px solid #c66',
                color: '#c66', borderRadius: 8, padding: '10px 18px',
                fontWeight: 700, fontSize: 13, cursor: 'pointer',
                alignSelf: 'flex-start',
              }}
            >
              השבת 2FA
            </button>
          </div>
        )}
      </div>
    </>
  )
}
