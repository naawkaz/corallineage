import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeftIcon } from '@heroicons/react/24/outline'
import VersionsList from '../components/VersionsList'

export default function VersionBrowser() {
  const navigate = useNavigate()
  const { sourceId: sourceIdParam } = useParams()
  const sourceId = Number(sourceIdParam)

  return (
    <>
      <div className="page-actions with-back">
        <button
          className="btn btn-secondary btn-sm"
          onClick={() => navigate(`/sources/${sourceId}`)}
          style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
        >
          <ArrowLeftIcon style={{ width: 14, height: 14 }} />
          Back
        </button>
      </div>

      <div className="content">
        <VersionsList sourceId={sourceId} />
      </div>
    </>
  )
}
