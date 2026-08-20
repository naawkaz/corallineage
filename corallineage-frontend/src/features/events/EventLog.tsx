import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ClipboardDocumentListIcon } from '@heroicons/react/24/outline'
import { fetchEvents } from '../events/eventsApi'
import { fetchVersions } from '../datasets/versionsApi'
import { fetchModels } from '../training/modelsApi'
import { errorMessage } from '../../shared/api/client'
import { EVENT_TYPES, eventTypeLabel } from '../../shared/constants'
import { formatDate } from '../../shared/lib/format'
import Pagination from '../../shared/components/Pagination'
import type { AnnotationEvent, EventFilters, EventType } from '../events/eventsTypes'
import type { Page } from '../datasets/versionsTypes'
import type { DatasetVersion } from '../datasets/versionsTypes'
import type { ModelVersion } from '../training/modelsTypes'

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

  const [datasetVersions, setDatasetVersions] = useState<DatasetVersion[]>([])
  const [modelVersions, setModelVersions] = useState<ModelVersion[]>([])

  const filters: EventFilters = {
    eventType: eventType === '' ? undefined : (eventType as EventType),
    datasetVersionId: datasetVersionId === '' ? undefined : Number(datasetVersionId),
    modelVersionId: modelVersionId === '' ? undefined : Number(modelVersionId),
    from: from === '' ? undefined : new Date(from).toISOString(),
    to: to === '' ? undefined : new Date(to).toISOString(),
  }

  useEffect(() => {
    if (!sourceId) return
    fetchVersions(sourceId, 0, 50)
      .then((res) => setDatasetVersions(res.content))
      .catch(() => setDatasetVersions([]))
    fetchModels(sourceId, 0, 50)
      .then((res) => setModelVersions(res.content))
      .catch(() => setModelVersions([]))
  }, [sourceId])

  useEffect(() => {
    let cancelled = false
    fetchEvents({ ...filters, datasetId: sourceId }, page, EVENT_PAGE_SIZE)
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventType, datasetVersionId, modelVersionId, from, to, page, sourceId])

  const hasActiveFilters = Object.values(filters).some((v) => v !== undefined)

  return (
    <>
      <div className="content page-top-spacing">
        <div className="section-header">
          <div className="filter-bar" style={{ flexWrap: 'wrap', marginBottom: 0 }}>
            <select
              className="form-select"
              style={{ width: 'auto' }}
              value={eventType}
              onChange={(e) => {
                setPage(0)
                setEventType(e.target.value)
              }}
            >
              <option value="">All event types</option>
              {EVENT_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
            {sourceId && (
              <select
                className="form-select"
                style={{ width: 'auto' }}
                value={datasetVersionId}
                onChange={(e) => {
                  setPage(0)
                  setDatasetVersionId(e.target.value)
                }}
              >
                <option value="">All dataset versions</option>
                {datasetVersions.map((v) => (
                  <option key={v.id} value={v.id}>
                    {v.versionTag}
                  </option>
                ))}
              </select>
            )}
            {sourceId && (
              <select
                className="form-select"
                style={{ width: 'auto' }}
                value={modelVersionId}
                onChange={(e) => {
                  setPage(0)
                  setModelVersionId(e.target.value)
                }}
              >
                <option value="">All model versions</option>
                {modelVersions.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.modelTag}
                  </option>
                ))}
              </select>
            )}
            <input
              type="date"
              aria-label="From"
              value={from}
              onChange={(e) => {
                setPage(0)
                setFrom(e.target.value)
              }}
            />
            <input
              type="date"
              aria-label="To"
              value={to}
              onChange={(e) => {
                setPage(0)
                setTo(e.target.value)
              }}
            />
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
              <p>No events found{hasActiveFilters ? ' matching your filters' : ' yet'}.</p>
            </div>
          ) : (
            <div>
              {data.content.map((e) => (
                <div className="event-row" key={e.id}>
                  <div className="event-content">
                    <div className="event-type">{eventTypeLabel(e.eventType)}</div>
                    <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 2 }}>
                      {e.description ?? '-'}
                      {e.modelVersionId != null && <> · model v{e.modelVersionId}</>}
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
