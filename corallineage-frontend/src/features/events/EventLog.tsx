import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ClipboardDocumentListIcon } from '@heroicons/react/24/outline'
import { fetchEvents } from '../events/eventsApi'
import { errorMessage } from '../../shared/api/client'
import { EVENT_TYPES, eventTypeLabel } from '../../shared/constants'
import { formatDate } from '../../shared/lib/format'
import Pagination from '../../shared/components/Pagination'
import type { AnnotationEvent, EventFilters, EventType } from '../events/eventsTypes'
import type { Page } from '../datasets/versionsTypes'

const EVENT_PAGE_SIZE = 10

export default function EventLog() {
  const { sourceId: sourceIdParam } = useParams<{ sourceId?: string }>()
  const sourceId = sourceIdParam ? Number(sourceIdParam) : undefined

  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<AnnotationEvent> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [eventType, setEventType] = useState('')
  const [datasetVersionId, setDatasetVersionId] = useState('')
  const [modelVersionId, setModelVersionId] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [appliedFilters, setAppliedFilters] = useState<EventFilters>({})

  useEffect(() => {
    let cancelled = false
    fetchEvents({ ...appliedFilters, datasetId: sourceId }, page, EVENT_PAGE_SIZE)
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
  }, [appliedFilters, page, sourceId])

  function applyFilters() {
    setPage(0)
    setAppliedFilters({
      eventType: eventType === '' ? undefined : (eventType as EventType),
      datasetVersionId: datasetVersionId === '' ? undefined : Number(datasetVersionId),
      modelVersionId: modelVersionId === '' ? undefined : Number(modelVersionId),
      from: from === '' ? undefined : new Date(from).toISOString(),
      to: to === '' ? undefined : new Date(to).toISOString(),
    })
  }

  function clearFilters() {
    setEventType('')
    setDatasetVersionId('')
    setModelVersionId('')
    setFrom('')
    setTo('')
    setPage(0)
    setAppliedFilters({})
  }

  return (
    <>
      <div className="content">
        <div className="section-header">
          <div className="filter-bar" style={{ flexWrap: 'wrap', marginBottom: 0 }}>
            <select className="form-select" value={eventType} onChange={(e) => setEventType(e.target.value)}>
              <option value="">All event types</option>
              {EVENT_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
            <input
              placeholder="Dataset version ID"
              style={{ width: 160 }}
              value={datasetVersionId}
              onChange={(e) => setDatasetVersionId(e.target.value.replace(/\D/g, ''))}
            />
            <input
              placeholder="Model version ID"
              style={{ width: 160 }}
              value={modelVersionId}
              onChange={(e) => setModelVersionId(e.target.value.replace(/\D/g, ''))}
            />
            <input
              type="datetime-local"
              aria-label="From"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
            />
            <input
              type="datetime-local"
              aria-label="To"
              value={to}
              onChange={(e) => setTo(e.target.value)}
            />
            <button className="btn btn-primary" onClick={applyFilters}>
              Apply
            </button>
            <button className="btn btn-secondary" onClick={clearFilters}>
              Clear
            </button>
          </div>
          {data && data.totalPages > 0 && (
            <Pagination
              page={page}
              totalPages={data.totalPages}
              onPageChange={setPage}
              totalElements={data.totalElements}
              pageSize={EVENT_PAGE_SIZE}
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
              <span className="spinner" /> Loading events…
            </div>
          ) : !data || data.content.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon">
                <ClipboardDocumentListIcon style={{ width: 32, height: 32 }} />
              </div>
              <p>No events found{Object.keys(appliedFilters).length > 0 ? ' matching your filters' : ' yet'}.</p>
            </div>
          ) : (
            <div>
              {data.content.map((e) => (
                <div className="event-row" key={e.id}>
                  <div className="event-content">
                    <div className="event-type">{eventTypeLabel(e.eventType)}</div>
                    <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 2 }}>
                      {e.description ?? '—'}
                      {e.modelVersionId != null && (
                        <>
                          {' '}
                          · model v<span className="mono">{e.modelVersionId}</span>
                        </>
                      )}
                    </div>
                    <div className="event-meta">
                      {e.performedBy} · {formatDate(e.occurredAt)}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

        </div>
      </div>
    </>
  )
}
