import { useEffect, useMemo, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { ArrowRightIcon, FolderOpenIcon } from '@heroicons/react/24/outline'
import { fetchVersions } from '../datasets/versionsApi'
import { errorMessage } from '../../shared/api/client'
import { PAGE_SIZE, TRIGGER_REASONS, triggerReasonBadge, triggerReasonLabel } from '../../shared/constants'
import { formatCount, formatDate, shortHash } from '../../shared/lib/format'
import CreateVersionModal from './CreateVersionModal'
import Pagination from '../../shared/components/Pagination'
import type { DatasetVersion, Page } from '../datasets/versionsTypes'

interface VersionsListProps {
  sourceId: number
}

/**
 * The Dataset Versions list (filters + table + pagination), embedded inline on the Source
 * Dashboard below the stat cards (item 33). "New Version" opens CreateVersionModal, which
 * now also carries the full import flow (formerly the standalone Import page).
 */
export default function VersionsList({ sourceId }: VersionsListProps) {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<DatasetVersion> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [trigger, setTrigger] = useState('ALL')
  const [showCreate, setShowCreate] = useState(false)

  useEffect(() => {
    let cancelled = false
    fetchVersions(sourceId, page, PAGE_SIZE)
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
  }, [sourceId, page])

  const filtered = useMemo(() => {
    if (!data) return []
    const q = search.trim().toLowerCase()
    return data.content.filter((v) => {
      const matchesSearch =
        !q || v.versionTag.toLowerCase().includes(q) || (v.notes ?? '').toLowerCase().includes(q)
      const matchesTrigger = trigger === 'ALL' || v.triggerReason === trigger
      return matchesSearch && matchesTrigger
    })
  }, [data, search, trigger])

  return (
    <div>
      <div className="section-header">
        <div className="section-title">Dataset Versions</div>
        <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)}>
          + New Version
        </button>
      </div>

      <div className="section-header">
        <div className="filter-bar" style={{ marginBottom: 0 }}>
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
        {data && data.totalPages > 0 && (
          <Pagination
            page={page}
            totalPages={data.totalPages}
            onPageChange={setPage}
            totalElements={data.totalElements}
            pageSize={PAGE_SIZE}
          />
        )}
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
            <div className="empty-icon">
              <FolderOpenIcon style={{ width: 32, height: 32 }} />
            </div>
            <p>
              No versions found
              {search || trigger !== 'ALL' ? ' matching your filters' : ' yet'}.
              {search || trigger !== 'ALL' ? '' : ' Create your first snapshot to get started.'}
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
                  onClick={() => navigate(`/sources/${sourceId}/versions/${v.id}`)}
                >
                  <td>
                    <span className="mono">{v.versionTag}</span>
                  </td>
                  <td>
                    <span className={`badge ${triggerReasonBadge(v.triggerReason)}`}>
                      {triggerReasonLabel(v.triggerReason)}
                    </span>
                    {!v.complete && (
                      <Link
                        to={`/sources/${sourceId}/ontology?datasetVersionId=${v.id}`}
                        className="badge badge-amber"
                        style={{ marginLeft: 6, textDecoration: 'none' }}
                        onClick={(e) => e.stopPropagation()}
                        title={`${v.unresolvedDirtyCount} unresolved dirty annotation(s)`}
                      >
                        Incomplete
                      </Link>
                    )}
                  </td>
                  <td>{formatCount(v.imageCount)}</td>
                  <td>{formatCount(v.annotationCount)}</td>
                  <td>
                    <span className="mono">{shortHash(v.dvcCommitHash)}</span>
                  </td>
                  <td style={{ color: 'var(--text-secondary)' }}>{formatDate(v.createdAt)}</td>
                  <td style={{ color: 'var(--text-secondary)' }}>{v.createdBy}</td>
                  <td>
                    <span style={{ color: 'var(--accent)', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 2 }}>
                      View <ArrowRightIcon style={{ width: 12, height: 12 }} />
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <CreateVersionModal
        datasetId={sourceId}
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onCreated={(v) => {
          setShowCreate(false)
          navigate(`/sources/${sourceId}/versions/${v.id}`)
        }}
      />
    </div>
  )
}
