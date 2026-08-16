import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Box } from 'lucide-react'
import { fetchModels } from '../api/models'
import { errorMessage } from '../api/client'
import { PAGE_SIZE } from '../constants'
import { formatDate } from '../lib/format'
import type { ModelStatus, ModelVersion } from '../types/model'
import type { Page } from '../types/version'

const STATUS_BADGE: Record<ModelStatus, string> = {
  ACTIVE: 'badge-green',
  ARCHIVED: 'badge-gray',
  DEPRECATED: 'badge-red',
}

export default function ModelRegistry() {
  const navigate = useNavigate()
  const { projectId: projectIdParam } = useParams()
  const projectId = Number(projectIdParam)
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('ALL')
  const [data, setData] = useState<Page<ModelVersion> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchModels(projectId, page, PAGE_SIZE, status === 'ALL' ? undefined : status)
      .then((res) => {
        setData(res)
        setError(null)
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [projectId, page, status])

  const pageNumbers = data ? Array.from({ length: data.totalPages }, (_, i) => i) : []

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title">Model Registry</div>
          <div className="topbar-sub">Models registered from completed training jobs</div>
        </div>
      </div>

      <div className="content">
        <div className="filter-bar">
          <select
            className="form-select"
            value={status}
            onChange={(e) => {
              setLoading(true)
              setStatus(e.target.value)
              setPage(0)
            }}
          >
            <option value="ALL">All statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="ARCHIVED">Archived</option>
            <option value="DEPRECATED">Deprecated</option>
          </select>
        </div>

        {error && (
          <div className="alert alert-error" style={{ marginBottom: 16 }}>
            {error}
          </div>
        )}

        <div className="card">
          {loading ? (
            <div className="loading-state">
              <span className="spinner" /> Loading models…
            </div>
          ) : !data || data.content.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon">
                <Box size={32} strokeWidth={1.5} />
              </div>
              <p>No models registered yet. Complete a training job to register one.</p>
            </div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Model</th>
                  <th>Architecture</th>
                  <th>Accuracy</th>
                  <th>Dataset version</th>
                  <th>Status</th>
                  <th>Trained</th>
                  <th>By</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((m) => (
                  <tr key={m.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/projects/${projectId}/models/${m.id}`)}>
                    <td>
                      <span className="mono">{m.modelTag}</span>
                    </td>
                    <td>{m.architecture}</td>
                    <td>{m.accuracy != null ? `${(m.accuracy * 100).toFixed(1)}%` : '—'}</td>
                    <td>
                      {m.trainedOnDatasetVersion ? (
                        <span className="mono">{m.trainedOnDatasetVersion.versionTag}</span>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td>
                      <span className={`badge ${STATUS_BADGE[m.status]}`}>{m.status}</span>
                    </td>
                    <td style={{ color: 'var(--text-secondary)' }}>{formatDate(m.trainedAt)}</td>
                    <td style={{ color: 'var(--text-secondary)' }}>{m.trainedBy}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {data && data.totalPages > 0 && (
            <div className="pagination">
              Showing {data.number * PAGE_SIZE + 1}–
              {Math.min((data.number + 1) * PAGE_SIZE, data.totalElements)} of {data.totalElements}
              <button
                className="page-btn"
                disabled={page === 0}
                onClick={() => {
                  setLoading(true)
                  setPage(page - 1)
                }}
              >
                ‹
              </button>
              {pageNumbers.map((n) => (
                <button
                  key={n}
                  className={`page-btn${n === page ? ' active' : ''}`}
                  onClick={() => {
                    setLoading(true)
                    setPage(n)
                  }}
                >
                  {n + 1}
                </button>
              ))}
              <button
                className="page-btn"
                disabled={page >= data.totalPages - 1}
                onClick={() => {
                  setLoading(true)
                  setPage(page + 1)
                }}
              >
                ›
              </button>
            </div>
          )}
        </div>
      </div>
    </>
  )
}
