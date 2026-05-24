import { Link } from 'react-router-dom'
import { Icon } from './Icon'
import { comingSoon } from './Toast'
import { useDeliveryConfig } from '../delivery/deliveryConfig'

export function Hero() {
  const courierFlatAgorot = useDeliveryConfig(s => s.courierFlatAgorot)
  const freeAboveAgorot = useDeliveryConfig(s => s.freeAboveAgorot)
  const freeAboveShekels = Math.round(freeAboveAgorot / 100)
  const courierShekels = (courierFlatAgorot / 100).toFixed(2)
  return (
    <section className="cls-hero">
      <div className="cls-hero-main">
        <div>
          <div className="eyebrow">
            <span className="dot" />
            המרקט המוביל ישראל · 2026
          </div>
          <h1>
            כל מה שצריך<br />
            <span className="hl">במקום אחד.</span>
          </h1>
          <p>
            אלפי מוצרים, מאות מותגים, משלוח מהיר לכל הארץ.
            המרקט אונליין שעובד בשבילך — מבוקר ועד ערב.
          </p>
          <div className="cta">
            <Link to="/" className="primary">
              לקטלוג המלא
              <Icon name="arrow" size={14} stroke={2.2} />
            </Link>
            <a className="ghost" onClick={() => comingSoon('מבצעים חמים')}>
              מבצעים חמים
            </a>
          </div>
          <div className="stats">
            <div className="stat">
              <div className="num">26+</div>
              <div className="lbl">מוצרים</div>
            </div>
            <div className="stat">
              <div className="num">4</div>
              <div className="lbl">מחלקות</div>
            </div>
            <div className="stat">
              <div className="num">14</div>
              <div className="lbl">יום החזרה</div>
            </div>
          </div>
        </div>
        <div className="cls-hero-art">
          <span className="tag">HALILOV · MARKET</span>
        </div>
      </div>

      <div className="cls-hero-side">
        <div className="eyebrow">דיל היום</div>
        <h3>משלוח חינם<br />מעל {freeAboveShekels}₪</h3>
        <div className="price-strike">
          <span className="old">{courierShekels}₪</span>
          <span className="now">חינם</span>
        </div>
        <div className="side-art">
          <span className="mini-tag">ש״ח</span>
        </div>
      </div>
    </section>
  )
}
