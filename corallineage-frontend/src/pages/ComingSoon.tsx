import { Construction } from 'lucide-react'

export default function ComingSoon() {
  return (
    <div className="content">
      <div className="empty-state">
        <div className="empty-icon">
          <Construction size={32} strokeWidth={1.5} />
        </div>
        <p>This module has not been built yet.</p>
      </div>
    </div>
  )
}
