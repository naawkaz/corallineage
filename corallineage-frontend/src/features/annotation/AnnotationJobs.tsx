import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ArrowRightIcon, PlusIcon, XMarkIcon } from '@heroicons/react/24/outline'
import { fetchAnnotationJob, fetchAnnotationJobs, submitNewImagesJob, submitValidationJob } from '../annotation/annotationApi'
import { fetchImages, imageFileUrlById } from '../datasets/imagesApi'
import { fetchModels } from '../training/modelsApi'
import { fetchVersions } from '../datasets/versionsApi'
import { errorMessage } from '../../shared/api/client'
import { formatDate } from '../../shared/lib/format'
import { scrollToAndHighlightAll } from '../../shared/lib/highlight'
import { useAuthContext } from '../../shared/lib/authContextCore'
import TitledCard from '../../shared/components/TitledCard'
import Pagination from '../../shared/components/Pagination'
import type { AnnotationJob } from '../annotation/annotationTypes'
import type { DatasetImage } from '../datasets/imagesTypes'
import type { ModelVersion } from '../training/modelsTypes'
import type { DatasetVersion } from '../datasets/versionsTypes'

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

export default function AnnotationJobs() {
  const navigate = useNavigate()
  const { sourceId: sourceIdParam } = useParams()
  const sourceId = Number(sourceIdParam)
  const { user } = useAuthContext()
  const [searchParams] = useSearchParams()

  const [mode, setMode] = useState<'validate' | 'new-images'>('validate')

  const [models, setModels] = useState<ModelVersion[]>([])
  const [selectedModelId, setSelectedModelId] = useState<number | null>(null)

  // Mode A: validate
  const [versions, setVersions] = useState<DatasetVersion[]>([])
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null)
  const [images, setImages] = useState<DatasetImage[]>([])
  const [imagesLoading, setImagesLoading] = useState(false)
  const [selectedImageIds, setSelectedImageIds] = useState<number[]>([])
  const [imagesPage, setImagesPage] = useState(0)
  const IMAGES_PER_PAGE = 40

  // Mode B: new images
  const [gridRows, setGridRows] = useState(3)
  const [gridCols, setGridCols] = useState(4)
  const [uploadFiles, setUploadFiles] = useState<File[]>([])

  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [activeJob, setActiveJob] = useState<AnnotationJob | null>(null)

  const [jobs, setJobs] = useState<AnnotationJob[]>([])
  const [jobsLoading, setJobsLoading] = useState(true)
  const [jobsError, setJobsError] = useState<string | null>(null)

  const [annotateDialogOpen, setAnnotateDialogOpen] = useState(false)

  const pollingRef = useRef<number | null>(null)

  function loadJobs() {
    fetchAnnotationJobs(sourceId, 0, 20)
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

  // Arriving from the Overview page's "Predicted annotations to resolve" ring's Go button
  // (?highlight=pending) scrolls to and flashes every job with unreviewed predictions once the
  // table has actually loaded.
  useEffect(() => {
    if (searchParams.get('highlight') !== 'pending' || jobsLoading) return
    const pendingIds = jobs
      .filter((j) => j.totalPredictions - j.reviewedPredictions > 0)
      .map((j) => `annotation-job-row-${j.jobId}`)
    scrollToAndHighlightAll(pendingIds)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobsLoading])

  useEffect(() => {
    fetchModels(sourceId, 0, 50, 'ACTIVE')
      .then((res) => {
        setModels(res.content)
        if (res.content.length > 0) setSelectedModelId(res.content[0].id)
      })
      .catch(() => setModels([]))
  }, [sourceId])

  useEffect(() => {
    if (mode !== 'validate') return
    fetchVersions(sourceId, 0, 50)
      .then((res) => {
        setVersions(res.content)
        if (res.content.length > 0) setSelectedVersionId(res.content[0].id)
      })
      .catch(() => setVersions([]))
  }, [sourceId, mode])

  useEffect(() => {
    if (mode !== 'validate' || !selectedVersionId) return
    fetchImages(sourceId, selectedVersionId, 0, 200, true)
      .then((res) => {
        setImages(res.content)
        setImagesPage(0)
        // Filtered images (human-labeled points) are selected by default; user can deselect.
        setSelectedImageIds(res.content.map((img) => img.id))
      })
      .catch(() => {
        setImages([])
        setSelectedImageIds([])
      })
      .finally(() => setImagesLoading(false))
  }, [sourceId, selectedVersionId, mode])

  useEffect(() => {
    if (!activeJob || activeJob.status !== 'RUNNING') return
    const intervalId = window.setInterval(() => {
      fetchAnnotationJob(activeJob.jobId)
        .then((updated) => {
          setActiveJob(updated)
          if (updated.status !== 'RUNNING' && pollingRef.current !== null) {
            window.clearInterval(pollingRef.current)
            pollingRef.current = null
            loadJobs()
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

  // Dialog shouldn't remember state from a previous open (mode, image selection, points per
  // image, uploaded files, etc), reset whenever it's opened rather than only on submit success.
  useEffect(() => {
    if (!annotateDialogOpen) return
    setMode('validate')
    setSelectedModelId(models[0]?.id ?? null)
    setSelectedVersionId(versions[0]?.id ?? null)
    setSelectedImageIds([])
    setImagesPage(0)
    setGridRows(3)
    setGridCols(4)
    setUploadFiles([])
    setSubmitError(null)
    setActiveJob(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [annotateDialogOpen])

  function toggleImage(id: number) {
    setSelectedImageIds((prev) => (prev.includes(id) ? prev.filter((i) => i !== id) : [...prev, id]))
  }

  async function handleSubmitValidation() {
    if (!selectedModelId || !selectedVersionId || selectedImageIds.length === 0) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      const created = await submitValidationJob({
        modelVersionId: selectedModelId,
        datasetVersionId: selectedVersionId,
        imageIds: selectedImageIds,
        initiatedBy: user?.username ?? '',
      })
      setActiveJob(created)
      loadJobs()
    } catch (err) {
      setSubmitError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleSubmitNewImages() {
    if (!selectedModelId || uploadFiles.length === 0) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      const created = await submitNewImagesJob(selectedModelId, sourceId, user?.username ?? '', gridRows, gridCols, uploadFiles)
      setActiveJob(created)
      setUploadFiles([])
      loadJobs()
    } catch (err) {
      setSubmitError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  const imagesTotalPages = Math.ceil(images.length / IMAGES_PER_PAGE)
  const visibleImages = images.slice(imagesPage * IMAGES_PER_PAGE, (imagesPage + 1) * IMAGES_PER_PAGE)

  return (
    <>
      <div className="content page-top-spacing">
        {/* item 42: "Recent Jobs" renamed to "Annotation History", full width */}
        <TitledCard
          title="Annotation history"
          bodyClassName="no-padding"
          actions={
            <button
              className="btn btn-primary"
              onClick={() => setAnnotateDialogOpen(true)}
              style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
            >
              <PlusIcon style={{ width: 14, height: 14 }} />
              Annotate
            </button>
          }
        >
          <JobsTable jobs={jobs} loading={jobsLoading} error={jobsError} navigate={navigate} sourceId={sourceId} />
        </TitledCard>
      </div>

      {annotateDialogOpen && (
        <div className="modal-overlay active">
          <div className="modal">
            <div className="modal-header">
              <div>
                <div className="modal-title">Annotate with model</div>
              </div>
              <button className="modal-close" onClick={() => setAnnotateDialogOpen(false)} aria-label="Close">
                <XMarkIcon style={{ width: 16, height: 16 }} />
              </button>
            </div>
            <div className="modal-body">
              {/* item 41: mode choice as radio options inside the submit card, not a page-level tab */}
              <div className="form-row">
                <label className="form-label">Mode</label>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
                    <input
                      type="radio"
                      name="annotationMode"
                      checked={mode === 'new-images'}
                      onChange={() => setMode('new-images')}
                    />
                    <span style={{ fontSize: 13, fontWeight: 500 }}>Annotate new images and add to dataset</span>
                  </label>
                  <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
                    <input
                      type="radio"
                      name="annotationMode"
                      checked={mode === 'validate'}
                      onChange={() => setMode('validate')}
                    />
                    <span style={{ fontSize: 13, fontWeight: 500 }}>Validate model performance</span>
                  </label>
                </div>
              </div>

              <div className="form-row">
                <label className="form-label">Model</label>
                <select
                  className="form-select"
                  value={selectedModelId ?? ''}
                  onChange={(e) => setSelectedModelId(e.target.value ? Number(e.target.value) : null)}
                >
                  {models.length === 0 && <option value="">No ACTIVE models available</option>}
                  {models.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.modelTag} ({m.accuracy != null ? `${(m.accuracy * 100).toFixed(1)}% acc` : 'n/a'})
                    </option>
                  ))}
                </select>
              </div>

              {mode === 'validate' ? (
                <>
                  <div className="form-row">
                    <label className="form-label">Dataset version</label>
                    <select
                      className="form-select"
                      value={selectedVersionId ?? ''}
                      onChange={(e) => {
                        setImagesLoading(true)
                        setSelectedVersionId(e.target.value ? Number(e.target.value) : null)
                      }}
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
                      {/* item 43: grid layout matching the Dataset Versions image grid, not a row list */}
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
                              {/* item 44: filename shown on hover (native title) plus a truncated label strip */}
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
                </>
              ) : (
                <>
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
                  <div className="form-row" style={{ marginBottom: 0 }}>
                    <label className="form-label">Images ({uploadFiles.length} selected)</label>
                    <input
                      type="file"
                      multiple
                      accept="image/*"
                      onChange={(e) => setUploadFiles(e.target.files ? Array.from(e.target.files) : [])}
                    />
                  </div>
                </>
              )}

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
                        {activeJob.jobType === 'VALIDATION' ? 'View results' : 'Review now'}
                      </button>
                    </div>
                  )}
                  {activeJob.status === 'FAILED' && (
                    <div className="alert alert-error">{activeJob.errorMessage}</div>
                  )}
                </div>
              )}
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setAnnotateDialogOpen(false)}>
                Cancel
              </button>
              {mode === 'validate' ? (
                <button
                  className="btn btn-primary"
                  onClick={handleSubmitValidation}
                  disabled={submitting || !selectedModelId || !selectedVersionId || selectedImageIds.length === 0}
                >
                  {submitting ? 'Submitting...' : 'Run Validation'}
                </button>
              ) : (
                <button
                  className="btn btn-primary"
                  onClick={handleSubmitNewImages}
                  disabled={submitting || !selectedModelId || uploadFiles.length === 0}
                >
                  {submitting ? 'Uploading...' : 'Upload and Annotate'}
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  )
}

function JobsTable({
  jobs,
  loading,
  error,
  navigate,
  sourceId,
}: {
  jobs: AnnotationJob[]
  loading: boolean
  error: string | null
  navigate: (path: string) => void
  sourceId: number
}) {
  return (
    <>
      {error && (
        <div className="alert alert-error" style={{ margin: 16 }}>
          {error}
        </div>
      )}
      {loading ? (
        <div className="loading-state">
          <span className="spinner" /> Loading jobs...
        </div>
      ) : jobs.length === 0 ? (
        <div className="empty-state">
          <p>No annotation jobs yet.</p>
        </div>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Type</th>
              <th>Status</th>
              <th>Review</th>
              <th>Model</th>
              <th className="num">Images</th>
              <th>Started</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {jobs.map((job) => (
              <tr
                key={job.jobId}
                id={`annotation-job-row-${job.jobId}`}
                className={job.status === 'COMPLETED' ? 'clickable' : undefined}
                onClick={() => job.status === 'COMPLETED' && navigate(`/sources/${sourceId}/annotation/jobs/${job.jobId}/review`)}
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
                <td>{job.modelTag ?? 'n/a'}</td>
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
    </>
  )
}
