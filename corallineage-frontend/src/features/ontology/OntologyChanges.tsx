import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ArrowRightIcon, PlusIcon, XMarkIcon } from '@heroicons/react/24/outline'
import { fetchOntologyChanges, recordOntologyChange } from '../ontology/ontologyApi'
import { fetchVersion, fetchVersions } from '../datasets/versionsApi'
import { errorMessage } from '../../shared/api/client'
import { CHANGE_TYPES, changeTypeBadge, changeTypeLabel } from '../../shared/constants'
import { formatDate } from '../../shared/lib/format'
import { useAuthContext } from '../../shared/lib/authContextCore'
import LabelCombobox from '../../shared/components/LabelCombobox'
import TitledCard from '../../shared/components/TitledCard'
import CircularProgress from '../../shared/components/CircularProgress'
import type { ChangeType, NewLabelSpec, OntologyChange } from '../ontology/ontologyTypes'
import type { DatasetVersion, LabelDefinition } from '../datasets/versionsTypes'

interface NewLabelRow extends NewLabelSpec {
  key: string
}

function emptyRow(): NewLabelRow {
  return { key: Math.random().toString(36).slice(2), code: '', name: '', category: '' }
}

export default function OntologyChanges() {
  const { sourceId: sourceIdParam } = useParams()
  const sourceId = Number(sourceIdParam)
  const navigate = useNavigate()
  const { user } = useAuthContext()

  const [searchParams] = useSearchParams()
  const filterVersionId = searchParams.get('datasetVersionId')
    ? Number(searchParams.get('datasetVersionId'))
    : null

  const [versions, setVersions] = useState<DatasetVersion[]>([])
  const [changeType, setChangeType] = useState<ChangeType>('LABEL_RENAMED')
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null)
  const [basedOnVersionLabels, setBasedOnVersionLabels] = useState<LabelDefinition[]>([])

  const [newVersionTag, setNewVersionTag] = useState('')
  const [oldLabelCode, setOldLabelCode] = useState('')
  const [newLabelRows, setNewLabelRows] = useState<NewLabelRow[]>([emptyRow()])
  const [replacementLabelCode, setReplacementLabelCode] = useState('')
  const [notes, setNotes] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const [changes, setChanges] = useState<OntologyChange[]>([])
  const [changesLoading, setChangesLoading] = useState(true)
  const [changesError, setChangesError] = useState<string | null>(null)

  const [recordDialogOpen, setRecordDialogOpen] = useState(false)

  function loadChanges() {
    fetchOntologyChanges(sourceId, 0, 50, filterVersionId ?? undefined)
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
  }, [sourceId, filterVersionId])

  useEffect(() => {
    fetchVersions(sourceId, 0, 50)
      .then((res) => {
        setVersions(res.content)
        if (res.content.length > 0) setSelectedVersionId(res.content[0].id)
      })
      .catch(() => setVersions([]))
  }, [sourceId])

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
    fetchVersion(sourceId, selectedVersionId)
      .then((v) => {
        if (!cancelled) setBasedOnVersionLabels((v.labelDefinitions ?? []).filter((l) => !l.deprecated))
      })
      .catch(() => {
        if (!cancelled) setBasedOnVersionLabels([])
      })
    return () => {
      cancelled = true
    }
  }, [sourceId, selectedVersionId])

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

  // RENAMED auto-fills the fixed row's category from the selected old label - still editable afterward.
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
    if (newVersionTag.trim() === '') return false
    if (changeType !== 'LABEL_ADDED' && oldLabelCode.trim() === '') return false
    if (changeType === 'LABEL_DEPRECATED') return true
    // ADDED/RENAMED/SPLIT all require every visible new-label row fully filled in.
    return newLabelRows.every((r) => r.code.trim() !== '' && r.name.trim() !== '')
  }, [selectedVersionId, newVersionTag, changeType, oldLabelCode, newLabelRows])

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
        newVersionTag: newVersionTag.trim(),
        oldLabelCode: changeType === 'LABEL_ADDED' || oldLabelCode.trim() === '' ? undefined : oldLabelCode.trim(),
        newLabels,
        replacementLabelCode:
          changeType === 'LABEL_DEPRECATED' && replacementLabelCode.trim() !== ''
            ? replacementLabelCode.trim()
            : undefined,
        changedBy: user?.username ?? '',
        notes: notes.trim() === '' ? undefined : notes.trim(),
      })
      setNewVersionTag('')
      setOldLabelCode('')
      setReplacementLabelCode('')
      setNewLabelRows([emptyRow()])
      setNotes('')
      setRecordDialogOpen(false)
      loadChanges()
    } catch (err) {
      setSubmitError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  const annotationsFlagged = changes.reduce((sum, c) => sum + c.dirtyAnnotationsFlagged, 0)
  const unresolvedFlags = changes.reduce((sum, c) => sum + (c.dirtyAnnotationsFlagged - c.resolvedCount), 0)
  const unresolvedPct = annotationsFlagged > 0 ? (unresolvedFlags / annotationsFlagged) * 100 : 0

  return (
    <>
      <div className="content page-top-spacing">
        {filterVersionId != null && (
          <div className="alert alert-info" style={{ marginBottom: 16 }}>
            Filtered to dataset version #{filterVersionId}
          </div>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: '4fr 1fr', gap: 24, alignItems: 'start' }}>
          {/* item 48: Ontology Change History; rows link to a dedicated review page instead of
              expanding an inline queue. */}
          <TitledCard
            title="Ontology change history"
            bodyClassName="no-padding"
            actions={
              <button
                className="btn btn-primary"
                onClick={() => setRecordDialogOpen(true)}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
              >
                <PlusIcon style={{ width: 14, height: 14 }} />
                Record Ontology Change
              </button>
            }
          >
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
                    <th className="num">Dirty flags</th>
                    <th className="num">Remaining</th>
                    <th>Status</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {changes.map((c) => {
                    const remaining = c.dirtyAnnotationsFlagged - c.resolvedCount
                    return (
                      <tr
                        key={c.id}
                        className="clickable"
                        onClick={() => navigate(`/sources/${sourceIdParam}/ontology/${c.id}`)}
                      >
                        <td>
                          <span className={`badge ${changeTypeBadge(c.changeType)}`}>
                            {changeTypeLabel(c.changeType)}
                          </span>
                        </td>
                        <td>
                          <span className="mono" style={{ fontSize: 12 }}>
                            {c.oldLabelCode ?? '(none)'} → {c.newLabelCodes?.join(', ') || (c.replacementLabelCode ? `(replacement: ${c.replacementLabelCode})` : '(none)')}
                          </span>
                        </td>
                        <td>{c.changedBy}</td>
                        <td style={{ color: 'var(--text-secondary)' }}>{formatDate(c.changedAt)}</td>
                        <td className="num">{c.dirtyAnnotationsFlagged}</td>
                        <td className="num">{remaining}/{c.dirtyAnnotationsFlagged}</td>
                        <td>
                          <span className={`badge ${remaining > 0 ? 'badge-amber' : 'badge-green'}`}>
                            {remaining > 0 ? 'Unresolved' : 'Resolved'}
                          </span>
                        </td>
                        <td>
                          <span style={{ color: 'var(--accent)', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 2 }}>
                            View <ArrowRightIcon style={{ width: 12, height: 12 }} />
                          </span>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            )}
          </TitledCard>

          <div className="card" style={{ padding: 20, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
            <CircularProgress percent={unresolvedPct} danger={unresolvedFlags > 0} />
            <div style={{ fontSize: 13, fontWeight: 600 }}>{unresolvedFlags}/{annotationsFlagged}</div>
            <div style={{ fontSize: 12, color: 'var(--text-secondary)', textAlign: 'center' }}>Unresolved</div>
          </div>
        </div>
      </div>

      {recordDialogOpen && (
        <div className="modal-overlay active">
          <div className="modal">
            <div className="modal-header">
              <div>
                <div className="modal-title">Record Ontology Change</div>
              </div>
              <button className="modal-close" onClick={() => setRecordDialogOpen(false)} aria-label="Close">
                <XMarkIcon style={{ width: 16, height: 16 }} />
              </button>
            </div>
            <div className="modal-body">
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

              <div className="form-row">
                <label className="form-label" htmlFor="newVersionTag">
                  New version tag
                </label>
                <input
                  id="newVersionTag"
                  className="form-input"
                  value={newVersionTag}
                  onChange={(e) => setNewVersionTag(e.target.value)}
                />
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
                    Informational only - points on the deprecated label are still flagged dirty for review.
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
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setRecordDialogOpen(false)}>
                Cancel
              </button>
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
      )}
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
            <XMarkIcon style={{ width: 14, height: 14 }} />
          </button>
        )}
      </div>
    </div>
  )
}

