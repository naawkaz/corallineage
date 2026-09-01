import { useEffect, useRef, useState } from 'react'
import { EllipsisVerticalIcon } from '@heroicons/react/24/outline'

interface RowMenuItem {
  label: string
  onClick: () => void
  danger?: boolean
}

interface RowMenuProps {
  items: RowMenuItem[]
}

export default function RowMenu({ items }: RowMenuProps) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open])

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        onClick={() => setOpen((o) => !o)}
        aria-label="More actions"
        style={{
          background: 'none',
          border: 'none',
          cursor: 'pointer',
          padding: 4,
          color: 'var(--text-muted)',
          display: 'inline-flex',
          alignItems: 'center',
          borderRadius: 'var(--radius-sm)',
        }}
      >
        <EllipsisVerticalIcon style={{ width: 16, height: 16 }} />
      </button>
      {open && (
        <div
          style={{
            position: 'absolute',
            right: 0,
            top: '100%',
            marginTop: 4,
            background: 'var(--surface)',
            borderRadius: 'var(--radius)',
            boxShadow: 'var(--shadow-overlay)',
            minWidth: 140,
            zIndex: 30,
            padding: 4,
          }}
        >
          {items.map((item) => (
            <button
              key={item.label}
              onClick={() => {
                setOpen(false)
                item.onClick()
              }}
              style={{
                display: 'block',
                width: '100%',
                textAlign: 'left',
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                padding: '7px 10px',
                fontSize: 12.5,
                borderRadius: 'var(--radius-sm)',
                color: item.danger ? 'var(--danger)' : 'var(--text-primary)',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--canvas)')}
              onMouseLeave={(e) => (e.currentTarget.style.background = 'none')}
            >
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
