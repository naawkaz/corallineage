import { useEffect, useMemo, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
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
import LabelCombobox from '../components/LabelCombobox'
import type { ChangeType, DirtyAnnotation, NewLabelSpec, OntologyChange } from '../types/ontology'
import type { DatasetVersion, LabelDefinition } from '../types/version'

interface NewLabelRow extends NewLabelSpec {
  key: string
}

function emptyRow(): NewLabelRow {
  return { key: Math.random().toString(36).slice(2), code: '', name: '', category: '' }
}

export default function OntologyChanges() {
  const { projectId: projectIdParam } = useParams()
  const projectId = Number(projectIdParam)
  const { datasets } = useDatasetContext()
  const project = datasets.find((d) => d.id === projectId) ?? null

  const [searchParams] = useSearchParams()
  const filterVersionId = searchParams.get('datasetVersionId')
    ? Number(searchParams.get('datasetVersionId'))
    : null

  const [tab, setTab] = useState<'history' | 'dirty'>('history')

  const [versions, setVersions] = useState<DatasetVersion[]>([])
  const [changeType, setChangeType] = useState<ChangeType>('LABEL_RENAMED')
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null)
  const [basedOnVersionLabels, setBasedOnVersionLabels] = useState<LabelDefinition[]>([])

  const [oldLabelCode, setOldLabelCode] = useState('')
  const [newLabelRows, setNewLabelRows] = useState<NewLabelRow[]>([emptyRow()])
  const [replacementLabelCode, setReplacementLabelCode] = useState('')
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
    fetchOntologyChanges(projectId, 0, 50, filterVersionId ?? undefined)
      .then((res) => {
        setChanges(res.content)
        setChangesError(null)
      })
      .catch((err) => setChangesError(errorMessage(err)))
      .finally(() => setChangesLoading(false))
  }

  useEffect(() => {
    loadChanges()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId, filterVersionId])

  useEffect(() => {
    fetchVersions(projectId, 0, 50)
      .then((res) => {
        setVersions(res.content)
        if (res.content.length > 0) setSelectedVersionId(res.content[0].id)
      })
      .catch(() => setVersions([]))
  }, [projectId])

  // The old-label combobox and the replacement-label combobox are both fed from the selected
  // dataset version's active (non-deprecated) label definitions.
  useEffect(() => {
    let cancelled = false
    if (!selectedVersionId) {
      // Deferred via .then so the reset isn't a synchronous setState call in the effect body.
      Promise.resolve().then(() => {
        if (!cancelled) setBasedOnVersionLabels([])
      })
      return () => {
        cancelled = true
      }
    }
    fetchVersion(projectId, selectedVersionId)
      .then((v) => {
        if (!cancelled) setBasedOnVersionLabels((v.labelDefinitions ?? []).filter((l) => !l.deprecated))
      })
      .catch(() => {
        if (!cancelled) setBasedOnVersionLabels([])
      })
    return () => {
      cancelled = true
    }
  }, [projectId, selectedVersionId])

  // Resetting the change type clears fields whose meaning differs across types, so a stale
  // oldLabelCode / newLabels combination from a previous type never gets submitted by accident.
  // Done during render (React's "adjusting state when a prop changes" pattern) rather than in a
  // useEffect, since this is pure state derivation with no external system involved.
  const [syncedChangeType, setSyncedChangeType] = useState(changeType)
  if (changeType !== syncedChangeType) {
    setSyncedChangeType(changeType)
    setOldLabelCode('')
    setReplacementLabelCode('')
    setNewLabelRows([emptyRow()])
  }

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
    if (change) {
      fetchVersion(projectId, change.affectedDatasetVersionId)
        .then((v) => setLabelOptions(v.labelDefinitions ?? []))
        .catch(() => setLabelOptions([]))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedChangeId])

  // RENAMED auto-fills the fixed row's category from the selected old label — still editable afterward.
  function handleOldLabelChange(code: string) {
    setOldLabelCode(code)
    if (changeType === 'LABEL_RENAMED') {
      const label = basedOnVersionLabels.find((l) => l.labelCode === code)
      setNewLabelRows((rows) => {
        const [first, ...rest] = rows
        return [{ ...first, category: label?.labelCategory ?? first.category }, ...rest]
      })
    }
  }

  function updateRow(key: string, field: keyof NewLabelSpec, value: string) {
    setNewLabelRows((rows) => rows.map((r) => (r.key === key ? { ...r, [field]: value } : r)))
  }

  function addRow() {
    setNewLabelRows((rows) => [...rows, emptyRow()])
  }

  function removeRow(key: string) {
    setNewLabelRows((rows) => (rows.length > 1 ? rows.filter((r) => r.key !== key) : rows))
  }

  const canSubmit = useMemo(() => {
    if (!selectedVersionId) return false
    if (changeType !== 'LABEL_ADDED' && oldLabelCode.trim() === '') return false
    if (changeType === 'LABEL_DEPRECATED') return true
    // ADDED/RENAMED/SPLIT all require every visible new-label row fully filled in.
    return newLabelRows.every((r) => r.code.trim() !== '' && r.name.trim() !== '')
  }, [selectedVersionId, changeType, oldLabelCode, newLabelRows])

  async function handleRecordChange() {
    if (!selectedVersionId || !canSubmit) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      const newLabels: NewLabelSpec[] =
        changeType === 'LABEL_DEPRECATED'
          ? []
          : newLabelRows.map((r) => ({
              code: r.code.trim(),
              name: r.name.trim(),
              category: (r.category ?? '').trim() === '' ? undefined : (r.category ?? '').trim(),
            }))
      await recordOntologyChange({
        changeType,
        basedOnDatasetVersionId: selectedVersionId,
        oldLabelCode: changeType === 'LABEL_ADDED' || oldLabelCode.trim() === '' ? undefined : oldLabelCode.trim(),
        newLabels,
        replacementLabelCode:
          changeType === 'LABEL_DEPRECATED' && replacementLabelCode.trim() !== ''
            ? replacementLabelCode.trim()
            : undefined,
        changedBy: CURRENT_USER,
        notes: notes.trim() === '' ? undefined : notes.trim(),
      })
      setOldLabelCode('')
      setReplacementLabelCode('')
      setNewLabelRows([emptyRow()])
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
            {project ? `Label vocabulary changes for ${project.datasetName}` : 'Label vocabulary changes'}
            {filterVersionId != null ? ` — filtered to dataset version #${filterVersionId}` : ''}
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
          <>
            <div className="grid-3" style={{ marginBottom: 20 }}>
              <div className="stat-card">
                <div className="stat-label">Total Changes</div>
                <div className="stat-value">{changes.length}</div>
              </div>
              <div className="stat-card">
                <div className="stat-label">Annotations Flagged</div>
                <div className="stat-value">
                  {changes.reduce((sum, c) => sum + c.dirtyAnnotationsFlagged, 0)}
                </div>
              </div>
              <div className="stat-card">
                <div className="stat-label">Unresolved Flags</div>
                <div className="stat-value">
                  {changes.reduce((sum, c) => sum + (c.dirtyAnnotationsFlagged - c.resolvedCount), 0)}
                </div>
              </div>
            </div>
          <div className="grid-2" style={{ gap: 24, alignItems: 'start' }}>
            <div>
              <div className="section-header">
                <div className="section-title">Record Ontology Change</div>
              </div>
              <div className="card" style={{ padding: 16 }}>
                <div className="form-row">
                  <label className="form-label">Change type</label>
                  <select className="form-select" value={changeType} onChange={(e) => setChangeType(e.target.value as ChangeType)}>
                    {CHANGE_TYPES.map((t) => (
                      <option key={t.value} value={t.value}>
                        {t.label}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="form-row">
                  <label className="form-label">Dataset version</label>
                  <select
                    className="form-select"
                    value={selectedVersionId ?? ''}
                    onChange={(e) => setSelectedVersionId(e.target.value ? Number(e.target.value) : null)}
                  >
                    {versions.map((v) => (
                      <option key={v.id} value={v.id}>
                        {v.versionTag}
                      </option>
                    ))}
                  </select>
                  <div className="form-hint">
                    Branches a new version from this one. Its label set and image membership are copied
                    forward, then the mutation below is applied.
                  </div>
                </div>

                {changeType !== 'LABEL_ADDED' && (
                  <div className="form-row">
                    <label className="form-label" htmlFor="oldLabelCode">
                      Old label code
                    </label>
                    <LabelCombobox
                      id="oldLabelCode"
                      options={basedOnVersionLabels}
                      value={oldLabelCode}
                      onChange={handleOldLabelChange}
                      placeholder="Search existing labels…"
                    />
                    <div className="form-hint">Must already exist (and be active) on the selected dataset version.</div>
                  </div>
                )}

                {(changeType === 'LABEL_ADDED' || changeType === 'LABEL_RENAMED') && (
                  <NewLabelRowFields
                    row={newLabelRows[0]}
                    onChange={(field, value) => updateRow(newLabelRows[0].key, field, value)}
                    removable={false}
                  />
                )}

                {changeType === 'LABEL_SPLIT' && (
                  <div className="form-row">
                    <label className="form-label">New labels (split from old)</label>
                    {newLabelRows.map((row) => (
                      <div
                        key={row.key}
                        className="card"
                        style={{ padding: 12, marginBottom: 8, background: 'var(--canvas)' }}
                      >
                        <NewLabelRowFields
                          row={row}
                          onChange={(field, value) => updateRow(row.key, field, value)}
                          removable={newLabelRows.length > 1}
                          onRemove={() => removeRow(row.key)}
                          compact
                        />
                      </div>
                    ))}
                    <button type="button" className="btn btn-secondary btn-sm" onClick={addRow}>
                      + Add another new label
                    </button>
                  </div>
                )}

                {changeType === 'LABEL_DEPRECATED' && (
                  <div className="form-row">
                    <label className="form-label" htmlFor="replacementLabelCode">
                      Replacement label (optional)
                    </label>
                    <LabelCombobox
                      id="replacementLabelCode"
                      options={basedOnVersionLabels.filter((l) => l.labelCode !== oldLabelCode)}
                      value={replacementLabelCode}
                      onChange={setReplacementLabelCode}
                      placeholder="Search existing labels…"
                    />
                    <div className="form-hint">
                      Informational only — points on the deprecated label are still flagged dirty for review.
                    </div>
                  </div>
                )}

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
                    disabled={submitting || !canSubmit}
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
                        <th>Resolved</th>
                      </tr>
                    </thead>
                    <tbody>
                      {changes.map((c) => {
                        const pct = c.dirtyAnnotationsFlagged > 0
                          ? (c.resolvedCount / c.dirtyAnnotationsFlagged) * 100
                          : 0
                        return (
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
                                {c.oldLabelCode ?? '—'} → {c.newLabelCodes?.join(', ') || (c.replacementLabelCode ? `(replacement: ${c.replacementLabelCode})` : '—')}
                              </span>
                            </td>
                            <td>{c.changedBy}</td>
                            <td style={{ color: 'var(--text-secondary)' }}>{formatDate(c.changedAt)}</td>
                            <td>{c.dirtyAnnotationsFlagged}</td>
                            <td>
                              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                <div className="progress-bar-bg" style={{ width: 60 }}>
                                  <div
                                    className="progress-bar-fill"
                                    style={{ width: `${pct}%`, background: 'var(--success)' }}
                                  />
                                </div>
                                <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                                  {c.resolvedCount}/{c.dirtyAnnotationsFlagged}
                                </span>
                              </div>
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                )}
              </div>
            </div>
          </div>
          </>
        )}

        {tab === 'dirty' && (
          <div>
            <div className="section-header">
              <div className="section-title">Dirty Annotations Queue</div>
            </div>
            {selectedChange && (selectedChange.dirtyAnnotationsFlagged - selectedChange.resolvedCount) > 0 && (
              <div className="impact-box" style={{ marginBottom: 16, marginTop: 0 }}>
                <span>⚠</span>
                <span>
                  {selectedChange.dirtyAnnotationsFlagged - selectedChange.resolvedCount} annotation point
                  {selectedChange.dirtyAnnotationsFlagged - selectedChange.resolvedCount === 1 ? '' : 's'} carry a
                  deprecated label from change #{selectedChange.id} and still need review.
                </span>
              </div>
            )}
            <div className="card" style={{ padding: 16, marginBottom: 16 }}>
              <div className="form-row" style={{ marginBottom: 0 }}>
                <label className="form-label">Ontology change</label>
                <select
                  className="form-select"
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

function NewLabelRowFields({
  row,
  onChange,
  removable,
  onRemove,
  compact,
}: {
  row: NewLabelRow
  onChange: (field: keyof NewLabelSpec, value: string) => void
  removable: boolean
  onRemove?: () => void
  compact?: boolean
}) {
  return (
    <div>
      {!compact && <label className="form-label">New label</label>}
      <div style={{ display: 'flex', gap: 8, marginBottom: 8, alignItems: 'flex-start' }}>
        <input
          className="form-input"
          placeholder="Code, e.g. BLEACHED_ACROPORA"
          value={row.code}
          onChange={(e) => onChange('code', e.target.value)}
        />
        <input
          className="form-input"
          placeholder="Name, e.g. Bleached Acropora"
          value={row.name}
          onChange={(e) => onChange('name', e.target.value)}
        />
        <input
          className="form-input"
          placeholder="Category (optional)"
          value={row.category ?? ''}
          onChange={(e) => onChange('category', e.target.value)}
        />
        {removable && (
          <button type="button" className="btn btn-secondary btn-sm" onClick={onRemove} title="Remove">
            ✕
          </button>
        )}
      </div>
    </div>
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
          <select className="form-select" value={selected} onChange={(e) => setSelected(e.target.value)}>
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
