import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { CubeIcon } from '@heroicons/react/24/outline'
import { fetchModels } from '../api/models'
import { errorMessage } from '../api/client'
import { PAGE_SIZE } from '../constants'
import { formatDate } from '../lib/format'
import Pagination from '../components/Pagination'
import type { ModelStatus, ModelVersion } from '../types/model'
import type { Page } from '../types/version'

const STATUS_BADGE: Record<ModelStatus, string> = {
  ACTIVE: 'badge-green',
  ARCHIVED: 'badge-gray',
  DEPRECATED: 'badge-red',
}

export default function ModelRegistry() {
  const navigate = useNavigate()
  const { sourceId: sourceIdParam } = useParams()
  const sourceId = Number(sourceIdParam)
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('ALL')
  const [data, setData] = useState<Page<ModelVersion> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchModels(sourceId, page, PAGE_SIZE, status === 'ALL' ? undefined : status)
      .then((res) => {
        setData(res)
        setError(null)
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [sourceId, page, status])

  return (
    <>
      <div className="content">
        {error && (
          <div className="alert alert-error" style={{ marginBottom: 16 }}>
            {error}
          </div>
        )}

        <div className="card">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 16px 0' }}>
            <select
              className="form-select"
              style={{ width: 'auto' }}
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
            {data && data.totalPages > 0 && (
              <Pagination
                page={page}
                totalPages={data.totalPages}
                onPageChange={(p) => {
                  setLoading(true)
                  setPage(p)
                }}
                totalElements={data.totalElements}
                pageSize={PAGE_SIZE}
              />
            )}
          </div>

          {loading ? (
            <div className="loading-state">
              <span className="spinner" /> Loading models…
            </div>
          ) : !data || data.content.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon">
                <CubeIcon style={{ width: 32, height: 32 }} />
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
                  <tr key={m.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/sources/${sourceId}/models/${m.id}`)}>
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

        </div>
      </div>
    </>
  )
}
