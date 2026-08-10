import { useEffect, useState } from 'react'
import { fetchEvents } from '../api/events'
import { errorMessage } from '../api/client'
import { EVENT_TYPES, PAGE_SIZE, eventTypeBadge, eventTypeLabel } from '../constants'
import { formatDate } from '../lib/format'
import type { AnnotationEvent, EventFilters, EventType } from '../types/event'
import type { Page } from '../types/version'

export default function EventLog() {
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
    fetchEvents(appliedFilters, page, PAGE_SIZE)
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
  }, [appliedFilters, page])

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

  const pageNumbers = data ? Array.from({ length: data.totalPages }, (_, i) => i) : []

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title">Event Log</div>
          <div className="topbar-sub">Immutable, append-only record of every workflow action</div>
        </div>
      </div>

      <div className="content">
        <div className="filter-bar" style={{ flexWrap: 'wrap' }}>
          <select value={eventType} onChange={(e) => setEventType(e.target.value)}>
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
              <div className="empty-icon">📋</div>
              <p>No events found{Object.keys(appliedFilters).length > 0 ? ' matching your filters' : ' yet'}.</p>
            </div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Description</th>
                  <th>Dataset Version</th>
                  <th>Model Version</th>
                  <th>By</th>
                  <th>Occurred</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((e) => (
                  <tr key={e.id}>
                    <td>
                      <span className={`badge ${eventTypeBadge(e.eventType)}`}>
                        {eventTypeLabel(e.eventType)}
                      </span>
                    </td>
                    <td>{e.description ?? '—'}</td>
                    <td>{e.datasetVersionId ?? '—'}</td>
                    <td>{e.modelVersionId ?? '—'}</td>
                    <td style={{ color: 'var(--text-secondary)' }}>{e.performedBy}</td>
                    <td style={{ color: 'var(--text-secondary)' }}>{formatDate(e.occurredAt)}</td>
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
    </>
  )
}
