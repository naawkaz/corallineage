import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { TransformComponent, TransformWrapper } from 'react-zoom-pan-pinch'
import type { ReactZoomPanPinchContentRef } from 'react-zoom-pan-pinch'
import { ArrowLeftIcon, CheckIcon, XMarkIcon } from '@heroicons/react/24/outline'
import {
  addToProject,
  fetchAnnotationJob,
  fetchPredictions,
  reviewPrediction,
} from '../annotation/annotationApi'
import { imageFileUrlById } from '../datasets/imagesApi'
import { fetchVersion } from '../datasets/versionsApi'
import { errorMessage } from '../../shared/api/client'
import { useAuthContext } from '../../shared/lib/authContextCore'
import LabelCombobox from '../../shared/components/LabelCombobox'
import type { AnnotationJob, AnnotationPrediction } from '../annotation/annotationTypes'
import type { LabelDefinition } from '../datasets/versionsTypes'

function dotClassFor(job: AnnotationJob, prediction: AnnotationPrediction, isSelected: boolean): string {
  if (isSelected) return 'dot-selected'
  if (job.jobType === 'VALIDATION') {
    return prediction.agreesWithHuman ? 'dot-accepted' : 'dot-corrected'
  }
  if (prediction.accepted === null) return 'dot-pending'
  return prediction.accepted ? 'dot-accepted' : 'dot-corrected'
}

export default function AnnotationReview() {
  const { id, sourceId: sourceIdParam } = useParams<{ id: string; sourceId: string }>()
  const jobId = Number(id)
  const sourceId = Number(sourceIdParam)
  const navigate = useNavigate()
  const { user } = useAuthContext()

  const [job, setJob] = useState<AnnotationJob | null>(null)
  const [predictions, setPredictions] = useState<AnnotationPrediction[]>([])
  const [labelOptions, setLabelOptions] = useState<LabelDefinition[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [selectedImageId, setSelectedImageId] = useState<number | null>(null)
  const [selectedPredictionId, setSelectedPredictionId] = useState<number | null>(null)
  const [canvasNaturalSize, setCanvasNaturalSize] = useState<{ width: number; height: number } | null>(null)
  const [labelsVisible, setLabelsVisible] = useState(false)
  const [dotsVisible, setDotsVisible] = useState(true)
  const transformRef = useRef<ReactZoomPanPinchContentRef>(null)

  const [addOpen, setAddOpen] = useState(false)
  const [versionTag, setVersionTag] = useState('')
  const [addNotes, setAddNotes] = useState('')
  const [adding, setAdding] = useState(false)
  const [addError, setAddError] = useState<string | null>(null)

  const loadAll = useCallback(() => {
    return Promise.all([fetchAnnotationJob(jobId), fetchPredictions(jobId, 0, 5000)])
      .then(([jobRes, predictionsRes]) => {
        setJob(jobRes)
        setPredictions(predictionsRes.content)
        setError(null)
        return jobRes
      })
      .catch((err) => {
        setError(errorMessage(err))
        return null
      })
      .finally(() => setLoading(false))
  }, [jobId])

  useEffect(() => {
    loadAll()
  }, [loadAll])

  useEffect(() => {
    if (!job || !job.datasetVersionId) return
    fetchVersion(sourceId, job.datasetVersionId)
      .then((version) => setLabelOptions((version.labelDefinitions ?? []).filter((l) => !l.deprecated)))
      .catch(() => setLabelOptions([]))
  }, [job, sourceId])

  const imageIds = useMemo(() => {
    const seen = new Set<number>()
    const order: number[] = []
    for (const p of predictions) {
      if (!seen.has(p.imageId)) {
        seen.add(p.imageId)
        order.push(p.imageId)
      }
    }
    return order
  }, [predictions])

  const predictionsByImage = useMemo(() => {
    const map = new Map<number, AnnotationPrediction[]>()
    for (const p of predictions) {
      const list = map.get(p.imageId) ?? []
      list.push(p)
      map.set(p.imageId, list)
    }
    return map
  }, [predictions])

  const imageKey = imageIds.join(',')
  const [syncedImageKey, setSyncedImageKey] = useState('')
  if (imageKey !== syncedImageKey) {
    setSyncedImageKey(imageKey)
    setSelectedImageId((prev) => (prev != null && imageIds.includes(prev) ? prev : imageIds[0] ?? null))
    setCanvasNaturalSize(null)
  }

  const currentImagePredictions = selectedImageId != null ? predictionsByImage.get(selectedImageId) ?? [] : []
  const currentFilename = currentImagePredictions[0]?.filename ?? ''
  const effectiveSelectedId = selectedPredictionId ?? currentImagePredictions[0]?.predictionId ?? null

  useEffect(() => {
    if (effectiveSelectedId == null) return
    document.getElementById(`review-card-${effectiveSelectedId}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    if (!transformRef.current || !canvasNaturalSize) return
    const dotEl = document.getElementById(`review-dot-${effectiveSelectedId}`)
    if (!dotEl) return
    transformRef.current.zoomToElement(dotEl, transformRef.current.state.scale)
  }, [effectiveSelectedId, canvasNaturalSize])

  async function acceptPrediction(prediction: AnnotationPrediction) {
    try {
      await reviewPrediction(jobId, prediction.predictionId, { accepted: true, reviewedBy: user?.username ?? '' })
      await loadAll()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  async function overridePrediction(prediction: AnnotationPrediction, labelCode: string) {
    try {
      await reviewPrediction(jobId, prediction.predictionId, {
        accepted: false,
        correctedLabelCode: labelCode,
        reviewedBy: user?.username ?? '',
      })
      await loadAll()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  // Dialog shouldn't remember state from a previous open, reset whenever it's opened.
  useEffect(() => {
    if (!addOpen) return
    setVersionTag('')
    setAddNotes('')
    setAddError(null)
  }, [addOpen])

  async function handleAddToProject() {
    if (!versionTag.trim()) return
    setAdding(true)
    setAddError(null)
    try {
      await addToProject(jobId, {
        versionTag: versionTag.trim(),
        notes: addNotes.trim() || undefined,
        createdBy: user?.username ?? '',
      })
      // Re-fetch the canonical job state through the same loadAll() path used on mount, rather
      // than trusting the add-to-project response body directly. This is what a manual page
      // reload already did to reveal the correct state, so folding it into the success path here
      // closes the gap by construction instead of depending on the POST response always matching
      // the shape this component expects.
      await loadAll()
      setAddOpen(false)
    } catch (err) {
      setAddError(errorMessage(err))
    } finally {
      setAdding(false)
    }
  }

  if (loading) {
    return (
      <div className="content">
        <div className="loading-state">
          <span className="spinner" /> Loading review...
        </div>
      </div>
    )
  }

  if (error || !job) {
    return (
      <div className="content">
        <div className="alert alert-error">{error ?? 'Job not found'}</div>
      </div>
    )
  }

  const isValidation = job.jobType === 'VALIDATION'
  const progressPct = job.totalPredictions > 0 ? (job.reviewedPredictions / job.totalPredictions) * 100 : 0
  const readyToAdd = job.jobType === 'NEW_IMAGES' && job.reviewStatus === 'COMPLETED' && !job.addedToProjectVersionId

  return (
    <>
      <div style={{ padding: '36px 28px 0' }}>
        <button className="btn btn-secondary" onClick={() => navigate(-1)} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
          <ArrowLeftIcon style={{ width: 14, height: 14 }} />
          Back
        </button>
      </div>

      {!isValidation && (
        <>
          <div style={{ margin: '0 24px', display: 'flex', alignItems: 'center', justifyContent: 'flex-end' }}>
            <span style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
              {job.reviewedPredictions} / {job.totalPredictions} reviewed
            </span>
          </div>
          <div style={{ height: 4, background: 'var(--border)', margin: '4px 24px 0' }}>
            <div style={{ height: 4, width: `${progressPct}%`, background: '#0d9488', transition: 'width 0.2s' }} />
          </div>
        </>
      )}

      <div className="content">
        {isValidation && (
          <div className="card" style={{ padding: 16, marginBottom: 16, display: 'flex', gap: 32, alignItems: 'center' }}>
            <div>
              <div style={{ fontSize: 11, color: 'var(--text-secondary)', textTransform: 'uppercase' }}>Accuracy</div>
              <div style={{ fontSize: 28, fontWeight: 700 }}>
                {job.accuracyPercent != null ? `${job.accuracyPercent.toFixed(1)}%` : 'n/a'}
              </div>
            </div>
            <div>
              <div style={{ fontSize: 11, color: 'var(--text-secondary)', textTransform: 'uppercase' }}>Agree</div>
              <div style={{ fontSize: 18, fontWeight: 600, color: 'var(--success)' }}>{job.correctPredictions ?? 0}</div>
            </div>
            <div>
              <div style={{ fontSize: 11, color: 'var(--text-secondary)', textTransform: 'uppercase' }}>Disagree</div>
              <div style={{ fontSize: 18, fontWeight: 600, color: 'var(--danger)' }}>
                {job.totalPredictions - (job.correctPredictions ?? 0)}
              </div>
            </div>
            <div>
              <div style={{ fontSize: 11, color: 'var(--text-secondary)', textTransform: 'uppercase' }}>Total points</div>
              <div style={{ fontSize: 18, fontWeight: 600 }}>{job.totalPredictions}</div>
            </div>
          </div>
        )}

        {readyToAdd && (
          <div className="card" style={{ padding: 16, marginBottom: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div>
                <div style={{ fontWeight: 600, marginBottom: 2 }}>All points reviewed</div>
                <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                  Add these images and their final labels to the source as a new dataset version.
                </div>
              </div>
              <button className="btn btn-primary" onClick={() => setAddOpen(true)}>
                Add to Source
              </button>
            </div>
          </div>
        )}

        {addOpen && (
          <div className="modal-overlay active">
            <div className="modal">
              <div className="modal-header">
                <div>
                  <div className="modal-title">Add to Source</div>
                  <div className="modal-subtitle">
                    Add these images and their final labels to the source as a new dataset version.
                  </div>
                </div>
                <button className="modal-close" onClick={() => setAddOpen(false)} aria-label="Close">
                  <XMarkIcon style={{ width: 16, height: 16 }} />
                </button>
              </div>
              <div className="modal-body">
                <div className="form-row">
                  <label className="form-label" htmlFor="addVersionTag">
                    Version tag
                  </label>
                  <input
                    id="addVersionTag"
                    className="form-input"
                    placeholder="e.g. v2.0"
                    value={versionTag}
                    onChange={(e) => setVersionTag(e.target.value)}
                  />
                </div>
                <div className="form-row" style={{ marginBottom: 0 }}>
                  <label className="form-label" htmlFor="addNotes">
                    Notes
                  </label>
                  <textarea
                    id="addNotes"
                    className="form-textarea"
                    placeholder="Optional description"
                    value={addNotes}
                    onChange={(e) => setAddNotes(e.target.value)}
                  />
                </div>
                {addError && (
                  <div className="alert alert-error" style={{ marginTop: 16 }}>
                    {addError}
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setAddOpen(false)}>
                  Cancel
                </button>
                <button className="btn btn-primary" disabled={adding || !versionTag.trim()} onClick={handleAddToProject}>
                  {adding ? 'Creating version…' : 'Create Dataset Version'}
                </button>
              </div>
            </div>
          </div>
        )}

        {job.addedToProjectVersionId && (
          <div className="impact-box safe" style={{ marginBottom: 16 }}>
            <span><CheckIcon style={{ width: 16, height: 16 }} /></span>
            <span>
              Added to the source as dataset version #{job.addedToProjectVersionId}.
            </span>
          </div>
        )}

        {error && (
          <div className="alert alert-error" style={{ marginBottom: 16 }}>
            {error}
          </div>
        )}

        {predictions.length === 0 ? (
          <div className="card">
            <div className="empty-state">
              <p>No predictions found for this job yet.</p>
            </div>
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: 16 }}>
            <div className="inset-container" style={{ maxHeight: '70vh', overflowY: 'auto' }}>
            <div className="card" style={{ padding: 0 }}>
              {imageIds.map((imgId) => {
                const imgPredictions = predictionsByImage.get(imgId) ?? []
                let dotColor: string
                if (isValidation) {
                  const correct = imgPredictions.filter((p) => p.agreesWithHuman).length
                  dotColor = correct === imgPredictions.length ? '#16A34A' : correct === 0 ? '#DC2626' : '#D97706'
                } else {
                  const reviewed = imgPredictions.filter((p) => p.accepted !== null).length
                  dotColor = reviewed === 0 ? 'var(--text-muted)' : reviewed < imgPredictions.length ? '#D97706' : '#16A34A'
                }
                return (
                  <div
                    key={imgId}
                    className={`queue-list-item${imgId === selectedImageId ? ' selected' : ''}`}
                    onClick={() => {
                      setSelectedImageId(imgId)
                      setSelectedPredictionId(null)
                      setCanvasNaturalSize(null)
                    }}
                  >
                    <span className="queue-list-item-thumb">
                      <img src={imageFileUrlById(imgId)} alt="" loading="lazy" />
                      <span className="queue-dot" style={{ background: dotColor }} />
                    </span>
                    <span className="queue-list-item-text">
                      <span className="queue-list-item-filename">{imgPredictions[0]?.filename ?? `image ${imgId}`}</span>
                    </span>
                  </div>
                )
              })}
            </div>
            </div>

            <div className="review-layout">
              <div>
                <div style={{ display: 'flex', gap: 16, marginBottom: 8, alignItems: 'center', justifyContent: 'flex-end' }}>
                  <label className="toggle-checkbox">
                    <input type="checkbox" checked={dotsVisible} onChange={(e) => setDotsVisible(e.target.checked)} />
                    Points
                  </label>
                  <label className="toggle-checkbox">
                    <input type="checkbox" checked={labelsVisible} onChange={(e) => setLabelsVisible(e.target.checked)} />
                    Labels
                  </label>
                </div>
                <div className="image-canvas">
                  {selectedImageId != null ? (
                    <TransformWrapper key={selectedImageId} ref={transformRef} initialScale={1} minScale={1} maxScale={4} centerOnInit>
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
                          {dotsVisible && currentImagePredictions.map((p) => {
                            const hasPosition = p.rowPosition != null && p.colPosition != null && canvasNaturalSize
                            const top = hasPosition ? `${((p.rowPosition as number) / canvasNaturalSize!.height) * 100}%` : '50%'
                            const left = hasPosition ? `${((p.colPosition as number) / canvasNaturalSize!.width) * 100}%` : '50%'
                            return (
                              <div
                                key={p.predictionId}
                                id={`review-dot-${p.predictionId}`}
                                className={`annotation-dot ${dotClassFor(job, p, p.predictionId === effectiveSelectedId)}`}
                                style={{ top, left }}
                                title={`${p.predictedLabelCode} (conf ${p.confidenceScore.toFixed(4)})`}
                                onClick={() => setSelectedPredictionId(p.predictionId)}
                              >
                                {p.pointIndex}
                                {labelsVisible && (
                                  <span
                                    className="mono"
                                    style={{
                                      position: 'absolute',
                                      top: -22,
                                      left: '50%',
                                      transform: 'translateX(-50%)',
                                      whiteSpace: 'nowrap',
                                      fontSize: 10,
                                      padding: '1px 5px',
                                    }}
                                  >
                                    {p.accepted === false ? p.correctedLabelCode : p.predictedLabelCode}
                                  </span>
                                )}
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
                  {isValidation ? (
                    <>
                      <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#16A34A', display: 'inline-block' }} />
                        Agrees with human label
                      </span>
                      <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#D97706', display: 'inline-block' }} />
                        Disagrees
                      </span>
                    </>
                  ) : (
                    <>
                      <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#16A34A', display: 'inline-block' }} />
                        Accepted
                      </span>
                      <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#2563EB', display: 'inline-block' }} />
                        Pending
                      </span>
                      <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#D97706', display: 'inline-block' }} />
                        Overridden
                      </span>
                    </>
                  )}
                </div>
              </div>

              <div className="inset-container" style={{ maxHeight: '70vh', overflowY: 'auto' }}>
                <div className="review-panel" style={{ maxHeight: 'none', overflowY: 'visible' }}>
                  {currentImagePredictions.map((prediction) => (
                    <PredictionCard
                      key={prediction.predictionId}
                      prediction={prediction}
                      isValidation={isValidation}
                      selected={prediction.predictionId === effectiveSelectedId}
                      labelOptions={labelOptions}
                      onSelect={() => setSelectedPredictionId(prediction.predictionId)}
                      onAccept={acceptPrediction}
                      onOverride={overridePrediction}
                    />
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </>
  )
}

function PredictionCard({
  prediction,
  isValidation,
  selected,
  labelOptions,
  onSelect,
  onAccept,
  onOverride,
}: {
  prediction: AnnotationPrediction
  isValidation: boolean
  selected: boolean
  labelOptions: LabelDefinition[]
  onSelect: () => void
  onAccept: (prediction: AnnotationPrediction) => void
  onOverride: (prediction: AnnotationPrediction, labelCode: string) => void
}) {
  const [overrideOpen, setOverrideOpen] = useState(false)
  const [picked, setPicked] = useState('')
  const canReview = !isValidation && prediction.accepted === null
  const editing = canReview && overrideOpen

  return (
    <div id={`review-card-${prediction.predictionId}`} className={`review-point-card${selected ? ' selected' : ''}`} onClick={onSelect}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <span style={{ fontSize: 12, fontWeight: 600 }}>Point #{prediction.pointIndex}</span>
        {isValidation ? (
          <span className={`badge ${prediction.agreesWithHuman ? 'badge-green' : 'badge-red'}`}>
            {prediction.agreesWithHuman ? 'Agrees' : 'Disagrees'}
          </span>
        ) : prediction.accepted !== null ? (
          <span className="badge badge-green">
            {prediction.accepted ? prediction.predictedLabelCode : prediction.correctedLabelCode}
          </span>
        ) : (
          <span className="badge" style={{ background: 'var(--coral, #e05d44)', color: '#fff' }}>
            {prediction.predictedLabelCode}
          </span>
        )}
      </div>

      <div onClick={(e) => canReview && e.stopPropagation()}>
        <LabelCombobox
          options={labelOptions}
          value={editing ? picked : ''}
          onChange={editing ? setPicked : () => {}}
          disabled={!editing}
          placeholder={
            editing
              ? 'Search labels…'
              : `Predicted: ${prediction.predictedLabelCode} (conf ${prediction.confidenceScore.toFixed(4)})`
          }
        />
      </div>

      {isValidation ? (
        <div style={{ fontSize: 12, color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: 4, marginTop: 8 }}>
          {prediction.agreesWithHuman ? (
            <>
              <CheckIcon style={{ width: 12, height: 12 }} /> Matches human label
            </>
          ) : (
            <>
              <XMarkIcon style={{ width: 12, height: 12 }} /> Does not match human label
            </>
          )}
        </div>
      ) : prediction.accepted !== null ? (
        <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 8 }}>
          {prediction.accepted ? 'Accepted' : `Overridden: ${prediction.correctedLabelCode}`}
          {prediction.reviewedBy ? ` by ${prediction.reviewedBy}` : ''}
        </div>
      ) : (
        <div onClick={(e) => e.stopPropagation()} style={{ marginTop: 12, display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          {editing ? (
            <>
              <button
                className="btn btn-secondary"
                onClick={() => {
                  setOverrideOpen(false)
                  setPicked('')
                }}
              >
                Cancel
              </button>
              <button
                className="btn btn-accept"
                disabled={!picked}
                onClick={() => {
                  onOverride(prediction, picked)
                  setOverrideOpen(false)
                }}
              >
                Confirm Override
              </button>
            </>
          ) : (
            <>
              <button className="btn btn-secondary" onClick={() => setOverrideOpen(true)}>
                Override
              </button>
              <button className="btn btn-accept" onClick={() => onAccept(prediction)}>
                Accept
              </button>
            </>
          )}
        </div>
      )}
    </div>
  )
}
