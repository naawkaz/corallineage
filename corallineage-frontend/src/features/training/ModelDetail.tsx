import { useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeftIcon,
  ArrowRightIcon,
  ArrowTopRightOnSquareIcon,
  ArchiveBoxIcon,
  NoSymbolIcon,
  ArrowPathIcon,
  PlusIcon,
  XMarkIcon,
} from '@heroicons/react/24/outline'
import { fetchMlflowDetails, fetchModel, updateModelStatus } from '../training/modelsApi'
import {
  fetchAnnotationJob,
  fetchAnnotationJobsByModel,
  submitNewImagesJob,
  submitValidationJob,
} from '../annotation/annotationApi'
import { fetchImages, imageFileUrlById } from '../datasets/imagesApi'
import { fetchVersions } from '../datasets/versionsApi'
import { errorMessage } from '../../shared/api/client'
import { formatDate } from '../../shared/lib/format'
import { useAuthContext } from '../../shared/lib/authContextCore'
import { useToast } from '../../shared/lib/toastContextCore'
import { useJobPolling } from '../../shared/lib/jobPollingContextCore'
import LossCurveChart from '../training/LossCurveChart'
import TitledCard from '../../shared/components/TitledCard'
import Pagination from '../../shared/components/Pagination'
import type { AnnotationJob } from '../annotation/annotationTypes'
import type { DatasetImage } from '../datasets/imagesTypes'
import type { DatasetVersion } from '../datasets/versionsTypes'
import type { MlflowDetails, ModelStatus, ModelVersion } from '../training/modelsTypes'

function jobStatusBadge(status: AnnotationJob['status']): string {
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

function reviewStatusBadge(status: AnnotationJob['reviewStatus']): string {
  switch (status) {
    case 'COMPLETED':
      return 'badge-green'
    case 'IN_PROGRESS':
      return 'badge-amber'
    default:
      return 'badge-gray'
  }
}

function jobTypeLabel(jobType: AnnotationJob['jobType']): string {
  if (jobType === 'VALIDATION') return 'Validation'
  if (jobType === 'NEW_IMAGES') return 'New Images'
  return 'Legacy'
}

const STATUS_BADGE: Record<ModelStatus, string> = {
  ACTIVE: 'badge-green',
  ARCHIVED: 'badge-gray',
  DEPRECATED: 'badge-red',
}

type DiagnosticsState =
  | { kind: 'loading' }
  | { kind: 'ready'; details: MlflowDetails }
  | { kind: 'not-found' }
  | { kind: 'unavailable' }
  | { kind: 'error'; message: string }

export default function ModelDetail() {
  const { id, sourceId: sourceIdParam } = useParams()
  const navigate = useNavigate()
  const modelId = Number(id)
  const modelsListPath = `/sources/${sourceIdParam}/models`
  const { user } = useAuthContext()
  const { refreshSignal } = useJobPolling()

  const [model, setModel] = useState<ModelVersion | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [statusChanging, setStatusChanging] = useState(false)
  const [statusError, setStatusError] = useState<string | null>(null)

  const [diagnostics, setDiagnostics] = useState<DiagnosticsState>({ kind: 'loading' })

  const [validateOpen, setValidateOpen] = useState(false)
  const [uploadOpen, setUploadOpen] = useState(false)

  const [jobs, setJobs] = useState<AnnotationJob[]>([])
  const [jobsLoading, setJobsLoading] = useState(true)
  const [jobsError, setJobsError] = useState<string | null>(null)

  function loadJobs() {
    fetchAnnotationJobsByModel(modelId, 0, 20)
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
  }, [modelId, refreshSignal])

  function loadModel() {
    fetchModel(modelId)
      .then((res) => {
        setModel(res)
        setError(null)
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadModel()
    fetchMlflowDetails(modelId)
      .then((details) => setDiagnostics({ kind: 'ready', details }))
      .catch((err) => {
        if (axios.isAxiosError(err)) {
          if (err.response?.status === 404) {
            setDiagnostics({ kind: 'not-found' })
            return
          }
          if (err.response?.status === 503) {
            setDiagnostics({ kind: 'unavailable' })
            return
          }
        }
        setDiagnostics({ kind: 'error', message: errorMessage(err) })
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modelId])

  async function handleStatusChange(newStatus: ModelStatus) {
    setStatusChanging(true)
    setStatusError(null)
    try {
      const updated = await updateModelStatus(modelId, newStatus, user?.username ?? '')
      setModel(updated)
    } catch (err) {
      setStatusError(errorMessage(err))
    } finally {
      setStatusChanging(false)
    }
  }

  if (loading && !model) {
    return (
      <div className="content">
        <div className="loading-state">
          <span className="spinner" /> Loading model...
        </div>
      </div>
    )
  }

  if (error && !model) {
    return (
      <div className="content">
        <div className="alert alert-error">{error}</div>
      </div>
    )
  }

  if (!model) return null

  const perClass = model.performanceMetrics?.perClass ?? []

  return (
    <>
      <div className="content">
        <div className="page-back-row">
          <button className="btn btn-secondary" onClick={() => navigate(modelsListPath)} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <ArrowLeftIcon style={{ width: 14, height: 14 }} />
            Back
          </button>
        </div>

        <div className="page-title-row" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
          <div className="page-title">Model Version: {model.modelTag}</div>
          <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
            {/* <button
              className="btn btn-secondary"
              onClick={() => setValidateOpen(true)}
              style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
            >
              Validate Performance
            </button> */}
            <button
              className="btn btn-primary"
              onClick={() => setUploadOpen(true)}
              style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
            >
              <PlusIcon style={{ width: 14, height: 14 }} />
              Use to Annotate New Images
            </button>
            {model.status !== 'ARCHIVED' && (
              <button
                className="btn btn-secondary"
                disabled={statusChanging}
                onClick={() => handleStatusChange('ARCHIVED')}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
              >
                <ArchiveBoxIcon style={{ width: 14, height: 14 }} />
                Archive
              </button>
            )}
            {model.status !== 'DEPRECATED' && (
              <button
                className="btn"
                disabled={statusChanging}
                onClick={() => handleStatusChange('DEPRECATED')}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6, background: 'var(--danger-700)', color: '#fff', borderColor: 'var(--danger-700)' }}
              >
                <NoSymbolIcon style={{ width: 14, height: 14 }} />
                Deprecate
              </button>
            )}
            {model.status !== 'ACTIVE' && (
              <button
                className="btn btn-primary"
                disabled={statusChanging}
                onClick={() => handleStatusChange('ACTIVE')}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
              >
                <ArrowPathIcon style={{ width: 14, height: 14 }} />
                Reactivate
              </button>
            )}
          </div>
        </div>
        {statusError && (
          <div className="alert alert-error" style={{ marginBottom: 16 }}>
            {statusError}
          </div>
        )}

        <div className="grid-2" style={{ gap: 24, alignItems: 'start' }}>
          <TitledCard title="Model metadata">
              <div className="detail-grid">
                <div className="detail-row">
                  <div className="detail-key">Architecture</div>
                  <div>
                    <span className="badge badge-blue">{model.architecture}</span>
                  </div>
                </div>
                <div className="detail-row">
                  <div className="detail-key">Status</div>
                  <div>
                    <span className={`badge ${STATUS_BADGE[model.status]}`}>{model.status}</span>
                  </div>
                </div>
                <div className="detail-row">
                  <div className="detail-key">Accuracy</div>
                  <div>{model.accuracy != null ? `${(model.accuracy * 100).toFixed(1)}%` : '-'}</div>
                </div>
                <div className="detail-row">
                  <div className="detail-key">Dataset version</div>
                  <div>
                    {model.trainedOnDatasetVersion ? (
                      <Link
                        to={`/sources/${sourceIdParam}/versions/${model.trainedOnDatasetVersion.id}`}
                        className="mono"
                        style={{ color: 'var(--accent)' }}
                      >
                        {model.trainedOnDatasetVersion.versionTag}
                      </Link>
                    ) : (
                      '-'
                    )}
                  </div>
                </div>
                <div className="detail-row">
                  <div className="detail-key">Hyperparameters</div>
                  <div style={{ color: 'var(--text-secondary)' }}>
                    {model.hyperparameters
                      ? Object.entries(model.hyperparameters)
                          .map(([k, v]) => `${k}: ${v}`)
                          .join(', ')
                      : '-'}
                  </div>
                </div>
                <div className="detail-row">
                  <div className="detail-key">MLflow run</div>
                  <div>
                    <span className="mono">{model.mlflowRunId ?? '-'}</span>
                  </div>
                </div>
              </div>
          </TitledCard>

          <TitledCard title="Class mappings & per-class metrics" bodyClassName="no-padding">
              {model.classMappings && (
                <div style={{ padding: 16, borderBottom: '1px solid var(--border-md)' }}>
                  {Object.entries(model.classMappings).map(([idx, code]) => (
                    <span key={idx} className="tag" style={{ marginRight: 6 }}>
                      {idx}: {code}
                    </span>
                  ))}
                </div>
              )}
              {perClass.length > 0 ? (
                <table>
                  <thead>
                    <tr>
                      <th>Label</th>
                      <th className="num">Accuracy</th>
                      <th className="num">Samples</th>
                    </tr>
                  </thead>
                  <tbody>
                    {perClass.map((c) => (
                      <tr key={c.labelCode}>
                        <td>
                          <span className="mono">{c.labelCode}</span>
                        </td>
                        <td className="num">
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 8 }}>
                            <div
                              style={{
                                width: 80,
                                height: 6,
                                borderRadius: 3,
                                background: 'var(--bg-secondary)',
                                overflow: 'hidden',
                              }}
                            >
                              <div
                                style={{
                                  width: `${((c.accuracy ?? 0) * 100).toFixed(0)}%`,
                                  height: '100%',
                                  background: 'var(--accent)',
                                }}
                              />
                            </div>
                            <span>{c.accuracy != null ? `${(c.accuracy * 100).toFixed(0)}%` : '-'}</span>
                          </div>
                        </td>
                        <td className="num">{c.sampleCount ?? '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <div className="empty-state">
                  <p>No per-class metrics recorded.</p>
                </div>
              )}
          </TitledCard>
        </div>

        <div className="grid-2" style={{ gridTemplateColumns: '2fr 1fr', gap: 24, alignItems: 'start', marginTop: 24 }}>
        <TitledCard
          title="Model diagnostics"
          actions={
            diagnostics.kind === 'ready' && (
              <a
                className="btn btn-primary"
                href={diagnostics.details.mlflowUiUrl}
                target="_blank"
                rel="noreferrer"
                style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
              >
                Open in MLflow
                <ArrowTopRightOnSquareIcon style={{ width: 14, height: 14 }} />
              </a>
            )
          }
        >
            {diagnostics.kind === 'loading' && (
              <div className="loading-state">
                <span className="spinner" /> Loading MLflow details...
              </div>
            )}
            {diagnostics.kind === 'not-found' && (
              <div className="empty-state">
                <p>No MLflow run recorded for this model.</p>
              </div>
            )}
            {diagnostics.kind === 'unavailable' && (
              <div className="alert alert-error">
                MLflow tracking server is unreachable - training metadata is still shown above, but
                deep diagnostics (environment, loss curve, confusion matrix) can't be loaded right now.
              </div>
            )}
            {diagnostics.kind === 'error' && <div className="alert alert-error">{diagnostics.message}</div>}
            {diagnostics.kind === 'ready' && (
              <>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
                  {diagnostics.details.environment.pythonVersion && (
                    <span className="tag">Python {diagnostics.details.environment.pythonVersion}</span>
                  )}
                  {diagnostics.details.environment.pytorchVersion && (
                    <span className="tag">PyTorch {diagnostics.details.environment.pytorchVersion}</span>
                  )}
                  {diagnostics.details.environment.cudaVersion && (
                    <span className="tag">{diagnostics.details.environment.cudaVersion.toUpperCase()}</span>
                  )}
                  {diagnostics.details.environment.randomSeed && (
                    <span className="tag">Seed {diagnostics.details.environment.randomSeed}</span>
                  )}
                  {diagnostics.details.environment.totalDurationSeconds != null && (
                    <span className="tag">{diagnostics.details.environment.totalDurationSeconds}s</span>
                  )}
                </div>

                <LossCurveChart epochMetrics={diagnostics.details.epochMetrics} />

                {diagnostics.details.artifactUrls.confusionMatrixPng && (
                  <div style={{ marginBottom: 16 }}>
                    <div className="detail-key" style={{ marginBottom: 8 }}>Confusion matrix</div>
                    <img
                      src={diagnostics.details.artifactUrls.confusionMatrixPng}
                      alt="Confusion matrix"
                      style={{ maxWidth: 400, border: '1px solid var(--border-md)', borderRadius: 6 }}
                    />
                  </div>
                )}
              </>
            )}
        </TitledCard>

        <TitledCard title="Annotation jobs" bodyClassName="no-padding">
          {jobsError && (
            <div className="alert alert-error" style={{ margin: 16 }}>
              {jobsError}
            </div>
          )}
          {jobsLoading ? (
            <div className="loading-state">
              <span className="spinner" /> Loading jobs...
            </div>
          ) : jobs.length === 0 ? (
            <div className="empty-state">
              <p>No annotation jobs yet for this model.</p>
            </div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Status</th>
                  <th>Review</th>
                  <th className="num">Images</th>
                  <th>Started</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <tr
                    key={job.jobId}
                    className={job.status === 'COMPLETED' ? 'clickable' : undefined}
                    onClick={() => job.status === 'COMPLETED' && navigate(`/sources/${sourceIdParam}/annotation/jobs/${job.jobId}/review`)}
                  >
                    <td>
                      <span className="badge badge-gray">{jobTypeLabel(job.jobType)}</span>
                    </td>
                    <td>
                      <span className={`badge ${jobStatusBadge(job.status)}`}>{job.status}</span>
                    </td>
                    <td>
                      {job.jobType === 'VALIDATION' ? (
                        <span className="mono" style={{ fontSize: 12 }}>
                          {job.accuracyPercent != null ? `${job.accuracyPercent.toFixed(1)}% accuracy` : 'n/a'}
                        </span>
                      ) : (
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <span className={`badge ${reviewStatusBadge(job.reviewStatus)}`}>{job.reviewStatus}</span>
                          {job.reviewStatus === 'IN_PROGRESS' && job.totalPredictions > 0 && (
                            <div className="progress-bar-bg" style={{ width: 80 }}>
                              <div
                                className="progress-bar-fill"
                                style={{ width: `${(job.reviewedPredictions / job.totalPredictions) * 100}%` }}
                              />
                            </div>
                          )}
                        </div>
                      )}
                    </td>
                    <td className="num">{job.imageCount}</td>
                    <td style={{ color: 'var(--text-secondary)' }}>{formatDate(job.initiatedAt)}</td>
                    <td>
                      {job.status === 'COMPLETED' && (
                        <span style={{ color: 'var(--accent)', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 2 }}>
                          View <ArrowRightIcon style={{ width: 12, height: 12 }} />
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </TitledCard>
        </div>
      </div>

      {validateOpen && (
        <ValidateModelDialog
          model={model}
          sourceId={Number(sourceIdParam)}
          initiatedBy={user?.username ?? ''}
          navigate={navigate}
          onClose={() => setValidateOpen(false)}
          onCompleted={loadJobs}
        />
      )}

      {uploadOpen && (
        <UploadAndAnnotateDialog
          model={model}
          sourceId={Number(sourceIdParam)}
          initiatedBy={user?.username ?? ''}
          navigate={navigate}
          onClose={() => setUploadOpen(false)}
          onCompleted={loadJobs}
        />
      )}
    </>
  )
}

interface AnnotationDialogProps {
  model: ModelVersion
  sourceId: number
  initiatedBy: string
  navigate: (path: string) => void
  onClose: () => void
  onCompleted: () => void
}

/**
 * "Validate model performance": rechecks this model's predictions against human-labeled points
 * on an existing dataset version, a direct accuracy comparison with no review step.
 */
function ValidateModelDialog({ model, sourceId, initiatedBy, navigate, onClose, onCompleted }: AnnotationDialogProps) {
  const [versions, setVersions] = useState<DatasetVersion[]>([])
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null)
  const [images, setImages] = useState<DatasetImage[]>([])
  const [imagesLoading, setImagesLoading] = useState(false)
  const [selectedImageIds, setSelectedImageIds] = useState<number[]>([])
  const [imagesPage, setImagesPage] = useState(0)
  const IMAGES_PER_PAGE = 40

  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [activeJob, setActiveJob] = useState<AnnotationJob | null>(null)

  const pollingRef = useRef<number | null>(null)
  const toast = useToast()

  useEffect(() => {
    fetchVersions(sourceId, 0, 50)
      .then((res) => {
        setVersions(res.content)
        if (res.content.length > 0) setSelectedVersionId(res.content[0].id)
      })
      .catch(() => setVersions([]))
  }, [sourceId])

  useEffect(() => {
    if (!selectedVersionId) return
    setImagesLoading(true)
    fetchImages(sourceId, selectedVersionId, 0, 200, true)
      .then((res) => {
        setImages(res.content)
        setImagesPage(0)
        // Filtered images selected by default. Able to deselect
        setSelectedImageIds(res.content.map((img) => img.id))
      })
      .catch(() => {
        setImages([])
        setSelectedImageIds([])
      })
      .finally(() => setImagesLoading(false))
  }, [sourceId, selectedVersionId])

  // Drives live status UI while dialog stays open
  useEffect(() => {
    if (!activeJob || activeJob.status !== 'RUNNING') return
    const intervalId = window.setInterval(() => {
      fetchAnnotationJob(activeJob.jobId)
        .then((updated) => {
          setActiveJob(updated)
          if (updated.status !== 'RUNNING' && pollingRef.current !== null) {
            window.clearInterval(pollingRef.current)
            pollingRef.current = null
            onCompleted()
          }
        })
        .catch(() => {
          // transient poll failure, try again next tick
        })
    }, 3000)
    pollingRef.current = intervalId
    return () => {
      window.clearInterval(intervalId)
      pollingRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeJob])

  function toggleImage(id: number) {
    setSelectedImageIds((prev) => (prev.includes(id) ? prev.filter((i) => i !== id) : [...prev, id]))
  }

  async function handleSubmit() {
    if (!selectedVersionId || selectedImageIds.length === 0) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      const created = await submitValidationJob({
        modelVersionId: model.id,
        datasetVersionId: selectedVersionId,
        imageIds: selectedImageIds,
        initiatedBy,
      })
      setActiveJob(created)
      toast.push({ variant: 'info', title: 'Validation started', detail: 'Track progress below, or come back later.' })
      onCompleted()
    } catch (err) {
      setSubmitError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  const imagesTotalPages = Math.ceil(images.length / IMAGES_PER_PAGE)
  const visibleImages = images.slice(imagesPage * IMAGES_PER_PAGE, (imagesPage + 1) * IMAGES_PER_PAGE)

  return (
    <div className="modal-overlay active">
      <div className="modal">
        <div className="modal-header">
          <div>
            <div className="modal-title">Validate Performance</div>
            <div className="modal-subtitle">
              Recheck {model.modelTag}'s predictions against real human-labeled points on an existing dataset version.
            </div>
          </div>
          <button className="modal-close" onClick={onClose} aria-label="Close">
            <XMarkIcon style={{ width: 16, height: 16 }} />
          </button>
        </div>
        <div className="modal-body">
          <div className="form-row">
            <label className="form-label">Dataset version</label>
            <select
              className="form-select"
              value={selectedVersionId ?? ''}
              onChange={(e) => setSelectedVersionId(e.target.value ? Number(e.target.value) : null)}
            >
              {versions.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.versionTag}
                </option>
              ))}
            </select>
          </div>

          <div className="form-row" style={{ marginBottom: 0 }}>
            <label className="form-label">
              Images with human-labeled points ({selectedImageIds.length} of {images.length} selected)
            </label>
            {imagesLoading ? (
              <div className="loading-state" style={{ padding: 16 }}>
                <span className="spinner" /> Loading images...
              </div>
            ) : images.length === 0 ? (
              <div className="empty-state" style={{ padding: 16 }}>
                <p>No images with human-labeled points in this version.</p>
              </div>
            ) : (
              <>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(110px, 1fr))',
                  gap: 8,
                  maxHeight: 340,
                  overflowY: 'auto',
                }}
              >
                {visibleImages.map((img) => {
                  const selected = selectedImageIds.includes(img.id)
                  return (
                    <button
                      key={img.id}
                      type="button"
                      onClick={() => toggleImage(img.id)}
                      title={img.filename}
                      style={{
                        position: 'relative',
                        padding: 0,
                        border: selected ? '2px solid var(--accent)' : '2px solid transparent',
                        borderRadius: 6,
                        overflow: 'hidden',
                        cursor: 'pointer',
                        aspectRatio: '1 / 1',
                        background: 'var(--neutral-100)',
                      }}
                    >
                      <img
                        src={imageFileUrlById(img.id)}
                        alt={img.filename}
                        loading="lazy"
                        style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
                      />
                      <span
                        style={{
                          position: 'absolute',
                          top: 4,
                          left: 4,
                          width: 14,
                          height: 14,
                          borderRadius: 3,
                          background: selected ? 'var(--accent)' : 'rgba(20,24,30,0.55)',
                          border: '1px solid #fff',
                        }}
                      />
                      <span
                        className="mono"
                        style={{
                          position: 'absolute',
                          bottom: 0,
                          left: 0,
                          right: 0,
                          fontSize: 10,
                          padding: '2px 4px',
                          background: 'rgba(20,24,30,0.75)',
                          color: '#fff',
                          border: 'none',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                        }}
                      >
                        {img.filename}
                      </span>
                    </button>
                  )
                })}
              </div>
              {imagesTotalPages > 1 && (
                <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 10 }}>
                  <Pagination page={imagesPage} totalPages={imagesTotalPages} onPageChange={setImagesPage} />
                </div>
              )}
              </>
            )}
          </div>

          {submitError && (
            <div className="alert alert-error" style={{ marginTop: 16 }}>
              {submitError}
            </div>
          )}

          {activeJob && (
            <div style={{ marginTop: 16, borderTop: '1px solid var(--border-md)', paddingTop: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                <span className={`badge ${jobStatusBadge(activeJob.status)}`}>{activeJob.status}</span>
                <span className="mono" style={{ fontSize: 12 }}>job #{activeJob.jobId}</span>
              </div>
              {activeJob.status === 'RUNNING' && (
                <div className="loading-state">
                  <span className="spinner" /> Running inference...
                </div>
              )}
              {activeJob.status === 'COMPLETED' && (
                <div>
                  {activeJob.totalPredictions} point(s) checked.{' '}
                  <button
                    className="btn btn-secondary"
                    onClick={() => navigate(`/sources/${sourceId}/annotation/jobs/${activeJob.jobId}/review`)}
                  >
                    View results
                  </button>
                </div>
              )}
              {activeJob.status === 'FAILED' && <div className="alert alert-error">{activeJob.errorMessage}</div>}
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn btn-primary"
            onClick={handleSubmit}
            disabled={submitting || !selectedVersionId || selectedImageIds.length === 0}
          >
            {submitting ? 'Submitting...' : 'Run Validation'}
          </button>
        </div>
      </div>
    </div>
  )
}

/**
 * "Upload & Annotate New Images": uploads images not yet part of any dataset version, runs them
 * through this model, auto-confirms predictions above the confidence threshold, and queues the
 * rest for manual review before they're added to the dataset.
 */
function UploadAndAnnotateDialog({ model, sourceId, initiatedBy, navigate, onClose, onCompleted }: AnnotationDialogProps) {
  const [gridRows, setGridRows] = useState(3)
  const [gridCols, setGridCols] = useState(4)
  const [confidenceThreshold, setConfidenceThreshold] = useState(0.85)
  const [uploadFiles, setUploadFiles] = useState<File[]>([])

  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [activeJob, setActiveJob] = useState<AnnotationJob | null>(null)

  const pollingRef = useRef<number | null>(null)
  const toast = useToast()

  // Drives only live status UI while dialog stays open
  useEffect(() => {
    if (!activeJob || activeJob.status !== 'RUNNING') return
    const intervalId = window.setInterval(() => {
      fetchAnnotationJob(activeJob.jobId)
        .then((updated) => {
          setActiveJob(updated)
          if (updated.status !== 'RUNNING' && pollingRef.current !== null) {
            window.clearInterval(pollingRef.current)
            pollingRef.current = null
            onCompleted()
          }
        })
        .catch(() => {
          // transient poll failure, try again next tick
        })
    }, 3000)
    pollingRef.current = intervalId
    return () => {
      window.clearInterval(intervalId)
      pollingRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeJob])

  async function handleSubmit() {
    if (uploadFiles.length === 0) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      const created = await submitNewImagesJob(
        model.id,
        sourceId,
        initiatedBy,
        gridRows,
        gridCols,
        confidenceThreshold,
        uploadFiles,
      )
      setActiveJob(created)
      setUploadFiles([])
      toast.push({ variant: 'info', title: 'Annotating new images started', detail: 'Track progress below, or come back later.' })
      onCompleted()
    } catch (err) {
      setSubmitError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="modal-overlay active">
      <div className="modal">
        <div className="modal-header">
          <div>
            <div className="modal-title">Upload &amp; Annotate New Images</div>
            <div className="modal-subtitle">
              Use {model.modelTag} to annotate images not yet part of the dataset
            </div>
          </div>
          <button className="modal-close" onClick={onClose} aria-label="Close">
            <XMarkIcon style={{ width: 16, height: 16 }} />
          </button>
        </div>
        <div className="modal-body">
          <div className="form-row">
            <label className="form-label">Grid size</label>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input
                className="form-input"
                type="number"
                min={1}
                max={20}
                style={{ width: 80 }}
                value={gridRows}
                onChange={(e) => setGridRows(Math.max(1, Math.min(20, Number(e.target.value) || 1)))}
              />
              <span>rows x</span>
              <input
                className="form-input"
                type="number"
                min={1}
                max={20}
                style={{ width: 80 }}
                value={gridCols}
                onChange={(e) => setGridCols(Math.max(1, Math.min(20, Number(e.target.value) || 1)))}
              />
              <span>cols</span>
            </div>
            <div className="form-hint">{gridRows * gridCols} points per image</div>
          </div>
          <div className="form-row">
            <label className="form-label" htmlFor="uploadConfidenceThreshold">
              Confidence threshold ({Math.round(confidenceThreshold * 100)}%)
            </label>
            <input
              id="uploadConfidenceThreshold"
              type="range"
              min={0.5}
              max={0.99}
              step={0.01}
              style={{ width: '100%' }}
              value={confidenceThreshold}
              onChange={(e) => setConfidenceThreshold(Number(e.target.value))}
            />
            <div className="form-hint">
              Predicted points above this confidence threshold auto-confirm.
            </div>
          </div>
          <div className="form-row" style={{ marginBottom: 0 }}>
            <label className="form-label">Images ({uploadFiles.length} selected)</label>
            <input
              type="file"
              multiple
              accept="image/*"
              onChange={(e) => setUploadFiles(e.target.files ? Array.from(e.target.files) : [])}
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
                <span className={`badge ${jobStatusBadge(activeJob.status)}`}>{activeJob.status}</span>
                <span className="mono" style={{ fontSize: 12 }}>job #{activeJob.jobId}</span>
              </div>
              {activeJob.status === 'RUNNING' && (
                <div className="loading-state">
                  <span className="spinner" /> Running inference...
                </div>
              )}
              {activeJob.status === 'COMPLETED' && (
                <div>
                  {activeJob.totalPredictions} point(s) ready.{' '}
                  <button
                    className="btn btn-secondary"
                    onClick={() => navigate(`/sources/${sourceId}/annotation/jobs/${activeJob.jobId}/review`)}
                  >
                    Review now
                  </button>
                </div>
              )}
              {activeJob.status === 'FAILED' && <div className="alert alert-error">{activeJob.errorMessage}</div>}
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn btn-primary"
            onClick={handleSubmit}
            disabled={submitting || uploadFiles.length === 0}
          >
            {submitting ? 'Uploading...' : 'Upload and Annotate'}
          </button>
        </div>
      </div>
    </div>
  )
}
