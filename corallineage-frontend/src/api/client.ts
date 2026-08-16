import axios from 'axios'

export const TOKEN_STORAGE_KEY = 'corallineage.auth.token'

export const api = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const isAuthEndpoint =
      axios.isAxiosError(error) &&
      typeof error.config?.url === 'string' &&
      error.config.url.includes('/auth/')

    if (axios.isAxiosError(error) && error.response?.status === 401 && !isAuthEndpoint) {
      localStorage.removeItem(TOKEN_STORAGE_KEY)
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  },
)

export interface ApiErrorBody {
  timestamp: string
  status: number
  error: string
  message: string
}

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as Partial<ApiErrorBody> | undefined
    if (body?.message) return body.message
    if (error.code === 'ECONNABORTED' || error.message === 'Network Error') {
      return 'Unable to reach the backend server'
    }
    return error.message
  }
  return 'An unexpected error occurred'
}
