import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type Category, type Product } from '../api'
import { ProductCard } from '../components/ProductCard'
import { Footer } from '../components/Footer'
import { Icon } from '../components/Icon'
import { useFavorites } from '../favorites/favoritesStore'

export function FavoritesPage() {
  const ids = useFavorites(s => s.ids)
  const adjustments = useFavorites(s => s.adjustments)
  const dismissAdjustment = useFavorites(s => s.dismissAdjustment)
  const reconcileWithRemote = useFavorites(s => s.reconcileWithRemote)
  const [products, setProducts] = useState<Product[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [loading, setLoading] = useState(true)

  // Same focus-style sync as the cart: on mount, reconcile with the server
  // so a heart added on another device shows up immediately (instead of
  // waiting for the next visibilitychange).
  useEffect(() => { void reconcileWithRemote() }, [reconcileWithRemote])

  useEffect(() => {
    if (ids.length === 0) {
      setProducts([])
      setLoading(false)
      return
    }
    setLoading(true)
    Promise.all([
      api<{ content: Product[] }>(`/api/products?size=200`),
      api<Category[]>('/api/categories'),
    ])
      .then(([page, cats]) => {
        setCategories(cats)
        const map = new Map(page.content.map(p => [p.id, p]))
        setProducts(ids.map(id => map.get(id)).filter((p): p is Product => !!p))
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [ids])

  const removedBanner = adjustments.length > 0 && (
    <div className="cls-cart-alerts">
      {adjustments.map(a => (
        <div key={a.productId} className="cls-cart-alert removed">
          <span className="txt">{a.nameHe ?? 'פריט'} הוסר מהמועדפים — אינו זמין יותר</span>
          <button
            type="button"
            className="dismiss"
            onClick={() => dismissAdjustment(a.productId)}
            aria-label="סגירת ההודעה"
          >
            <Icon name="x" size={14} stroke={2.2} />
          </button>
        </div>
      ))}
    </div>
  )

  return (
    <>
      <div className="hm-fav-page">
        <div className="head">
          <h1>המועדפים שלי</h1>
          <span className="count">{ids.length} {ids.length === 1 ? 'מוצר' : 'מוצרים'}</span>
        </div>

        {removedBanner}

        {loading ? (
          <p style={{ color: 'var(--ink-3)' }}>טוען…</p>
        ) : products.length === 0 ? (
          <div className="hm-fav-empty">
            <div className="ico"><Icon name="heart" size={26} /></div>
            <h2>אין עדיין מועדפים</h2>
            <p>סמנו מוצרים שאהבתם עם הלב כדי לשמור אותם כאן.</p>
            <Link to="/" className="cta">
              <Icon name="arrow" size={14} stroke={2.2} />
              חזרה לקטלוג
            </Link>
          </div>
        ) : (
          <div className="cls-grid">
            {products.map(p => {
              const cat = categories.find(c => c.id === p.categoryId)
              return <ProductCard key={p.id} p={p} categoryName={cat?.nameHe} />
            })}
          </div>
        )}
      </div>
      <Footer />
    </>
  )
}
