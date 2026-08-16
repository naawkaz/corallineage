import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { fetchAnnotationJob, fetchAnnotationJobs, submitNewImagesJob, submitValidationJob } from '../api/annotations'
import { fetchImages, imageFileUrlById } from '../api/images'
import { fetchModels } from '../api/models'
import { fetchVersions } from '../api/versions'
import { errorMessage } from '../api/client'
import { CURRENT_USER } from '../constants'
import { formatDate } from '../lib/format'
import { useDatasetContext } from '../lib/datasetContextCore'
import type { AnnotationJob } from '../types/annotation'
import type { DatasetImage } from '../types/image'
import type { ModelVersion } from '../types/model'
import type { DatasetVersion } from '../types/version'

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
  const { projectId: projectIdParam } = useParams()
  const projectId = Number(projectIdParam)
  const { datasets } = useDatasetContext()
  const project = datasets.find((d) => d.id === projectId) ?? null

  const [tab, setTab] = useState<'submit' | 'review'>('submit')
  const [mode, setMode] = useState<'validate' | 'new-images'>('validate')

  const [models, setModels] = useState<ModelVersion[]>([])
  const [selectedModelId, setSelectedModelId] = useState<number | null>(null)

  // Mode A: validate
  const [versions, setVersions] = useState<DatasetVersion[]>([])
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null)
  const [images, setImages] = useState<DatasetImage[]>([])
  const [imagesLoading, setImagesLoading] = useState(false)
  const [selectedImageIds, setSelectedImageIds] = useState<number[]>([])

  // Mode B: new images
  const [pointsPerImage, setPointsPerImage] = useState(12)
  const [uploadFiles, setUploadFiles] = useState<File[]>([])

  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [activeJob, setActiveJob] = useState<AnnotationJob | null>(null)

  const [jobs, setJobs] = useState<AnnotationJob[]>([])
  const [jobsLoading, setJobsLoading] = useState(true)
  const [jobsError, setJobsError] = useState<string | null>(null)

  const pollingRef = useRef<number | null>(null)

  function loadJobs() {
    fetchAnnotationJobs(projectId, 0, 20)
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
  }, [projectId])

  useEffect(() => {
    fetchModels(projectId, 0, 50, 'ACTIVE')
      .then((res) => {
        setModels(res.content)
        if (res.content.length > 0) setSelectedModelId(res.content[0].id)
      })
      .catch(() => setModels([]))
  }, [projectId])

  useEffect(() => {
    if (mode !== 'validate') return
    fetchVersions(projectId, 0, 50)
      .then((res) => {
        setVersions(res.content)
        if (res.content.length > 0) setSelectedVersionId(res.content[0].id)
      })
      .catch(() => setVersions([]))
  }, [projectId, mode])

  useEffect(() => {
    if (mode !== 'validate' || !selectedVersionId) return
    fetchImages(projectId, selectedVersionId, 0, 200, true)
      .then((res) => {
        setImages(res.content)
        // Filtered images (human-labeled points) are selected by default; user can deselect.
        setSelectedImageIds(res.content.map((img) => img.id))
      })
      .catch(() => {
        setImages([])
        setSelectedImageIds([])
      })
      .finally(() => setImagesLoading(false))
  }, [projectId, selectedVersionId, mode])

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
        initiatedBy: CURRENT_USER,
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
      const created = await submitNewImagesJob(selectedModelId, projectId, CURRENT_USER, pointsPerImage, uploadFiles)
      setActiveJob(created)
      setUploadFiles([])
      loadJobs()
    } catch (err) {
      setSubmitError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title">Annotation Jobs</div>
          <div className="topbar-sub">
            {project ? `Run model inference on ${project.datasetName}` : 'Run model inference'}
          </div>
        </div>
      </div>

      <div className="content">
        <div style={{ display: 'flex', gap: 8, marginBottom: 20 }}>
          <button
            className={`btn ${tab === 'submit' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setTab('submit')}
          >
            Submit
          </button>
          <button
            className={`btn ${tab === 'review' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setTab('review')}
          >
            Review Jobs
          </button>
        </div>

        {tab === 'submit' && (
          <div className="grid-2" style={{ gap: 24, alignItems: 'start' }}>
            <div>
              <div className="section-header">
                <div className="section-title">New Annotation Job</div>
              </div>

              <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
                <button
                  className={`btn ${mode === 'validate' ? 'btn-primary' : 'btn-secondary'}`}
                  onClick={() => setMode('validate')}
                >
                  Validate Against Human Labels
                </button>
                <button
                  className={`btn ${mode === 'new-images' ? 'btn-primary' : 'btn-secondary'}`}
                  onClick={() => setMode('new-images')}
                >
                  Annotate New Images
                </button>
              </div>
              <p style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 12 }}>
                {mode === 'validate'
                  ? 'Recheck a model\'s accuracy against real human-given labels already in a dataset version, no correction step, just a comparison.'
                  : 'Upload brand-new images that are not yet part of any dataset version, generate points, run inference, then review each prediction before adding it to a project.'}
              </p>

              <div className="card" style={{ padding: 16 }}>
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
                      <div
                        style={{
                          maxHeight: 320,
                          overflowY: 'auto',
                          border: '1px solid var(--border-md)',
                          borderRadius: 6,
                        }}
                      >
                        {imagesLoading ? (
                          <div className="loading-state" style={{ padding: 16 }}>
                            <span className="spinner" /> Loading images...
                          </div>
                        ) : images.length === 0 ? (
                          <div className="empty-state" style={{ padding: 16 }}>
                            <p>No images with human-labeled points in this version.</p>
                          </div>
                        ) : (
                          images.map((img) => (
                            <label key={img.id} className="image-select-row">
                              <input
                                type="checkbox"
                                checked={selectedImageIds.includes(img.id)}
                                onChange={() => toggleImage(img.id)}
                              />
                              <span className="image-select-thumb">
                                <img src={imageFileUrlById(img.id)} alt={img.filename} loading="lazy" />
                              </span>
                              <span className="mono" style={{ fontSize: 12 }}>{img.filename}</span>
                            </label>
                          ))
                        )}
                      </div>
                    </div>
                  </>
                ) : (
                  <>
                    <div className="form-row">
                      <label className="form-label">Points per image</label>
                      <input
                        className="form-input"
                        type="number"
                        min={1}
                        max={50}
                        value={pointsPerImage}
                        onChange={(e) => setPointsPerImage(Math.max(1, Math.min(50, Number(e.target.value) || 1)))}
                      />
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

                <div style={{ marginTop: 16 }}>
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
                          onClick={() => navigate(`/projects/${projectId}/annotation/jobs/${activeJob.jobId}/review`)}
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
            </div>

            <div>
              <div className="section-header">
                <div className="section-title">Recent Jobs</div>
              </div>
              <JobsTable jobs={jobs.slice(0, 5)} loading={jobsLoading} error={jobsError} navigate={navigate} projectId={projectId} />
            </div>
          </div>
        )}

        {tab === 'review' && (
          <div>
            <div className="section-header">
              <div className="section-title">All Annotation Jobs</div>
            </div>
            <JobsTable jobs={jobs} loading={jobsLoading} error={jobsError} navigate={navigate} projectId={projectId} />
          </div>
        )}
      </div>
    </>
  )
}

function JobsTable({
  jobs,
  loading,
  error,
  navigate,
  projectId,
}: {
  jobs: AnnotationJob[]
  loading: boolean
  error: string | null
  navigate: (path: string) => void
  projectId: number
}) {
  return (
    <div className="card">
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
              <th>Images</th>
              <th>Started</th>
            </tr>
          </thead>
          <tbody>
            {jobs.map((job) => (
              <tr
                key={job.jobId}
                style={{ cursor: job.status === 'COMPLETED' ? 'pointer' : 'default' }}
                onClick={() => job.status === 'COMPLETED' && navigate(`/projects/${projectId}/annotation/jobs/${job.jobId}/review`)}
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
                <td>{job.imageCount}</td>
                <td style={{ color: 'var(--text-secondary)' }}>{formatDate(job.initiatedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
