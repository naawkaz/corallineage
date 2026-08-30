import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowRightIcon, CubeIcon, PlusIcon } from '@heroicons/react/24/outline'
import { fetchArchitectures, fetchTrainingJobs } from '../training/trainingApi'
import { fetchModels } from '../training/modelsApi'
import { fetchVersions } from '../datasets/versionsApi'
import { errorMessage } from '../../shared/api/client'
import { PAGE_SIZE } from '../../shared/constants'
import { formatDate } from '../../shared/lib/format'
import { useAuthContext } from '../../shared/lib/authContextCore'
import { useJobPolling } from '../../shared/lib/jobPollingContextCore'
import Pagination from '../../shared/components/Pagination'
import TitledCard from '../../shared/components/TitledCard'
import TrainModelDialog, { trainingStatusBadge } from '../training/TrainModelDialog'
import type { ModelStatus, ModelVersion } from '../training/modelsTypes'
import type { Architecture, TrainingJob } from '../training/trainingTypes'
import type { DatasetVersion, Page } from '../datasets/versionsTypes'

const MODEL_STATUS_BADGE: Record<ModelStatus, string> = {
  ACTIVE: 'badge-green',
  ARCHIVED: 'badge-gray',
  DEPRECATED: 'badge-red',
}

export default function ModelRegistry() {
  const navigate = useNavigate()
  const { sourceId: sourceIdParam } = useParams()
  const sourceId = Number(sourceIdParam)
  const { user } = useAuthContext()
  const { refreshSignal } = useJobPolling()

  // Model registry table
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('ALL')
  const [data, setData] = useState<Page<ModelVersion> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // New training job form's dropdown source data - fetched once at page level since it's
  // needed regardless of whether the dialog is open. The form fields themselves (architecture,
  // selectedVersionId, learningRate, etc) live inside TrainModelDialog below: they only matter
  // while that dialog is mounted, so mounting fresh IS the reset - see the note above
  // TrainModelDialog for why this replaced a "reset on open" useEffect.
  const [architectures, setArchitectures] = useState<Architecture[]>([])
  const [versions, setVersions] = useState<DatasetVersion[]>([])

  // Job history
  const [jobs, setJobs] = useState<TrainingJob[]>([])
  const [jobsLoading, setJobsLoading] = useState(true)
  const [jobsError, setJobsError] = useState<string | null>(null)

  const [trainDialogOpen, setTrainDialogOpen] = useState(false)

  const inProgressJobs = jobs.filter((j) => j.status === 'QUEUED' || j.status === 'RUNNING')

  function loadModels() {
    fetchModels(sourceId, page, PAGE_SIZE, status === 'ALL' ? undefined : status)
      .then((res) => {
        setData(res)
        setError(null)
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadModels()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceId, page, status, refreshSignal])

  function loadJobs() {
    fetchTrainingJobs(sourceId, 0, 20)
      .then((res) => {
        setJobs(res.content)
        setJobsError(null)
      })
      .catch((err) => setJobsError(errorMessage(err)))
      .finally(() => setJobsLoading(false))
  }

  useEffect(() => {
    loadJobs()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceId, refreshSignal])

  useEffect(() => {
    fetchArchitectures()
      .then((list) => setArchitectures(list))
      .catch(() => {
        // architecture dropdown just stays on the default; submit will still validate server-side
      })
  }, [])

  useEffect(() => {
    fetchVersions(sourceId, 0, 50)
      .then((res) => setVersions(res.content))
      .catch(() => setVersions([]))
  }, [sourceId])

  return (
    <>
      <div className="content page-top-spacing">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          {error && <div className="alert alert-error">{error}</div>}

          <TitledCard
            title="Model registry"
            bodyClassName="no-padding"
            actions={
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <select
                  className="form-select"
                  style={{ width: 'auto' }}
                  value={status}
                  onChange={(e) => {
                    setLoading(true)
                    setStatus(e.target.value)
                    setPage(0)
                  }}
                >
                  <option value="ALL">All statuses</option>
                  <option value="ACTIVE">Active</option>
                  <option value="ARCHIVED">Archived</option>
                  <option value="DEPRECATED">Deprecated</option>
                </select>
                <button
                  className="btn btn-primary"
                  onClick={() => setTrainDialogOpen(true)}
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
                >
                  <PlusIcon style={{ width: 14, height: 14 }} />
                  Train New Model
                </button>
              </div>
            }
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', padding: '14px 16px' }}>
              {data && data.totalPages > 0 && (
                <Pagination
                  page={page}
                  totalPages={data.totalPages}
                  onPageChange={(p) => {
                    setLoading(true)
                    setPage(p)
                  }}
                  totalElements={data.totalElements}
                  pageSize={PAGE_SIZE}
                />
              )}
            </div>

            {loading ? (
              <div className="loading-state">
                <span className="spinner" /> Loading models…
              </div>
            ) : (!data || data.content.length === 0) && inProgressJobs.length === 0 ? (
              <div className="empty-state">
                <div className="empty-icon">
                  <CubeIcon style={{ width: 32, height: 32 }} />
                </div>
                <p>No models registered yet. Complete a training job to register one.</p>
              </div>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Status</th>
                    <th>Model</th>
                    <th>Architecture</th>
                    <th className="num">Accuracy</th>
                    <th>Dataset version</th>
                    <th>Trained</th>
                    <th>By</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {/* A model still training has no ModelVersion row yet (that's only created once
                      training completes) - only page 0 shows these placeholder rows, so they don't
                      duplicate across pages of the real, paginated model list below them. */}
                  {page === 0 &&
                    inProgressJobs.map((job) => (
                      <tr key={`job-${job.jobId}`}>
                        <td>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                            <span className="badge badge-amber">TRAINING</span>
                            <span className="skeleton-bar" />
                            {job.currentEpoch != null && (
                              <span style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                                epoch {job.currentEpoch}/{job.totalEpochs ?? '-'}
                              </span>
                            )}
                          </span>
                        </td>
                        <td style={{ color: 'var(--text-secondary)' }}>-</td>
                        <td>{job.architecture}</td>
                        <td className="num">-</td>
                        <td>
                          <span className="mono">{job.datasetVersionTag}</span>
                        </td>
                        <td style={{ color: 'var(--text-secondary)' }}>{formatDate(job.initiatedAt)}</td>
                        <td style={{ color: 'var(--text-secondary)' }}>{job.initiatedBy}</td>
                        <td></td>
                      </tr>
                    ))}
                  {data?.content.map((m) => (
                    <tr key={m.id} className="clickable" onClick={() => navigate(`/sources/${sourceId}/models/${m.id}`)}>
                      <td>
                        <span className={`badge ${MODEL_STATUS_BADGE[m.status]}`}>{m.status}</span>
                      </td>
                      <td>{m.modelTag}</td>
                      <td>{m.architecture}</td>
                      <td className="num">{m.accuracy != null ? `${(m.accuracy * 100).toFixed(1)}%` : '-'}</td>
                      <td>
                        {m.trainedOnDatasetVersion ? (
                          <span className="mono">{m.trainedOnDatasetVersion.versionTag}</span>
                        ) : (
                          '-'
                        )}
                      </td>
                      <td style={{ color: 'var(--text-secondary)' }}>{formatDate(m.trainedAt)}</td>
                      <td style={{ color: 'var(--text-secondary)' }}>{m.trainedBy}</td>
                      <td>
                        <span style={{ color: 'var(--accent)', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 2 }}>
                          View <ArrowRightIcon style={{ width: 12, height: 12 }} />
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </TitledCard>

          <TitledCard title="Training history" bodyClassName="no-padding">
            {jobsError && (
              <div className="alert alert-error" style={{ margin: 16 }}>
                {jobsError}
              </div>
            )}
            {jobsLoading ? (
              <div className="loading-state">
                <span className="spinner" /> Loading jobs…
              </div>
            ) : jobs.length === 0 ? (
              <div className="empty-state">
                <p>No training jobs yet.</p>
              </div>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Status</th>
                    <th>Model</th>
                    <th>Started</th>
                  </tr>
                </thead>
                <tbody>
                  {jobs.map((job) => {
                    const inProgress = job.status === 'QUEUED' || job.status === 'RUNNING'
                    return (
                      <tr key={job.jobId}>
                        <td>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                            <span className={`badge ${trainingStatusBadge(job.status)}`}>{job.status}</span>
                            {inProgress && (
                              <>
                                <span className="skeleton-bar" />
                                {job.currentEpoch != null && (
                                  <span style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                                    epoch {job.currentEpoch}/{job.totalEpochs ?? '-'}
                                  </span>
                                )}
                              </>
                            )}
                          </span>
                        </td>
                        <td>{job.modelTag ?? '-'}</td>
                        <td style={{ color: 'var(--text-secondary)' }}>{formatDate(job.initiatedAt)}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            )}
          </TitledCard>
        </div>
      </div>

      {trainDialogOpen && (
        <TrainModelDialog
          sourceId={sourceId}
          initiatedBy={user?.username ?? ''}
          architectures={architectures}
          versions={versions}
          onClose={() => setTrainDialogOpen(false)}
          onCompleted={() => {
            loadJobs()
            loadModels()
          }}
        />
      )}
    </>
  )
}
