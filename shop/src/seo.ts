/**
 * Centralised per-route SEO for the SPA.
 *
 * We ship a single static index.html for every route (no SSR), so any
 * canonical/robots tag baked into the shell would apply to ALL pages — which
 * is exactly how every info page ended up canonicalised to the homepage
 * ("Alternate page with proper canonical tag" in Search Console). App mounts a
 * single effect that calls into here on every navigation, making each route
 * report its own canonical and keeping non-public routes out of the index.
 *
 * Keep {@link INDEXABLE_EXACT} in sync with STATIC_PATHS in
 * backend/src/main/java/com/halilov/online/catalog/SitemapController.java.
 */

const ORIGIN = 'https://halilov.co.il'

// Public, indexable static routes — mirrors SitemapController.STATIC_PATHS.
// Product pages (/p/{slug}) are matched separately by prefix.
const INDEXABLE_EXACT = new Set([
  '/', '/about', '/shipping', '/returns', '/faq',
  '/contact', '/terms', '/privacy', '/accessibility',
])

/** Drop trailing slashes so /about and /about/ canonicalise to one URL. */
function normalizePath(pathname: string): string {
  const p = pathname.replace(/\/+$/, '')
  return p === '' ? '/' : p
}

/** True for routes that should be crawled + indexed (catalog, products, info). */
export function isIndexable(pathname: string): boolean {
  const p = normalizePath(pathname)
  return INDEXABLE_EXACT.has(p) || p.startsWith('/p/')
}

/** Absolute, query-free canonical URL for a route. */
export function canonicalFor(pathname: string): string {
  return ORIGIN + normalizePath(pathname)
}

/** Set, or remove when href is null, the single rel="canonical" link. */
export function setCanonical(href: string | null): void {
  const existing = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')
  if (href === null) {
    existing?.remove()
    return
  }
  if (existing) {
    existing.setAttribute('href', href)
    return
  }
  const el = document.createElement('link')
  el.setAttribute('rel', 'canonical')
  el.setAttribute('href', href)
  document.head.appendChild(el)
}

/** Set, or remove when content is null, the single robots meta. */
export function setRobots(content: string | null): void {
  const existing = document.head.querySelector<HTMLMetaElement>('meta[name="robots"]')
  if (content === null) {
    existing?.remove()
    return
  }
  if (existing) {
    existing.setAttribute('content', content)
    return
  }
  const el = document.createElement('meta')
  el.setAttribute('name', 'robots')
  el.setAttribute('content', content)
  document.head.appendChild(el)
}
