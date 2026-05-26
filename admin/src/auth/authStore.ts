import { create } from 'zustand'
import { api, ApiError, setToken, getToken, type AuthResponse, type Me } from '../api'

type LoginStepResult = 'done' | 'totp-required'

type ChallengeResponse = { requires2FA: true; challenge: string }

type AuthState = {
  token: string | null
  user: Me | null
  loading: boolean
  error: string | null
  bootstrapped: boolean
  // Holds the 2FA challenge token when the backend asks for a code.
  pendingChallenge: string | null
  login: (email: string, password: string) => Promise<LoginStepResult>
  submitTotp: (code: string) => Promise<void>
  cancelTotp: () => void
  logout: () => void
  fetchMe: () => Promise<void>
}

function isChallenge(res: unknown): res is ChallengeResponse {
  return !!res && typeof res === 'object' && (res as { requires2FA?: boolean }).requires2FA === true
}

export const useAuth = create<AuthState>((set, get) => ({
  token: getToken(),
  user: null,
  loading: false,
  error: null,
  bootstrapped: false,
  pendingChallenge: null,

  async login(email, password): Promise<LoginStepResult> {
    set({ loading: true, error: null, pendingChallenge: null })
    try {
      const res = await api<AuthResponse | ChallengeResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      if (isChallenge(res)) {
        set({ pendingChallenge: res.challenge })
        return 'totp-required'
      }
      if (res.role !== 'ADMIN') {
        throw new Error('המשתמש אינו מנהל')
      }
      setToken(res.token)
      set({ token: res.token })
      await get().fetchMe()
      return 'done'
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'שגיאת התחברות'
      set({ error: msg })
      throw e
    } finally {
      set({ loading: false })
    }
  },

  async submitTotp(code) {
    const challenge = get().pendingChallenge
    if (!challenge) throw new Error('אין אתגר פעיל. נסו להתחבר שוב.')
    set({ loading: true, error: null })
    try {
      const res = await api<AuthResponse>('/api/auth/login/totp', {
        method: 'POST',
        body: JSON.stringify({ challenge, code: code.trim() }),
      })
      if (res.role !== 'ADMIN') {
        throw new Error('המשתמש אינו מנהל')
      }
      setToken(res.token)
      set({ token: res.token, pendingChallenge: null })
      await get().fetchMe()
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'שגיאת אימות'
      set({ error: msg })
      throw e
    } finally {
      set({ loading: false })
    }
  },

  cancelTotp() {
    set({ pendingChallenge: null, error: null })
  },

  logout() {
    setToken(null)
    set({ token: null, user: null })
  },

  async fetchMe() {
    if (!getToken()) {
      set({ user: null, bootstrapped: true })
      return
    }
    try {
      const me = await api<Me>('/api/auth/me')
      if (me.role !== 'ADMIN') {
        setToken(null)
        set({ token: null, user: null })
      } else {
        set({ user: me })
      }
    } catch (e) {
      // Only treat real auth failures (401/403) as a logout. 429/5xx/network
      // blips must not nuke the session.
      if (e instanceof ApiError && (e.status === 401 || e.status === 403)) {
        setToken(null)
        set({ token: null, user: null })
      }
    } finally {
      set({ bootstrapped: true })
    }
  },
}))
