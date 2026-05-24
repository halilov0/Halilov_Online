import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, type Category, type Page, type Product } from '../api'
import { Hero } from '../components/Hero'
import { ProductCard } from '../components/ProductCard'
import { Footer } from '../components/Footer'
import { Icon } from '../components/Icon'
import { comingSoon } from '../components/Toast'
import { useCart } from '../cart/cartStore'

const DEPT_COLORS = [
  '#f4a261', '#2a9d8f', '#e76f51', '#264653',
  '#e9c46a', '#8338ec', '#06b6d4', '#ef4444',
]

const STUB_BRANDS = [
  'Tnuva', 'Strauss', 'Elite', 'Osem', 'Tara',
  'Wissotzky', 'Sano', 'Telma', 'Nestlé', 'Soglowek',
  'Yoplait', 'Coca-Cola',
]

function FlashCard({ p, savePct }: { p: Product; savePct: number }) {
  const add = useCart(s => s.add)
  const outOfStock = p.stockQty <= 0
  const stockPct = Math.max(8, Math.min(90, p.stockQty * 6))
  const [shekels, agorot] = (p.priceAgorot / 100).toFixed(2).split('.')

  return (
    <Link to={`/p/${p.slug}`} className="cls-flash-card">
      <span className="save-tag">-{savePct}%</span>
      <div className="img-wrap">
        {p.imageUrl ? (
          <img src={p.imageUrl} alt={p.nameHe} loading="lazy" />
        ) : (
          <span className="ph">{p.sku}</span>
        )}
      </div>
      <div className="name">{p.nameHe}</div>
      <div className="price-row">
        <span className="price">
          <span style={{ color: 'var(--ink-3)', fontSize: 12, marginInlineEnd: 2, fontWeight: 500 }}>₪</span>
          {shekels}
          <span style={{ fontSize: 13, color: 'var(--accent-deep)' }}>.{agorot}</span>
        </span>
        <button
          onClick={(e) => { e.preventDefault(); e.stopPropagation(); if (!outOfStock) add(p, 1) }}
          disabled={outOfStock}
          aria-label="הוסף לסל"
          style={{
            background: outOfStock ? 'var(--surface-2)' : 'var(--ink)',
            color: outOfStock ? 'var(--ink-4)' : '#fff',
            border: 'none', borderRadius: 'var(--r-sm)',
            padding: '6px 10px', fontWeight: 700, fontSize: 12,
            display: 'inline-flex', alignItems: 'center', gap: 4,
            cursor: outOfStock ? 'not-allowed' : 'pointer',
            marginInlineStart: 'auto',
          }}
        >
          <Icon name="plus" size={12} stroke={2.2} />
        </button>
      </div>
      <div className="progress"><div className="bar" style={{ width: `${stockPct}%` }} /></div>
      <div className="stock-row">
        <span className="left">נמכרו {Math.max(3, 100 - p.stockQty)} יח׳</span>
        <span>נשארו {p.stockQty}</span>
      </div>
    </Link>
  )
}

function DeptTile({ c, idx, productCount }: { c: Category; idx: number; productCount: number }) {
  const color = DEPT_COLORS[idx % DEPT_COLORS.length]
  return (
    <Link to={`/?categoryId=${c.id}`} className="cls-dept-tile">
      <div>
        <h3>{c.nameHe}</h3>
        <div className="count">{productCount} מוצרים</div>
      </div>
      <span className="cta-link">
        לקנייה
        <Icon name="arrow" size={12} stroke={2.2} />
      </span>
      <span className="swatch" style={{ background: color }} />
      <span className="swatch-2" />
    </Link>
  )
}

export function CatalogPage() {
  const [searchParams] = useSearchParams()
  const q = (searchParams.get('q') ?? '').trim()
  const urlCategoryId = searchParams.get('categoryId')
  const categoryId = urlCategoryId ? Number(urlCategoryId) : null
  const [categories, setCategories] = useState<Category[]>([])
  const [products, setProducts] = useState<Product[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<Category[]>('/api/categories').then(setCategories).catch(() => {})
  }, [])

  useEffect(() => {
    setLoading(true)
    const params = new URLSearchParams()
    params.set('size', '50')
    if (categoryId) params.set('categoryId', String(categoryId))
    if (q) params.set('q', q)
    api<Page<Product>>(`/api/products?${params.toString()}`)
      .then(p => setProducts(p.content))
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [categoryId, q])

  const categoryNameById = useMemo(() => {
    const m = new Map<number, string>()
    categories.forEach(c => m.set(c.id, c.nameHe))
    return m
  }, [categories])

  const isHome = !q && !categoryId
  const activeCategoryName = categoryId ? categoryNameById.get(categoryId) : null

  const flashProducts = products.slice(0, 4)
  const gridProducts = isHome ? products.slice(4) : products

  return (
    <>
      <div className="cls-page">
        {isHome && <Hero />}

        {isHome && (
          <div className="cls-brand-strip">
            <span className="label">מותגים מובילים</span>
            <div className="brands">
              {STUB_BRANDS.map(b => (
                <a key={b} className="brand" onClick={() => comingSoon(`מותג ${b}`)}>
                  {b}
                </a>
              ))}
            </div>
          </div>
        )}

        {isHome && categories.length > 0 && (
          <>
            <div className="cls-section-head">
              <div className="title">
                <h2>קנה לפי מחלקה</h2>
                <span className="meta">{categories.length} מחלקות זמינות</span>
              </div>
            </div>
            <div className="cls-dept-tiles">
              {categories.map((c, i) => (
                <DeptTile
                  key={c.id}
                  c={c}
                  idx={i}
                  productCount={products.filter(p => p.categoryId === c.id).length}
                />
              ))}
            </div>
          </>
        )}

        {isHome && flashProducts.length > 0 && (
          <div className="cls-flash-wrap">
            <div className="cls-flash-head">
              <div className="title-row">
                <span className="lbl">
                  <Icon name="bolt" size={14} stroke={2.2} />
                  פלאש דיל
                </span>
                <h2>מומלצים השבוע</h2>
              </div>
              <span className="meta">מבצעים מוגבלים בזמן</span>
            </div>
            <div className="cls-flash-grid">
              {flashProducts.map((p, i) => (
                <FlashCard key={p.id} p={p} savePct={[15, 22, 18, 25][i] ?? 15} />
              ))}
            </div>
          </div>
        )}

        <div className="cls-section-head">
          <div className="title">
            <h2>
              {q ? `חיפוש: "${q}"` : activeCategoryName ? activeCategoryName : 'כל המוצרים'}
            </h2>
            {!loading && (
              <span className="meta">
                {q || categoryId ? `${products.length} תוצאות` : `${gridProducts.length} מוצרים נוספים`}
              </span>
            )}
          </div>
          <button
            className="see-all"
            onClick={() => comingSoon('מיון')}
          >
            מיין לפי: חדש קודם
            <Icon name="chev-d" size={14} stroke={2.2} />
          </button>
        </div>

        {error && <div className="hm-error" style={{ marginBottom: 14 }}>{error}</div>}
        {loading && <p style={{ color: 'var(--ink-3)' }}>טוען…</p>}

        <div className="cls-grid">
          {gridProducts.map(p => (
            <ProductCard
              key={p.id}
              p={p}
              categoryName={p.categoryId != null ? categoryNameById.get(p.categoryId) : undefined}
            />
          ))}
        </div>

        {!loading && products.length === 0 && (
          <p style={{ marginTop: 24, color: 'var(--ink-3)' }}>אין מוצרים להצגה.</p>
        )}

        {isHome && (
          <div className="cls-promo-2">
            <div className="cls-promo-card">
              <div>
                <div className="eyebrow">מצטרפים חדשים</div>
                <h3>10% הנחה<br />בהזמנה ראשונה</h3>
                <p className="lede">פתחו חשבון בחינם וקבלו קופון לקנייה הבאה.</p>
                <Link to="/register" className="promo-cta">
                  פתיחת חשבון
                  <Icon name="arrow" size={12} stroke={2.2} />
                </Link>
              </div>
              <div className="promo-art">
                <span className="mini-tag">NEW · 10%</span>
              </div>
            </div>
            <div className="cls-promo-card dark">
              <div>
                <div className="eyebrow">משלוחים</div>
                <h3>שליח<br />עד הבית</h3>
                <p className="lede">3-5 ימי עסקים. חינם בהזמנה מעל ₪300.</p>
                <Link to="/shipping" className="promo-cta">
                  קרא עוד
                  <Icon name="arrow" size={12} stroke={2.2} />
                </Link>
              </div>
              <div className="promo-art">
                <span className="mini-tag">24H · DELIVERY</span>
              </div>
            </div>
          </div>
        )}

        <div className="cls-trust">
          {([
            ['truck', 'משלוח לכל הארץ', 'שליח עד הבית · חינם מעל ₪300.'],
            ['secure','תשלום מאובטח',   'Grow/Meshulam · PCI Compliant.'],
            ['pkg',   'החזרות חינם',     '14 יום להחזיר ללא שאלות.'],
            ['phone', 'שירות לקוחות',   'דברו איתנו · 054-6770020.'],
          ] as const).map(([i, t, s]) => (
            <div key={i} className="cls-trust-item">
              <div className="ico">
                <Icon name={i} size={20} />
              </div>
              <div>
                <div className="t">{t}</div>
                <div className="s">{s}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
      <Footer />
    </>
  )
}
