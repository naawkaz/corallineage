import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { createLabelDefinition, fetchVersion } from '../api/versions'
import { errorMessage } from '../api/client'
import { fetchImages, imageFileUrl } from '../api/images'
import { triggerReasonBadge, triggerReasonLabel } from '../constants'
import { formatCount, formatDate } from '../lib/format'
import type { DatasetVersion } from '../types/version'
import type { DatasetImage } from '../types/image'

const IMAGE_PAGE_SIZE = 40

export default function VersionDetail() {
  const { id, projectId: projectIdParam } = useParams()
  const navigate = useNavigate()
  const versionId = Number(id)
  const datasetId = Number(projectIdParam)
  const versionsListPath = `/projects/${projectIdParam}/versions`
  const [version, setVersion] = useState<DatasetVersion | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [images, setImages] = useState<DatasetImage[]>([])
  const [imagePage, setImagePage] = useState(0)
  const [imageTotalPages, setImageTotalPages] = useState(0)
  const [imagesLoading, setImagesLoading] = useState(true)
  const [imagesError, setImagesError] = useState<string | null>(null)
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null)

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
      <div className="topbar">
        <div>
          <div className="topbar-title" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Link
              to={versionsListPath}
              style={{
                color: 'var(--text-muted)',
                fontWeight: 400,
                textDecoration: 'none',
                cursor: 'pointer',
              }}
            >
              Version History
            </Link>
            <span style={{ color: 'var(--border-md)' }}>/</span>
            {version ? (
              <span className="mono" style={{ fontSize: 14 }}>
                {version.versionTag}
              </span>
            ) : (
              <span>…</span>
            )}
            {version && !version.complete && (
              <Link
                to={`/projects/${projectIdParam}/ontology?datasetVersionId=${version.id}`}
                className="badge badge-amber"
                style={{ textDecoration: 'none' }}
                title={`${version.unresolvedDirtyCount} unresolved dirty annotation(s)`}
              >
                Incomplete
              </Link>
            )}
          </div>
          <div className="topbar-sub">
            {version ? `Created ${formatDate(version.createdAt)} by ${version.createdBy}` : ''}
          </div>
        </div>
        <button className="btn btn-secondary btn-sm" onClick={() => navigate(versionsListPath)}>
          ← Back
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
            </div>
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
                    {images.map((image, idx) => (
                      <button
                        key={image.id}
                        onClick={() => setLightboxIndex(idx)}
                        style={{
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
                      </button>
                    ))}
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
          <div
            onClick={() => setLightboxIndex(null)}
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
                setLightboxIndex((i) => (i !== null && i > 0 ? i - 1 : i))
              }}
              disabled={lightboxIndex === 0}
              className="btn btn-secondary"
              style={{ position: 'absolute', left: 24 }}
            >
              ‹ Prev
            </button>
            <img
              src={imageFileUrl(images[lightboxIndex])}
              alt={images[lightboxIndex].filename}
              onClick={(e) => e.stopPropagation()}
              style={{ maxWidth: '80vw', maxHeight: '80vh', objectFit: 'contain' }}
            />
            <button
              onClick={(e) => {
                e.stopPropagation()
                setLightboxIndex((i) => (i !== null && i < images.length - 1 ? i + 1 : i))
              }}
              disabled={lightboxIndex >= images.length - 1}
              className="btn btn-secondary"
              style={{ position: 'absolute', right: 24 }}
            >
              Next ›
            </button>
            <div
              style={{
                position: 'absolute',
                bottom: 24,
                color: '#fff',
                fontSize: 13,
              }}
            >
              {images[lightboxIndex].filename} ({lightboxIndex + 1} / {images.length})
            </div>
          </div>
        )}
      </div>
    </>
  )
}
