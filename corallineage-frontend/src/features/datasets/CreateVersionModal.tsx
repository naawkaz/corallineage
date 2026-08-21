import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { XMarkIcon } from '@heroicons/react/24/outline'
import { createVersion, fetchVersion, fetchVersions } from '../datasets/versionsApi'
import { fetchImportJob, submitImport } from '../datasets/importsApi'
import { startPointAnnotationJob } from '../pointannotation/pointAnnotationApi'
import { errorMessage } from '../../shared/api/client'
import { useAuthContext } from '../../shared/lib/authContextCore'
import { useDatasetContext } from '../../shared/lib/datasetContextCore'
import type { CreateVersionRequest, DatasetVersion } from '../datasets/versionsTypes'
import type { ImportFormat, ImportJob, LabelDeclaration } from '../datasets/importsTypes'

interface CreateVersionModalProps {
  datasetId: number
  open: boolean
  onClose: () => void
  onCreated: (version: DatasetVersion) => void
}

const FORMATS: { value: ImportFormat; label: string; hint: string }[] = [
  { value: 'MLC', label: 'MLC', hint: 'One .txt per image, lines of "row col label_code"' },
  { value: 'CORALNET_CSV', label: 'CoralNet CSV', hint: 'Single CSV: image_filename, row, col, label_code' },
  { value: 'COCO_JSON', label: 'COCO JSON', hint: 'annotations.json with images/annotations/categories' },
  { value: 'BLANK', label: 'Blank / Unlabeled Dataset', hint: 'Just images, declare your label set below, then label points afterward' },
]

function jobNumericId(job: ImportJob): number {
  return Number(job.importJobId.replace('IMP-', ''))
}

/**
 * "+ New Version" dialog on the Source Dashboard (rendered from VersionsList.tsx). Covers both
 * a bare manual snapshot (no files picked, triggerReason hardcoded to MANUAL_SNAPSHOT, no UI
 * control for it) and a full dataset import (MLC/CoralNet CSV/COCO JSON/Blank formats, directory
 * upload, Blank-format label declaration with cross-source label reuse, and a post-import "start
 * point annotation" step) - folded in from what used to be the standalone Import page.
 */
export default function CreateVersionModal({ datasetId, open, onClose, onCreated }: CreateVersionModalProps) {
  const navigate = useNavigate()
  const { user } = useAuthContext()
  const { datasets } = useDatasetContext()

  const [versionTag, setVersionTag] = useState('')
  const [notes, setNotes] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const [format, setFormat] = useState<ImportFormat>('MLC')
  const [files, setFiles] = useState<File[]>([])
  const [declaredLabels, setDeclaredLabels] = useState<LabelDeclaration[]>([])
  const [job, setJob] = useState<ImportJob | null>(null)
  const [pollError, setPollError] = useState<string | null>(null)

  // "Reuse labels from <source> <version>", a two-dropdown sentence rather than a free-text
  // vocabulary field, since the label set is structural (code, name, optional category), not
  // prose. Scoped to sibling Sources within the same Project as this one.
  const [reuseSourceId, setReuseSourceId] = useState<number | ''>('')
  const [reuseVersions, setReuseVersions] = useState<DatasetVersion[]>([])
  const [reuseVersionId, setReuseVersionId] = useState<number | ''>('')
  const [reuseVersionsLoading, setReuseVersionsLoading] = useState(false)
  const [reuseApplying, setReuseApplying] = useState(false)
  const [reuseApplied, setReuseApplied] = useState(false)
  const [reuseError, setReuseError] = useState<string | null>(null)

  const [newLabelCode, setNewLabelCode] = useState('')
  const [newLabelName, setNewLabelName] = useState('')
  const [newLabelCategory, setNewLabelCategory] = useState('')

  // "Import from JSON file", a third way to populate declaredLabels alongside reuse and
  // manual add. Accepts the same shape produced by VersionDetail's "Export label set" button.
  const [labelsFileError, setLabelsFileError] = useState<string | null>(null)

  const [seedImageCount, setSeedImageCount] = useState(20)
  const [confidenceThreshold, setConfidenceThreshold] = useState(0.85)
  const [gridRows, setGridRows] = useState(7)
  const [gridCols, setGridCols] = useState(7)
  const [startingPointAnnotation, setStartingPointAnnotation] = useState(false)
  const [pointAnnotationError, setPointAnnotationError] = useState<string | null>(null)

  const pollingRef = useRef<number | null>(null)

  useEffect(() => {
    if (!job || job.status !== 'PROCESSING') {
      return
    }
    const intervalId = window.setInterval(() => {
      fetchImportJob(datasetId, jobNumericId(job))
        .then((updated) => {
          setJob(updated)
          if (updated.status !== 'PROCESSING' && pollingRef.current !== null) {
            window.clearInterval(pollingRef.current)
            pollingRef.current = null
          }
        })
        .catch((err) => setPollError(errorMessage(err)))
    }, 2000)
    pollingRef.current = intervalId
    return () => {
      window.clearInterval(intervalId)
      pollingRef.current = null
    }
  }, [job, datasetId])

  useEffect(() => {
    if (reuseSourceId === '') {
      setReuseVersions([])
      setReuseVersionId('')
      return
    }
    let cancelled = false
    setReuseVersionsLoading(true)
    setReuseError(null)
    setReuseApplied(false)
    fetchVersions(reuseSourceId, 0, 100)
      .then((page) => {
        if (cancelled) return
        setReuseVersions(page.content)
        setReuseVersionId(page.content[0]?.id ?? '')
      })
      .catch((err) => {
        if (!cancelled) setReuseError(errorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setReuseVersionsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [reuseSourceId])

  async function applyReuseSelection() {
    if (reuseSourceId === '' || reuseVersionId === '') return
    setReuseApplying(true)
    setReuseError(null)
    try {
      const full = await fetchVersion(reuseSourceId, reuseVersionId)
      const labels: LabelDeclaration[] = (full.labelDefinitions ?? [])
        .filter((l) => !l.deprecated)
        .map((l) => ({
          labelCode: l.labelCode,
          labelName: l.labelName,
          labelCategory: l.labelCategory ?? undefined,
        }))
      setDeclaredLabels(labels)
      setReuseApplied(true)
    } catch (err) {
      setReuseError(errorMessage(err))
    } finally {
      setReuseApplying(false)
    }
  }

  function addDeclaredLabel() {
    const code = newLabelCode.trim()
    const name = newLabelName.trim()
    if (code === '' || name === '') return
    if (declaredLabels.some((l) => l.labelCode === code)) return
    const category = newLabelCategory.trim()
    setDeclaredLabels((prev) => [
      ...prev,
      { labelCode: code, labelName: name, labelCategory: category === '' ? undefined : category },
    ])
    setNewLabelCode('')
    setNewLabelName('')
    setNewLabelCategory('')
  }

  function removeDeclaredLabel(labelCode: string) {
    setDeclaredLabels((prev) => prev.filter((l) => l.labelCode !== labelCode))
  }

  async function handleLabelsFileImport(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setLabelsFileError(null)
    try {
      const text = await file.text()
      const parsed: unknown = JSON.parse(text)
      if (!Array.isArray(parsed)) {
        throw new Error('File must contain a JSON array of labels.')
      }
      const labels: LabelDeclaration[] = parsed.map((item, idx) => {
        if (
          typeof item !== 'object' ||
          item === null ||
          typeof (item as Record<string, unknown>).labelCode !== 'string' ||
          typeof (item as Record<string, unknown>).labelName !== 'string'
        ) {
          throw new Error(`Entry ${idx + 1} is missing a valid labelCode or labelName.`)
        }
        const record = item as Record<string, unknown>
        return {
          labelCode: record.labelCode as string,
          labelName: record.labelName as string,
          labelCategory: typeof record.labelCategory === 'string' ? record.labelCategory : undefined,
        }
      })
      setDeclaredLabels(labels)
    } catch (err) {
      setLabelsFileError(err instanceof Error ? err.message : 'Failed to parse label set JSON file.')
    }
  }

  const isBlank = format === 'BLANK'

  function resetAll() {
    setJob(null)
    setSubmitError(null)
    setPollError(null)
    setPointAnnotationError(null)
    setFiles([])
    setVersionTag('')
    setNotes('')
    setDeclaredLabels([])
    setReuseSourceId('')
    setReuseVersions([])
    setReuseVersionId('')
    setReuseApplied(false)
    setReuseError(null)
    setNewLabelCode('')
    setNewLabelName('')
    setNewLabelCategory('')
    setLabelsFileError(null)
    setFormat('MLC')
    setSeedImageCount(20)
    setConfidenceThreshold(0.85)
    setGridRows(7)
    setGridCols(7)
  }

  function handleClose() {
    resetAll()
    onClose()
  }

  // Dialogs shouldn't remember state from a previous open, reset whenever it's opened
  // rather than relying solely on handleClose (which is skipped by e.g. a parent
  // programmatically flipping `open` to false without going through this component's own
  // close handlers).
  useEffect(() => {
    if (open) resetAll()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  if (!open) return null

  async function handleSubmit() {
    setSubmitting(true)
    setSubmitError(null)
    setPollError(null)
    try {
      if (files.length === 0) {
        // Bare manual snapshot - no files picked, no import job needed. Trigger reason is
        // always MANUAL_SNAPSHOT here since this path has no other way to have been reached.
        const request: CreateVersionRequest = {
          versionTag: versionTag.trim(),
          triggerReason: 'MANUAL_SNAPSHOT',
          createdBy: user?.username ?? '',
          notes: notes.trim() === '' ? null : notes.trim(),
        }
        const created = await createVersion(datasetId, request)
        onCreated(created)
        return
      }
      const created = await submitImport(
        datasetId, files, format, versionTag.trim(), user?.username ?? '', notes, isBlank ? declaredLabels : undefined,
      )
      setJob(created)
    } catch (err) {
      setSubmitError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleStartPointAnnotation() {
    if (!job?.createdDatasetVersionId) return
    setStartingPointAnnotation(true)
    setPointAnnotationError(null)
    try {
      const pointJob = await startPointAnnotationJob({
        datasetVersionId: job.createdDatasetVersionId,
        seedImageCount,
        confidenceThreshold,
        gridRows,
        gridCols,
        initiatedBy: user?.username ?? '',
      })
      handleClose()
      navigate(`/sources/${datasetId}/blank-import/jobs/${pointJob.jobId}/label`)
    } catch (err) {
      setPointAnnotationError(errorMessage(err))
    } finally {
      setStartingPointAnnotation(false)
    }
  }

  return (
    <div className="modal-overlay active">
      <div className="modal" style={{ width: 680, maxWidth: '100%' }}>
        <div className="modal-header">
          <div>
            <div className="modal-title">Import images</div>
          </div>
          <button className="modal-close" onClick={handleClose} aria-label="Close">
            <XMarkIcon style={{ width: 16, height: 16 }} />
          </button>
        </div>
        <div className="modal-body" style={{ maxHeight: '70vh', overflowY: 'auto' }}>
          {!job && (
            <>
              <div className="form-row">
                <label className="form-label">Format</label>
                {FORMATS.map((f) => (
                  <label key={f.value} style={{ display: 'flex', alignItems: 'flex-start', gap: 8, marginBottom: 8 }}>
                    <input
                      type="radio"
                      name="format"
                      checked={format === f.value}
                      onChange={() => setFormat(f.value)}
                    />
                    <span>
                      <strong>{f.label}</strong>
                      <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{f.hint}</div>
                    </span>
                  </label>
                ))}
              </div>

              <div className="form-row">
                <label className="form-label" htmlFor="importFile">
                  Dataset directory
                </label>
                <input
                  id="importFile"
                  type="file"
                  ref={(el) => {
                    if (el) el.setAttribute('webkitdirectory', '')
                  }}
                  multiple
                  onChange={(e) => setFiles(e.target.files ? Array.from(e.target.files) : [])}
                />
                {files.length > 0 && (
                  <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 4 }}>
                    {files.length} file{files.length === 1 ? '' : 's'} selected
                  </div>
                )}
              </div>

              {files.length > 0 && isBlank && (
                <div className="form-row">
                  <label className="form-label">Label set</label>

                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      flexWrap: 'wrap',
                      gap: 8,
                      fontSize: 13,
                      marginBottom: 10,
                    }}
                  >
                    <span>Reuse labels from</span>
                    <select
                      className="form-input"
                      style={{ width: 'auto' }}
                      value={reuseSourceId}
                      onChange={(e) => setReuseSourceId(e.target.value === '' ? '' : Number(e.target.value))}
                    >
                      <option value="">a source…</option>
                      {datasets.map((d) => (
                        <option key={d.id} value={d.id}>
                          {d.datasetName}
                        </option>
                      ))}
                    </select>
                    <select
                      className="form-input"
                      style={{ width: 'auto' }}
                      value={reuseVersionId}
                      disabled={reuseSourceId === '' || reuseVersionsLoading || reuseVersions.length === 0}
                      onChange={(e) => {
                        setReuseVersionId(e.target.value === '' ? '' : Number(e.target.value))
                        setReuseApplied(false)
                      }}
                    >
                      <option value="">
                        {reuseVersionsLoading ? 'loading…' : 'a version…'}
                      </option>
                      {reuseVersions.map((v) => (
                        <option key={v.id} value={v.id}>
                          {v.versionTag}
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      className={`btn btn-sm ${reuseSourceId !== '' && reuseVersionId !== '' && !reuseApplied ? 'btn-primary' : 'btn-secondary'}`}
                      onClick={applyReuseSelection}
                      disabled={reuseSourceId === '' || reuseVersionId === '' || reuseApplying}
                    >
                      {reuseApplying ? 'Loading…' : 'Use these labels'}
                    </button>
                  </div>
                  {reuseError && (
                    <div className="alert alert-error" style={{ marginBottom: 10 }}>
                      {reuseError}
                    </div>
                  )}

                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      flexWrap: 'wrap',
                      gap: 8,
                      fontSize: 13,
                      marginBottom: 10,
                    }}
                  >
                    <span>Or import from JSON file</span>
                    <input
                      type="file"
                      accept="application/json"
                      className="form-input"
                      style={{ width: 'auto' }}
                      onChange={handleLabelsFileImport}
                    />
                  </div>
                  {labelsFileError && (
                    <div className="alert alert-error" style={{ marginBottom: 10 }}>
                      {labelsFileError}
                    </div>
                  )}

                  {declaredLabels.length > 0 && (
                    <div style={{ border: '1px solid var(--border)', borderRadius: 6, marginBottom: 10 }}>
                      {declaredLabels.map((label) => (
                        <div
                          key={label.labelCode}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            padding: '6px 10px',
                            borderBottom: '1px solid var(--border)',
                          }}
                        >
                          <span style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
                            <span className="mono">{label.labelCode}</span>
                            <span>{label.labelName}</span>
                            {label.labelCategory && <span className="tag">{label.labelCategory}</span>}
                          </span>
                          <button
                            type="button"
                            onClick={() => removeDeclaredLabel(label.labelCode)}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              background: 'none',
                              border: 'none',
                              cursor: 'pointer',
                              color: 'var(--text-secondary)',
                            }}
                            title="Remove"
                          >
                            <XMarkIcon style={{ width: 14, height: 14 }} />
                          </button>
                        </div>
                      ))}
                    </div>
                  )}

                  {/* <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'flex-end' }}>
                    <div>
                      <label className="form-label" htmlFor="newLabelCode">
                        Label code
                      </label>
                      <input
                        id="newLabelCode"
                        className="form-input"
                        placeholder="e.g. CCA"
                        value={newLabelCode}
                        onChange={(e) => setNewLabelCode(e.target.value)}
                      />
                    </div>
                    <div>
                      <label className="form-label" htmlFor="newLabelName">
                        Label name
                      </label>
                      <input
                        id="newLabelName"
                        className="form-input"
                        placeholder="e.g. Crustose Coralline Algae"
                        value={newLabelName}
                        onChange={(e) => setNewLabelName(e.target.value)}
                      />
                    </div>
                    <div>
                      <label className="form-label" htmlFor="newLabelCategory">
                        Category (optional)
                      </label>
                      <input
                        id="newLabelCategory"
                        className="form-input"
                        placeholder="e.g. Algae"
                        value={newLabelCategory}
                        onChange={(e) => setNewLabelCategory(e.target.value)}
                      />
                    </div>
                    <button
                      type="button"
                      className="btn btn-secondary"
                      onClick={addDeclaredLabel}
                      disabled={!newLabelCode.trim() || !newLabelName.trim()}
                    >
                      Add
                    </button>
                  </div> */}
                  {/* <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 6 }}>
                    {declaredLabels.length > 0
                      ? `${declaredLabels.length} label(s) declared`
                      : 'Declare the label set upfront, reused from another version or added by hand'}
                  </div> */}
                </div>
              )}

              <div className="form-row">
                <label className="form-label" htmlFor="versionTag">
                  Version tag
                </label>
                <input
                  id="versionTag"
                  className="form-input"
                  placeholder="e.g. v1.1"
                  value={versionTag}
                  onChange={(e) => setVersionTag(e.target.value)}
                />
                <div className="form-hint">Must be unique within this source.</div>
              </div>

              <div className="form-row" style={{ marginBottom: 0 }}>
                <label className="form-label" htmlFor="notes">
                  Notes
                </label>
                <textarea
                  id="notes"
                  className="form-textarea"
                  placeholder="Optional description"
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                />
              </div>

              {submitError && (
                <div className="alert alert-error" style={{ marginTop: 16 }}>
                  {submitError}
                </div>
              )}
            </>
          )}

          {job && job.status === 'PROCESSING' && (
            <>
              <div className="loading-state">
                <span className="spinner" /> Processing import ({job.importJobId})…
              </div>
              {pollError && (
                <div className="alert alert-error" style={{ marginTop: 12 }}>
                  {pollError}
                </div>
              )}
            </>
          )}

          {job && job.status === 'COMPLETED' && (
            <div>
              <div style={{ fontWeight: 600, marginBottom: 12 }}>Import completed</div>
              <div className="detail-grid">
                <div className="detail-row">
                  <div className="detail-key">Images imported</div>
                  <div>{job.imagesImported}</div>
                </div>
                <div className="detail-row">
                  <div className="detail-key">Annotation points imported</div>
                  <div>{job.annotationPointsImported}</div>
                </div>
                <div className="detail-row">
                  <div className="detail-key">Labels {job.format === 'BLANK' ? 'declared' : 'resolved'}</div>
                  <div>{job.labelsResolved}</div>
                </div>
                <div className="detail-row">
                  <div className="detail-key">New version</div>
                  <div>
                    <span className="mono">{job.createdVersionTag}</span>
                  </div>
                </div>
              </div>

              {job.format === 'BLANK' && (
                <>
                  <div style={{ borderTop: '1px solid var(--border)', margin: '16px 0' }} />
                  <div style={{ fontWeight: 600, marginBottom: 8 }}>Start Blank Import</div>
                  <div className="form-row">
                    <label className="form-label" htmlFor="seedImageCount">
                      Seed images to label manually
                    </label>
                    <input
                      id="seedImageCount"
                      type="number"
                      className="form-input"
                      min={1}
                      max={job.imagesImported ?? undefined}
                      value={seedImageCount}
                      onChange={(e) => setSeedImageCount(Number(e.target.value))}
                    />
                  </div>
                  <div className="form-row">
                    <label className="form-label" htmlFor="confidenceThreshold">
                      Confidence threshold ({Math.round(confidenceThreshold * 100)}%)
                    </label>
                    <input
                      id="confidenceThreshold"
                      type="range"
                      min={0.5}
                      max={0.99}
                      step={0.01}
                      value={confidenceThreshold}
                      onChange={(e) => setConfidenceThreshold(Number(e.target.value))}
                    />
                    <div className="form-hint">
                      Points the trained model predicts above this confidence auto-confirm. Rest reviewed manually.
                    </div>
                  </div>
                  <div className="form-row" style={{ marginBottom: 0 }}>
                    <label className="form-label">Grid size</label>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <input
                        type="number"
                        className="form-input"
                        min={1}
                        max={20}
                        style={{ width: 80 }}
                        value={gridRows}
                        onChange={(e) => setGridRows(Math.max(1, Math.min(20, Number(e.target.value) || 1)))}
                      />
                      <span>rows x</span>
                      <input
                        type="number"
                        className="form-input"
                        min={1}
                        max={20}
                        style={{ width: 80 }}
                        value={gridCols}
                        onChange={(e) => setGridCols(Math.max(1, Math.min(20, Number(e.target.value) || 1)))}
                      />
                      <span>cols</span>
                    </div>
                    <div className="form-hint">{gridRows * gridCols} points per image</div>
                  </div>
                  {pointAnnotationError && (
                    <div className="alert alert-error" style={{ marginTop: 12 }}>
                      {pointAnnotationError}
                    </div>
                  )}
                </>
              )}
            </div>
          )}

          {job && job.status === 'FAILED' && (
            <div>
              <div style={{ fontWeight: 600, marginBottom: 12, color: 'var(--red)' }}>Import failed</div>
              <div className="alert alert-error">{job.errorMessage}</div>
            </div>
          )}
        </div>
        <div className="modal-footer">
          {!job && (
            <>
              <button className="btn btn-secondary" onClick={handleClose}>
                Cancel
              </button>
              <button
                className="btn btn-primary"
                onClick={handleSubmit}
                disabled={
                  submitting ||
                  !versionTag.trim() ||
                  (files.length > 0 && isBlank && declaredLabels.length === 0)
                }
              >
                {submitting ? 'Creating…' : files.length > 0 ? 'Start Import' : 'Create version'}
              </button>
            </>
          )}
          {job && job.status === 'PROCESSING' && (
            <button className="btn btn-secondary" onClick={handleClose}>
              Close
            </button>
          )}
          {job && job.status === 'COMPLETED' && job.format === 'BLANK' && (
            <>
              <button className="btn btn-secondary" onClick={handleClose}>
                Close
              </button>
              <button
                className="btn btn-primary"
                onClick={handleStartPointAnnotation}
                disabled={startingPointAnnotation}
              >
                {startingPointAnnotation ? 'Generating points…' : 'Generate Points & Start Labeling'}
              </button>
            </>
          )}
          {job && job.status === 'COMPLETED' && job.format !== 'BLANK' && (
            <>
              <button className="btn btn-secondary" onClick={handleClose}>
                Close
              </button>
              <button
                className="btn btn-primary"
                onClick={() => {
                  const versionId = job.createdDatasetVersionId
                  handleClose()
                  if (versionId != null) {
                    navigate(`/sources/${datasetId}/versions/${versionId}`)
                  }
                }}
              >
                View version
              </button>
            </>
          )}
          {job && job.status === 'FAILED' && (
            <button className="btn btn-secondary" onClick={handleClose}>
              Close
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
