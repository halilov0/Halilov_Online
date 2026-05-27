import { create } from 'zustand'
import { api, ApiError, setToken, getToken, type AuthResponse, type Me } from '../api'
import { useCart, getCartOwner, setCartOwner, clearCartBaseline, cancelPendingCartPush } from '../cart/cartStore'

/**
 * Shop auth store.
 *
 * `token` is hydrated synchronously from `localStorage`; `user` is
 * `null` until `fetchMe()` resolves. Protected routes must gate on
 * `!token`, not `!user`, otherwise they bounce to login on every F5
 * while `/me` is in flight.
 *
 * `login` / `register` use {@link adoptAuth} to reconcile the local cart
 * with the account, keyed off the cart's owner tag (see
 * {@link getCartOwner}). It merges only the lines added since the cart
 * last matched the server (the baseline), so a genuine guest cart folds
 * in whole, while the residue of an expired session contributes just the
 * items added during the logged-out window — its already-persisted lines
 * aren't summed into themselves and doubled.
 * `logout` flushes the cart to the server first, then wipes the local
 * copy, owner tag, and baseline so the next visitor in this browser
 * starts clean.
 */
type AuthState = {
  token: string | null
  user: Me | null
  loading: boolean
  error: string | null
  login: (email: string, password: string) => Promise<void>
  register: (input: { email: string; password: string; fullName: string; phone?: string; marketingOptIn?: boolean }) => Promise<void>
  logout: () => Promise<void>
  fetchMe: () => Promise<void>
}

function loginErrorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 401) return 'אימייל או סיסמה שגויים.'
    if (e.status === 403) return 'החשבון מושבת. ליצירת קשר: halilov.store@gmail.com'
  }
  return e instanceof Error ? e.message : 'שגיאת התחברות'
}

// When the user explicitly authenticates (login/register button), reconcile the
// browser cart with the account based on who the local cart belongs to:
//
//   - Owner == a different user → leftover cart from someone else on this
//     browser. Drop it so it can't bleed into this account, then adopt the
//     server cart.
//   - Otherwise (no owner = genuine guest cart, or owner == this user = residue
//     of this user's own possibly-expired session) → merge only the lines added
//     or increased since the cart last matched the server. For a guest the
//     baseline is empty so the whole cart folds in; for an expired-session
//     residue the lines already in the DB are skipped (no doubling) while any
//     items added during the logged-out window are still merged in (no loss).
//
// We key off the owner tag rather than "is a token present?" because a silently
// expired token is wiped before re-login, so the old heuristic couldn't tell an
// expired-session cart apart from a fresh guest cart — which is how the
// cart-doubling bug slipped through.
async function adoptAuth(token: string, set: (s: Partial<AuthState>) => void, get: () => AuthState) {
  const prevOwner = getCartOwner()
  setToken(token)
  set({ token })
  await get().fetchMe()
  const myId = get().user?.id ?? null

  const cart = useCart.getState()
  if (prevOwner != null && prevOwner !== myId) {
    cart.clearLocal()
    // Drop the previous user's baseline too, so if loadFromRemote fails we're
    // left clean (empty local + empty baseline) rather than "dirty" — otherwise
    // the next focus sync would flush the empty cart and wipe N's server cart.
    clearCartBaseline()
    await cart.loadFromRemote()
  } else {
    await cart.mergeAdditionsWithRemote()
  }

  if (myId != null) setCartOwner(myId)
}

export const useAuth = create<AuthState>((set, get) => ({
  token: getToken(),
  user: null,
  loading: false,
  error: null,

  async login(email, password) {
    set({ loading: true, error: null })
    try {
      const res = await api<AuthResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      await adoptAuth(res.token, set, get)
    } catch (e) {
      set({ error: loginErrorMessage(e) })
      throw e
    } finally {
      set({ loading: false })
    }
  },

  async register(input) {
    set({ loading: true, error: null })
    try {
      const res = await api<AuthResponse>('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify(input),
      })
      await adoptAuth(res.token, set, get)
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'שגיאת הרשמה'
      set({ error: msg })
      throw e
    } finally {
      set({ loading: false })
    }
  },

  async logout() {
    // SAVE_TO_DB(User_Cart) → CLEAR_LOCAL_SESSION. Continuous sync keeps the
    // DB cart fresh during the session; this final push catches any debounced
    // mutation still in flight, then the local cart is wiped so the next
    // guest visiting this browser starts from zero. Cancel the debounce timer
    // first so a stray push can't fire into the session we're tearing down.
    cancelPendingCartPush()
    await useCart.getState().pushToRemote()
    useCart.getState().clearLocal()
    setCartOwner(null)
    clearCartBaseline()
    setToken(null)
    set({ token: null, user: null })
  },

  async fetchMe() {
    if (!getToken()) {
      set({ user: null })
      return
    }
    try {
      const me = await api<Me>('/api/auth/me')
      set({ user: me })
      // Tag the cart with its owner while the token is still valid, so a later
      // silent expiry → re-login is recognised as residue (not a guest cart)
      // even for sessions that predate this code. Never cleared on auth failure
      // below — only an explicit logout drops it.
      setCartOwner(me.id)
    } catch (e) {
      // Only treat real auth failures (401/403) as a logout. 429/5xx/network
      // blips must not nuke the session — that caused users to get kicked
      // after a few rapid actions and then unable to log back in.
      if (e instanceof ApiError && (e.status === 401 || e.status === 403)) {
        setToken(null)
        set({ token: null, user: null })
      }
    }
  },
}))
