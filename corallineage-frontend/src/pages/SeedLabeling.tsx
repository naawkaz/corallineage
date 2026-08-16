import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { TransformComponent, TransformWrapper } from 'react-zoom-pan-pinch'
import type { ReactZoomPanPinchContentRef } from 'react-zoom-pan-pinch'
import {
  autoAnnotatePointAnnotationJob,
  fetchGeneratedPoints,
  fetchPointAnnotationJob,
  labelPoint,
  trainPointAnnotationJob,
} from '../api/pointAnnotation'
import { Check, Plus } from 'lucide-react'
import { fetchTrainingJob } from '../api/training'
import { createLabelDefinition, fetchVersion } from '../api/versions'
import { imageFileUrlById } from '../api/images'
import { errorMessage } from '../api/client'
import { useAuthContext } from '../lib/authContextCore'
import LabelCombobox from '../components/LabelCombobox'
import type { GeneratedPoint, PointAnnotationJob } from '../types/pointAnnotation'
import type { TrainingJobStatus } from '../types/training'
import type { LabelDefinition } from '../types/version'

export default function SeedLabeling() {
  const { id, projectId: projectIdParam } = useParams<{ id: string; projectId: string }>()
  const jobId = Number(id)
  const projectId = Number(projectIdParam)
  const navigate = useNavigate()
  const { user } = useAuthContext()

  const [job, setJob] = useState<PointAnnotationJob | null>(null)
  const [points, setPoints] = useState<GeneratedPoint[]>([])
  const [labelOptions, setLabelOptions] = useState<LabelDefinition[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [selectedImageId, setSelectedImageId] = useState<number | null>(null)
  const [selectedPointId, setSelectedPointId] = useState<number | null>(null)
  const [canvasNaturalSize, setCanvasNaturalSize] = useState<{ width: number; height: number } | null>(null)
  const transformRef = useRef<ReactZoomPanPinchContentRef>(null)

  const [training, setTraining] = useState(false)
  const [autoAnnotating, setAutoAnnotating] = useState(false)
  const [trainingJobStatus, setTrainingJobStatus] = useState<TrainingJobStatus | null>(null)

  const [addLabelOpen, setAddLabelOpen] = useState(false)
  const [newLabelCode, setNewLabelCode] = useState('')
  const [newLabelName, setNewLabelName] = useState('')
  const [newLabelCategory, setNewLabelCategory] = useState('')
  const [addingLabel, setAddingLabel] = useState(false)
  const [addLabelError, setAddLabelError] = useState<string | null>(null)

  const loadPoints = useCallback(() => {
    return fetchGeneratedPoints(jobId, true, 0, 5000).then((res) => setPoints(res.content))
  }, [jobId])

  useEffect(() => {
    Promise.all([fetchPointAnnotationJob(jobId), loadPoints()])
      .then(([jobRes]) => {
        setJob(jobRes)
        setError(null)
        return fetchVersion(projectId, jobRes.datasetVersionId)
      })
      .then((version) => setLabelOptions((version.labelDefinitions ?? []).filter((l) => !l.deprecated)))
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [jobId, projectId, loadPoints])

  const imageIds = useMemo(() => {
    const seen = new Set<number>()
    const order: number[] = []
    for (const p of points) {
      if (!seen.has(p.imageId)) {
        seen.add(p.imageId)
        order.push(p.imageId)
      }
    }
    return order
  }, [points])

  const pointsByImage = useMemo(() => {
    const map = new Map<number, GeneratedPoint[]>()
    for (const p of points) {
      const list = map.get(p.imageId) ?? []
      list.push(p)
      map.set(p.imageId, list)
    }
    return map
  }, [points])

  // Re-selects the first image whenever the point set changes for a different reason than one
  // point flipping status (e.g. initial load) — same derived-during-render pattern the Dirty
  // Annotations Queue uses for its equivalent sync.
  const imageKey = imageIds.join(',')
  const [syncedImageKey, setSyncedImageKey] = useState('')
  if (imageKey !== syncedImageKey) {
    setSyncedImageKey(imageKey)
    setSelectedImageId((prev) => (prev != null && imageIds.includes(prev) ? prev : imageIds[0] ?? null))
    setCanvasNaturalSize(null)
  }

  const currentImagePoints = selectedImageId != null ? pointsByImage.get(selectedImageId) ?? [] : []
  const currentFilename = currentImagePoints[0]?.filename ?? ''
  const effectiveSelectedPointId = selectedPointId ?? currentImagePoints[0]?.id ?? null

  useEffect(() => {
    if (effectiveSelectedPointId == null) return
    document.getElementById(`seed-card-${effectiveSelectedPointId}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    if (!transformRef.current || !canvasNaturalSize) return
    const dotEl = document.getElementById(`seed-dot-${effectiveSelectedPointId}`)
    if (!dotEl) return
    transformRef.current.zoomToElement(dotEl, transformRef.current.state.scale)
  }, [effectiveSelectedPointId, canvasNaturalSize])

  async function handleLabel(point: GeneratedPoint, labelCode: string) {
    try {
      await labelPoint(point.id, { labelCode, annotatedBy: user?.username ?? '' })
      await loadPoints()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  // A distinct, deliberate action, reached through the full labelset panel, not the per-point
  // picker, so adding a new vocabulary entry mid-session is a conscious choice with its own
  // moment of friction, never something a mistyped keystroke in the fast, repetitive seed-labeling
  // loop can do by accident.
  async function handleAddLabel() {
    if (!job || !newLabelCode.trim() || !newLabelName.trim()) return
    setAddingLabel(true)
    setAddLabelError(null)
    try {
      const created = await createLabelDefinition(projectId, job.datasetVersionId, {
        labelCode: newLabelCode.trim(),
        labelName: newLabelName.trim(),
        labelCategory: newLabelCategory.trim() === '' ? undefined : newLabelCategory.trim(),
      })
      setLabelOptions((prev) => [...prev, created])
      setNewLabelCode('')
      setNewLabelName('')
      setNewLabelCategory('')
      setAddLabelOpen(false)
    } catch (err) {
      setAddLabelError(errorMessage(err))
    } finally {
      setAddingLabel(false)
    }
  }

  async function handleTrain() {
    setTraining(true)
    setError(null)
    try {
      const updated = await trainPointAnnotationJob(jobId)
      setJob(updated)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setTraining(false)
    }
  }

  // Polls the underlying (real, unmodified) Training Job while this point-annotation job is
  // TRAINING — reuses the existing Training Jobs page's own 3s-polling convention.
  useEffect(() => {
    if (!job || job.status !== 'TRAINING' || job.trainingJobId == null) return
    let cancelled = false
    const interval = setInterval(() => {
      fetchTrainingJob(job.trainingJobId as number)
        .then((tj) => {
          if (!cancelled) setTrainingJobStatus(tj.status)
        })
        .catch(() => {
          /* transient poll failure — try again next tick */
        })
    }, 3000)
    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [job])

  async function handleAutoAnnotate() {
    setAutoAnnotating(true)
    setError(null)
    try {
      const updated = await autoAnnotatePointAnnotationJob(jobId)
      setJob(updated)
      if (updated.status === 'REVIEW') {
        navigate(`/projects/${projectId}/point-annotation/jobs/${jobId}/review`)
      }
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setAutoAnnotating(false)
    }
  }

  if (loading) {
    return (
      <div className="content">
        <div className="loading-state">
          <span className="spinner" /> Loading seed labeling…
        </div>
      </div>
    )
  }

  if (error && !job) {
    return (
      <div className="content">
        <div className="alert alert-error">{error}</div>
      </div>
    )
  }
  if (!job) return null

  const labeledCount = points.filter((p) => p.status === 'HUMAN_LABELED').length
  const totalCount = points.length
  const progressPct = totalCount > 0 ? (labeledCount / totalCount) * 100 : 0
  const allLabeled = totalCount > 0 && labeledCount === totalCount

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title">Seed Labeling — Job #{job.jobId}</div>
          <div className="topbar-sub">
            {labeledCount} / {totalCount} seed points labeled ({job.seedImageCount} image(s), {job.pointsPerImage}{' '}
            points/image)
          </div>
        </div>
        <button className="btn btn-secondary" onClick={() => navigate(`/projects/${projectId}/point-annotation`)}>
          Back
        </button>
      </div>

      <div style={{ height: 4, background: 'var(--border)', margin: '0 24px' }}>
        <div style={{ height: 4, width: `${progressPct}%`, background: '#0d9488', transition: 'width 0.2s' }} />
      </div>

      <div className="content">
        {allLabeled && job.status === 'SEED_LABELING' && (
          <div className="impact-box" style={{ marginBottom: 16, alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span><Check size={16} strokeWidth={1.5} /></span>
              <span>All seed points are labeled — ready to train.</span>
            </span>
            <button className="btn btn-primary" onClick={handleTrain} disabled={training}>
              {training ? 'Starting…' : 'Train Model'}
            </button>
          </div>
        )}
        {job.status === 'TRAINING' && (
          <div className="impact-box" style={{ marginBottom: 16, alignItems: 'center', justifyContent: 'space-between' }}>
            <span>Training job #{job.trainingJobId} — status: {trainingJobStatus ?? 'QUEUED'}</span>
            <button
              className="btn btn-primary"
              onClick={handleAutoAnnotate}
              disabled={autoAnnotating || trainingJobStatus !== 'COMPLETED'}
            >
              {autoAnnotating ? 'Running…' : 'Run Auto-Annotation'}
            </button>
          </div>
        )}
        {(job.status === 'REVIEW' || job.status === 'COMPLETE') && (
          <div className="impact-box" style={{ marginBottom: 16, alignItems: 'center', justifyContent: 'space-between' }}>
            <span>
              Auto-annotation complete with {job.modelTag}.{' '}
              {job.status === 'REVIEW' ? 'Some points need review.' : 'Every point was auto-confirmed.'}
            </span>
            {job.status === 'REVIEW' && (
              <button
                className="btn btn-primary"
                onClick={() => navigate(`/projects/${projectId}/point-annotation/jobs/${jobId}/review`)}
              >
                Go to Review Queue
              </button>
            )}
          </div>
        )}
        {error && (
          <div className="alert alert-error" style={{ marginBottom: 16 }}>
            {error}
          </div>
        )}

        {totalCount === 0 ? (
          <div className="card">
            <div className="empty-state">
              <p>No seed points found for this job.</p>
            </div>
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: 16 }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div className="card" style={{ padding: 0, maxHeight: '40vh', overflowY: 'auto' }}>
                {imageIds.map((imgId) => {
                  const imgPoints = pointsByImage.get(imgId) ?? []
                  const labeled = imgPoints.filter((p) => p.status === 'HUMAN_LABELED').length
                  const dotColor = labeled === 0 ? 'var(--text-muted)' : labeled < imgPoints.length ? '#D97706' : '#16A34A'
                  return (
                    <div
                      key={imgId}
                      className={`queue-list-item${imgId === selectedImageId ? ' selected' : ''}`}
                      onClick={() => {
                        setSelectedImageId(imgId)
                        setSelectedPointId(null)
                        setCanvasNaturalSize(null)
                      }}
                    >
                      <span className="queue-dot" style={{ background: dotColor }} />
                      <span className="mono" style={{ fontSize: 12 }}>{imgPoints[0]?.filename ?? `image ${imgId}`}</span>
                    </div>
                  )
                })}
              </div>

              <div className="card" style={{ padding: 0 }}>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '8px 12px',
                    borderBottom: '1px solid var(--border)',
                  }}
                >
                  <span style={{ fontSize: 12, fontWeight: 600 }}>Label set ({labelOptions.length})</span>
                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    onClick={() => {
                      setAddLabelOpen((prev) => !prev)
                      setAddLabelError(null)
                    }}
                    style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
                  >
                    <Plus size={14} strokeWidth={1.5} />
                    New label
                  </button>
                </div>

                {addLabelOpen && (
                  <div style={{ padding: 12, borderBottom: '1px solid var(--border)' }}>
                    <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 8 }}>
                      Declares a new vocabulary entry on this dataset version, for a genuinely new class found
                      mid-session, not as a shortcut for picking from the existing list.
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 8 }}>
                      <input
                        className="form-input"
                        placeholder="Code, e.g. BLEACHED_ACRO"
                        value={newLabelCode}
                        onChange={(e) => setNewLabelCode(e.target.value)}
                      />
                      <input
                        className="form-input"
                        placeholder="Name, e.g. Bleached Acropora"
                        value={newLabelName}
                        onChange={(e) => setNewLabelName(e.target.value)}
                      />
                      <input
                        className="form-input"
                        placeholder="Category (optional), e.g. Coral"
                        value={newLabelCategory}
                        onChange={(e) => setNewLabelCategory(e.target.value)}
                      />
                    </div>
                    {addLabelError && (
                      <div className="alert alert-error" style={{ marginBottom: 8 }}>
                        {addLabelError}
                      </div>
                    )}
                    <div style={{ display: 'flex', gap: 8 }}>
                      <button
                        className="btn btn-primary btn-sm"
                        onClick={handleAddLabel}
                        disabled={addingLabel || !newLabelCode.trim() || !newLabelName.trim()}
                      >
                        {addingLabel ? 'Creating…' : 'Create Label'}
                      </button>
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={() => {
                          setAddLabelOpen(false)
                          setAddLabelError(null)
                          setNewLabelCode('')
                          setNewLabelName('')
                          setNewLabelCategory('')
                        }}
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}

                <div style={{ maxHeight: '30vh', overflowY: 'auto' }}>
                  {labelOptions.length === 0 ? (
                    <div style={{ padding: 12, fontSize: 12, color: 'var(--text-secondary)' }}>
                      No labels declared on this dataset version yet.
                    </div>
                  ) : (
                    labelOptions.map((label) => (
                      <div
                        key={label.labelCode}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 8,
                          padding: '6px 12px',
                          borderBottom: '1px solid var(--border)',
                        }}
                      >
                        <span className="mono" style={{ fontSize: 11 }}>{label.labelCode}</span>
                        <span style={{ fontSize: 12, flex: 1 }}>{label.labelName}</span>
                        {label.labelCategory && <span className="tag">{label.labelCategory}</span>}
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>

            <div className="review-layout">
              <div>
                <div className="image-canvas">
                  {selectedImageId != null ? (
                    <TransformWrapper
                      key={selectedImageId}
                      ref={transformRef}
                      initialScale={1}
                      minScale={1}
                      maxScale={4}
                      centerOnInit
                    >
                      <TransformComponent wrapperStyle={{ width: '100%', height: '100%' }} contentStyle={{ width: '100%', height: '100%' }}>
                        <div style={{ position: 'relative', width: '100%', height: '100%' }}>
                          <img
                            src={imageFileUrlById(selectedImageId)}
                            alt={currentFilename}
                            style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
                            onLoad={(e) => {
                              const img = e.currentTarget
                              setCanvasNaturalSize({ width: img.naturalWidth, height: img.naturalHeight })
                            }}
                          />
                          {canvasNaturalSize &&
                            currentImagePoints.map((p) => {
                              const dotClass =
                                p.id === effectiveSelectedPointId
                                  ? 'dot-selected'
                                  : p.status === 'HUMAN_LABELED'
                                    ? 'dot-accepted'
                                    : 'dot-pending'
                              return (
                                <div
                                  key={p.id}
                                  id={`seed-dot-${p.id}`}
                                  className={`annotation-dot ${dotClass}`}
                                  style={{
                                    top: `${(p.rowPosition / canvasNaturalSize.height) * 100}%`,
                                    left: `${(p.colPosition / canvasNaturalSize.width) * 100}%`,
                                  }}
                                  title={p.status === 'HUMAN_LABELED' ? `${p.finalLabelCode}` : 'Unlabeled'}
                                  onClick={() => setSelectedPointId(p.id)}
                                >
                                  {p.pointIndex}
                                </div>
                              )
                            })}
                        </div>
                      </TransformComponent>
                    </TransformWrapper>
                  ) : (
                    <div className="image-placeholder">No image selected</div>
                  )}
                </div>
                <div style={{ display: 'flex', gap: 16, marginTop: 10, fontSize: 11, color: 'var(--text-secondary)' }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#16A34A', display: 'inline-block' }} />
                    Labeled
                  </span>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#2563EB', display: 'inline-block' }} />
                    Unlabeled
                  </span>
                </div>
              </div>

              <div className="review-panel">
                {currentImagePoints.map((point) => (
                  <SeedPointCard
                    key={point.id}
                    point={point}
                    selected={point.id === effectiveSelectedPointId}
                    labelOptions={labelOptions}
                    onSelect={() => setSelectedPointId(point.id)}
                    onLabel={handleLabel}
                  />
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </>
  )
}

function SeedPointCard({
  point,
  selected,
  labelOptions,
  onSelect,
  onLabel,
}: {
  point: GeneratedPoint
  selected: boolean
  labelOptions: LabelDefinition[]
  onSelect: () => void
  onLabel: (point: GeneratedPoint, labelCode: string) => void
}) {
  const [picked, setPicked] = useState('')

  return (
    <div id={`seed-card-${point.id}`} className={`review-point-card${selected ? ' selected' : ''}`} onClick={onSelect}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <span style={{ fontSize: 12, fontWeight: 600 }}>Point #{point.pointIndex}</span>
        {point.status === 'HUMAN_LABELED' ? (
          <span className="badge badge-green">{point.finalLabelCode}</span>
        ) : (
          <span className="badge">Unlabeled</span>
        )}
      </div>

      {point.status !== 'HUMAN_LABELED' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }} onClick={(e) => e.stopPropagation()}>
          <LabelCombobox
            options={labelOptions}
            value={picked}
            onChange={setPicked}
            placeholder="Search labels…"
          />
          <button className="btn btn-secondary" disabled={!picked} onClick={() => onLabel(point, picked)}>
            Label
          </button>
        </div>
      )}
    </div>
  )
}
