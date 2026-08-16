import { api } from './client'
import type { AuthResponse, LoginRequest, RegisterRequest, AuthUser } from '../types/auth'

export async function registerUser(request: RegisterRequest): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/register', request)
  return data
}

export async function loginUser(request: LoginRequest): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/login', request)
  return data
}

export async function fetchCurrentUser(): Promise<AuthUser> {
  const { data } = await api.get<AuthUser>('/auth/me')
  return data
}
