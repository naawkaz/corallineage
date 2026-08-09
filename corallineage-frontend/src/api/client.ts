import axios from 'axios'

export const api = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
})

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
