import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { fetchDatasetDiff } from '../datasets/diffApi'
import { fetchVersions } from '../datasets/versionsApi'
import { imageFileUrlById } from '../datasets/imagesApi'
import { errorMessage } from '../../shared/api/client'
import { formatCount } from '../../shared/lib/format'
import TitledCard from '../../shared/components/TitledCard'
import type { DatasetDiff } from '../datasets/diffTypes'
import type { DatasetVersion } from '../datasets/versionsTypes'

export default function DatasetDiffPage() {
  const { sourceId: sourceIdParam } = useParams()
  const sourceId = Number(sourceIdParam)

  const [versions, setVersions] = useState<DatasetVersion[]>([])
  const [fromVersionId, setFromVersionId] = useState<number | null>(null)
  const [toVersionId, setToVersionId] = useState<number | null>(null)

  const [diff, setDiff] = useState<DatasetDiff | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchVersions(sourceId, 0, 50)
      .then((res) => {
        setVersions(res.content)
        if (res.content.length >= 2) {
          setToVersionId(res.content[0].id)
          setFromVersionId(res.content[res.content.length - 1].id)
        } else if (res.content.length === 1) {
          setToVersionId(res.content[0].id)
        }
      })
      .catch(() => setVersions([]))
  }, [sourceId])

  async function handleCompare() {
    if (!fromVersionId || !toVersionId) return
    setLoading(true)
    setError(null)
    try {
      const result = await fetchDatasetDiff(sourceId, fromVersionId, toVersionId)
      setDiff(result)
    } catch (err) {
      setDiff(null)
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <div className="content page-top-spacing">
        <TitledCard title="Compare versions" style={{ marginBottom: 24 }}>
          <div style={{ display: 'flex', gap: 16, alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <div className="form-row" style={{ marginBottom: 0, width: 180 }}>
              <label className="form-label">From version</label>
              <select
                className="form-select"
                value={fromVersionId ?? ''}
                onChange={(e) => setFromVersionId(e.target.value ? Number(e.target.value) : null)}
              >
                <option value="">Select…</option>
                {versions.map((v) => (
                  <option key={v.id} value={v.id}>
                    {v.versionTag}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-row" style={{ marginBottom: 0, width: 180 }}>
              <label className="form-label">To version</label>
              <select
                className="form-select"
                value={toVersionId ?? ''}
                onChange={(e) => setToVersionId(e.target.value ? Number(e.target.value) : null)}
              >
                <option value="">Select…</option>
                {versions.map((v) => (
                  <option key={v.id} value={v.id}>
                    {v.versionTag}
                  </option>
                ))}
              </select>
            </div>
            <button
              className="btn btn-primary"
              onClick={handleCompare}
              disabled={loading || !fromVersionId || !toVersionId}
            >
              {loading ? 'Comparing…' : 'Compare'}
            </button>
          </div>
          {error && (
            <div className="alert alert-error" style={{ marginTop: 16 }}>
              {error}
            </div>
          )}
        </TitledCard>

        {diff && (
          <>
            {diff.labelChanges.length > 0 && (
              <TitledCard
                title={
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span>Label changes</span>
                    {diff.summary.labelsAdded > 0 && (
                      <span className="badge badge-green">+{diff.summary.labelsAdded}</span>
                    )}
                    {diff.summary.labelsDeprecated > 0 && (
                      <span className="badge badge-gray">-{diff.summary.labelsDeprecated}</span>
                    )}
                  </div>
                }
                style={{ marginBottom: 24 }}
              >
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  {diff.labelChanges.map((c) => (
                    <span
                      key={c.labelCode}
                      className={`badge ${c.changeType === 'ADDED' ? 'badge-green' : 'badge-gray'}`}
                    >
                      {c.changeType === 'ADDED' ? '+ ' : '− '}
                      {c.labelCode}
                    </span>
                  ))}
                </div>
              </TitledCard>
            )}

            <TitledCard title="Image changes" bodyClassName="no-padding" style={{ marginBottom: 24 }}>
              <div className="diff-layout">
                <div className="diff-side">
                  <div className="diff-header">
                    <span style={{ fontSize: 12, fontWeight: 600 }}>Removed</span>
                    <span className="badge badge-red">{diff.imagesRemoved.length}</span>
                  </div>
                  {diff.imagesRemoved.length === 0 ? (
                    <div className="empty-state">
                      <p>No images removed between these versions.</p>
                    </div>
                  ) : (
                    <div className="diff-grid">
                      {diff.imagesRemoved.map((img) => (
                        <div key={img.imageId} className="diff-thumb removed" title={img.filename}>
                          <div className="diff-badge">−</div>
                          <img
                            src={imageFileUrlById(img.imageId)}
                            alt={img.filename}
                            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                          />
                        </div>
                      ))}
                    </div>
                  )}
                </div>
                <div className="diff-side">
                  <div className="diff-header">
                    <span style={{ fontSize: 12, fontWeight: 600 }}>Added</span>
                    <span className="badge badge-green">{diff.imagesAdded.length}</span>
                  </div>
                  {diff.imagesAdded.length === 0 ? (
                    <div className="empty-state">
                      <p>No images added between these versions.</p>
                    </div>
                  ) : (
                    <div className="diff-grid">
                      {diff.imagesAdded.map((img) => (
                        <div key={img.imageId} className="diff-thumb added" title={img.filename}>
                          <div className="diff-badge">+</div>
                          <img
                            src={imageFileUrlById(img.imageId)}
                            alt={img.filename}
                            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                          />
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </TitledCard>

            {/* title={`Class distribution data · Δ ${formatCount(diff.summary.annotationsModified)}`} */}

            <TitledCard
              title={
                <div style={{display: 'flex', alignItems: 'center', gap: 8}}>
                  <span>Class distribution data</span>
                  <span className='badge badge-gray'>
                    Δ {formatCount(diff.summary.annotationsModified)}
                  </span>
                </div>
              }
            >
              {diff.classDistributionDiff.length === 0 ? (
                <div className="empty-state">
                  <p>No annotation data to compare.</p>
                </div>
              ) : (
                <div style={{ height: 300 }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={diff.classDistributionDiff}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="labelCode" />
                      <YAxis />
                      <Tooltip />
                      <Bar dataKey="delta" name="Delta">
                        {diff.classDistributionDiff.map((entry) => (
                          <Cell
                            key={entry.labelCode}
                            fill={entry.delta >= 0 ? 'var(--teal, #0d9488)' : 'var(--coral, #e05d44)'}
                          />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </TitledCard>
          </>
        )}
      </div>
    </>
  )
}
