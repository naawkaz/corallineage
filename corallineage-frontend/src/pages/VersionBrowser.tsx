import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { fetchVersions } from '../api/versions'
import { errorMessage } from '../api/client'
import { PAGE_SIZE, TRIGGER_REASONS, triggerReasonBadge, triggerReasonLabel } from '../constants'
import { formatCount, formatDate, shortHash } from '../lib/format'
import { useDatasetContext } from '../lib/datasetContextCore'
import CreateVersionModal from '../components/CreateVersionModal'
import type { DatasetVersion, Page } from '../types/version'

export default function VersionBrowser() {
  const navigate = useNavigate()
  const { projectId: projectIdParam } = useParams()
  const projectId = Number(projectIdParam)
  const { datasets } = useDatasetContext()
  const project = datasets.find((d) => d.id === projectId) ?? null
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<DatasetVersion> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [trigger, setTrigger] = useState('ALL')
  const [showCreate, setShowCreate] = useState(false)

  useEffect(() => {
    let cancelled = false
    fetchVersions(projectId, page, PAGE_SIZE)
      .then((res) => {
        if (!cancelled) {
          setData(res)
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
  }, [projectId, page])

  const filtered = useMemo(() => {
    if (!data) return []
    const q = search.trim().toLowerCase()
    return data.content.filter((v) => {
      const matchesSearch =
        !q ||
        v.versionTag.toLowerCase().includes(q) ||
        (v.notes ?? '').toLowerCase().includes(q)
      const matchesTrigger = trigger === 'ALL' || v.triggerReason === trigger
      return matchesSearch && matchesTrigger
    })
  }, [data, search, trigger])

  const latest = data?.content[0] ?? null
  const pageNumbers = data ? Array.from({ length: data.totalPages }, (_, i) => i) : []

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span
              style={{ color: 'var(--text-muted)', fontWeight: 400, cursor: 'pointer' }}
              onClick={() => navigate('/')}
            >
              All Projects
            </span>
            <span style={{ color: 'var(--border-md)' }}>/</span>
            <span>Version History</span>
          </div>
          <div className="topbar-sub">
            {project ? `${project.datasetName} — snapshots tracked by DVC` : 'Snapshots tracked by DVC'}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn btn-secondary btn-sm" onClick={() => navigate('/')}>
            ← Back
          </button>
          <button className="btn btn-primary" onClick={() => setShowCreate(true)}>
            ＋ New Version
          </button>
        </div>
      </div>

      <div className="content">
        {data && (
          <div className="grid-4" style={{ marginBottom: 24 }}>
            <div className="stat-card">
              <div className="stat-label">Total Versions</div>
              <div className="stat-value">{formatCount(data.totalElements)}</div>
              <div className="stat-sub">Across all history</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Images</div>
              <div className="stat-value">{latest ? formatCount(latest.imageCount) : '—'}</div>
              <div className="stat-sub">Latest listed version</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Annotation Points</div>
              <div className="stat-value">
                {latest ? formatCount(latest.annotationCount) : '—'}
              </div>
              <div className="stat-sub">Latest listed version</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Label Classes</div>
              <div className="stat-value">{latest?.labelDefinitions?.length ?? '—'}</div>
              <div className="stat-sub">Latest listed version</div>
            </div>
          </div>
        )}

        <div className="filter-bar">
          <input
            placeholder="Search version tag or notes…"
            style={{ width: 260 }}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <select className="form-select" value={trigger} onChange={(e) => setTrigger(e.target.value)}>
            <option value="ALL">All triggers</option>
            {TRIGGER_REASONS.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
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
              <span className="spinner" /> Loading versions…
            </div>
          ) : filtered.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon">🗂</div>
              <p>
                No versions found
                {search || trigger !== 'ALL' ? ' matching your filters' : ' yet'}.
                {search || trigger !== 'ALL'
                  ? ''
                  : ' Create your first snapshot to get started.'}
              </p>
            </div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Version</th>
                  <th>Trigger</th>
                  <th>Images</th>
                  <th>Points</th>
                  <th>DVC Hash</th>
                  <th>Created</th>
                  <th>By</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((v) => (
                  <tr
                    key={v.id}
                    style={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/projects/${projectId}/versions/${v.id}`)}
                  >
                    <td>
                      <span className="mono">{v.versionTag}</span>
                    </td>
                    <td>
                      <span className={`badge ${triggerReasonBadge(v.triggerReason)}`}>
                        {triggerReasonLabel(v.triggerReason)}
                      </span>
                    </td>
                    <td>{formatCount(v.imageCount)}</td>
                    <td>{formatCount(v.annotationCount)}</td>
                    <td>
                      <span className="mono">{shortHash(v.dvcCommitHash)}</span>
                    </td>
                    <td style={{ color: 'var(--text-secondary)' }}>{formatDate(v.createdAt)}</td>
                    <td style={{ color: 'var(--text-secondary)' }}>{v.createdBy}</td>
                    <td>
                      <span style={{ color: 'var(--accent)', fontSize: 12 }}>View →</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {data && data.totalPages > 0 && (
            <div className="pagination">
              Showing {data.number * PAGE_SIZE + 1}–
              {Math.min((data.number + 1) * PAGE_SIZE, data.totalElements)} of{' '}
              {data.totalElements}
              <button
                className="page-btn"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                ‹
              </button>
              {pageNumbers.map((n) => (
                <button
                  key={n}
                  className={`page-btn${n === page ? ' active' : ''}`}
                  onClick={() => setPage(n)}
                >
                  {n + 1}
                </button>
              ))}
              <button
                className="page-btn"
                disabled={page >= data.totalPages - 1}
                onClick={() => setPage(page + 1)}
              >
                ›
              </button>
              <div style={{ marginLeft: 'auto', color: 'var(--text-muted)' }}>
                {PAGE_SIZE} per page
              </div>
            </div>
          )}
        </div>
      </div>

      <CreateVersionModal
        datasetId={projectId}
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onCreated={(v) => {
          setShowCreate(false)
          navigate(`/projects/${projectId}/versions/${v.id}`)
        }}
      />
    </>
  )
}
