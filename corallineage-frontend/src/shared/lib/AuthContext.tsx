import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { fetchCurrentUser, loginUser, registerUser } from '../../features/auth/authApi'
import { TOKEN_STORAGE_KEY } from '../api/client'
import { AuthContext } from './authContextCore'
import type { AuthUser } from '../../features/auth/authTypes'

const USER_STORAGE_KEY = 'corallineage.auth.user'

function readStoredUser(): AuthUser | null {
  const stored = localStorage.getItem(USER_STORAGE_KEY)
  if (!stored) return null
  try {
    return JSON.parse(stored) as AuthUser
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  // Restore from localStorage synchronously on first render (not inside the effect below)
  // so the UI doesn't flash a logged-out state while the /auth/me validation call is in
  // flight, and loading only ever starts true when there is actually a token to validate.
  const [user, setUser] = useState<AuthUser | null>(() => (localStorage.getItem(TOKEN_STORAGE_KEY) ? readStoredUser() : null))
  const [loading, setLoading] = useState(() => localStorage.getItem(TOKEN_STORAGE_KEY) !== null)

  useEffect(() => {
    let cancelled = false
    const token = localStorage.getItem(TOKEN_STORAGE_KEY)

    if (!token) {
      return
    }

    fetchCurrentUser()
      .then((current) => {
        if (cancelled) return
        setUser(current)
        localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(current))
      })
      .catch(() => {
        if (cancelled) return
        localStorage.removeItem(TOKEN_STORAGE_KEY)
        localStorage.removeItem(USER_STORAGE_KEY)
        setUser(null)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const response = await loginUser({ username, password })
    localStorage.setItem(TOKEN_STORAGE_KEY, response.token)
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(response.user))
    setUser(response.user)
  }, [])

  const register = useCallback(async (username: string, password: string, displayName?: string) => {
    const response = await registerUser({ username, password, displayName: displayName || undefined })
    localStorage.setItem(TOKEN_STORAGE_KEY, response.token)
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(response.user))
    setUser(response.user)
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
    localStorage.removeItem(USER_STORAGE_KEY)
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}
