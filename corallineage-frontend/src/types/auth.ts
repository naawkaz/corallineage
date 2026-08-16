export interface AuthUser {
  id: number
  username: string
  displayName: string
  createdAt: string
}

export interface AuthResponse {
  token: string
  user: AuthUser
}

export interface RegisterRequest {
  username: string
  password: string
  displayName?: string | null
}

export interface LoginRequest {
  username: string
  password: string
}
