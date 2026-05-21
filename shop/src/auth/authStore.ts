import { create } from 'zustand'
import { api, ApiError, setToken, getToken, type AuthResponse, type Me } from '../api'
import { useCart } from '../cart/cartStore'

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

// When the user explicitly authenticates (login/register button), merge the
// browser cart into the server cart and adopt the merged result. If a token
// already exists (user-switch without explicit logout), wipe the previous
// session's local cart first so it doesn't bleed into the new user's DB cart —
// the previous cart is already safe in the previous user's DB via continuous sync.
async function adoptAuth(token: string, set: (s: Partial<AuthState>) => void, fetchMe: () => Promise<void>) {
  const wasLoggedIn = !!getToken()
  if (wasLoggedIn) {
    useCart.getState().clearLocal()
  }
  setToken(token)
  set({ token })
  await fetchMe()
  await useCart.getState().mergeWithRemote()
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
      await adoptAuth(res.token, set, get().fetchMe)
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'שגיאת התחברות'
      set({ error: msg })
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
      await adoptAuth(res.token, set, get().fetchMe)
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
    // guest visiting this browser starts from zero.
    await useCart.getState().pushToRemote()
    useCart.getState().clearLocal()
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
