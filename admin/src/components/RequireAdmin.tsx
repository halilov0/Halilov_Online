import { useEffect } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/authStore'

/**
 * Route guard for the admin SPA.
 *
 * - Triggers `fetchMe()` once on mount when the auth store hasn't
 *   bootstrapped yet.
 * - Shows a brief loading hint until `bootstrapped` flips to `true`
 *   (otherwise an authed user would see a flash of the login redirect
 *   on every F5 while `/me` resolves).
 * - Bounces unauthenticated users to `/login`, preserving the intended
 *   path in `state.from` for post-login redirect.
 */
export function RequireAdmin({ children }: { children: React.ReactNode }) {
  const { user, bootstrapped, fetchMe } = useAuth()
  const location = useLocation()

  useEffect(() => {
    if (!bootstrapped) fetchMe()
  }, [bootstrapped, fetchMe])

  if (!bootstrapped) return <div style={{ padding: '2rem' }}>טוען…</div>
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />
  return <>{children}</>
}
