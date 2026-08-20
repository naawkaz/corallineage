import type { CSSProperties, ReactNode } from 'react'

export default function TitledCard({
  title,
  actions,
  children,
  bodyClassName,
  style,
}: {
  title: ReactNode
  actions?: ReactNode
  children: ReactNode
  bodyClassName?: string
  style?: CSSProperties
}) {
  return (
    <div className="titled-card" style={style}>
      <div className="titled-card-header">
        <div className="titled-card-title">{title}</div>
        {actions && <div className="titled-card-actions">{actions}</div>}
      </div>
      <div className={`titled-card-body${bodyClassName ? ` ${bodyClassName}` : ''}`}>{children}</div>
    </div>
  )
}
