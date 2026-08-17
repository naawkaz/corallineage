import { WrenchScrewdriverIcon } from '@heroicons/react/24/outline'

export default function ComingSoon() {
  return (
    <div className="content">
      <div className="empty-state">
        <div className="empty-icon">
          <WrenchScrewdriverIcon style={{ width: 32, height: 32 }} />
        </div>
        <p>This module has not been built yet.</p>
      </div>
    </div>
  )
}
