import { createContext, useContext } from 'react'

export type ToastVariant = 'progress' | 'success' | 'error' | 'info'

export interface ToastInput {
  variant: ToastVariant
  title: string
  detail?: string
  /** Determinate progress (0-100). Omitted means an indeterminate spinner for the progress variant. */
  percent?: number
  /** Non-progress toasts default to 4000ms; progress toasts never auto-dismiss until updated to another variant. */
  autoDismissMs?: number
}

export interface Toast extends ToastInput {
  id: string
}

export interface ToastApi {
  push(toast: ToastInput): string
  update(id: string, patch: Partial<ToastInput>): void
  dismiss(id: string): void
}

export const ToastContext = createContext<ToastApi | null>(null)

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used within a ToastProvider')
  return ctx
}
