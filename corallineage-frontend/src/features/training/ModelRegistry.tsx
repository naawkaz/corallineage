import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowRightIcon, CubeIcon, PlusIcon, XMarkIcon } from '@heroicons/react/24/outline'
import {
  fetchArchitectures,
  fetchTrainingJob,
  fetchTrainingJobEpochMetrics,
  fetchTrainingJobs,
  submitTrainingJob,
} from '../training/trainingApi'
import { fetchModels } from '../training/modelsApi'
import { fetchVersions } from '../datasets/versionsApi'
import { errorMessage } from '../../shared/api/client'
import { PAGE_SIZE } from '../../shared/constants'
import { formatDate } from '../../shared/lib/format'
import { useAuthContext } from '../../shared/lib/authContextCore'
import Pagination from '../../shared/components/Pagination'
import TitledCard from '../../shared/components/TitledCard'
import LossCurveChart from '../training/LossCurveChart'
import type { ModelStatus, ModelVersion, MlflowEpochMetricPoint } from '../training/modelsTypes'
import type { Architecture, TrainingJob } from '../training/trainingTypes'
import type { DatasetVersion, Page } from '../datasets/versionsTypes'

const MODEL_STATUS_BADGE: Record<ModelStatus, string> = {
  ACTIVE: 'badge-green',
  ARCHIVED: 'badge-gray',
  DEPRECATED: 'badge-red',
}

function trainingStatusBadge(status: TrainingJob['status']): string {
  switch (status) {
    case 'COMPLETED':
      return 'badge-green'
    case 'FAILED':
      return 'badge-red'
    case 'RUNNING':
      return 'badge-amber'
    default:
      return 'badge-gray'
  }
}

export default function ModelRegistry() {
  const navigate = useNavigate()
  const { sourceId: sourceIdParam } = useParams()
  const sourceId = Number(sourceIdParam)
  const { user } = useAuthContext()

  // Model registry table
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('ALL')
  const [data, setData] = useState<Page<ModelVersion> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // New training job form
  const [architectures, setArchitectures] = useState<Architecture[]>([])
  const [versions, setVersions] = useState<DatasetVersion[]>([])
  const [architecture, setArchitecture] = useState('efficientnet_b0')
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null)
  const [learningRate, setLearningRate] = useState('0.001')
  const [batchSize, setBatchSize] = useState('32')
  const [epochs, setEpochs] = useState('10')

  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [activeJob, setActiveJob] = useState<TrainingJob | null>(null)
  const [activeJobEpochMetrics, setActiveJobEpochMetrics] = useState<MlflowEpochMetricPoint[]>([])

  // Job history
  const [jobs, setJobs] = useState<TrainingJob[]>([])
  const [jobsLoading, setJobsLoading] = useState(true)
  const [jobsError, setJobsError] = useState<string | null>(null)

  const [trainDialogOpen, setTrainDialogOpen] = useState(false)

  const pollingRef = useRef<number | null>(null)

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
  }, [sourceId, page, status])

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
  }, [sourceId])

  useEffect(() => {
    fetchArchitectures()
      .then((list) => {
        setArchitectures(list)
        if (list.length > 0) setArchitecture(list[0].id)
      })
      .catch(() => {
        // architecture dropdown just stays on the default; submit will still validate server-side
      })
  }, [])

  useEffect(() => {
    fetchVersions(sourceId, 0, 50)
      .then((res) => setVersions(res.content))
      .catch(() => setVersions([]))
  }, [sourceId])

  // Polls the active training job every 3s while it's QUEUED/RUNNING, same convention this
  // page previously used on the standalone Training Jobs page. Once it settles, refreshes both
  // the job history table and the model registry table, since a COMPLETED job registers a model.
  useEffect(() => {
    if (!activeJob || (activeJob.status !== 'QUEUED' && activeJob.status !== 'RUNNING')) {
      return
    }
    const intervalId = window.setInterval(() => {
      fetchTrainingJob(activeJob.jobId)
        .then((updated) => {
          setActiveJob(updated)
          if (updated.mlflowRunId) {
            fetchTrainingJobEpochMetrics(updated.jobId)
              .then((points) => setActiveJobEpochMetrics(points))
              .catch(() => {
                // transient poll failure, the epoch progress bar still covers this tick
              })
          }
          if (updated.status !== 'QUEUED' && updated.status !== 'RUNNING' && pollingRef.current !== null) {
            window.clearInterval(pollingRef.current)
            pollingRef.current = null
            loadJobs()
            loadModels()
          }
        })
        .catch(() => {
          // transient poll failure - try again next tick
        })
    }, 3000)
    pollingRef.current = intervalId
    return () => {
      window.clearInterval(intervalId)
      pollingRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeJob])

  // Dialog shouldn't remember state from a previous open (selected version, in-flight job
  // status, etc), reset the form whenever it's opened rather than only on submit success.
  useEffect(() => {
    if (!trainDialogOpen) return
    setSelectedVersionId(null)
    setLearningRate('0.001')
    setBatchSize('32')
    setEpochs('10')
    setSubmitError(null)
    setActiveJob(null)
    setActiveJobEpochMetrics([])
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [trainDialogOpen])

  async function handleSubmit() {
    setSubmitting(true)
    setSubmitError(null)
    try {
      const created = await submitTrainingJob({
        datasetId: sourceId,
        datasetVersionId: selectedVersionId ?? undefined,
        architecture,
        learningRate: Number(learningRate),
        batchSize: Number(batchSize),
        epochs: Number(epochs),
        initiatedBy: user?.username ?? '',
      })
      setActiveJob(created)
      setActiveJobEpochMetrics([])
      loadJobs()
    } catch (err) {
      setSubmitError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <div className="content page-top-spacing">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          {error && <div className="alert alert-error">{error}</div>}

          <TitledCard
            title="Model registry"
            bodyClassName="no-padding"
            actions={
              <button
                className="btn btn-primary"
                onClick={() => setTrainDialogOpen(true)}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
              >
                <PlusIcon style={{ width: 14, height: 14 }} />
                Train New Model
              </button>
            }
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 16px' }}>
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
            ) : !data || data.content.length === 0 ? (
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
                    <th>Model</th>
                    <th>Architecture</th>
                    <th className="num">Accuracy</th>
                    <th>Dataset version</th>
                    <th>Status</th>
                    <th>Trained</th>
                    <th>By</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((m) => (
                    <tr key={m.id} className="clickable" onClick={() => navigate(`/sources/${sourceId}/models/${m.id}`)}>
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
                      <td>
                        <span className={`badge ${MODEL_STATUS_BADGE[m.status]}`}>{m.status}</span>
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
                    <th>Model</th>
                    <th>Status</th>
                    <th>Started</th>
                  </tr>
                </thead>
                <tbody>
                  {jobs.map((job) => (
                    <tr key={job.jobId}>
                      <td>{job.modelTag ?? '-'}</td>
                      <td>
                        <span className={`badge ${trainingStatusBadge(job.status)}`}>{job.status}</span>
                      </td>
                      <td style={{ color: 'var(--text-secondary)' }}>{formatDate(job.initiatedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </TitledCard>
        </div>
      </div>

      {trainDialogOpen && (
        <div className="modal-overlay active">
          <div className="modal">
            <div className="modal-header">
              <div>
                <div className="modal-title">Train New Model</div>
              </div>
              <button className="modal-close" onClick={() => setTrainDialogOpen(false)} aria-label="Close">
                <XMarkIcon style={{ width: 16, height: 16 }} />
              </button>
            </div>
            <div className="modal-body">
              <div className="form-row">
                <label className="form-label">Architecture</label>
                <select className="form-select" value={architecture} onChange={(e) => setArchitecture(e.target.value)}>
                  {(architectures.length > 0
                    ? architectures
                    : [{ id: 'efficientnet_b0', displayName: 'EfficientNet-B0', description: '' }]
                  ).map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.displayName}
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-row">
                <label className="form-label" htmlFor="datasetVersion">
                  Dataset version
                </label>
                <select
                  id="datasetVersion"
                  className="form-select"
                  value={selectedVersionId ?? ''}
                  onChange={(e) => setSelectedVersionId(e.target.value ? Number(e.target.value) : null)}
                >
                  <option value="" disabled>
                    Select a version…
                  </option>
                  {versions.map((v) => (
                    <option key={v.id} value={v.id}>
                      {v.versionTag}
                      {!v.complete ? ' (incomplete - training will be rejected)' : ''}
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-row">
                <label className="form-label" htmlFor="learningRate">
                  Learning rate
                </label>
                <input
                  id="learningRate"
                  className="form-input"
                  value={learningRate}
                  onChange={(e) => setLearningRate(e.target.value)}
                />
              </div>
              <div className="form-row">
                <label className="form-label" htmlFor="batchSize">
                  Batch size
                </label>
                <input
                  id="batchSize"
                  className="form-input"
                  value={batchSize}
                  onChange={(e) => setBatchSize(e.target.value)}
                />
              </div>
              <div className="form-row" style={{ marginBottom: 0 }}>
                <label className="form-label" htmlFor="epochs">
                  Epochs
                </label>
                <input
                  id="epochs"
                  className="form-input"
                  value={epochs}
                  onChange={(e) => setEpochs(e.target.value)}
                />
              </div>

              {submitError && (
                <div className="alert alert-error" style={{ marginTop: 16 }}>
                  {submitError}
                </div>
              )}

              {activeJob && (
                <div style={{ marginTop: 16, borderTop: '1px solid var(--border-md)', paddingTop: 16 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                    <span className={`badge ${trainingStatusBadge(activeJob.status)}`}>{activeJob.status}</span>
                    <span className="mono" style={{ fontSize: 12 }}>job #{activeJob.jobId}</span>
                    {activeJob.mlflowRunId && (
                      <span className="mono" style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                        run {activeJob.mlflowRunId}
                      </span>
                    )}
                  </div>
                  {activeJob.status === 'RUNNING' && activeJob.currentEpoch != null ? (
                    <div>
                      <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 6 }}>
                        Epoch {activeJob.currentEpoch} / {activeJob.totalEpochs}
                      </div>
                      <div style={{ height: 4, background: 'var(--border)', marginBottom: 16 }}>
                        <div
                          style={{
                            height: 4,
                            width: `${activeJob.totalEpochs ? (activeJob.currentEpoch / activeJob.totalEpochs) * 100 : 0}%`,
                            background: '#0d9488',
                            transition: 'width 0.2s',
                          }}
                        />
                      </div>
                      {/* Fed by GET /training/jobs/{id}/epoch-metrics, which reads the job's own
                          mlflowRunId rather than requiring a registered ModelVersion, so the same
                          loss curve used for a completed model can grow live while still RUNNING. */}
                      <LossCurveChart epochMetrics={activeJobEpochMetrics} />
                    </div>
                  ) : (
                    (activeJob.status === 'QUEUED' || activeJob.status === 'RUNNING') && (
                      <div className="loading-state">
                        <span className="spinner" /> Training in progress…
                      </div>
                    )
                  )}
                  {activeJob.status === 'COMPLETED' && (
                    <div>
                      Model registered: <span className="mono">{activeJob.modelTag}</span>
                    </div>
                  )}
                  {activeJob.status === 'FAILED' && (
                    <div className="alert alert-error">{activeJob.errorMessage}</div>
                  )}
                </div>
              )}
            </div>
            <div className="modal-footer">
              <button className="btn btn-primary" onClick={handleSubmit} disabled={submitting || selectedVersionId == null}>
                {submitting ? 'Submitting…' : 'Start Training'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
