import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { fetchVersion } from '../api/versions'
import { errorMessage } from '../api/client'
import { triggerReasonBadge, triggerReasonLabel } from '../constants'
import { formatCount, formatDate } from '../lib/format'
import { useDatasetContext } from '../lib/datasetContextCore'
import type { DatasetVersion } from '../types/version'

export default function VersionDetail() {
  const { id } = useParams()
  const versionId = Number(id)
  const { activeDatasetId } = useDatasetContext()
  const [version, setVersion] = useState<DatasetVersion | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!activeDatasetId) return
    let cancelled = false
    fetchVersion(activeDatasetId, versionId)
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
  }, [activeDatasetId, versionId])

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Link
              to="/versions"
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
          </div>
          <div className="topbar-sub">
            {version ? `Created ${formatDate(version.createdAt)} by ${version.createdBy}` : ''}
          </div>
        </div>
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
              </div>
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
      </div>
    </>
  )
}
