import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowDownTrayIcon, ArrowLeftIcon, ViewfinderCircleIcon, XMarkIcon } from '@heroicons/react/24/outline'
import { TransformComponent, TransformWrapper } from 'react-zoom-pan-pinch'
import { createLabelDefinition, fetchVersion } from '../datasets/versionsApi'
import { errorMessage } from '../../shared/api/client'
import { fetchAnnotationPoints, fetchImages, imageFileUrl, imageFileUrlById } from '../datasets/imagesApi'
import { triggerReasonBadge, triggerReasonLabel } from '../../shared/constants'
import { formatCount, formatDate } from '../../shared/lib/format'
import type { DatasetVersion } from '../datasets/versionsTypes'
import type { AnnotationPointSummary, DatasetImage } from '../datasets/imagesTypes'
import type { LabelDeclaration } from '../datasets/importsTypes'

const IMAGE_PAGE_SIZE = 40

export default function VersionDetail() {
  const { id, sourceId: sourceIdParam } = useParams()
  const navigate = useNavigate()
  const versionId = Number(id)
  const datasetId = Number(sourceIdParam)
  const versionsListPath = `/sources/${sourceIdParam}`
  const [version, setVersion] = useState<DatasetVersion | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [images, setImages] = useState<DatasetImage[]>([])
  const [imagePage, setImagePage] = useState(0)
  const [imageTotalPages, setImageTotalPages] = useState(0)
  const [imagesLoading, setImagesLoading] = useState(true)
  const [imagesError, setImagesError] = useState<string | null>(null)
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null)

  // Points overlay toggle for the image grid. Plain local state, deliberately not persisted
  // anywhere, resets whenever this page unmounts/remounts (e.g. navigating away and back).
  const [pointsToggle, setPointsToggle] = useState(false)
  const [points, setPoints] = useState<AnnotationPointSummary[] | null>(null)
  const [pointsLoading, setPointsLoading] = useState(false)
  const [pointsError, setPointsError] = useState<string | null>(null)

  const [addLabelOpen, setAddLabelOpen] = useState(false)
  const [labelCode, setLabelCode] = useState('')
  const [labelName, setLabelName] = useState('')
  const [labelCategory, setLabelCategory] = useState('')
  const [addLabelSubmitting, setAddLabelSubmitting] = useState(false)
  const [addLabelError, setAddLabelError] = useState<string | null>(null)

  useEffect(() => {
    if (!datasetId) return
    let cancelled = false
    fetchVersion(datasetId, versionId)
      .then((res) => {
        if (!cancelled) {
          setVersion(res)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(errorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [datasetId, versionId])

  useEffect(() => {
    if (!datasetId) return
    let cancelled = false
    fetchImages(datasetId, versionId, imagePage, IMAGE_PAGE_SIZE)
      .then((res) => {
        if (!cancelled) {
          setImages(res.content)
          setImageTotalPages(res.totalPages)
          setImagesError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setImagesError(errorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setImagesLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [datasetId, versionId, imagePage])

  useEffect(() => {
    if (!datasetId || !pointsToggle || !version?.complete || points != null) return
    let cancelled = false
    Promise.resolve().then(() => {
      if (!cancelled) setPointsLoading(true)
    })
    fetchAnnotationPoints(datasetId, versionId)
      .then((res) => {
        if (!cancelled) {
          setPoints(res)
          setPointsError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setPointsError(errorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setPointsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [datasetId, versionId, pointsToggle, version?.complete, points])

  const pointsByImage = useMemo(() => {
    const map = new Map<number, AnnotationPointSummary[]>()
    for (const p of points ?? []) {
      const list = map.get(p.imageId) ?? []
      list.push(p)
      map.set(p.imageId, list)
    }
    return map
  }, [points])

  const overlayActive = pointsToggle && Boolean(version?.complete)

  function handleExportLabelSet() {
    if (!version?.labelDefinitions) return
    const labels: LabelDeclaration[] = version.labelDefinitions.map((l) => ({
      labelCode: l.labelCode,
      labelName: l.labelName,
      labelCategory: l.labelCategory ?? undefined,
    }))
    const blob = new Blob([JSON.stringify(labels, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${version.versionTag}-labelset.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }

  async function handleAddLabel() {
    if (!datasetId) return
    setAddLabelSubmitting(true)
    setAddLabelError(null)
    try {
      await createLabelDefinition(datasetId, versionId, {
        labelCode: labelCode.trim(),
        labelName: labelName.trim(),
        labelCategory: labelCategory.trim() === '' ? undefined : labelCategory.trim(),
      })
      const updated = await fetchVersion(datasetId, versionId)
      setVersion(updated)
      setAddLabelOpen(false)
      setLabelCode('')
      setLabelName('')
      setLabelCategory('')
    } catch (err) {
      setAddLabelError(errorMessage(err))
    } finally {
      setAddLabelSubmitting(false)
    }
  }

  return (
    <>
      <div className="page-actions with-back">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {version ? (
            <span className="mono" style={{ fontSize: 14 }}>
              {version.versionTag}
            </span>
          ) : (
            <span>…</span>
          )}
          {version && !version.complete && (
            <Link
              to={`/sources/${sourceIdParam}/ontology?datasetVersionId=${version.id}`}
              className="badge badge-amber"
              style={{ textDecoration: 'none' }}
              title={`${version.unresolvedDirtyCount} unresolved dirty annotation(s)`}
            >
              Incomplete
            </Link>
          )}
        </div>
        <button className="btn btn-secondary btn-sm" onClick={() => navigate(versionsListPath)} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
          <ArrowLeftIcon style={{ width: 14, height: 14 }} />
          Back
        </button>
      </div>

      <div className="content">
        {error && (
          <div className="alert alert-error" style={{ marginBottom: 16 }}>
            {error}
          </div>
        )}
        {loading && !version && (
          <div className="loading-state">
            <span className="spinner" /> Loading version…
          </div>
        )}

        {version && (
          <div className="grid-2" style={{ gap: 24, alignItems: 'start' }}>
            <div>
              <div className="section-header">
                <div className="section-title">Version metadata</div>
              </div>
              <div className="card" style={{ padding: 16 }}>
                <div className="detail-grid">
                  <div className="detail-row">
                    <div className="detail-key">Version tag</div>
                    <div>
                      <span className="mono">{version.versionTag}</span>
                    </div>
                  </div>
                  <div className="detail-row">
                    <div className="detail-key">DVC commit</div>
                    <div>
                      <span className="mono">{version.dvcCommitHash}</span>
                    </div>
                  </div>
                  <div className="detail-row">
                    <div className="detail-key">Trigger</div>
                    <div>
                      <span className={`badge ${triggerReasonBadge(version.triggerReason)}`}>
                        {triggerReasonLabel(version.triggerReason)}
                      </span>
                    </div>
                  </div>
                  <div className="detail-row">
                    <div className="detail-key">Images</div>
                    <div>{formatCount(version.imageCount)}</div>
                  </div>
                  <div className="detail-row">
                    <div className="detail-key">Points</div>
                    <div>{formatCount(version.annotationCount)}</div>
                  </div>
                  <div className="detail-row">
                    <div className="detail-key">Created by</div>
                    <div>{version.createdBy}</div>
                  </div>
                  <div className="detail-row">
                    <div className="detail-key">Created at</div>
                    <div style={{ color: 'var(--text-secondary)' }}>
                      {formatDate(version.createdAt)}
                    </div>
                  </div>
                  <div className="detail-row">
                    <div className="detail-key">Notes</div>
                    <div style={{ color: 'var(--text-secondary)' }}>
                      {version.notes ?? '—'}
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div>
              <div className="section-header">
                <div className="section-title">Label definitions at this version</div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button
                    className="btn btn-secondary"
                    onClick={handleExportLabelSet}
                    disabled={!version.labelDefinitions || version.labelDefinitions.length === 0}
                    style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
                  >
                    <ArrowDownTrayIcon style={{ width: 14, height: 14 }} />
                    Export label set
                  </button>
                  <button
                    className="btn btn-secondary"
                    onClick={() => {
                      setAddLabelOpen((prev) => !prev)
                      setAddLabelError(null)
                    }}
                  >
                    {addLabelOpen ? 'Cancel' : 'Add Label'}
                  </button>
                </div>
              </div>
              {addLabelOpen && (
                <div className="card" style={{ padding: 16, marginBottom: 12 }}>
                  {addLabelError && (
                    <div className="alert alert-error" style={{ marginBottom: 12 }}>
                      {addLabelError}
                    </div>
                  )}
                  <div className="form-row">
                    <label className="form-label" htmlFor="labelCode">
                      Label code
                    </label>
                    <input
                      id="labelCode"
                      className="form-input"
                      placeholder="e.g. ACROPORA"
                      value={labelCode}
                      onChange={(e) => setLabelCode(e.target.value)}
                    />
                    <div className="form-hint">Must be unique within this version.</div>
                  </div>
                  <div className="form-row">
                    <label className="form-label" htmlFor="labelName">
                      Label name
                    </label>
                    <input
                      id="labelName"
                      className="form-input"
                      placeholder="e.g. Acropora"
                      value={labelName}
                      onChange={(e) => setLabelName(e.target.value)}
                    />
                  </div>
                  <div className="form-row" style={{ marginBottom: 0 }}>
                    <label className="form-label" htmlFor="labelCategory">
                      Category (optional)
                    </label>
                    <input
                      id="labelCategory"
                      className="form-input"
                      placeholder="e.g. Coral"
                      value={labelCategory}
                      onChange={(e) => setLabelCategory(e.target.value)}
                    />
                  </div>
                  <div style={{ marginTop: 16 }}>
                    <button
                      className="btn btn-primary"
                      onClick={handleAddLabel}
                      disabled={addLabelSubmitting || !labelCode.trim() || !labelName.trim()}
                    >
                      {addLabelSubmitting ? 'Adding…' : 'Add Label'}
                    </button>
                  </div>
                </div>
              )}
              <div className="card">
                {version.labelDefinitions && version.labelDefinitions.length > 0 ? (
                  <table>
                    <thead>
                      <tr>
                        <th>Code</th>
                        <th>Name</th>
                        <th>Category</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {version.labelDefinitions.map((label) => (
                        <tr key={label.labelCode}>
                          <td>
                            <span className="mono">{label.labelCode}</span>
                          </td>
                          <td>{label.labelName}</td>
                          <td>
                            {label.labelCategory ? (
                              <span className="tag">{label.labelCategory}</span>
                            ) : (
                              <span style={{ color: 'var(--text-muted)' }}>—</span>
                            )}
                          </td>
                          <td>
                            <span
                              className={`badge ${label.deprecated ? 'badge-gray' : 'badge-green'}`}
                            >
                              {label.deprecated ? 'Deprecated' : 'Active'}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                ) : (
                  <div className="empty-state">
                    <p>No label definitions recorded for this version.</p>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {version && (
          <div style={{ marginTop: 24 }}>
            <div className="section-header">
              <div className="section-title">Images ({formatCount(version.imageCount)})</div>
              <button
                className={`btn btn-sm ${overlayActive ? 'btn-primary' : 'btn-secondary'}`}
                disabled={!version.complete}
                onClick={() => setPointsToggle((v) => !v)}
                title={
                  version.complete
                    ? 'Show a dot-count badge on each image and overlay real annotation points in the zoomed view'
                    : 'Points can only be shown once this version has no unresolved dirty annotations'
                }
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
              >
                <ViewfinderCircleIcon style={{ width: 14, height: 14 }} />
                {overlayActive ? 'Points on' : 'Show points'}
              </button>
            </div>
            {pointsError && (
              <div className="alert alert-error" style={{ marginBottom: 12 }}>
                {pointsError}
              </div>
            )}
            <div className="card" style={{ padding: 16 }}>
              {imagesError && <div className="alert alert-error">{imagesError}</div>}
              {imagesLoading && images.length === 0 && (
                <div className="loading-state">
                  <span className="spinner" /> Loading images…
                </div>
              )}
              {!imagesLoading && !imagesError && images.length === 0 && (
                <div className="empty-state">
                  <p>No images recorded for this version.</p>
                </div>
              )}
              {images.length > 0 && (
                <>
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fill, minmax(120px, 1fr))',
                      gap: 8,
                    }}
                  >
                    {images.map((image, idx) => {
                      const imagePoints = pointsByImage.get(image.id) ?? []
                      return (
                        <button
                          key={image.id}
                          onClick={() => setLightboxIndex(idx)}
                          style={{
                            position: 'relative',
                            padding: 0,
                            border: '1px solid var(--border-md)',
                            borderRadius: 6,
                            overflow: 'hidden',
                            cursor: 'pointer',
                            aspectRatio: '1 / 1',
                            background: 'var(--bg-secondary)',
                          }}
                          title={image.filename}
                        >
                          <img
                            src={imageFileUrl(image)}
                            alt={image.filename}
                            style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
                          />
                          {overlayActive && !pointsLoading && (
                            <span
                              className="mono"
                              style={{
                                position: 'absolute',
                                top: 4,
                                right: 4,
                                fontSize: 10,
                                padding: '1px 6px',
                                background: 'rgba(20,24,30,0.75)',
                                color: '#fff',
                                border: 'none',
                              }}
                            >
                              {imagePoints.length} pt{imagePoints.length === 1 ? '' : 's'}
                            </span>
                          )}
                        </button>
                      )
                    })}
                  </div>
                  {imageTotalPages > 1 && (
                    <div style={{ display: 'flex', gap: 8, marginTop: 12, alignItems: 'center' }}>
                      <button
                        className="btn btn-secondary"
                        onClick={() => {
                          setImagesLoading(true)
                          setImagePage((p) => Math.max(0, p - 1))
                        }}
                        disabled={imagePage === 0}
                      >
                        Previous page
                      </button>
                      <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                        Page {imagePage + 1} of {imageTotalPages}
                      </span>
                      <button
                        className="btn btn-secondary"
                        onClick={() => {
                          setImagesLoading(true)
                          setImagePage((p) => Math.min(imageTotalPages - 1, p + 1))
                        }}
                        disabled={imagePage + 1 >= imageTotalPages}
                      >
                        Next page
                      </button>
                    </div>
                  )}
                </>
              )}
            </div>
          </div>
        )}

        {lightboxIndex !== null && images[lightboxIndex] && (
          <Lightbox
            image={images[lightboxIndex]}
            index={lightboxIndex}
            total={images.length}
            points={overlayActive ? pointsByImage.get(images[lightboxIndex].id) ?? [] : null}
            onClose={() => setLightboxIndex(null)}
            onPrev={() => setLightboxIndex((i) => (i !== null && i > 0 ? i - 1 : i))}
            onNext={() => setLightboxIndex((i) => (i !== null && i < images.length - 1 ? i + 1 : i))}
          />
        )}
      </div>
    </>
  )
}

/**
 * The zoomed single-image view. A dedicated component (rather than inline JSX in VersionDetail)
 * so its label-toggle state is truly local, it unmounts (and resets) whenever the lightbox
 * closes, independently of the outer grid's points toggle in the parent.
 */
function Lightbox({
  image,
  index,
  total,
  points,
  onClose,
  onPrev,
  onNext,
}: {
  image: DatasetImage
  index: number
  total: number
  points: AnnotationPointSummary[] | null
  onClose: () => void
  onPrev: () => void
  onNext: () => void
}) {
  const [labelsVisible, setLabelsVisible] = useState(false)
  const [naturalSize, setNaturalSize] = useState<{ width: number; height: number } | null>(null)

  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.8)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
      }}
    >
      <button
        onClick={(e) => {
          e.stopPropagation()
          onPrev()
        }}
        disabled={index === 0}
        className="btn btn-secondary"
        style={{ position: 'absolute', left: 24 }}
      >
        ‹ Prev
      </button>

      <div
        onClick={(e) => e.stopPropagation()}
        style={{ width: '80vw', height: '80vh', display: 'flex', flexDirection: 'column', gap: 8 }}
      >
        {points != null && (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <button
              className={`btn btn-sm ${labelsVisible ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setLabelsVisible((v) => !v)}
            >
              {labelsVisible ? 'Hide labels' : 'Show labels'}
            </button>
          </div>
        )}
        <div style={{ position: 'relative', flex: 1, minHeight: 0 }}>
          {points != null ? (
            <TransformWrapper key={image.id} initialScale={1} minScale={1} maxScale={4} centerOnInit>
              <TransformComponent wrapperStyle={{ width: '100%', height: '100%' }} contentStyle={{ width: '100%', height: '100%' }}>
                <div style={{ position: 'relative', width: '100%', height: '100%' }}>
                  <img
                    src={imageFileUrlById(image.id)}
                    alt={image.filename}
                    style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
                    onLoad={(e) => {
                      const img = e.currentTarget
                      setNaturalSize({ width: img.naturalWidth, height: img.naturalHeight })
                    }}
                  />
                  {naturalSize &&
                    points.map((p) => (
                      <div
                        key={p.id}
                        className="annotation-dot dot-pending"
                        style={{
                          top: `${(p.rowPosition / naturalSize.height) * 100}%`,
                          left: `${(p.colPosition / naturalSize.width) * 100}%`,
                        }}
                        title={p.labelCode}
                      >
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
                            {p.labelCode}
                          </span>
                        )}
                      </div>
                    ))}
                </div>
              </TransformComponent>
            </TransformWrapper>
          ) : (
            <img
              src={imageFileUrl(image)}
              alt={image.filename}
              style={{ width: '100%', height: '100%', objectFit: 'contain' }}
            />
          )}
        </div>
      </div>

      <button
        onClick={(e) => {
          e.stopPropagation()
          onNext()
        }}
        disabled={index >= total - 1}
        className="btn btn-secondary"
        style={{ position: 'absolute', right: 24 }}
      >
        Next ›
      </button>
      <button
        onClick={(e) => {
          e.stopPropagation()
          onClose()
        }}
        className="btn btn-secondary"
        style={{ position: 'absolute', top: 24, right: 24, display: 'inline-flex', alignItems: 'center', gap: 4 }}
      >
        <XMarkIcon style={{ width: 14, height: 14 }} />
        Close
      </button>
      <div
        style={{
          position: 'absolute',
          bottom: 24,
          color: '#fff',
          fontSize: 13,
        }}
      >
        {image.filename} ({index + 1} / {total})
      </div>
    </div>
  )
}
