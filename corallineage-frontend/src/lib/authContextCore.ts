import { createContext, useContext } from 'react'
import type { AuthUser } from '../types/auth'

export interface AuthContextValue {
  user: AuthUser | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  register: (username: string, password: string, displayName?: string) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuthContext(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuthContext must be used within an AuthProvider')
  return ctx
}
