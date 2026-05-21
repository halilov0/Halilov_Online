import { create } from 'zustand'
import { api, ApiError, setToken, getToken, type AuthResponse, type Me } from '../api'

type AuthState = {
  token: string | null
  user: Me | null
  loading: boolean
  error: string | null
  login: (email: string, password: string) => Promise<void>
  register: (input: { email: string; password: string; fullName: string; phone?: string; marketingOptIn?: boolean }) => Promise<void>
  logout: () => void
  fetchMe: () => Promise<void>
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
      setToken(res.token)
      set({ token: res.token })
      await get().fetchMe()
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
      setToken(res.token)
      set({ token: res.token })
      await get().fetchMe()
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'שגיאת הרשמה'
      set({ error: msg })
      throw e
    } finally {
      set({ loading: false })
    }
  },

  logout() {
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
