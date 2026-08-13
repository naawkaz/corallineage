import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { fetchAnnotationJob, fetchPredictions, reviewPrediction } from '../api/annotations'
import { imageFileUrlById } from '../api/images'
import { fetchModel } from '../api/models'
import { fetchVersion } from '../api/versions'
import { errorMessage } from '../api/client'
import { CURRENT_USER } from '../constants'
import type { AnnotationJob, AnnotationPrediction } from '../types/annotation'
import type { LabelDefinition } from '../types/version'

function dotColor(predictions: AnnotationPrediction[]): string {
  if (predictions.length === 0) return 'var(--text-muted)'
  const reviewed = predictions.filter((p) => p.accepted !== null).length
  if (reviewed === 0) return 'var(--text-muted)'
  if (reviewed < predictions.length) return 'var(--amber, #d97706)'
  return 'var(--teal, #0d9488)'
}

function dotStatusClass(prediction: AnnotationPrediction): string {
  if (prediction.accepted === null) return 'dot-pending'
  return prediction.accepted ? 'dot-accepted' : 'dot-corrected'
}

export default function AnnotationReview() {
  const { id, projectId: projectIdParam } = useParams<{ id: string; projectId: string }>()
  const jobId = Number(id)
  const projectId = Number(projectIdParam)
  const navigate = useNavigate()

  const [job, setJob] = useState<AnnotationJob | null>(null)
  const [predictions, setPredictions] = useState<AnnotationPrediction[]>([])
  const [labelOptions, setLabelOptions] = useState<LabelDefinition[]>([])
  const [selectedImageId, setSelectedImageId] = useState<number | null>(null)
  const [overrideOpen, setOverrideOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadAll = useCallback(() => {
    Promise.all([fetchAnnotationJob(jobId), fetchPredictions(jobId, 0, 1000)])
      .then(([jobRes, predictionsRes]) => {
        setJob(jobRes)
        setPredictions(predictionsRes.content)
        setError(null)
        setSelectedImageId((prev) => prev ?? predictionsRes.content[0]?.imageId ?? null)
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [jobId])

  useEffect(() => {
    loadAll()
  }, [loadAll])

  useEffect(() => {
    if (!job) return
    fetchModel(job.modelVersionId)
      .then((model) => {
        const versionId = model.trainedOnDatasetVersion?.id
        if (!versionId) return
        return fetchVersion(projectId, versionId)
      })
      .then((version) => setLabelOptions(version?.labelDefinitions ?? []))
      .catch(() => setLabelOptions([]))
  }, [job, projectId])

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

  const currentImagePredictions = selectedImageId != null ? predictionsByImage.get(selectedImageId) ?? [] : []
  const currentFilename = currentImagePredictions[0]?.filename ?? ''
  const currentPrediction = currentImagePredictions[0] ?? null

  async function acceptPrediction(prediction: AnnotationPrediction) {
    try {
      await reviewPrediction(jobId, prediction.predictionId, { accepted: true, reviewedBy: CURRENT_USER })
      loadAll()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  async function overridePrediction(prediction: AnnotationPrediction, labelCode: string) {
    try {
      await reviewPrediction(jobId, prediction.predictionId, {
        accepted: false,
        correctedLabelCode: labelCode,
        reviewedBy: CURRENT_USER,
      })
      setOverrideOpen(false)
      loadAll()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (!currentPrediction) return
      if (e.key === 'a' || e.key === 'A') {
        acceptPrediction(currentPrediction)
      } else if (e.key === 'o' || e.key === 'O') {
        setOverrideOpen((prev) => !prev)
      } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault()
        const idx = selectedImageId != null ? imageIds.indexOf(selectedImageId) : -1
        const delta = e.key === 'ArrowDown' ? 1 : -1
        const nextIdx = Math.min(Math.max(idx + delta, 0), imageIds.length - 1)
        setSelectedImageId(imageIds[nextIdx]);
        setOverrideOpen(false)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentPrediction, selectedImageId, imageIds])

  if (loading) {
    return (
      <div className="content">
        <div className="loading-state">
          <span className="spinner" /> Loading review…
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

  const progressPct = job.totalPredictions > 0 ? (job.reviewedPredictions / job.totalPredictions) * 100 : 0

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title">Review Job #{job.jobId}</div>
          <div className="topbar-sub">
            {job.reviewedPredictions} / {job.totalPredictions} reviewed
          </div>
        </div>
        <button className="btn btn-secondary" onClick={() => navigate(`/projects/${projectId}/annotation`)}>
          Back to jobs
        </button>
      </div>

      <div style={{ height: 4, background: 'var(--border)', margin: '0 24px' }}>
        <div style={{ height: 4, width: `${progressPct}%`, background: '#0d9488', transition: 'width 0.2s' }} />
      </div>

      <div className="content" style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: 16 }}>
        {/* Left panel: image list */}
        <div className="card" style={{ padding: 0, maxHeight: '70vh', overflowY: 'auto' }}>
          {imageIds.map((imgId) => {
            const preds = predictionsByImage.get(imgId) ?? []
            return (
              <div
                key={imgId}
                onClick={() => {
                  setSelectedImageId(imgId)
                  setOverrideOpen(false)
                }}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '8px 12px',
                  cursor: 'pointer',
                  borderBottom: '1px solid var(--border)',
                  background: imgId === selectedImageId ? 'var(--bg-hover, rgba(13,148,136,0.08))' : 'transparent',
                }}
              >
                <span
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: dotColor(preds),
                    flexShrink: 0,
                  }}
                />
                <span className="mono" style={{ fontSize: 12 }}>{preds[0]?.filename ?? `image ${imgId}`}</span>
              </div>
            )
          })}
        </div>

        <div className="review-layout">
          {/* Center: image canvas + point overlay */}
          <div>
            <div className="image-canvas">
              {selectedImageId != null ? (
                <>
                  <img
                    src={imageFileUrlById(selectedImageId)}
                    alt={currentFilename}
                    style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
                  />
                  {currentPrediction && (
                    <div
                      className={`annotation-dot ${dotStatusClass(currentPrediction)}`}
                      style={{ top: '50%', left: '50%' }}
                      title={`${currentPrediction.predictedLabelCode} — conf ${currentPrediction.confidenceScore.toFixed(4)}`}
                    >
                      {currentPrediction.pointIndex}
                    </div>
                  )}
                </>
              ) : (
                <div className="image-placeholder">No image selected</div>
              )}
            </div>
            <div style={{ display: 'flex', gap: 16, marginTop: 10, fontSize: 11, color: 'var(--text-secondary)' }}>
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
                Corrected
              </span>
            </div>
          </div>

          {/* Right: point detail */}
          <div className="review-panel">
            <div className="review-point-card">
              {currentPrediction ? (
                <>
                  <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 4 }}>
                    Point #{currentPrediction.pointIndex}
                  </div>
                  <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>
                    {currentPrediction.accepted === false
                      ? currentPrediction.correctedLabelCode
                      : currentPrediction.predictedLabelCode}
                  </div>
                  <div className="mono" style={{ fontSize: 13, marginBottom: 4 }}>
                    Conf: {currentPrediction.confidenceScore.toFixed(4)}
                  </div>
                  {currentPrediction.accepted !== null && (
                    <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 8 }}>
                      {currentPrediction.accepted ? 'Accepted' : `Overridden: ${currentPrediction.correctedLabelCode}`}
                      {currentPrediction.reviewedBy ? ` by ${currentPrediction.reviewedBy}` : ''}
                    </div>
                  )}

                  <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
                    <button
                      className="btn btn-primary"
                      style={{ color: 'var(--success)', borderColor: '#86EFAC' }}
                      onClick={() => acceptPrediction(currentPrediction)}
                    >
                      Accept
                    </button>
                    <button className="btn btn-secondary" onClick={() => setOverrideOpen((prev) => !prev)}>
                      Override ▾
                    </button>
                  </div>

                  {overrideOpen && (
                    <div style={{ marginTop: 12 }}>
                      <select
                        className="form-select"
                        onChange={(e) => e.target.value && overridePrediction(currentPrediction, e.target.value)}
                        defaultValue=""
                      >
                        <option value="" disabled>
                          Select label…
                        </option>
                        {labelOptions.map((l) => (
                          <option key={l.labelCode} value={l.labelCode}>
                            {l.labelName} ({l.labelCode})
                          </option>
                        ))}
                      </select>
                    </div>
                  )}

                  <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 24 }}>
                    Shortcuts: A accept · O override · ↑↓ navigate images
                  </div>
                </>
              ) : (
                <div className="empty-state">
                  <p>No point selected.</p>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
