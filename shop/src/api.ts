const TOKEN_KEY = 'halilov.token'
const GUEST_ORDERS_KEY = 'halilov.guestOrders'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

// Tokens issued at guest order creation, used to gate later anonymous
// reads / payment calls. Stored in sessionStorage so they vanish when the
// browser tab closes — long-lived persistence belongs to a real account.
type GuestOrderMap = Record<string, string>

function readGuestOrders(): GuestOrderMap {
  try {
    const raw = sessionStorage.getItem(GUEST_ORDERS_KEY)
    return raw ? (JSON.parse(raw) as GuestOrderMap) : {}
  } catch {
    return {}
  }
}

export function getGuestOrderToken(orderNumber: string): string | null {
  return readGuestOrders()[orderNumber] ?? null
}

export function rememberGuestOrder(orderNumber: string, token: string) {
  const map = readGuestOrders()
  map[orderNumber] = token
  sessionStorage.setItem(GUEST_ORDERS_KEY, JSON.stringify(map))
}

function guestTokenForPath(path: string): string | null {
  // Match the orderNumber segment in /api/orders/{n}, /api/orders/{n}/pay,
  // and /api/payments/mock/{n}/complete. Strip query string first.
  const clean = path.split('?')[0]
  const orderMatch = clean.match(/^\/api\/orders\/([^/]+)(?:\/pay)?$/)
  if (orderMatch) return getGuestOrderToken(orderMatch[1])
  const payMatch = clean.match(/^\/api\/payments\/mock\/([^/]+)\/complete$/)
  if (payMatch) return getGuestOrderToken(payMatch[1])
  return null
}

export class ApiError extends Error {
  status: number
  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (!token && !headers.has('X-Guest-Token')) {
    const guestToken = guestTokenForPath(path)
    if (guestToken) headers.set('X-Guest-Token', guestToken)
  }

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
    // Reject HTML / nginx error pages — surface a generic message instead.
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

// ----- types -----
export type Category = {
  id: number
  slug: string
  nameHe: string
  parentId: number | null
  sortOrder: number
}

export type Product = {
  id: number
  sku: string
  slug: string
  nameHe: string
  descriptionHe: string | null
  categoryId: number | null
  priceAgorot: number
  stockQty: number
  imageUrl: string | null
  imageUrls: string[]
  active: boolean
}

export type Page<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type AuthResponse = {
  token: string
  email: string
  role: 'CUSTOMER' | 'ADMIN'
  fullName: string
}

export type Me = {
  id: number
  email: string
  fullName: string
  phone: string | null
  role: 'CUSTOMER' | 'ADMIN'
  marketingOptIn: boolean
}

export type SavedAddress = {
  id: number
  label: string | null
  fullName: string
  phone: string
  street: string
  houseNo: string | null
  apartment: string | null
  city: string
  postalCode: string | null
  notes: string | null
  isDefault: boolean
}

export type SavedAddressUpsert = {
  label?: string
  fullName: string
  phone: string
  street: string
  houseNo?: string
  apartment?: string
  city: string
  postalCode?: string
  notes?: string
  isDefault: boolean
}

export type ProfileUpdate = {
  fullName: string
  phone?: string
}

export function formatPrice(agorot: number): string {
  return `₪${(agorot / 100).toFixed(2)}`
}

export type OrderItemRequest = { productId: number; quantity: number }

export type ShippingRequest = {
  fullName: string
  phone: string
  street: string
  houseNo?: string
  apartment?: string
  city: string
  postalCode?: string
  notes?: string
}

export type DeliveryMethod = 'COURIER'

export type CreateOrderRequest = {
  items: OrderItemRequest[]
  shipping: ShippingRequest
  deliveryMethod: DeliveryMethod
  couponCode?: string
  guestEmail?: string
}

export type DeliveryOption = {
  method: DeliveryMethod
  label: string
  description: string
  priceAgorot: number
  basePriceAgorot: number
  freeAboveAgorot: number
}

export type DeliveryQuote = {
  options: DeliveryOption[]
}

export type DeliveryConfig = {
  courierFlatAgorot: number
  freeAboveAgorot: number
}

export type CouponType = 'PERCENT' | 'FIXED'

export type CouponValidateResponse = {
  code: string
  type: CouponType
  value: number
  discountAgorot: number
}

export type OrderItemView = {
  productId: number
  nameHe: string
  sku: string
  unitPriceAgorot: number
  quantity: number
  lineTotalAgorot: number
}

export type ShippingView = {
  fullName: string
  phone: string
  street: string
  houseNo: string | null
  apartment: string | null
  city: string
  postalCode: string | null
  notes: string | null
}

export type OrderView = {
  orderNumber: string
  status: 'PENDING' | 'PAID' | 'FULFILLED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'REFUNDED'
  subtotalAgorot: number
  shippingAgorot: number
  vatAgorot: number
  discountAgorot: number
  couponCode: string | null
  totalAgorot: number
  items: OrderItemView[]
  shipping: ShippingView | null
  createdAt: string
  deliveryMethod: DeliveryMethod
  cancelledAt: string | null
  cancellationReason: string | null
  cancelledBy: 'CUSTOMER' | 'ADMIN' | 'SYSTEM' | null
  refundedAt: string | null
  refundAmountAgorot: number | null
  guestEmail: string | null
  guestToken: string | null
}

export function canCustomerCancel(status: OrderView['status']): boolean {
  return status === 'PENDING' || status === 'PAID' || status === 'FULFILLED'
}

// ----- cart sync -----

export type CartLineView = {
  productId: number
  slug: string
  nameHe: string
  priceAgorot: number
  quantity: number
  stockQty: number
  imageUrl: string | null
}

export type CartUpsertItem = { productId: number; quantity: number }
export type CartReplaceRequest = { items: CartUpsertItem[] }
