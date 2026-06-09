import { create } from 'zustand'
import { api, ApiError, setToken, getToken, type AuthResponse, type Me } from '../api'

/**
 * Admin auth store with a two-step login flow.
 *
 * Step 1: `login(email, password)` POSTs `/api/auth/login`. The
 * response is either an `AuthResponse` (token issued — trusted IP,
 * 2FA not required) or a `ChallengeResponse` (`{ requires2FA, challenge }`).
 *
 * Step 2 (when challenged): `submitTotp(code)` POSTs the challenge +
 * code to `/api/auth/login/totp`. On success the resulting token is
 * adopted exactly as in step 1.
 *
 * `bootstrapped` flips to `true` after the first `fetchMe()` resolves
 * (success or fail). The `RequireAdmin` route guard reads this so it
 * can distinguish "still hydrating" from "definitely not logged in"
 * and avoid bouncing to /login on every F5.
 *
 * A non-ADMIN role returned by `/me` is treated as a logout — there's
 * no customer UI in this app, and we don't want to keep a stale
 * session around.
 */
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
  submitTotp: (code: string, trustDevice: boolean) => Promise<void>
  cancelTotp: () => void
  logout: () => void
  fetchMe: () => Promise<void>
  /** Adopt a freshly issued token (e.g. after a password change rotated
   *  `force_logout_at` and invalidated the prior JWT). Persists to
   *  localStorage and updates the store in one shot. */
  adoptToken: (token: string) => void
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

  async submitTotp(code, trustDevice) {
    const challenge = get().pendingChallenge
    if (!challenge) throw new Error('אין אתגר פעיל. נסו להתחבר שוב.')
    set({ loading: true, error: null })
    try {
      // trustDevice → backend sets the hm_device cookie so this browser skips
      // 2FA for 30 days. The cookie is same-origin + HttpOnly; the browser
      // stores and re-sends it automatically on the next login.
      const res = await api<AuthResponse>('/api/auth/login/totp', {
        method: 'POST',
        body: JSON.stringify({ challenge, code: code.trim(), trustDevice }),
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

  adoptToken(token) {
    setToken(token)
    set({ token })
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
