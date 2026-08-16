import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { TransformComponent, TransformWrapper } from 'react-zoom-pan-pinch'
import type { ReactZoomPanPinchContentRef } from 'react-zoom-pan-pinch'
import { Check } from 'lucide-react'
import { fetchGeneratedPoints, fetchPointAnnotationJob, reviewGeneratedPoint } from '../api/pointAnnotation'
import { fetchVersion } from '../api/versions'
import { imageFileUrlById } from '../api/images'
import { errorMessage } from '../api/client'
import { useAuthContext } from '../lib/authContextCore'
import LabelCombobox from '../components/LabelCombobox'
import type { GeneratedPoint, PointAnnotationJob } from '../types/pointAnnotation'
import type { LabelDefinition } from '../types/version'

export default function PointAnnotationReview() {
  const { id, sourceId: sourceIdParam } = useParams<{ id: string; sourceId: string }>()
  const jobId = Number(id)
  const sourceId = Number(sourceIdParam)
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

  const loadPoints = useCallback(() => {
    return fetchGeneratedPoints(jobId, false, 0, 5000).then((res) => setPoints(res.content))
  }, [jobId])

  useEffect(() => {
    Promise.all([fetchPointAnnotationJob(jobId), loadPoints()])
      .then(([jobRes]) => {
        setJob(jobRes)
        setError(null)
        return fetchVersion(sourceId, jobRes.datasetVersionId)
      })
      .then((version) => setLabelOptions((version.labelDefinitions ?? []).filter((l) => !l.deprecated)))
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [jobId, sourceId, loadPoints])

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
    document.getElementById(`review-card-${effectiveSelectedPointId}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    if (!transformRef.current || !canvasNaturalSize) return
    const dotEl = document.getElementById(`review-dot-${effectiveSelectedPointId}`)
    if (!dotEl) return
    transformRef.current.zoomToElement(dotEl, transformRef.current.state.scale)
  }, [effectiveSelectedPointId, canvasNaturalSize])

  async function acceptPoint(point: GeneratedPoint) {
    try {
      await reviewGeneratedPoint(point.id, { accepted: true, reviewedBy: user?.username ?? '' })
      await loadPoints()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  async function overridePoint(point: GeneratedPoint, labelCode: string) {
    try {
      await reviewGeneratedPoint(point.id, { accepted: false, correctedLabelCode: labelCode, reviewedBy: user?.username ?? '' })
      await loadPoints()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  if (loading) {
    return (
      <div className="content">
        <div className="loading-state">
          <span className="spinner" /> Loading review queue…
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

  const confirmedCount = points.filter((p) => p.status === 'CONFIRMED').length
  const totalCount = points.length
  const pendingCount = points.filter((p) => p.status === 'MODEL_PREDICTED').length
  const progressPct = totalCount > 0 ? (confirmedCount / totalCount) * 100 : 0

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title">Point Annotation Review — Job #{job.jobId}</div>
          <div className="topbar-sub">
            {confirmedCount} / {totalCount} points confirmed — model {job.modelTag}, threshold{' '}
            {Math.round(job.confidenceThreshold * 100)}%
          </div>
        </div>
        <button className="btn btn-secondary" onClick={() => navigate(`/sources/${sourceId}/point-annotation`)}>
          Back
        </button>
      </div>

      <div style={{ height: 4, background: 'var(--border)', margin: '0 24px' }}>
        <div style={{ height: 4, width: `${progressPct}%`, background: '#0d9488', transition: 'width 0.2s' }} />
      </div>

      <div className="content">
        {pendingCount === 0 && totalCount > 0 && (
          <div className="impact-box" style={{ marginBottom: 16 }}>
            <span><Check size={16} strokeWidth={1.5} /></span>
            <span>All points reviewed — this point-annotation job is complete.</span>
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
              <p>No auto-annotated points found for this job yet.</p>
            </div>
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: 16 }}>
            <div className="card" style={{ padding: 0, maxHeight: '70vh', overflowY: 'auto' }}>
              {imageIds.map((imgId) => {
                const imgPoints = pointsByImage.get(imgId) ?? []
                const confirmed = imgPoints.filter((p) => p.status === 'CONFIRMED').length
                const dotColor =
                  confirmed === 0 ? 'var(--text-muted)' : confirmed < imgPoints.length ? '#D97706' : '#16A34A'
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
                                  : p.status === 'CONFIRMED'
                                    ? 'dot-accepted'
                                    : 'dot-pending'
                              return (
                                <div
                                  key={p.id}
                                  id={`review-dot-${p.id}`}
                                  className={`annotation-dot ${dotClass}`}
                                  style={{
                                    top: `${(p.rowPosition / canvasNaturalSize.height) * 100}%`,
                                    left: `${(p.colPosition / canvasNaturalSize.width) * 100}%`,
                                  }}
                                  title={`${p.predictedLabelCode ?? '?'} — conf ${p.confidenceScore?.toFixed(4) ?? 'n/a'}`}
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
                    Confirmed
                  </span>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#2563EB', display: 'inline-block' }} />
                    Pending review
                  </span>
                </div>
              </div>

              <div className="review-panel">
                {currentImagePoints.map((point) => (
                  <ReviewPointCard
                    key={point.id}
                    point={point}
                    selected={point.id === effectiveSelectedPointId}
                    labelOptions={labelOptions}
                    onSelect={() => setSelectedPointId(point.id)}
                    onAccept={acceptPoint}
                    onOverride={overridePoint}
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

function ReviewPointCard({
  point,
  selected,
  labelOptions,
  onSelect,
  onAccept,
  onOverride,
}: {
  point: GeneratedPoint
  selected: boolean
  labelOptions: LabelDefinition[]
  onSelect: () => void
  onAccept: (point: GeneratedPoint) => void
  onOverride: (point: GeneratedPoint, labelCode: string) => void
}) {
  const [overrideOpen, setOverrideOpen] = useState(false)
  const [picked, setPicked] = useState('')

  return (
    <div id={`review-card-${point.id}`} className={`review-point-card${selected ? ' selected' : ''}`} onClick={onSelect}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <span style={{ fontSize: 12, fontWeight: 600 }}>Point #{point.pointIndex}</span>
        {point.status === 'CONFIRMED' ? (
          <span className="badge badge-green">{point.finalLabelCode}</span>
        ) : (
          <span className="badge" style={{ background: 'var(--coral, #e05d44)', color: '#fff' }}>
            {point.predictedLabelCode}
          </span>
        )}
      </div>

      {point.status === 'CONFIRMED' ? (
        <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
          Confirmed{point.confidenceScore != null ? ` — conf ${point.confidenceScore.toFixed(4)}` : ''}
        </div>
      ) : (
        <div onClick={(e) => e.stopPropagation()}>
          <div className="mono" style={{ fontSize: 13, marginBottom: 8 }}>
            Predicted: {point.predictedLabelCode} (conf {point.confidenceScore?.toFixed(4) ?? 'n/a'})
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              className="btn btn-primary"
              style={{ color: 'var(--success)', borderColor: '#86EFAC' }}
              onClick={() => onAccept(point)}
            >
              Accept
            </button>
            <button className="btn btn-secondary" onClick={() => setOverrideOpen((v) => !v)}>
              Override ▾
            </button>
          </div>
          {overrideOpen && (
            <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 8 }}>
              <LabelCombobox options={labelOptions} value={picked} onChange={setPicked} placeholder="Search labels…" />
              <button
                className="btn btn-secondary"
                disabled={!picked}
                onClick={() => {
                  onOverride(point, picked)
                  setOverrideOpen(false)
                }}
              >
                Confirm Override
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
