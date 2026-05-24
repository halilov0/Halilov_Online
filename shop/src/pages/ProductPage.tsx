import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, type Product, type Category } from '../api'
import { useCart } from '../cart/cartStore'
import { useFavorites } from '../favorites/favoritesStore'
import { Icon } from '../components/Icon'
import { Footer } from '../components/Footer'
import { useToast } from '../components/Toast'

function formatPriceParts(agorot: number) {
  const [s, a] = (agorot / 100).toFixed(2).split('.')
  return { shekels: s, agorot: a }
}

function NotifyWhenInStock({
  productId, isFav, onToggleFav,
}: { productId: number; isFav: boolean; onToggleFav: () => void }) {
  const [email, setEmail] = useState('')
  const [state, setState] = useState<'idle' | 'submitting' | 'done'>('idle')
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (state !== 'idle') return
    const trimmed = email.trim()
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
      setError('כתובת מייל לא תקינה')
      return
    }
    setError(null)
    setState('submitting')
    try {
      await api<void>(`/api/products/${productId}/stock-notify`, {
        method: 'POST',
        body: JSON.stringify({ email: trimmed }),
      })
      setState('done')
    } catch (err) {
      setState('idle')
      setError(err instanceof Error ? err.message : 'שגיאה')
    }
  }

  return (
    <div className="cls-pdp-notify">
      <div className="head">
        <div>
          <div className="title">המוצר אזל מהמלאי</div>
          <div className="sub">השאירו מייל ונעדכן אתכם ברגע שיחזור.</div>
        </div>
        <button
          type="button"
          className={`fav-cta${isFav ? ' active' : ''}`}
          onClick={onToggleFav}
          aria-label={isFav ? 'הסר ממועדפים' : 'הוסף למועדפים'}
          aria-pressed={isFav}
        >
          <Icon name="heart" size={18} />
        </button>
      </div>
      {state === 'done' ? (
        <div className="ok">
          <Icon name="check" size={14} stroke={2.4} />
          נרשמת. נשלח אליך מייל כשהמוצר יחזור למלאי.
        </div>
      ) : (
        <form onSubmit={submit}>
          <input
            type="email"
            inputMode="email"
            placeholder="המייל שלך"
            value={email}
            onChange={e => setEmail(e.target.value)}
            disabled={state === 'submitting'}
            dir="ltr"
            required
          />
          <button type="submit" disabled={state === 'submitting'}>
            {state === 'submitting' ? 'שולח…' : 'עדכנו אותי'}
          </button>
        </form>
      )}
      {error && <div className="err">{error}</div>}
    </div>
  )
}

export function ProductPage() {
  const { slug } = useParams<{ slug: string }>()
  const [product, setProduct] = useState<Product | null>(null)
  const [categories, setCategories] = useState<Category[]>([])
  const [error, setError] = useState<string | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [activeImage, setActiveImage] = useState<string | null>(null)
  const add = useCart(s => s.add)
  const favIds = useFavorites(s => s.ids)
  const toggleFav = useFavorites(s => s.toggle)
  const pushToast = useToast(s => s.push)
  const nav = useNavigate()

  useEffect(() => {
    if (!slug) return
    setError(null)
    setProduct(null)
    setActiveImage(null)
    api<Product>(`/api/products/${slug}`)
      .then(p => {
        setProduct(p)
        setActiveImage(p.imageUrl ?? p.imageUrls?.[0] ?? null)
      })
      .catch(e => setError(e.message))
  }, [slug])

  useEffect(() => {
    api<Category[]>('/api/categories').then(setCategories).catch(() => {})
  }, [])

  if (error) return <div className="cls-page"><div className="hm-error">{error}</div></div>
  if (!product) return <div className="cls-page"><p style={{ color: 'var(--ink-3)' }}>טוען…</p></div>

  const categoryName = product.categoryId != null
    ? categories.find(c => c.id === product.categoryId)?.nameHe
    : undefined

  const isFav = favIds.includes(product.id)
  const outOfStock = product.stockQty <= 0
  const lowStock = product.stockQty > 0 && product.stockQty < 10
  const lineTotal = product.priceAgorot * quantity
  const totalParts = formatPriceParts(lineTotal)
  const price = formatPriceParts(product.priceAgorot)

  return (
    <>
      <div className="cls-page">
        <div className="cls-crumb">
          <Link to="/">קטלוג</Link>
          {categoryName && (
            <>
              <span className="sep">›</span>
              <Link to={`/?categoryId=${product.categoryId}`}>{categoryName}</Link>
            </>
          )}
          <span className="sep">›</span>
          <span className="current">{product.nameHe}</span>
        </div>

        <div className="cls-pdp">
          <div>
            <div className="cls-pdp-gallery">
              <div className="badges">
                {outOfStock && <span className="badge-pill out" style={{ background: 'var(--ink-4)', color: '#fff', fontFamily: 'var(--mono)', fontSize: 11, fontWeight: 700, padding: '4px 10px', borderRadius: 'var(--r-xs)' }}>אזל</span>}
                {!outOfStock && lowStock && <span className="badge-pill" style={{ background: 'var(--accent)', color: '#fff', fontFamily: 'var(--mono)', fontSize: 11, fontWeight: 700, padding: '4px 10px', borderRadius: 'var(--r-xs)' }}>מלאי אחרון</span>}
              </div>
              {activeImage ? (
                <img src={activeImage} alt={product.nameHe} />
              ) : (
                <span className="ph">{product.sku}</span>
              )}
            </div>
            {(() => {
              const gallery = [product.imageUrl, ...(product.imageUrls ?? [])]
                .filter((u): u is string => !!u)
              if (gallery.length < 2) return null
              return (
                <div className="cls-pdp-thumbs">
                  {gallery.map(url => (
                    <button
                      key={url}
                      type="button"
                      className={url === activeImage ? 'active' : ''}
                      onClick={() => setActiveImage(url)}
                      aria-label="צפייה בתמונה"
                    >
                      <img src={url} alt="" />
                    </button>
                  ))}
                </div>
              )
            })()}
          </div>

          <div className="cls-pdp-info">
            <div>
              <div className="eyebrow">
                {categoryName ? `${categoryName} · ` : ''}{product.sku}
              </div>
              <h1>{product.nameHe}</h1>
            </div>

            {product.descriptionHe && <p className="desc">{product.descriptionHe}</p>}

            <div className="cls-pdp-price-box">
              <div>
                <div className="label">מחיר ליחידה</div>
                <div className="price">
                  <span className="sym">₪</span>{price.shekels}
                  <span className="agorot">.{price.agorot}</span>
                </div>
              </div>
              <div style={{ textAlign: 'end' }}>
                {outOfStock ? (
                  <span className="stock-pill out"><span className="dot" />אזל מהמלאי</span>
                ) : (
                  <span className="stock-pill in"><span className="dot" />במלאי · {product.stockQty} יח׳</span>
                )}
                <div className="ship-note">משלוח חינם מעל ₪300</div>
              </div>
            </div>

            {outOfStock ? (
              <NotifyWhenInStock
                productId={product.id}
                isFav={isFav}
                onToggleFav={() => {
                  const nowFav = toggleFav(product.id)
                  pushToast(nowFav ? 'נוסף למועדפים' : 'הוסר מהמועדפים')
                }}
              />
            ) : (
              <>
                <div className="cls-pdp-cta-row">
                  <div className="cls-qty">
                    <button
                      type="button"
                      onClick={() => setQuantity(q => Math.max(1, q - 1))}
                      disabled={quantity <= 1}
                      aria-label="פחות"
                    >
                      <Icon name="minus" size={16} stroke={2.2} />
                    </button>
                    <span className="val">{quantity}</span>
                    <button
                      type="button"
                      onClick={() => setQuantity(q => Math.min(Math.min(99, product.stockQty), q + 1))}
                      disabled={quantity >= Math.min(99, product.stockQty)}
                      aria-label="עוד"
                    >
                      <Icon name="plus" size={16} stroke={2.2} />
                    </button>
                  </div>
                  <button
                    className="add-cta"
                    onClick={() => add(product, quantity)}
                  >
                    <Icon name="bag" size={16} stroke={2.2} />
                    הוסף לסל · ₪{totalParts.shekels}.{totalParts.agorot}
                  </button>
                  <button
                    className={`fav-cta${isFav ? ' active' : ''}`}
                    onClick={() => {
                      const nowFav = toggleFav(product.id)
                      pushToast(nowFav ? 'נוסף למועדפים' : 'הוסר מהמועדפים')
                    }}
                    aria-label={isFav ? 'הסר ממועדפים' : 'הוסף למועדפים'}
                    aria-pressed={isFav}
                    type="button"
                  >
                    <Icon name="heart" size={18} />
                  </button>
                </div>

                <button
                  className="cls-pdp-buy-now"
                  onClick={() => { add(product, quantity); nav('/cart') }}
                  type="button"
                >
                  קנייה מהירה
                  <Icon name="arrow" size={14} stroke={2.2} />
                </button>
              </>
            )}

            <div className="cls-pdp-trust">
              {([
                ['truck',  'משלוח חינם',   'מעל ₪300 · 3-5 ימי עסקים'],
                ['secure', 'תשלום מאובטח',  'Grow/Meshulam · PCI'],
                ['pkg',    'החזרות 14 יום', 'בלי שאלות. בלי כאב ראש.'],
              ] as const).map(([i, t, s]) => (
                <div key={i} className="tile">
                  <div className="ico"><Icon name={i} size={18} /></div>
                  <div className="t">{t}</div>
                  <div className="s">{s}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
      <Footer />
    </>
  )
}
