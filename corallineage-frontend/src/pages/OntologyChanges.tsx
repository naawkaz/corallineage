import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  fetchDirtyAnnotations,
  fetchOntologyChanges,
  recordOntologyChange,
  resolveDirtyAnnotation,
} from '../api/ontology'
import { fetchVersion, fetchVersions } from '../api/versions'
import { errorMessage } from '../api/client'
import { CHANGE_TYPES, CURRENT_USER, changeTypeBadge, changeTypeLabel } from '../constants'
import { formatDate } from '../lib/format'
import { useDatasetContext } from '../lib/datasetContextCore'
import type { ChangeType, DirtyAnnotation, OntologyChange } from '../types/ontology'
import type { DatasetVersion, LabelDefinition } from '../types/version'

export default function OntologyChanges() {
  const navigate = useNavigate()
  const { activeDataset, activeDatasetId, loading: datasetsLoading } = useDatasetContext()

  const [tab, setTab] = useState<'history' | 'dirty'>('history')

  const [versions, setVersions] = useState<DatasetVersion[]>([])
  const [changeType, setChangeType] = useState<ChangeType>('LABEL_RENAMED')
  const [oldLabelCode, setOldLabelCode] = useState('')
  const [newLabelCodes, setNewLabelCodes] = useState('')
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null)
  const [notes, setNotes] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const [changes, setChanges] = useState<OntologyChange[]>([])
  const [changesLoading, setChangesLoading] = useState(true)
  const [changesError, setChangesError] = useState<string | null>(null)

  const [selectedChangeId, setSelectedChangeId] = useState<number | null>(null)
  const [dirtyAnnotations, setDirtyAnnotations] = useState<DirtyAnnotation[]>([])
  const [dirtyLoading, setDirtyLoading] = useState(false)
  const [dirtyError, setDirtyError] = useState<string | null>(null)
  const [labelOptions, setLabelOptions] = useState<LabelDefinition[]>([])

  function loadChanges() {
    fetchOntologyChanges(0, 50)
      .then((res) => {
        setChanges(res.content)
        setChangesError(null)
      })
      .catch((err) => setChangesError(errorMessage(err)))
      .finally(() => setChangesLoading(false))
  }

  useEffect(() => {
    loadChanges()
  }, [])

  useEffect(() => {
    if (!activeDatasetId) return
    fetchVersions(activeDatasetId, 0, 50)
      .then((res) => {
        setVersions(res.content)
        if (res.content.length > 0) setSelectedVersionId(res.content[0].id)
      })
      .catch(() => setVersions([]))
  }, [activeDatasetId])

  function loadDirty(ontologyChangeId: number) {
    fetchDirtyAnnotations(0, 500, ontologyChangeId)
      .then((res) => {
        setDirtyAnnotations(res.content)
        setDirtyError(null)
      })
      .catch((err) => setDirtyError(errorMessage(err)))
      .finally(() => setDirtyLoading(false))
  }

  useEffect(() => {
    if (selectedChangeId == null) {
      return
    }
    loadDirty(selectedChangeId)
    const change = changes.find((c) => c.id === selectedChangeId)
    if (change && activeDatasetId) {
      fetchVersion(activeDatasetId, change.affectedDatasetVersionId)
        .then((v) => setLabelOptions(v.labelDefinitions ?? []))
        .catch(() => setLabelOptions([]))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedChangeId])

  if (!datasetsLoading && !activeDatasetId) {
    return (
      <div className="content">
        <div className="card">
          <div className="empty-state">
            <div className="empty-icon">🪸</div>
            <p>No dataset selected. Create or choose a dataset first.</p>
            <button className="btn btn-primary" onClick={() => navigate('/datasets')}>
              Go to Datasets
            </button>
          </div>
        </div>
      </div>
    )
  }

  async function handleRecordChange() {
    if (!selectedVersionId) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      await recordOntologyChange({
        changeType,
        oldLabelCode: oldLabelCode.trim() === '' ? undefined : oldLabelCode.trim(),
        newLabelCodes: newLabelCodes
          .split(',')
          .map((s) => s.trim())
          .filter((s) => s.length > 0),
        affectedDatasetVersionId: selectedVersionId,
        changedBy: CURRENT_USER,
        notes: notes.trim() === '' ? undefined : notes.trim(),
      })
      setOldLabelCode('')
      setNewLabelCodes('')
      setNotes('')
      loadChanges()
    } catch (err) {
      setSubmitError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleResolve(dirty: DirtyAnnotation, newLabelCode: string) {
    try {
      await resolveDirtyAnnotation(dirty.id, { newLabelCode, resolvedBy: CURRENT_USER })
      if (selectedChangeId != null) loadDirty(selectedChangeId)
      loadChanges()
    } catch (err) {
      setDirtyError(errorMessage(err))
    }
  }

  function selectChange(id: number | null) {
    setSelectedChangeId(id)
    if (id != null) setDirtyLoading(true)
  }

  const selectedChange = changes.find((c) => c.id === selectedChangeId) ?? null
  const resolvedCount = dirtyAnnotations.filter((d) => d.resolved).length
  const totalCount = selectedChange?.dirtyAnnotationsFlagged ?? dirtyAnnotations.length
  const progressPct = totalCount > 0 ? (resolvedCount / totalCount) * 100 : 0

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title">Ontology Changes</div>
          <div className="topbar-sub">
            {activeDataset ? `Label vocabulary changes for ${activeDataset.datasetName}` : 'Label vocabulary changes'}
          </div>
        </div>
      </div>

      <div className="content">
        <div style={{ display: 'flex', gap: 8, marginBottom: 20 }}>
          <button
            className={`btn ${tab === 'history' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setTab('history')}
          >
            Change History
          </button>
          <button
            className={`btn ${tab === 'dirty' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setTab('dirty')}
          >
            Dirty Annotations Queue
          </button>
        </div>

        {tab === 'history' && (
          <div className="grid-2" style={{ gap: 24, alignItems: 'start' }}>
            <div>
              <div className="section-header">
                <div className="section-title">Record Ontology Change</div>
              </div>
              <div className="card" style={{ padding: 16 }}>
                <div className="form-row">
                  <label className="form-label">Change type</label>
                  <select value={changeType} onChange={(e) => setChangeType(e.target.value as ChangeType)}>
                    {CHANGE_TYPES.map((t) => (
                      <option key={t.value} value={t.value}>
                        {t.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="form-row">
                  <label className="form-label" htmlFor="oldLabelCode">
                    Old label code {changeType === 'LABEL_ADDED' ? '(leave blank)' : ''}
                  </label>
                  <input
                    id="oldLabelCode"
                    className="form-input"
                    placeholder="e.g. BLEACHED_CORAL"
                    value={oldLabelCode}
                    onChange={(e) => setOldLabelCode(e.target.value)}
                  />
                  <div className="form-hint">Must already exist on the selected dataset version.</div>
                </div>
                <div className="form-row">
                  <label className="form-label" htmlFor="newLabelCodes">
                    New label code(s)
                  </label>
                  <input
                    id="newLabelCodes"
                    className="form-input"
                    placeholder="comma-separated, e.g. BLEACHED_ACROPORA, BLEACHED_PORITES"
                    value={newLabelCodes}
                    onChange={(e) => setNewLabelCodes(e.target.value)}
                  />
                </div>
                <div className="form-row">
                  <label className="form-label">Dataset version</label>
                  <select
                    value={selectedVersionId ?? ''}
                    onChange={(e) => setSelectedVersionId(e.target.value ? Number(e.target.value) : null)}
                  >
                    {versions.map((v) => (
                      <option key={v.id} value={v.id}>
                        {v.versionTag}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="form-row" style={{ marginBottom: 0 }}>
                  <label className="form-label" htmlFor="ontologyNotes">
                    Notes
                  </label>
                  <textarea
                    id="ontologyNotes"
                    className="form-textarea"
                    placeholder="Optional context for this change"
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                  />
                </div>

                {submitError && (
                  <div className="alert alert-error" style={{ marginTop: 16 }}>
                    {submitError}
                  </div>
                )}

                <div style={{ marginTop: 16 }}>
                  <button
                    className="btn btn-primary"
                    onClick={handleRecordChange}
                    disabled={submitting || !selectedVersionId}
                  >
                    {submitting ? 'Recording…' : 'Record Change'}
                  </button>
                </div>
              </div>
            </div>

            <div>
              <div className="section-header">
                <div className="section-title">Change History</div>
              </div>
              <div className="card">
                {changesError && (
                  <div className="alert alert-error" style={{ margin: 16 }}>
                    {changesError}
                  </div>
                )}
                {changesLoading ? (
                  <div className="loading-state">
                    <span className="spinner" /> Loading changes…
                  </div>
                ) : changes.length === 0 ? (
                  <div className="empty-state">
                    <p>No ontology changes recorded yet.</p>
                  </div>
                ) : (
                  <table>
                    <thead>
                      <tr>
                        <th>Type</th>
                        <th>Old → New</th>
                        <th>Changed by</th>
                        <th>Changed at</th>
                        <th>Dirty flags</th>
                      </tr>
                    </thead>
                    <tbody>
                      {changes.map((c) => (
                        <tr
                          key={c.id}
                          style={{ cursor: 'pointer' }}
                          onClick={() => {
                            selectChange(c.id)
                            setTab('dirty')
                          }}
                        >
                          <td>
                            <span className={`badge ${changeTypeBadge(c.changeType)}`}>
                              {changeTypeLabel(c.changeType)}
                            </span>
                          </td>
                          <td>
                            <span className="mono" style={{ fontSize: 12 }}>
                              {c.oldLabelCode ?? '—'} → {c.newLabelCodes?.join(', ') || '—'}
                            </span>
                          </td>
                          <td>{c.changedBy}</td>
                          <td style={{ color: 'var(--text-secondary)' }}>{formatDate(c.changedAt)}</td>
                          <td>{c.dirtyAnnotationsFlagged}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </div>
          </div>
        )}

        {tab === 'dirty' && (
          <div>
            <div className="section-header">
              <div className="section-title">Dirty Annotations Queue</div>
            </div>
            <div className="card" style={{ padding: 16, marginBottom: 16 }}>
              <div className="form-row" style={{ marginBottom: 0 }}>
                <label className="form-label">Ontology change</label>
                <select
                  value={selectedChangeId ?? ''}
                  onChange={(e) => selectChange(e.target.value ? Number(e.target.value) : null)}
                >
                  <option value="">Select a change…</option>
                  {changes.map((c) => (
                    <option key={c.id} value={c.id}>
                      #{c.id} — {c.oldLabelCode ?? changeTypeLabel(c.changeType)} ({c.dirtyAnnotationsFlagged} flagged)
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {selectedChangeId != null && (
              <>
                <div style={{ height: 4, background: 'var(--border)', marginBottom: 16 }}>
                  <div
                    style={{ height: 4, width: `${progressPct}%`, background: '#0d9488', transition: 'width 0.2s' }}
                  />
                </div>
                <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 12 }}>
                  {resolvedCount} / {totalCount} resolved
                </div>
              </>
            )}

            <div className="card">
              {dirtyError && (
                <div className="alert alert-error" style={{ margin: 16 }}>
                  {dirtyError}
                </div>
              )}
              {selectedChangeId == null ? (
                <div className="empty-state">
                  <p>Select an ontology change to view its dirty annotation queue.</p>
                </div>
              ) : dirtyLoading ? (
                <div className="loading-state">
                  <span className="spinner" /> Loading dirty annotations…
                </div>
              ) : dirtyAnnotations.length === 0 ? (
                <div className="empty-state">
                  <p>No dirty annotations for this change.</p>
                </div>
              ) : (
                <table>
                  <thead>
                    <tr>
                      <th>Image</th>
                      <th>Point</th>
                      <th>Current label</th>
                      <th>Flagged at</th>
                      <th>Resolution</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dirtyAnnotations.map((d) => (
                      <DirtyRow key={d.id} dirty={d} labelOptions={labelOptions} onResolve={handleResolve} />
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        )}
      </div>
    </>
  )
}

function DirtyRow({
  dirty,
  labelOptions,
  onResolve,
}: {
  dirty: DirtyAnnotation
  labelOptions: LabelDefinition[]
  onResolve: (dirty: DirtyAnnotation, newLabelCode: string) => void
}) {
  const [selected, setSelected] = useState('')

  if (dirty.resolved) {
    return (
      <tr>
        <td>{dirty.filename}</td>
        <td>
          <span className="mono">{dirty.pointIndex}</span>
        </td>
        <td>
          <span style={{ color: 'var(--text-muted)' }}>{dirty.currentLabelCode}</span>
        </td>
        <td style={{ color: 'var(--text-secondary)' }}>{formatDate(dirty.flaggedAt)}</td>
        <td>
          <span className="badge badge-green">Resolved → {dirty.newLabelCode}</span>
        </td>
      </tr>
    )
  }

  return (
    <tr>
      <td>{dirty.filename}</td>
      <td>
        <span className="mono">{dirty.pointIndex}</span>
      </td>
      <td>
        <span className="badge" style={{ background: 'var(--coral, #e05d44)', color: '#fff' }}>
          {dirty.currentLabelCode}
        </span>
      </td>
      <td style={{ color: 'var(--text-secondary)' }}>{formatDate(dirty.flaggedAt)}</td>
      <td>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <select value={selected} onChange={(e) => setSelected(e.target.value)}>
            <option value="">Select label…</option>
            {labelOptions.map((l) => (
              <option key={l.labelCode} value={l.labelCode}>
                {l.labelName} ({l.labelCode})
              </option>
            ))}
          </select>
          <button
            className="btn btn-secondary"
            disabled={!selected}
            onClick={() => onResolve(dirty, selected)}
          >
            Resolve
          </button>
        </div>
      </td>
    </tr>
  )
}
