import { NavLink } from 'react-router-dom'
import { useAuth } from '../auth/authStore'
import { Icon, type IconName } from './Icon'
import { comingSoon } from './Toast'

type Item = { to: string; label: string; icon: IconName; end?: boolean; onClick?: () => void }

const OPS_ITEMS: Item[] = [
  { to: '/', label: 'דשבורד', icon: 'dash', end: true },
  { to: '/orders', label: 'הזמנות', icon: 'orders' },
  { to: '/products', label: 'מוצרים', icon: 'box' },
  { to: '/categories', label: 'קטגוריות', icon: 'tag' },
  { to: '/coupons', label: 'קופונים', icon: 'percent' },
]

const GROWTH_ITEMS: Item[] = [
  { to: '/marketing', label: 'שיווק', icon: 'megaphone' },
  { to: '/users', label: 'לקוחות', icon: 'users' },
  { to: '/audit-log', label: 'לוג פעולות', icon: 'history' },
]

const GROWTH_LABELS: { label: string; icon: IconName }[] = [
  { label: 'דו"חות', icon: 'chart' },
  { label: 'הגדרות', icon: 'cog' },
]

export function Sidebar() {
  const { user } = useAuth()
  const initial = user?.fullName?.[0] ?? 'א'

  return (
    <aside className="adm-side">
      <NavLink to="/" className="brand">
        <span className="mark">ח</span>
        <span>חלילוב · ניהול</span>
      </NavLink>

      <div className="label">תפעול</div>
      {OPS_ITEMS.map(item => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          className={({ isActive }) => `adm-nav-item ${isActive ? 'active' : ''}`}
        >
          <Icon name={item.icon} size={16} />
          <span>{item.label}</span>
        </NavLink>
      ))}

      <div className="label">צמיחה</div>
      {GROWTH_ITEMS.map(item => (
        <NavLink
          key={item.to}
          to={item.to}
          className={({ isActive }) => `adm-nav-item ${isActive ? 'active' : ''}`}
        >
          <Icon name={item.icon} size={16} />
          <span>{item.label}</span>
        </NavLink>
      ))}
      {GROWTH_LABELS.map(item => (
        <a
          key={item.label}
          className="adm-nav-item"
          onClick={() => comingSoon(item.label)}
        >
          <Icon name={item.icon} size={16} />
          <span>{item.label}</span>
        </a>
      ))}

      <div className="me-card">
        <div className="avatar">{initial}</div>
        <div style={{ fontSize: 12.5, lineHeight: 1.3, minWidth: 0 }}>
          <div style={{ color: 'var(--paper)', fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {user?.fullName ?? '—'}
          </div>
          <div style={{ color: 'oklch(0.6 0.02 80)', fontSize: 11 }}>סופר-אדמין</div>
        </div>
      </div>
    </aside>
  )
}
