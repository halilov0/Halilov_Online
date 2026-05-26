import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/authStore'
import { Icon } from './Icon'

function useCrumb(): string {
  const loc = useLocation()
  const params = useParams()
  const p = loc.pathname
  if (p === '/') return 'דשבורד'
  if (p.startsWith('/orders/')) return `הזמנות › ${params.orderNumber ?? ''}`
  if (p === '/orders') return 'הזמנות'
  if (p === '/products') return 'מוצרים'
  if (p === '/categories') return 'קטגוריות'
  return ''
}

export function TopBar() {
  const { user, logout } = useAuth()
  const nav = useNavigate()
  const [searchParams] = useSearchParams()
  const initial = user?.fullName?.[0] ?? 'א'
  const crumb = useCrumb()
  const [query, setQuery] = useState(searchParams.get('q') ?? '')

  useEffect(() => { setQuery(searchParams.get('q') ?? '') }, [searchParams])

  const submitSearch = (e: React.FormEvent) => {
    e.preventDefault()
    const v = query.trim()
    nav(v ? `/products?q=${encodeURIComponent(v)}` : '/products')
  }

  return (
    <div className="adm-top">
      <div className="crumb">
        <span>חלילוב</span><span style={{ opacity: .4 }}>›</span>
        <b>{crumb}</b>
      </div>
      <form className="search" onSubmit={submitSearch} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <button type="submit" aria-label="חפש" style={{
          border: 'none', background: 'transparent', padding: 0,
          color: 'var(--ink-3)', display: 'grid', placeItems: 'center', cursor: 'pointer',
        }}>
          <Icon name="search" size={14} />
        </button>
        <input
          type="search"
          value={query}
          onChange={e => setQuery(e.target.value)}
          placeholder="חפש מק״ט, שם מוצר…"
          style={{
            flex: 1, border: 'none', outline: 'none', background: 'transparent',
            fontSize: 13, color: 'var(--ink)', minWidth: 0, fontFamily: 'inherit',
          }}
        />
      </form>
      <a href="https://halilov.co.il/" target="_blank" rel="noreferrer" className="hm-btn hm-btn-quiet" style={{ padding: '8px 14px', fontSize: 12.5 }}>
        צפה בחנות
      </a>
      <div className="who">
        <div className="avatar">{initial}</div>
        <span style={{ fontWeight: 600 }}>{user?.fullName?.split(' ')[0] ?? '—'}</span>
        <a onClick={() => { logout(); nav('/login') }} style={{ color: 'var(--ink-3)', fontSize: 12, marginInlineStart: 6 }}>
          צא
        </a>
      </div>
    </div>
  )
}
