import { useEffect, useState } from 'react'
import { Link, useNavigate, useLocation, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/authStore'
import { useCart } from '../cart/cartStore'
import { useFavorites } from '../favorites/favoritesStore'
import { useDeliveryConfig } from '../delivery/deliveryConfig'
import { api, type Category } from '../api'
import { Icon } from './Icon'
import { comingSoon } from './Toast'

export function Header() {
  const { user, logout } = useAuth()
  const items = useCart(s => s.lines)
  const totalItems = items.reduce((s, l) => s + l.quantity, 0)
  const favCount = useFavorites(s => s.ids.length)
  const freeAboveShekels = Math.round(useDeliveryConfig(s => s.freeAboveAgorot) / 100)
  const nav = useNavigate()
  const loc = useLocation()
  const [searchParams] = useSearchParams()
  const onCatalog = loc.pathname === '/'
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState(searchParams.get('q') ?? '')
  const [categories, setCategories] = useState<Category[]>([])
  const [selectedDept, setSelectedDept] = useState<string>('all')
  const close = () => setOpen(false)

  useEffect(() => { setQuery(searchParams.get('q') ?? '') }, [searchParams])

  useEffect(() => {
    api<Category[]>('/api/categories').then(setCategories).catch(() => {})
  }, [])

  const submitSearch = (e: React.FormEvent) => {
    e.preventDefault()
    const v = query.trim()
    close()
    nav(v ? `/?q=${encodeURIComponent(v)}` : '/')
  }

  const greeting = user ? `שלום, ${user.fullName.split(' ')[0]}` : 'שלום, אורח'

  return (
    <>
      <div className="cls-util-bar">
        <div className="cls-util-inner">
          <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <Icon name="pin" size={12} />
            משלוח לכל הארץ
          </span>
          <span className="sep" />
          <Link to="/contact">שירות לקוחות</Link>
          <span className="sep" />
          <Link to="/track">מעקב הזמנה</Link>
          <span className="sep" />
          <a onClick={() => comingSoon('חנויות מורשות')}>חנויות מורשות</a>
          <div className="util-end">
            <a href="tel:+972546770020">
              <Icon name="phone" size={12} />
              054-6770020
            </a>
            <span className="sep" />
            <a onClick={() => comingSoon('English')}>EN</a>
            <span className="sep" />
            <a>ILS ₪</a>
          </div>
        </div>
      </div>

      <header className={`cls-header${open ? ' open' : ''}`}>
        <div className="cls-header-inner">
          <Link to="/" className="cls-logo" onClick={close} aria-label="חלילוב אונליין · דף הבית">
            <img src="/logo.png" alt="חלילוב אונליין" width={600} height={331} />
          </Link>

          <form className="cls-search" onSubmit={submitSearch}>
            <select
              value={selectedDept}
              onChange={e => setSelectedDept(e.target.value)}
              aria-label="מחלקה"
            >
              <option value="all">כל המחלקות</option>
              {categories.map(c => (
                <option key={c.id} value={c.slug}>{c.nameHe}</option>
              ))}
            </select>
            <input
              type="search"
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder="חיפוש מוצר, מותג או מק״ט…"
            />
            <button type="submit" className="go">
              <Icon name="search" size={16} stroke={2.2} />
              <span className="go-label">חיפוש</span>
            </button>
          </form>

          <div className="cls-header-actions">
            {user ? (
              <Link to="/account" className="cls-icon-btn" onClick={close} title="החשבון שלי">
                <Icon name="user" size={20} />
                <span className="label">
                  <span className="top">{greeting}</span>
                  <span className="bot">החשבון שלי</span>
                </span>
              </Link>
            ) : (
              <Link to="/login" className="cls-icon-btn" onClick={close} title="התחברות">
                <Icon name="user" size={20} />
                <span className="label">
                  <span className="top">{greeting}</span>
                  <span className="bot">התחברות</span>
                </span>
              </Link>
            )}
            <Link to="/favorites" className="cls-icon-btn" title="מועדפים" onClick={close}>
              <Icon name="heart" size={20} />
              <span className="label">
                <span className="top">רשימת</span>
                <span className="bot">מועדפים</span>
              </span>
              {favCount > 0 && <span className="hm-fav-pill">{favCount}</span>}
            </Link>
            <Link
              to="/cart"
              className="cls-icon-btn"
              style={{ background: 'var(--surface)' }}
              onClick={close}
              title="סל קניות"
            >
              <Icon name="bag" size={20} />
              <span className="label">
                <span className="top">הסל</span>
                <span className="bot">שלי</span>
              </span>
              {totalItems > 0 && <span className="cart-pill">{totalItems}</span>}
            </Link>
            <button
              className="cls-menu-toggle"
              aria-label={open ? 'סגור תפריט' : 'פתח תפריט'}
              onClick={() => setOpen(o => !o)}
            >
              <Icon name={open ? 'x' : 'menu'} />
            </button>
          </div>
        </div>

        <div className="cls-mobile-drawer">
          <Link to="/" onClick={close}>כל המוצרים</Link>
          {categories.map(c => (
            <Link key={c.id} to={`/?categoryId=${c.id}`} onClick={close}>
              {c.nameHe}
            </Link>
          ))}
          <a onClick={() => { comingSoon('מבצעים'); close() }}>מבצעים חמים</a>
          <a onClick={() => { comingSoon('חדש בקטלוג'); close() }}>חדש בקטלוג</a>
          <Link to="/favorites" onClick={close}>מועדפים</Link>
          {user ? (
            <>
              <Link to="/account" onClick={close}>החשבון שלי</Link>
              <a onClick={() => { logout(); nav('/'); close() }} style={{ color: 'var(--accent)' }}>
                התנתקות
              </a>
            </>
          ) : (
            <>
              <Link to="/login" onClick={close}>התחברות</Link>
              <Link to="/register" onClick={close} style={{ color: 'var(--accent)', fontWeight: 700 }}>
                הרשמה
              </Link>
            </>
          )}
        </div>
      </header>

      <nav className="cls-deptnav">
        <div className="cls-deptnav-inner">
          <button
            className="all-btn"
            onClick={() => nav('/')}
            type="button"
          >
            <Icon name="grid" size={14} stroke={2.2} />
            כל המחלקות
          </button>
          <Link to="/" className={onCatalog && !searchParams.get('categoryId') ? 'active' : ''}>
            ראשי
          </Link>
          {categories.map(c => {
            const isActive = searchParams.get('categoryId') === String(c.id)
            return (
              <Link
                key={c.id}
                to={`/?categoryId=${c.id}`}
                className={isActive ? 'active' : ''}
              >
                {c.nameHe}
              </Link>
            )
          })}
          <a onClick={() => comingSoon('מבצעים חמים')}>מבצעים חמים</a>
          <a onClick={() => comingSoon('חדש בקטלוג')}>חדש בקטלוג</a>
          <div className="end">
            <span className="deal">
              <Icon name="bolt" size={14} />
              משלוח חינם מ-{freeAboveShekels}₪
            </span>
          </div>
        </div>
      </nav>
    </>
  )
}
