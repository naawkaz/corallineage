import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeftIcon } from '@heroicons/react/24/outline'
import { TransformComponent, TransformWrapper } from 'react-zoom-pan-pinch'
import type { ReactZoomPanPinchContentRef } from 'react-zoom-pan-pinch'
import { fetchDirtyAnnotations, fetchOntologyChanges, resolveDirtyAnnotation } from '../ontology/ontologyApi'
import { fetchVersion } from '../datasets/versionsApi'
import { imageFileUrlById } from '../datasets/imagesApi'
import { errorMessage } from '../../shared/api/client'
import { changeTypeLabel } from '../../shared/constants'
import { useAuthContext } from '../../shared/lib/authContextCore'
import LabelCombobox from '../../shared/components/LabelCombobox'
import { formatDate } from '../../shared/lib/format'
import type { DirtyAnnotation, OntologyChange } from '../ontology/ontologyTypes'
import type { LabelDefinition } from '../datasets/versionsTypes'

function dirtyQueueDotColor(points: DirtyAnnotation[]): string {
  if (points.length === 0) return 'var(--text-muted)'
  const resolved = points.filter((p) => p.resolved).length
  if (resolved === 0) return '#2563EB'
  if (resolved < points.length) return '#D97706'
  return '#16A34A'
}

export default function OntologyChangeReview() {
  const { sourceId: sourceIdParam, changeId: changeIdParam } = useParams()
  const sourceId = Number(sourceIdParam)
  const changeId = Number(changeIdParam)
  const navigate = useNavigate()
  const { user } = useAuthContext()

  const [change, setChange] = useState<OntologyChange | null>(null)
  const [changeLoading, setChangeLoading] = useState(true)
  const [changeError, setChangeError] = useState<string | null>(null)

  const [dirtyAnnotations, setDirtyAnnotations] = useState<DirtyAnnotation[]>([])
  const [dirtyLoading, setDirtyLoading] = useState(true)
  const [dirtyError, setDirtyError] = useState<string | null>(null)
  const [labelOptions, setLabelOptions] = useState<LabelDefinition[]>([])

  const [selectedDirtyImageId, setSelectedDirtyImageId] = useState<number | null>(null)
  const [selectedDirtyPointId, setSelectedDirtyPointId] = useState<number | null>(null)
  const [canvasNaturalSize, setCanvasNaturalSize] = useState<{ width: number; height: number } | null>(null)
  const [dotsVisible, setDotsVisible] = useState(true)
  const transformRef = useRef<ReactZoomPanPinchContentRef>(null)

  function loadDirty() {
    fetchDirtyAnnotations(0, 500, changeId)
      .then((res) => {
        setDirtyAnnotations(res.content)
        setDirtyError(null)
      })
      .catch((err) => setDirtyError(errorMessage(err)))
      .finally(() => setDirtyLoading(false))
  }

  function loadChange() {
    fetchOntologyChanges(sourceId, 0, 50)
      .then((res) => {
        const found = res.content.find((c) => c.id === changeId) ?? null
        setChange(found)
        setChangeError(null)
        if (found) {
          fetchVersion(sourceId, found.affectedDatasetVersionId)
            .then((v) => setLabelOptions((v.labelDefinitions ?? []).filter((l) => !l.deprecated)))
            .catch(() => setLabelOptions([]))
        }
      })
      .catch((err) => setChangeError(errorMessage(err)))
      .finally(() => setChangeLoading(false))
  }

  useEffect(() => {
    loadChange()
    loadDirty()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceId, changeId])

  const dirtyImageIds = useMemo(() => {
    const seen = new Set<number>()
    const order: number[] = []
    for (const d of dirtyAnnotations) {
      if (!seen.has(d.imageId)) {
        seen.add(d.imageId)
        order.push(d.imageId)
      }
    }
    return order
  }, [dirtyAnnotations])

  const dirtyByImage = useMemo(() => {
    const map = new Map<number, DirtyAnnotation[]>()
    for (const d of dirtyAnnotations) {
      const list = map.get(d.imageId) ?? []
      list.push(d)
      map.set(d.imageId, list)
    }
    return map
  }, [dirtyAnnotations])

  const dirtyKey = dirtyImageIds.join(',')
  const [syncedDirtyKey, setSyncedDirtyKey] = useState('')
  if (dirtyKey !== syncedDirtyKey) {
    setSyncedDirtyKey(dirtyKey)
    setSelectedDirtyImageId((prev) => (prev != null && dirtyImageIds.includes(prev) ? prev : dirtyImageIds[0] ?? null))
    setCanvasNaturalSize(null)
  }

  const currentImagePoints = selectedDirtyImageId != null ? dirtyByImage.get(selectedDirtyImageId) ?? [] : []
  const currentFilename = currentImagePoints[0]?.filename ?? ''
  const effectiveSelectedPointId = selectedDirtyPointId ?? currentImagePoints[0]?.id ?? null

  useEffect(() => {
    if (effectiveSelectedPointId == null) return
    document.getElementById(`dirty-card-${effectiveSelectedPointId}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    if (!transformRef.current || !canvasNaturalSize) return
    const dotEl = document.getElementById(`dirty-dot-${effectiveSelectedPointId}`)
    if (!dotEl) return
    transformRef.current.zoomToElement(dotEl, transformRef.current.state.scale)
  }, [effectiveSelectedPointId, canvasNaturalSize])

  async function handleResolve(dirty: DirtyAnnotation, newLabelCode: string) {
    try {
      await resolveDirtyAnnotation(dirty.id, { newLabelCode, resolvedBy: user?.username ?? '' })
      loadDirty()
      loadChange()
    } catch (err) {
      setDirtyError(errorMessage(err))
    }
  }

  const resolvedCount = dirtyAnnotations.filter((d) => d.resolved).length
  const totalCount = change?.dirtyAnnotationsFlagged ?? dirtyAnnotations.length
  const progressPct = totalCount > 0 ? (resolvedCount / totalCount) * 100 : 0
  const hasUnresolved = !!change && change.dirtyAnnotationsFlagged - change.resolvedCount > 0

  const allowedResolveLabels = useMemo(() => {
    if (!change) return labelOptions
    const allowedCodes = new Set<string>(change.newLabelCodes ?? [])
    if (change.replacementLabelCode) allowedCodes.add(change.replacementLabelCode)
    const filtered = labelOptions.filter((l) => allowedCodes.has(l.labelCode))
    return filtered.length > 0 ? filtered : labelOptions
  }, [change, labelOptions])

  return (
    <div className="content">
      <div className="page-back-row">
        <button className="btn btn-secondary" onClick={() => navigate(-1)} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
          <ArrowLeftIcon style={{ width: 14, height: 14 }} />
          Back
        </button>
      </div>

      <div className="page-title-row" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 10 }}>
        <div className="page-title">
          {changeLoading || !change
            ? 'Ontology Change'
            : `${changeTypeLabel(change.changeType)}: ${change.oldLabelCode ?? '(none)'} -> ${change.newLabelCodes?.join(', ') || (change.replacementLabelCode ? `(replacement: ${change.replacementLabelCode})` : '(none)')}`}
        </div>
        {change && (
          <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
            Changed by {change.changedBy} on {formatDate(change.changedAt)}
          </div>
        )}
      </div>

      {changeError && (
        <div className="alert alert-error" style={{ marginBottom: 16 }}>
          {changeError}
        </div>
      )}

      {change && (
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
            {resolvedCount} / {totalCount} resolved
          </span>
        </div>
      )}
      <div style={{ height: 4, background: 'var(--border)', margin: '4px 0 16px' }}>
        {hasUnresolved && (
          <div style={{ height: 4, width: `${progressPct}%`, background: '#0d9488', transition: 'width 0.2s' }} />
        )}
      </div>

      {dirtyError && (
        <div className="alert alert-error" style={{ marginBottom: 16 }}>
          {dirtyError}
        </div>
      )}

      {dirtyLoading ? (
        <div className="card">
          <div className="loading-state">
            <span className="spinner" /> Loading dirty annotations...
          </div>
        </div>
      ) : dirtyAnnotations.length === 0 ? (
        <div className="card">
          <div className="empty-state">
            <p>No dirty annotations for this change.</p>
          </div>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: 16 }}>
          {/* Left: image queue */}
          <div className="inset-container" style={{ maxHeight: '70vh', overflowY: 'auto' }}>
          <div className="card" style={{ padding: 0 }}>
            {dirtyImageIds.map((imgId) => {
              const points = dirtyByImage.get(imgId) ?? []
              const resolved = points.filter((p) => p.resolved).length
              return (
                <div
                  key={imgId}
                  className={`queue-list-item${imgId === selectedDirtyImageId ? ' selected' : ''}`}
                  onClick={() => {
                    setSelectedDirtyImageId(imgId)
                    setSelectedDirtyPointId(null)
                    setCanvasNaturalSize(null)
                  }}
                >
                  <span className="queue-list-item-thumb">
                    <img src={imageFileUrlById(imgId)} alt="" loading="lazy" />
                    <span className="queue-dot" style={{ background: dirtyQueueDotColor(points) }} />
                  </span>
                  <span className="queue-list-item-text">
                    <span className="queue-list-item-filename">{points[0]?.filename ?? `image ${imgId}`}</span>
                    <span className="queue-list-item-progress">
                      {resolved}/{points.length} resolved
                    </span>
                  </span>
                </div>
              )
            })}
          </div>
          </div>

          <div className="review-layout">
            {/* Center: image canvas + point overlays */}
            <div>
              <div style={{ display: 'flex', gap: 16, marginBottom: 8, alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', gap: 16, fontSize: 11, color: 'var(--text-secondary)' }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#16A34A', display: 'inline-block' }} />
                    Resolved
                  </span>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#2563EB', display: 'inline-block' }} />
                    Pending
                  </span>
                </div>
                <label className="toggle-checkbox">
                  <input type="checkbox" checked={dotsVisible} onChange={(e) => setDotsVisible(e.target.checked)} />
                  Points
                </label>
              </div>
              <div className="image-canvas">
                {selectedDirtyImageId != null ? (
                  <TransformWrapper
                    key={selectedDirtyImageId}
                    ref={transformRef}
                    initialScale={1}
                    minScale={1}
                    maxScale={4}
                    centerOnInit
                  >
                    <TransformComponent wrapperStyle={{ width: '100%', height: '100%' }} contentStyle={{ width: '100%', height: '100%' }}>
                      <div style={{ position: 'relative', width: '100%', height: '100%' }}>
                        <img
                          src={imageFileUrlById(selectedDirtyImageId)}
                          alt={currentFilename}
                          style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
                          onLoad={(e) => {
                            const img = e.currentTarget
                            setCanvasNaturalSize({ width: img.naturalWidth, height: img.naturalHeight })
                          }}
                        />
                        {dotsVisible &&
                          canvasNaturalSize &&
                          currentImagePoints.map((p) => {
                            if (p.rowPosition == null || p.colPosition == null) return null
                            const dotClass =
                              p.id === effectiveSelectedPointId
                                ? 'dot-selected'
                                : p.resolved
                                  ? 'dot-accepted'
                                  : 'dot-pending'
                            return (
                              <div
                                key={p.id}
                                id={`dirty-dot-${p.id}`}
                                className={`annotation-dot ${dotClass}`}
                                style={{
                                  top: `${(p.rowPosition / canvasNaturalSize.height) * 100}%`,
                                  left: `${(p.colPosition / canvasNaturalSize.width) * 100}%`,
                                }}
                                title={`${p.currentLabelCode}${p.resolved ? ` -> ${p.newLabelCode}` : ''}`}
                                onClick={() => setSelectedDirtyPointId(p.id)}
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

              <div className="card" style={{ marginTop: 10, padding: 0 }}>
                <div
                  style={{
                    display: 'flex',
                    fontSize: 11,
                    fontWeight: 600,
                    color: 'var(--text-secondary)',
                    padding: '8px 12px',
                    borderBottom: '1px solid var(--border)',
                  }}
                >
                  <span style={{ width: 120 }}>Code</span>
                  <span style={{ flex: 1 }}>Name</span>
                  <span style={{ width: 120 }}>Category</span>
                </div>
                <div style={{ maxHeight: 220, overflowY: 'auto' }}>
                  {labelOptions.length === 0 ? (
                    <div style={{ padding: 12, fontSize: 12, color: 'var(--text-secondary)' }}>
                      No labels declared on this dataset version yet.
                    </div>
                  ) : (
                    labelOptions.map((label) => (
                      <div
                        key={label.labelCode}
                        style={{ display: 'flex', alignItems: 'center', padding: '6px 12px', borderBottom: '1px solid var(--border)' }}
                      >
                        <span style={{ width: 120, fontSize: 11 }}>{label.labelCode}</span>
                        <span style={{ flex: 1, fontSize: 12 }}>{label.labelName}</span>
                        <span style={{ width: 120 }}>{label.labelCategory && <span className="tag">{label.labelCategory}</span>}</span>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>

            {/* Right: one card per dirty point on the selected image */}
            <div style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden', minHeight: 0 }}>
              <div className="inset-container" style={{ maxHeight: '70vh', overflowY: 'auto' }}>
                <div className="review-panel" style={{ maxHeight: 'none', overflowY: 'visible' }}>
                  {currentImagePoints.map((dirty) => (
                    <DirtyPointCard
                      key={dirty.id}
                      dirty={dirty}
                      selected={dirty.id === effectiveSelectedPointId}
                      labelOptions={allowedResolveLabels}
                      onSelect={() => setSelectedDirtyPointId(dirty.id)}
                      onResolve={handleResolve}
                    />
                  ))}
                </div>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 10 }}>
                <button
                  className="btn btn-accept"
                  disabled={hasUnresolved}
                  onClick={() => navigate(`/sources/${sourceId}/ontology`)}
                >
                  Confirm Changes
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function DirtyPointCard({
  dirty,
  selected,
  labelOptions,
  onSelect,
  onResolve,
}: {
  dirty: DirtyAnnotation
  selected: boolean
  labelOptions: LabelDefinition[]
  onSelect: () => void
  onResolve: (dirty: DirtyAnnotation, newLabelCode: string) => void
}) {
  // The dropdown alone is the confirm action (no separate Resolve button), matching
  // AnnotationReview.tsx's PredictionCard: picking an option immediately resolves this point.
  const canResolve = !dirty.resolved
  const currentValue = dirty.resolved ? dirty.newLabelCode ?? '' : ''

  return (
    <div id={`dirty-card-${dirty.id}`} className={`review-point-card${selected ? ' selected' : ''}`} onClick={onSelect}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <span style={{ fontSize: 12, fontWeight: 600 }}>Point #{dirty.pointIndex}</span>
        {dirty.resolved ? (
          <span className="badge badge-green">Resolved</span>
        ) : (
          <span className="badge" style={{ background: 'var(--coral, #e05d44)', color: '#fff' }}>
            {dirty.currentLabelCode}
          </span>
        )}
      </div>
      <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 8 }}>
        Flagged {formatDate(dirty.flaggedAt)}
      </div>

      <div onClick={(e) => canResolve && e.stopPropagation()}>
        <LabelCombobox
          options={labelOptions}
          value={currentValue}
          onChange={canResolve ? (labelCode) => onResolve(dirty, labelCode) : () => {}}
          disabled={!canResolve}
          placeholder={canResolve ? 'Search allowed labels...' : undefined}
        />
      </div>

      {dirty.resolved && (
        <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 8 }}>
          {dirty.currentLabelCode} -> {dirty.newLabelCode}
          {dirty.resolvedBy ? ` by ${dirty.resolvedBy}` : ''}
        </div>
      )}
    </div>
  )
}
