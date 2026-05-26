import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

const STORAGE_KEY = 'halilov_cookie_consent_v1'

function readConsent(): boolean {
  try { return localStorage.getItem(STORAGE_KEY) === 'accepted' }
  catch { return true }
}

export function CookieBanner() {
  const [accepted, setAccepted] = useState(true)

  useEffect(() => { setAccepted(readConsent()) }, [])

  if (accepted) return null

  const accept = () => {
    try { localStorage.setItem(STORAGE_KEY, 'accepted') } catch {}
    setAccepted(true)
  }

  return (
    <div className="hm-cookie-banner" role="dialog" aria-label="הודעת עוגיות">
      <div className="hm-cookie-text">
        אנחנו משתמשים בעוגיות (Cookies) לצורך תפעול האתר ושיפור חוויית הקנייה
        (כמו זכירת עגלת הקניות וההעדפות שלך). המשך הגלישה מהווה הסכמה לשימוש.
        למידע נוסף ראו{' '}
        <Link to="/privacy">מדיניות פרטיות</Link>.
      </div>
      <button type="button" className="hm-cookie-accept" onClick={accept}>
        אישור
      </button>
    </div>
  )
}
