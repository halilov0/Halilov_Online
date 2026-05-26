import { useEffect, useMemo, useState } from 'react'
import DOMPurify from 'dompurify'
import { api } from '../api'
import { Field } from '../components/Field'
import { useToast } from '../components/Toast'

type RecipientCount = { eligibleCount: number }
type BroadcastResult = { queued: number; eligibleCount: number }

const SAMPLE_HTML = `<div style="font-family: system-ui, -apple-system, Arial, sans-serif; direction: rtl; max-width: 560px; margin: 0 auto; padding: 24px;">
  <h1 style="color:#1a1a1a;font-size:24px;margin:0 0 12px">שלום!</h1>
  <p style="color:#444;line-height:1.7">
    יש לנו חדשות בשבילכם.
  </p>
  <p style="margin-top:24px">
    <a href="https://halilov.online" style="background:#1a1a1a;color:#fff;padding:12px 24px;text-decoration:none;border-radius:8px;font-weight:700">לקטלוג</a>
  </p>
</div>`

export function MarketingPage() {
  const pushToast = useToast(s => s.push)
  const [count, setCount] = useState<number | null>(null)
  const [subject, setSubject] = useState('')
  const [htmlBody, setHtmlBody] = useState(SAMPLE_HTML)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastResult, setLastResult] = useState<BroadcastResult | null>(null)

  // Backend re-sanitizes on broadcast — this client pass keeps the preview
  // from executing anything the admin pasted (self-XSS in the live preview).
  const safePreview = useMemo(() => DOMPurify.sanitize(htmlBody), [htmlBody])

  function loadCount() {
    api<RecipientCount>('/api/admin/marketing/recipients')
      .then(r => setCount(r.eligibleCount))
      .catch(e => setError(e instanceof Error ? e.message : 'שגיאה'))
  }
  useEffect(loadCount, [])

  async function send() {
    if (!subject.trim()) { setError('נדרשת כותרת'); return }
    if (!htmlBody.trim()) { setError('נדרש תוכן'); return }
    if (!count || count === 0) { setError('אין נמענים שאישרו'); return }
    if (!confirm(`לשלוח ל-${count} נמענים?`)) return

    setError(null); setSending(true)
    try {
      const res = await api<BroadcastResult>('/api/admin/marketing/broadcast', {
        method: 'POST',
        body: JSON.stringify({ subject: subject.trim(), htmlBody }),
      })
      setLastResult(res)
      pushToast(`נשלח ל-${res.queued} נמענים`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'שגיאה בשליחה')
    } finally {
      setSending(false)
    }
  }

  return (
    <>
      <div style={{ marginBottom: 18 }}>
        <h1>שיווק במייל</h1>
        <div className="sub">
          {count === null ? 'טוען נמענים…' : `${count.toLocaleString('he-IL')} נמענים אישרו קבלת עדכונים`}
        </div>
      </div>

      {error && <div className="hm-error" style={{ marginBottom: 14 }}>{error}</div>}
      {lastResult && (
        <div className="adm-card" style={{ marginBottom: 14, background: '#f0f7f0', borderColor: '#cbe2cb' }}>
          הקמפיין נכנס לתור · {lastResult.queued} מתוך {lastResult.eligibleCount} נשלחו לתור המייל.
        </div>
      )}

      <div className="adm-card" style={{ display: 'grid', gap: 14, maxWidth: 920 }}>
        <Field
          label="כותרת המייל"
          required
          value={subject}
          placeholder="למשל: סוף שבוע · 15% הנחה על הכל"
          onChange={e => setSubject(e.target.value)}
        />

        <div className="hm-field">
          <label>תוכן (HTML)</label>
          <textarea
            className="hm-input mono"
            rows={16}
            value={htmlBody}
            onChange={e => setHtmlBody(e.target.value)}
            style={{ fontSize: 12, lineHeight: 1.5, resize: 'vertical' }}
          />
          <div style={{ fontSize: 11.5, color: 'var(--ink-3)', marginTop: 6 }}>
            קישור הסרה מתווסף אוטומטית בתחתית כל מייל. אל תוסיפו ידני.
          </div>
        </div>

        <div className="hm-field">
          <label>תצוגה מקדימה</label>
          <div
            style={{
              background: '#f7f5f0', padding: 16, borderRadius: 8,
              border: '1px solid var(--line)', maxHeight: 420, overflow: 'auto',
            }}
            dangerouslySetInnerHTML={{ __html: safePreview }}
          />
        </div>

        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <button
            type="button"
            className="hm-btn hm-btn-primary"
            onClick={send}
            disabled={sending || !count}
          >
            {sending ? 'שולח…' : `שליחה ל-${count ?? 0} נמענים`}
          </button>
          <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>
            השליחה דרך Brevo. תור פנימי דואג לניסיונות חוזרים.
          </span>
        </div>
      </div>
    </>
  )
}
