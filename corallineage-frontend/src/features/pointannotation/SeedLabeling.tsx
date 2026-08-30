import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { TransformComponent, TransformWrapper } from 'react-zoom-pan-pinch'
import type { ReactZoomPanPinchContentRef } from 'react-zoom-pan-pinch'
import {
  fetchGeneratedPoints,
  fetchPointAnnotationJob,
  labelPoint,
  trainPointAnnotationJob,
} from '../pointannotation/pointAnnotationApi'
import { ArrowLeftIcon, PlusIcon, XMarkIcon } from '@heroicons/react/24/outline'
import { fetchArchitectures, fetchTrainingJob } from '../training/trainingApi'
import TrainModelDialog from '../training/TrainModelDialog'
import { createLabelDefinition, fetchVersion, fetchVersions } from '../datasets/versionsApi'
import { imageFileUrlById } from '../datasets/imagesApi'
import { errorMessage } from '../../shared/api/client'
import { useAuthContext } from '../../shared/lib/authContextCore'
import { useToast } from '../../shared/lib/toastContextCore'
import LabelCombobox from '../../shared/components/LabelCombobox'
import type { GeneratedPoint, PointAnnotationJob } from '../pointannotation/pointAnnotationTypes'
import type { Architecture, TrainingJob } from '../training/trainingTypes'
import type { DatasetVersion, LabelDefinition } from '../datasets/versionsTypes'

export default function SeedLabeling() {
  const { id, sourceId: sourceIdParam } = useParams<{ id: string; sourceId: string }>()
  const jobId = Number(id)
  const sourceId = Number(sourceIdParam)
  const navigate = useNavigate()
  const { user } = useAuthContext()
  const toast = useToast()

  const [job, setJob] = useState<PointAnnotationJob | null>(null)
  const [points, setPoints] = useState<GeneratedPoint[]>([])
  const [labelOptions, setLabelOptions] = useState<LabelDefinition[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [selectedImageId, setSelectedImageId] = useState<number | null>(null)
  const [selectedPointId, setSelectedPointId] = useState<number | null>(null)
  const [canvasNaturalSize, setCanvasNaturalSize] = useState<{ width: number; height: number } | null>(null)
  const [dotsVisible, setDotsVisible] = useState(true)
  const transformRef = useRef<ReactZoomPanPinchContentRef>(null)

  const [trainingJob, setTrainingJob] = useState<TrainingJob | null>(null)
  const [trainDialogOpen, setTrainDialogOpen] = useState(false)
  const [architectures, setArchitectures] = useState<Architecture[]>([])
  const [versions, setVersions] = useState<DatasetVersion[]>([])

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
        // Landed here directly (a stale link, browser back/forward, a bookmark) on a job whose
        // seed-labeling stage is already behind it - forward straight to the delegated
        // AnnotationJob's review screen instead of rendering a dead seed-labeling UI for a step
        // that's already done. Mirrors the redirect the TRAINING poll below already does for the
        // case where that transition happens while this page is open.
        if (jobRes.status === 'COMPLETE' && jobRes.annotationJobId != null) {
          navigate(`/sources/${sourceId}/annotation/jobs/${jobRes.annotationJobId}/review`)
          return null
        }
        setJob(jobRes)
        setError(null)
        return fetchVersion(sourceId, jobRes.datasetVersionId)
      })
      .then((version) => version && setLabelOptions((version.labelDefinitions ?? []).filter((l) => !l.deprecated)))
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [jobId, sourceId, navigate, loadPoints])

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
  // point flipping status (e.g. initial load) - same derived-during-render pattern the Dirty
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
      const created = await createLabelDefinition(sourceId, job.datasetVersionId, {
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

  // Submitted via the shared TrainModelDialog (also used unmodified by ModelRegistry.tsx): unlike
  // that page's own usage, this one routes through trainPointAnnotationJob so the resulting
  // TrainingJob gets attached to this PointAnnotationJob (job.trainingJobId) instead of created as
  // a standalone job - see PointAnnotationJobService.trainModel and the plan doc's Part D notes for
  // why this distinction matters (TrainingJobExecutor.autoAnnotateAfterTraining reads it).
  //
  // Per the confirmed UX: once training is kicked off, the user leaves this page immediately
  // rather than watching progress here - the backend auto-delegates into auto-annotate the moment
  // training completes (TrainingJobExecutor -> PointAnnotationJobService.autoAnnotateAfterTraining,
  // no manual "Run Auto-Annotation" click), and cross-page feedback (a spinner on the Unlabeled
  // import queue entry, a toast once it's ready for review) is JobPollingProvider's job (Part G) -
  // it fires the "Training complete"/"Ready for review" toasts globally, so this only needs to
  // push its own immediate "started" acknowledgement before navigating away, since the shared
  // provider deliberately never toasts on a job's first sight (that's what avoids re-toasting
  // every already-in-progress job just because the user opened a new page).
  async function handleTrainSubmit(form: {
    architecture: string
    datasetVersionId: number
    learningRate: number
    batchSize: number
    epochs: number
  }): Promise<TrainingJob> {
    const updated = await trainPointAnnotationJob(jobId, {
      architecture: form.architecture,
      learningRate: form.learningRate,
      batchSize: form.batchSize,
      epochs: form.epochs,
    })
    if (updated.trainingJobId == null) {
      throw new Error('Training job was not created')
    }
    const trainingJobResponse = await fetchTrainingJob(updated.trainingJobId)
    toast.push({ variant: 'info', title: 'Training started', detail: 'You can track progress from the source overview.' })
    navigate(`/sources/${sourceId}`)
    return trainingJobResponse
  }

  // Polls the underlying (real, unmodified) Training Job while this point-annotation job is
  // TRAINING - reuses the existing Training Jobs page's own 3s-polling convention. Only matters if
  // the user navigates directly back to this page mid-training (the normal path leaves immediately
  // after submitting); once the job leaves TRAINING (delegated to auto-annotate), forward straight
  // to the delegated AnnotationJob's review screen rather than showing a dead-end status here.
  useEffect(() => {
    if (!job || job.status !== 'TRAINING' || job.trainingJobId == null) return
    let cancelled = false
    const interval = setInterval(() => {
      Promise.all([fetchTrainingJob(job.trainingJobId as number), fetchPointAnnotationJob(jobId)])
        .then(([tj, pointJob]) => {
          if (cancelled) return
          setTrainingJob(tj)
          if (pointJob.status !== 'TRAINING') {
            if (pointJob.annotationJobId != null) {
              navigate(`/sources/${sourceId}/annotation/jobs/${pointJob.annotationJobId}/review`)
            } else {
              setJob(pointJob)
            }
          }
        })
        .catch(() => {
          /* transient poll failure - try again next tick */
        })
    }, 3000)
    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [job, jobId, navigate, sourceId])

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

  const trainingProgressPct =
    trainingJob?.currentEpoch != null && trainingJob.totalEpochs
      ? (trainingJob.currentEpoch / trainingJob.totalEpochs) * 100
      : 0

  const displayPct = job.status === 'TRAINING' ? trainingProgressPct : progressPct
  const statusText =
    job.status === 'TRAINING'
      ? `Training, epoch ${trainingJob?.currentEpoch ?? '-'} / ${trainingJob?.totalEpochs ?? '-'}`
      : `${labeledCount} / ${totalCount} labeled`

  return (
    <>
      <div style={{ padding: '36px 28px 0' }}>
        <button className="btn btn-secondary" onClick={() => navigate(`/sources/${sourceId}`)} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
          <ArrowLeftIcon style={{ width: 14, height: 14 }} />
          Back
        </button>
      </div>

      <div style={{ margin: '0 24px', display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 16 }}>
        <span style={{ fontSize: 11, color: 'var(--text-secondary)' }}>{statusText}</span>
      </div>
      <div style={{ height: 4, background: 'var(--border)', margin: '4px 24px 0' }}>
        <div style={{ height: 4, width: `${displayPct}%`, background: '#0d9488', transition: 'width 0.2s' }} />
      </div>

      <div className="content">
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
          <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr 300px', gap: 16, height: 'calc(100vh - 149px)' }}>
            <div className="inset-container" style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
              <div className="card" style={{ padding: 0, flex: 1, overflowY: 'auto' }}>
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
                      <span className="queue-list-item-thumb">
                        <img src={imageFileUrlById(imgId)} alt="" loading="lazy" />
                        <span className="queue-dot" style={{ background: dotColor }} />
                      </span>
                      <span className="queue-list-item-text">
                        <span className="queue-list-item-filename">{imgPoints[0]?.filename ?? `image ${imgId}`}</span>
                        <span className="queue-list-item-progress">
                          {labeled}/{imgPoints.length} labeled
                        </span>
                      </span>
                    </div>
                  )
                })}
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
              <div style={{ display: 'flex', gap: 16, marginBottom: 8, alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', gap: 16, fontSize: 11, color: 'var(--text-secondary)' }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#16A34A', display: 'inline-block' }} />
                    Labeled
                  </span>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#2563EB', display: 'inline-block' }} />
                    Unlabeled
                  </span>
                </div>
                <label className="toggle-checkbox">
                  <input type="checkbox" checked={dotsVisible} onChange={(e) => setDotsVisible(e.target.checked)} />
                  Points
                </label>
              </div>
              <div className="image-canvas" style={{ flexShrink: 0 }}>
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
                        {dotsVisible &&
                          canvasNaturalSize &&
                          currentImagePoints.map((p, idx) => {
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
                                {idx + 1}
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

              <div className="card" style={{ marginTop: 10, flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
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
                    className="btn btn-primary btn-sm"
                    onClick={() => {
                      setAddLabelOpen(true)
                      setAddLabelError(null)
                    }}
                    style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
                  >
                    <PlusIcon style={{ width: 14, height: 14 }} />
                    New label
                  </button>
                </div>

                <div style={{ flex: 1, overflowY: 'auto' }}>
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

            <div style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden', minHeight: 0 }}>
              <div className="inset-container" style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden', flex: 1, minHeight: 0 }}>
                <div className="review-panel" style={{ flex: 1, minHeight: 0, maxHeight: 'none', overflowY: 'auto', padding: 12 }}>
                  {currentImagePoints.map((point, idx) => (
                    <SeedPointCard
                      key={point.id}
                      point={point}
                      pointNumber={idx + 1}
                      selected={point.id === effectiveSelectedPointId}
                      labelOptions={labelOptions}
                      onSelect={() => setSelectedPointId(point.id)}
                      onLabel={handleLabel}
                    />
                  ))}
                </div>
              </div>
              {job.status === 'SEED_LABELING' && (
                <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 10 }}>
                  <button className="btn btn-accept" disabled={!allLabeled} onClick={() => setTrainDialogOpen(true)}>
                    Train model &amp; run auto-annotation
                  </button>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {addLabelOpen && (
        <div className="modal-overlay active">
          <div className="modal">
            <div className="modal-header">
              <div>
                <div className="modal-title">New label</div>
              </div>
              <button
                className="modal-close"
                onClick={() => {
                  setAddLabelOpen(false)
                  setAddLabelError(null)
                  setNewLabelCode('')
                  setNewLabelName('')
                  setNewLabelCategory('')
                }}
                aria-label="Close"
              >
                <XMarkIcon style={{ width: 16, height: 16 }} />
              </button>
            </div>
            <div className="modal-body">
              {addLabelError && (
                <div className="alert alert-error" style={{ marginBottom: 16 }}>
                  {addLabelError}
                </div>
              )}
              <div className="form-row">
                <label className="form-label">Code</label>
                <input
                  className="form-input"
                  placeholder="e.g. BLEACHED_ACRO"
                  value={newLabelCode}
                  onChange={(e) => setNewLabelCode(e.target.value)}
                />
              </div>
              <div className="form-row">
                <label className="form-label">Name</label>
                <input
                  className="form-input"
                  placeholder="e.g. Bleached Acropora"
                  value={newLabelName}
                  onChange={(e) => setNewLabelName(e.target.value)}
                />
              </div>
              <div className="form-row" style={{ marginBottom: 0 }}>
                <label className="form-label">Category (optional)</label>
                <input
                  className="form-input"
                  placeholder="e.g. Coral"
                  value={newLabelCategory}
                  onChange={(e) => setNewLabelCategory(e.target.value)}
                />
              </div>
            </div>
            <div className="modal-footer">
              <button
                className="btn btn-secondary"
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
              <button
                className="btn btn-primary"
                onClick={handleAddLabel}
                disabled={addingLabel || !newLabelCode.trim() || !newLabelName.trim()}
              >
                {addingLabel ? 'Creating…' : 'Create Label'}
              </button>
            </div>
          </div>
        </div>
      )}

      {trainDialogOpen && (
        <TrainModelDialog
          sourceId={sourceId}
          initiatedBy={user?.username ?? ''}
          architectures={architectures}
          versions={versions}
          initialVersionId={job.datasetVersionId}
          onSubmit={handleTrainSubmit}
          onClose={() => setTrainDialogOpen(false)}
          onCompleted={() => {
            fetchPointAnnotationJob(jobId).then(setJob).catch(() => {})
          }}
        />
      )}
    </>
  )
}

function SeedPointCard({
  point,
  pointNumber,
  selected,
  labelOptions,
  onSelect,
  onLabel,
}: {
  point: GeneratedPoint
  pointNumber: number
  selected: boolean
  labelOptions: LabelDefinition[]
  onSelect: () => void
  onLabel: (point: GeneratedPoint, labelCode: string) => void
}) {
  // The dropdown alone is the confirm action (no separate "Label" button): picking an option
  // immediately labels the point - mirrors AnnotationReview.tsx's PredictionCard.
  const canLabel = point.status !== 'HUMAN_LABELED'

  return (
    <div id={`seed-card-${point.id}`} className={`review-point-card${selected ? ' selected' : ''}`} onClick={onSelect}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <span style={{ fontSize: 12, fontWeight: 600 }}>Point #{pointNumber}</span>
        {point.status === 'HUMAN_LABELED' ? (
          <span className="badge badge-green">{point.finalLabelCode}</span>
        ) : (
          <span className="badge">Unlabeled</span>
        )}
      </div>

      <div onClick={(e) => canLabel && e.stopPropagation()} onFocus={onSelect}>
        <LabelCombobox
          options={labelOptions}
          value={point.finalLabelCode ?? ''}
          onChange={canLabel ? (labelCode) => onLabel(point, labelCode) : () => {}}
          disabled={!canLabel}
          placeholder="Search labels…"
        />
      </div>
    </div>
  )
}
