import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { TransformComponent, TransformWrapper } from 'react-zoom-pan-pinch'
import type { ReactZoomPanPinchContentRef } from 'react-zoom-pan-pinch'
import {
  fetchDirtyAnnotations,
  fetchOntologyChanges,
  recordOntologyChange,
  resolveDirtyAnnotation,
} from '../api/ontology'
import { fetchVersion, fetchVersions } from '../api/versions'
import { imageFileUrlById } from '../api/images'
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

function dirtyQueueDotColor(points: DirtyAnnotation[]): string {
  if (points.length === 0) return 'var(--text-muted)'
  const resolved = points.filter((p) => p.resolved).length
  if (resolved === 0) return '#2563EB'
  if (resolved < points.length) return '#D97706'
  return '#16A34A'
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

  const [selectedDirtyImageId, setSelectedDirtyImageId] = useState<number | null>(null)
  const [selectedDirtyPointId, setSelectedDirtyPointId] = useState<number | null>(null)
  const [canvasNaturalSize, setCanvasNaturalSize] = useState<{ width: number; height: number } | null>(null)
  const [dotsVisible, setDotsVisible] = useState(true)
  const [displayScale, setDisplayScale] = useState(1)
  const transformRef = useRef<ReactZoomPanPinchContentRef>(null)

  const dirtyImageIds = useMemo(() => {
    const seen = new Set<number>()
    const order: number[] = []
    for (const d of dirtyAnnotations) {
      if (!seen.has(d.imageId)) {
        seen.add(d.imageId)
        order.push(d.imageId)
      }
    }
    return order
  }, [dirtyAnnotations])

  const dirtyByImage = useMemo(() => {
    const map = new Map<number, DirtyAnnotation[]>()
    for (const d of dirtyAnnotations) {
      const list = map.get(d.imageId) ?? []
      list.push(d)
      map.set(d.imageId, list)
    }
    return map
  }, [dirtyAnnotations])

  // Re-selects the first image whenever the underlying dirty set changes (new change selected, or
  // a resolve action reshuffles the list) — derived during render, same pattern as syncedChangeType.
  const dirtyKey = dirtyImageIds.join(',')
  const [syncedDirtyKey, setSyncedDirtyKey] = useState('')
  if (dirtyKey !== syncedDirtyKey) {
    setSyncedDirtyKey(dirtyKey)
    setSelectedDirtyImageId((prev) => (prev != null && dirtyImageIds.includes(prev) ? prev : dirtyImageIds[0] ?? null))
    setCanvasNaturalSize(null)
  }

  const currentImagePoints = selectedDirtyImageId != null ? dirtyByImage.get(selectedDirtyImageId) ?? [] : []
  const currentFilename = currentImagePoints[0]?.filename ?? ''
  const effectiveSelectedPointId = selectedDirtyPointId ?? currentImagePoints[0]?.id ?? null

  // Selecting a point (dot click, card click, or a fresh image's default point) always re-centers
  // the view on it at whatever zoom level is currently active — dragging is for fine-adjustment
  // afterward, not "sticky" across selection. Also scrolls the matching card into view, since the
  // card list scrolls independently of the page now.
  useEffect(() => {
    if (effectiveSelectedPointId == null) return
    document.getElementById(`dirty-card-${effectiveSelectedPointId}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    if (!transformRef.current || !canvasNaturalSize) return
    const dotEl = document.getElementById(`dirty-dot-${effectiveSelectedPointId}`)
    if (!dotEl) return
    transformRef.current.zoomToElement(dotEl, transformRef.current.state.scale)
  }, [effectiveSelectedPointId, canvasNaturalSize])

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

  const allowedResolveLabels = useMemo(() => {
    if (!selectedChange) return labelOptions
    const allowedCodes = new Set<string>(selectedChange.newLabelCodes ?? [])
    if (selectedChange.replacementLabelCode) allowedCodes.add(selectedChange.replacementLabelCode)
    const filtered = labelOptions.filter((l) => allowedCodes.has(l.labelCode))
    return filtered.length > 0 ? filtered : labelOptions
  }, [selectedChange, labelOptions])

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

            {dirtyError && (
              <div className="alert alert-error" style={{ marginBottom: 16 }}>
                {dirtyError}
              </div>
            )}
            {selectedChangeId == null ? (
              <div className="card">
                <div className="empty-state">
                  <p>Select an ontology change to view its dirty annotation queue.</p>
                </div>
              </div>
            ) : dirtyLoading ? (
              <div className="card">
                <div className="loading-state">
                  <span className="spinner" /> Loading dirty annotations…
                </div>
              </div>
            ) : dirtyAnnotations.length === 0 ? (
              <div className="card">
                <div className="empty-state">
                  <p>No dirty annotations for this change.</p>
                </div>
              </div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: 16 }}>
                {/* Left: image queue */}
                <div className="card" style={{ padding: 0, maxHeight: '70vh', overflowY: 'auto' }}>
                  {dirtyImageIds.map((imgId) => {
                    const points = dirtyByImage.get(imgId) ?? []
                    return (
                      <div
                        key={imgId}
                        className={`queue-list-item${imgId === selectedDirtyImageId ? ' selected' : ''}`}
                        onClick={() => {
                          setSelectedDirtyImageId(imgId)
                          setSelectedDirtyPointId(null)
                          setCanvasNaturalSize(null)
                        }}
                      >
                        <span className="queue-dot" style={{ background: dirtyQueueDotColor(points) }} />
                        <span className="mono" style={{ fontSize: 12 }}>{points[0]?.filename ?? `image ${imgId}`}</span>
                      </div>
                    )
                  })}
                </div>

                <div className="review-layout">
                  {/* Center: image canvas + point overlays */}
                  <div>
                    <div style={{ display: 'flex', gap: 8, marginBottom: 8, alignItems: 'center' }}>
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={() => setDotsVisible((v) => !v)}
                      >
                        {dotsVisible ? 'Hide points' : 'Show points'}
                      </button>
                      <div style={{ marginLeft: 'auto', display: 'flex', gap: 4, alignItems: 'center' }}>
                        <button
                          className="btn btn-secondary btn-sm"
                          onClick={() => transformRef.current?.zoomOut()}
                          disabled={displayScale <= 1}
                        >
                          −
                        </button>
                        <span style={{ fontSize: 12, color: 'var(--text-secondary)', width: 40, textAlign: 'center' }}>
                          {Math.round(displayScale * 100)}%
                        </span>
                        <button
                          className="btn btn-secondary btn-sm"
                          onClick={() => transformRef.current?.zoomIn()}
                          disabled={displayScale >= 4}
                        >
                          +
                        </button>
                        {displayScale !== 1 && (
                          <button className="btn btn-secondary btn-sm" onClick={() => transformRef.current?.resetTransform()}>
                            Reset
                          </button>
                        )}
                      </div>
                    </div>
                    <div className="image-canvas">
                      {selectedDirtyImageId != null ? (
                        <TransformWrapper
                          key={selectedDirtyImageId}
                          ref={transformRef}
                          initialScale={1}
                          minScale={1}
                          maxScale={4}
                          centerOnInit
                          onTransform={(_ref, state) => setDisplayScale(state.scale)}
                        >
                          <TransformComponent wrapperStyle={{ width: '100%', height: '100%' }} contentStyle={{ width: '100%', height: '100%' }}>
                            <div style={{ position: 'relative', width: '100%', height: '100%' }}>
                              <img
                                src={imageFileUrlById(selectedDirtyImageId)}
                                alt={currentFilename}
                                style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
                                onLoad={(e) => {
                                  const img = e.currentTarget
                                  setCanvasNaturalSize({ width: img.naturalWidth, height: img.naturalHeight })
                                }}
                              />
                              {dotsVisible &&
                                canvasNaturalSize &&
                                currentImagePoints.map((p) => {
                                  if (p.rowPosition == null || p.colPosition == null) return null
                                  const dotClass =
                                    p.id === effectiveSelectedPointId
                                      ? 'dot-selected'
                                      : p.resolved
                                        ? 'dot-accepted'
                                        : 'dot-pending'
                                  return (
                                    <div
                                      key={p.id}
                                      id={`dirty-dot-${p.id}`}
                                      className={`annotation-dot ${dotClass}`}
                                      style={{
                                        top: `${(p.rowPosition / canvasNaturalSize.height) * 100}%`,
                                        left: `${(p.colPosition / canvasNaturalSize.width) * 100}%`,
                                      }}
                                      title={`${p.currentLabelCode}${p.resolved ? ` → ${p.newLabelCode}` : ''}`}
                                      onClick={() => setSelectedDirtyPointId(p.id)}
                                    >
                                      {p.pointIndex}
                                    </div>
                                  )
                                })}
                            </div>
                          </TransformComponent>
                        </TransformWrapper>
                      ) : (
                        <div className="image-placeholder">No image selected</div>
                      )}
                    </div>
                    <div style={{ display: 'flex', gap: 16, marginTop: 10, fontSize: 11, color: 'var(--text-secondary)' }}>
                      <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#16A34A', display: 'inline-block' }} />
                        Resolved
                      </span>
                      <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#2563EB', display: 'inline-block' }} />
                        Pending
                      </span>
                    </div>
                  </div>

                  {/* Right: one card per dirty point on the selected image */}
                  <div className="review-panel">
                    {currentImagePoints.map((dirty) => (
                      <DirtyPointCard
                        key={dirty.id}
                        dirty={dirty}
                        selected={dirty.id === effectiveSelectedPointId}
                        labelOptions={allowedResolveLabels}
                        onSelect={() => setSelectedDirtyPointId(dirty.id)}
                        onResolve={handleResolve}
                      />
                    ))}
                  </div>
                </div>
              </div>
            )}
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

function DirtyPointCard({
  dirty,
  selected,
  labelOptions,
  onSelect,
  onResolve,
}: {
  dirty: DirtyAnnotation
  selected: boolean
  labelOptions: LabelDefinition[]
  onSelect: () => void
  onResolve: (dirty: DirtyAnnotation, newLabelCode: string) => void
}) {
  const [picked, setPicked] = useState('')

  return (
    <div id={`dirty-card-${dirty.id}`} className={`review-point-card${selected ? ' selected' : ''}`} onClick={onSelect}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <span style={{ fontSize: 12, fontWeight: 600 }}>Point #{dirty.pointIndex}</span>
        {dirty.resolved ? (
          <span className="badge badge-green">Resolved</span>
        ) : (
          <span className="badge" style={{ background: 'var(--coral, #e05d44)', color: '#fff' }}>
            {dirty.currentLabelCode}
          </span>
        )}
      </div>
      <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 8 }}>
        Flagged {formatDate(dirty.flaggedAt)}
      </div>

      {dirty.resolved ? (
        <div style={{ fontSize: 13 }}>
          <span className="mono">{dirty.currentLabelCode}</span> → <span className="mono">{dirty.newLabelCode}</span>
          {dirty.resolvedBy && (
            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>by {dirty.resolvedBy}</div>
          )}
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }} onClick={(e) => e.stopPropagation()}>
          <LabelCombobox
            options={labelOptions}
            value={picked}
            onChange={setPicked}
            placeholder="Search allowed labels…"
          />
          <button
            className="btn btn-secondary"
            disabled={!picked}
            onClick={() => onResolve(dirty, picked)}
          >
            Resolve
          </button>
        </div>
      )}
    </div>
  )
}
