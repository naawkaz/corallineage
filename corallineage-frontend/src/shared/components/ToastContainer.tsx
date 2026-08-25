import { CheckCircleIcon, ExclamationCircleIcon, InformationCircleIcon, XMarkIcon } from '@heroicons/react/24/outline'
import type { Toast } from '../lib/toastContextCore'

interface ToastContainerProps {
  toasts: Toast[]
  onDismiss: (id: string) => void
}

function ToastIcon({ toast }: { toast: Toast }) {
  switch (toast.variant) {
    case 'progress':
      return <span className="spinner" />
    case 'success':
      return <CheckCircleIcon className="toast-icon toast-icon-success" style={{ width: 18, height: 18 }} />
    case 'error':
      return <ExclamationCircleIcon className="toast-icon toast-icon-error" style={{ width: 18, height: 18 }} />
    case 'info':
      return <InformationCircleIcon className="toast-icon toast-icon-info" style={{ width: 18, height: 18 }} />
  }
}

export default function ToastContainer({ toasts, onDismiss }: ToastContainerProps) {
  if (toasts.length === 0) return null

  return (
    <div className="toast-stack">
      {toasts.map((toast) => (
        <div key={toast.id} className={`toast toast-${toast.variant}`}>
          <ToastIcon toast={toast} />
          <div className="toast-body">
            <div className="toast-title">{toast.title}</div>
            {toast.detail && <div className="toast-detail">{toast.detail}</div>}
            {toast.variant === 'progress' && typeof toast.percent === 'number' && (
              <div className="progress-bar-bg" style={{ marginTop: 6 }}>
                <div className="progress-bar-fill" style={{ width: `${Math.max(0, Math.min(100, toast.percent))}%` }} />
              </div>
            )}
          </div>
          <button className="toast-close" onClick={() => onDismiss(toast.id)} aria-label="Dismiss">
            <XMarkIcon style={{ width: 14, height: 14 }} />
          </button>
        </div>
      ))}
    </div>
  )
}
