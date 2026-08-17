import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { InformationCircleIcon } from '@heroicons/react/24/outline'
import { fetchPointAnnotationJobs } from '../pointannotation/pointAnnotationApi'
import { errorMessage } from '../../shared/api/client'
import { formatDate } from '../../shared/lib/format'
import type { PointAnnotationJob, PointAnnotationJobStatus } from '../pointannotation/pointAnnotationTypes'

function statusBadge(status: PointAnnotationJobStatus): string {
  switch (status) {
    case 'COMPLETE':
      return 'badge-green'
    case 'REVIEW':
    case 'AUTO_ANNOTATING':
      return 'badge-amber'
    default:
      return 'badge-gray'
  }
}

export default function PointAnnotationJobs() {
  const navigate = useNavigate()
  const { sourceId: sourceIdParam } = useParams()
  const sourceId = Number(sourceIdParam)

  const [jobs, setJobs] = useState<PointAnnotationJob[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchPointAnnotationJobs(sourceId, 0, 50)
      .then((res) => {
        setJobs(res.content)
        setError(null)
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [sourceId])

  function openJob(job: PointAnnotationJob) {
    const target = job.status === 'REVIEW' || job.status === 'COMPLETE' ? 'review' : 'label'
    navigate(`/sources/${sourceId}/point-annotation/jobs/${job.jobId}/${target}`)
  }

  return (
    <>

      <div className="content">
        <div className="impact-box" style={{ marginBottom: 16 }}>
          <span><InformationCircleIcon style={{ width: 16, height: 16 }} /></span>
          <span>
            New point-annotation jobs start from the "+ New Version" dialog on the Source Dashboard — pick the
            "Blank / Unlabeled Dataset" format, then continue straight into seed labeling.
          </span>
        </div>

        <div className="card">
          {error && (
            <div className="alert alert-error" style={{ margin: 16 }}>
              {error}
            </div>
          )}
          {loading ? (
            <div className="loading-state">
              <span className="spinner" /> Loading jobs…
            </div>
          ) : jobs.length === 0 ? (
            <div className="empty-state">
              <p>No point-annotation jobs yet.</p>
            </div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Status</th>
                  <th>Version</th>
                  <th>Seed progress</th>
                  <th>Model</th>
                  <th>Started</th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <tr key={job.jobId} style={{ cursor: 'pointer' }} onClick={() => openJob(job)}>
                    <td>
                      <span className={`badge ${statusBadge(job.status)}`}>{job.status}</span>
                    </td>
                    <td className="mono">{job.versionTag}</td>
                    <td>
                      {job.seedLabeledPoints} / {job.seedTotalPoints}
                    </td>
                    <td>{job.modelTag ?? '—'}</td>
                    <td style={{ color: 'var(--text-secondary)' }}>{formatDate(job.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </>
  )
}
