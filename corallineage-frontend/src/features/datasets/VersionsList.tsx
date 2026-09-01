import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowRightIcon, FolderOpenIcon } from '@heroicons/react/24/outline'
import { fetchVersions } from '../datasets/versionsApi'
import { errorMessage } from '../../shared/api/client'
import { PAGE_SIZE, TRIGGER_REASONS, triggerReasonBadge, triggerReasonLabel } from '../../shared/constants'
import { formatCount, formatDate, shortHash } from '../../shared/lib/format'
import { useJobPolling } from '../../shared/lib/jobPollingContextCore'
import CreateVersionModal from './CreateVersionModal'
import Pagination from '../../shared/components/Pagination'
import TitledCard from '../../shared/components/TitledCard'
import type { DatasetVersion, Page } from '../datasets/versionsTypes'

interface VersionsListProps {
  sourceId: number
}

/**
 * The dataset versions list (filters, table, pagination), embedded on the Source Dashboard.
 * "Add Images" opens CreateVersionModal, which also carries the full import flow.
 */
export default function VersionsList({ sourceId }: VersionsListProps) {
  const navigate = useNavigate()
  const { refreshSignal } = useJobPolling()
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<DatasetVersion> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [trigger, setTrigger] = useState('ALL')
  const [showCreate, setShowCreate] = useState(false)

  function load(showSpinner: boolean) {
    if (showSpinner) setLoading(true)
    return fetchVersions(sourceId, page, PAGE_SIZE)
      .then((res) => {
        setData(res)
        setError(null)
        return res
      })
      .catch((err) => {
        setError(errorMessage(err))
        return null
      })
      .finally(() => {
        if (showSpinner) setLoading(false)
      })
  }

  useEffect(() => {
    load(true)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceId, page])

  // Imports process in the background, so re-fetch whenever JobPollingProvider's refreshSignal
  // changes instead of running a poll here.
  const isFirstRefreshSignal = useRef(true)
  useEffect(() => {
    if (isFirstRefreshSignal.current) {
      isFirstRefreshSignal.current = false
      return
    }
    load(false)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshSignal])

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
      <TitledCard
        title="Dataset versions"
        actions={
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <input
              className="form-input"
              placeholder="Search version tag or notes..."
              style={{ width: 260 }}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <select
              className="form-select"
              style={{ width: 'auto' }}
              value={trigger}
              onChange={(e) => setTrigger(e.target.value)}
            >
              <option value="ALL">All triggers</option>
              {TRIGGER_REASONS.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
            <button className="btn btn-primary" onClick={() => setShowCreate(true)}>
              + Add Images
            </button>
          </div>
        }
      >
        <div className="filter-row" style={{ justifyContent: 'flex-end' }}>
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

        {loading ? (
          <div className="loading-state">
            <span className="spinner" /> Loading versions...
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
                <th className="num">#</th>
                <th>Version</th>
                <th>Trigger</th>
                <th className="num">Images</th>
                <th className="num">Points</th>
                <th>DVC Hash</th>
                <th>Created</th>
                <th>By</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((v) => {
                const processing = v.importStatus === 'PROCESSING'
                const failed = v.importStatus === 'FAILED'

                return (
                <tr
                  key={v.id}
                  id={`version-row-${v.id}`}
                  className={processing || failed ? undefined : 'clickable'}
                  onClick={() => {
                    if (processing || failed) return
                    navigate(`/sources/${sourceId}/versions/${v.id}`)
                  }}
                >
                  <td className="num" style={{ color: 'var(--text-secondary)' }}>{v.versionNumber}</td>
                  <td>
                    <span className="mono">{v.versionTag}</span>
                  </td>
                  <td>
                    <span className={`badge ${triggerReasonBadge(v.triggerReason)}`}>
                      {triggerReasonLabel(v.triggerReason)}
                    </span>
                    {failed && (
                      <span className="badge badge-red" style={{ marginLeft: 6 }} title="Import failed">
                        Import failed
                      </span>
                    )}
                  </td>
                  <td className="num">{formatCount(v.imageCount)}</td>
                  <td className="num">{formatCount(v.annotationCount)}</td>
                  <td>
                    <span className="mono">{shortHash(v.dvcCommitHash)}</span>
                  </td>
                  <td style={{ color: 'var(--text-secondary)' }}>{formatDate(v.createdAt)}</td>
                  <td style={{ color: 'var(--text-secondary)' }}>{v.createdBy}</td>
                  <td>
                    {processing ? (
                      <span className="spinner" style={{ width: 14, height: 14 }} />
                    ) : failed ? (
                      <span style={{ color: 'var(--danger)', fontSize: 12 }}>Failed</span>
                    ) : (
                      <span style={{ color: 'var(--accent)', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 2 }}>
                        View <ArrowRightIcon style={{ width: 12, height: 12 }} />
                      </span>
                    )}
                  </td>
                </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </TitledCard>

      <CreateVersionModal
        datasetId={sourceId}
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onSubmitted={() => {
          setShowCreate(false)
          load(false)
        }}
      />
    </div>
  )
}
