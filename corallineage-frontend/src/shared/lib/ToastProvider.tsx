import { useCallback, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { ToastContext } from './toastContextCore'
import type { Toast, ToastInput } from './toastContextCore'
import ToastContainer from '../components/ToastContainer'

const DEFAULT_AUTO_DISMISS_MS = 4000

let nextId = 0

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const timers = useRef(new Map<string, number>())

  const clearTimer = useCallback((id: string) => {
    const timer = timers.current.get(id)
    if (timer !== undefined) {
      window.clearTimeout(timer)
      timers.current.delete(id)
    }
  }, [])

  const dismiss = useCallback(
    (id: string) => {
      clearTimer(id)
      setToasts((prev) => prev.filter((t) => t.id !== id))
    },
    [clearTimer],
  )

  const scheduleAutoDismiss = useCallback(
    (id: string, toast: ToastInput) => {
      clearTimer(id)
      if (toast.variant === 'progress') return
      const delay = toast.autoDismissMs ?? DEFAULT_AUTO_DISMISS_MS
      const timer = window.setTimeout(() => dismiss(id), delay)
      timers.current.set(id, timer)
    },
    [clearTimer, dismiss],
  )

  const push = useCallback(
    (toast: ToastInput) => {
      const id = `toast-${++nextId}`
      setToasts((prev) => [...prev, { ...toast, id }])
      scheduleAutoDismiss(id, toast)
      return id
    },
    [scheduleAutoDismiss],
  )

  const update = useCallback(
    (id: string, patch: Partial<ToastInput>) => {
      setToasts((prev) =>
        prev.map((t) => {
          if (t.id !== id) return t
          const next = { ...t, ...patch }
          scheduleAutoDismiss(id, next)
          return next
        }),
      )
    },
    [scheduleAutoDismiss],
  )

  const api = useMemo(() => ({ push, update, dismiss }), [push, update, dismiss])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <ToastContainer toasts={toasts} onDismiss={dismiss} />
    </ToastContext.Provider>
  )
}
