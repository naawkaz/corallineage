import { ArrowRightIcon } from '@heroicons/react/24/outline'
import CircularProgress from './CircularProgress'

export default function StatRingCard({
  fraction,
  subtitle,
  percent,
  danger,
  onGo,
}: {
  fraction: string
  subtitle: string
  percent: number
  danger: boolean
  onGo?: () => void
}) {
  return (
    <div
      className="card"
      style={{
        position: 'relative',
        padding: 20,
        paddingBottom: onGo ? 40 : 20,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 8,
      }}
    >
      <CircularProgress percent={percent} danger={danger} />
      <div style={{ fontSize: 13, fontWeight: 600 }}>{fraction}</div>
      <div style={{ fontSize: 12, color: 'var(--text-secondary)', textAlign: 'center' }}>{subtitle}</div>
      {onGo && (
        <button
          className="btn btn-secondary btn-sm"
          onClick={onGo}
          style={{ position: 'absolute', bottom: 10, right: 10, display: 'inline-flex', alignItems: 'center', gap: 2 }}
        >
          Go <ArrowRightIcon style={{ width: 12, height: 12 }} />
        </button>
      )}
    </div>
  )
}
