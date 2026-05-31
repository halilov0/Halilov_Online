import { Link } from 'react-router-dom'
import { comingSoon } from './Toast'
import { whatsappUrl } from './WhatsAppFab'

export function Footer() {
  return (
    <footer className="cls-footer">
      <div className="cls-footer-inner">
        <div className="col brand-col">
          <div className="cls-logo">
            <div className="logo-wrap">
              <img src="/logo.png" alt="חלילוב אונליין" width={600} height={331} />
            </div>
          </div>
          <p>
            מרקט אונליין ישראלי. אלפי מוצרים, משלוח לכל הארץ,
            ושירות לקוחות שעובד גם בערב.
          </p>
          <div className="socials">
            <a onClick={() => comingSoon('Instagram')} aria-label="Instagram">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                <rect x="3" y="3" width="18" height="18" rx="5" />
                <circle cx="12" cy="12" r="4" />
                <circle cx="17.5" cy="6.5" r="1" fill="currentColor" />
              </svg>
            </a>
            <a onClick={() => comingSoon('Facebook')} aria-label="Facebook">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                <path d="M16 8h-2a2 2 0 0 0-2 2v12M9 13h6" />
              </svg>
            </a>
            <a href={whatsappUrl} target="_blank" rel="noopener noreferrer" aria-label="WhatsApp">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                <path d="M3 21l1.7-5A8 8 0 1 1 8 19z" />
                <path d="M9 10c.5 2 2 3.5 4 4l1-1 3 1v3a8 8 0 0 1-9-9z" />
              </svg>
            </a>
            <a onClick={() => comingSoon('TikTok')} aria-label="TikTok">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                <path d="M9 12a4 4 0 1 0 4 4V4c0 3 2 5 5 5" />
              </svg>
            </a>
          </div>
        </div>

        <div className="col">
          <h4>קנייה</h4>
          <Link to="/">קטלוג</Link>
          <a onClick={() => comingSoon('מבצעים חמים')}>מבצעים חמים</a>
          <a onClick={() => comingSoon('חדש בקטלוג')}>חדש בקטלוג</a>
          <Link to="/favorites">מועדפים</Link>
          <a onClick={() => comingSoon('מותגים')}>מותגים</a>
        </div>

        <div className="col">
          <h4>שירות</h4>
          <Link to="/shipping">משלוחים</Link>
          <Link to="/returns">החזרות וזיכויים</Link>
          <Link to="/track">מעקב הזמנה</Link>
          <Link to="/faq">שאלות נפוצות</Link>
          <Link to="/contact">צור קשר</Link>
        </div>

        <div className="col">
          <h4>חשבון</h4>
          <Link to="/account">החשבון שלי</Link>
          <Link to="/login">התחברות</Link>
          <Link to="/register">פתיחת חשבון</Link>
          <Link to="/cart">סל קניות</Link>
          <Link to="/favorites">מועדפים</Link>
        </div>

        <div className="col">
          <h4>על חלילוב</h4>
          <Link to="/about">עלינו</Link>
          <a onClick={() => comingSoon('קריירה')}>קריירה</a>
          <a onClick={() => comingSoon('עיתונות')}>עיתונות</a>
          <Link to="/terms">תקנון האתר</Link>
          <Link to="/privacy">מדיניות פרטיות</Link>
          <Link to="/accessibility">הצהרת נגישות</Link>
        </div>
      </div>

      <div className="cls-footer-bottom">
        <span>© {new Date().getFullYear()} חלילוב אונליין · עוסק פטור 325350643. כל הזכויות שמורות.</span>
        <div className="pays">
          <span className="pay-chip">VISA</span>
          <span className="pay-chip">MASTERCARD</span>
          <span className="pay-chip">AMEX</span>
        </div>
      </div>
    </footer>
  )
}
