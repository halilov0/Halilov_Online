/**
 * Admin SPA HTTP wrapper and shared types.
 *
 * Mirrors the shop's `api.ts` but with an admin-scoped token key so
 * shop login state and admin login state never collide on the same
 * device. No guest-token plumbing — every admin endpoint requires a
 * full bearer JWT.
 */
const TOKEN_KEY = 'halilov.admin.token'

/** Returns the persisted admin JWT, or `null` if unauthenticated. */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}
/** Persists or clears the admin JWT in `localStorage`. */
export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

/** Error thrown by {@link api} / {@link downloadFile} on non-2xx
 *  responses. The `status` field is the canonical HTTP status. */
export class ApiError extends Error {
  status: number
  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

/**
 * Authenticated file download. Used by the CSV export buttons —
 * we cannot rely on a plain `<a href>` because the request needs the
 * bearer token. Honours the server's `Content-Disposition` filename
 * when present, otherwise falls back to `fallbackName`.
 */
export async function downloadFile(path: string, fallbackName: string): Promise<void> {
  const headers = new Headers()
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const res = await fetch(path, { headers })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new ApiError(text || res.statusText, res.status)
  }
  const blob = await res.blob()
  const cd = res.headers.get('Content-Disposition') ?? ''
  const match = /filename="?([^"]+)"?/i.exec(cd)
  const name = match ? match[1] : fallbackName
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

/**
 * Typed `fetch` wrapper. Auto-injects the admin bearer token,
 * `Content-Type: application/json` (unless the body is a `FormData`),
 * and throws {@link ApiError} on non-2xx with a Hebrew error message
 * resolved by {@link extractErrorMessage}.
 *
 * @typeParam T - Expected JSON response shape.
 */
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const isFormData = init.body instanceof FormData
  if (init.body && !isFormData && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const res = await fetch(path, { ...init, headers })
  const text = await res.text()
  const body = text ? (() => { try { return JSON.parse(text) } catch { return text } })() : null

  if (!res.ok) {
    const msg = extractErrorMessage(body, res.status, res.statusText)
    throw new ApiError(msg, res.status)
  }
  return body as T
}

function extractErrorMessage(body: unknown, status: number, statusText: string): string {
  if (status === 429) return 'יותר מדי ניסיונות. נסו שוב בעוד דקה.'
  if (status === 503) return 'השירות זמנית לא זמין. ננסה שוב בעוד רגע.'
  if (typeof body === 'string' && body) {
    if (body.trimStart().startsWith('<')) return statusText || 'שגיאה'
    return body
  }
  if (body && typeof body === 'object') {
    const b = body as Record<string, unknown>
    if (typeof b.message === 'string' && b.message) return b.message
    if (typeof b.error === 'string' && b.error) return b.error
  }
  return statusText || 'שגיאה'
}

export type Role = 'CUSTOMER' | 'ADMIN'

export type AuthResponse = { token: string; email: string; role: Role; fullName: string }
export type Me = { id: number; email: string; fullName: string; phone: string | null; role: Role }

export type Category = { id: number; slug: string; nameHe: string; parentId: number | null; sortOrder: number }
export type CategoryUpsert = { slug: string; nameHe: string; parentId: number | null; sortOrder: number }

export type Product = {
  id: number; sku: string; slug: string; nameHe: string; descriptionHe: string | null
  categoryId: number | null; priceAgorot: number; stockQty: number
  imageUrl: string | null; imageUrls: string[]; active: boolean
}
export type ProductUpsert = Omit<Product, 'id' | 'imageUrls'> & { imageUrls: string[] }

export type OrderStatus = 'PENDING' | 'PAID' | 'FULFILLED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'REFUNDED'

export type OrderItemView = {
  productId: number; nameHe: string; sku: string
  unitPriceAgorot: number; quantity: number; lineTotalAgorot: number
}
export type ShippingView = {
  fullName: string; phone: string; street: string; houseNo: string | null
  apartment: string | null; city: string; postalCode: string | null; notes: string | null
}
export type OrderView = {
  orderNumber: string; status: OrderStatus
  subtotalAgorot: number; shippingAgorot: number; vatAgorot: number
  discountAgorot: number; couponCode: string | null
  totalAgorot: number
  items: OrderItemView[]; shipping: ShippingView | null; createdAt: string
  deliveryMethod: 'COURIER'
  cancelledAt: string | null; cancellationReason: string | null
  cancelledBy: 'CUSTOMER' | 'ADMIN' | 'SYSTEM' | null
  refundedAt: string | null; refundAmountAgorot: number | null
}

export type RefundRequest = {
  amountAgorot: number
  reason?: string
  restoreStock?: boolean
}

export type CouponType = 'PERCENT' | 'FIXED'

export type Coupon = {
  id: number
  code: string
  type: CouponType
  value: number
  minSubtotalAgorot: number
  maxUses: number | null
  usedCount: number
  expiresAt: string | null
  active: boolean
  createdAt: string
}

export type CouponUpsert = {
  code: string
  type: CouponType
  value: number
  minSubtotalAgorot: number
  maxUses: number | null
  expiresAt: string | null
  active: boolean
}

export function formatPrice(agorot: number): string {
  return `₪${(agorot / 100).toFixed(2)}`
}

export const ORDER_STATUSES: OrderStatus[] = [
  'PENDING', 'PAID', 'FULFILLED', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED'
]

export type AdminUserRow = {
  id: number
  email: string
  fullName: string
  phone: string | null
  role: Role
  enabled: boolean
  marketingOptIn: boolean
  createdAt: string
  forceLogoutAt: string | null
  orderCount: number
  spendAgorot: number
}
export type AdminUserList = { content: AdminUserRow[]; total: number; page: number; size: number }

export type AuditLogRow = {
  id: number
  actorUserId: number | null
  actorEmail: string | null
  actorRole: string | null
  actorIp: string | null
  action: string
  targetType: string | null
  targetId: string | null
  message: string | null
  metadata: string | null
  createdAt: string
}
export type AuditLogList = { content: AuditLogRow[]; total: number; page: number; size: number }

export type DashboardMetrics = {
  kpi: {
    revenueTodayAgorot: number
    revenueLast7Agorot: number
    revenueLast30Agorot: number
    revenueLifetimeAgorot: number
    ordersTodayCount: number
    ordersLifetimeCount: number
    paidOrdersLifetimeCount: number
    aovAgorot: number
  }
  statusCounts: Record<OrderStatus, number>
  dailyRevenue: { date: string; revenueAgorot: number; orderCount: number }[]
  topProducts: { productId: number; nameHe: string; sku: string; qtySold: number; revenueAgorot: number }[]
  lowStock: { id: number; nameHe: string; sku: string; stockQty: number }[]
  recentOrders: {
    orderNumber: string
    status: OrderStatus
    totalAgorot: number
    createdAt: string
    customerName: string | null
    city: string | null
    itemCount: number
  }[]
}
