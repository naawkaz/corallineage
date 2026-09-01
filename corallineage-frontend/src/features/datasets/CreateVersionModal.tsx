import { useEffect, useState } from 'react'
import { XMarkIcon } from '@heroicons/react/24/outline'
import { createVersion, fetchVersion, fetchVersions } from '../datasets/versionsApi'
import { submitImport } from '../datasets/importsApi'
import { errorMessage } from '../../shared/api/client'
import { useAuthContext } from '../../shared/lib/authContextCore'
import { useDatasetContext } from '../../shared/lib/datasetContextCore'
import { useToast } from '../../shared/lib/toastContextCore'
import type { CreateVersionRequest, DatasetVersion } from '../datasets/versionsTypes'
import type { ImportFormat, LabelDeclaration } from '../datasets/importsTypes'

interface CreateVersionModalProps {
  datasetId: number
  open: boolean
  onClose: () => void
  // Fires right after a successful submit (snapshot created, or import job accepted). The parent
  // closes the dialog and refreshes its list.
  onSubmitted: () => void
}

const FORMATS: { value: ImportFormat; label: string; hint: string }[] = [
  { value: 'BLANK', label: 'Unlabeled images', hint: 'Will not be committed as a new dataset version until all points are resolved' },
  { value: 'CORALNET_CSV', label: 'CoralNet CSV', hint: 'Single CSV: image_filename, row, col, label_code' },
  { value: 'MLC', label: 'MLC', hint: 'One .txt per image, lines of "row col label_code"' },
  { value: 'COCO_JSON', label: 'COCO JSON', hint: 'annotations.json with images/annotations/categories' },
]

/**
 * "Add Images" dialog on the Source Dashboard. Covers a bare manual snapshot (no files picked)
 * and a full dataset import (MLC / CoralNet CSV / COCO JSON / Blank formats, directory upload,
 * Blank-format label declaration with cross-source label reuse).
 *
 * Dismisses itself as soon as the submit request is accepted rather than waiting for the import
 * to process. Import status and the Blank-import "Resolve" step live in VersionsList's table row.
 */
// The parent mounts this unconditionally, so the wrapper renders the inner component only while
// `open` is true. Each open is then a fresh mount with fresh initial state; no reset-on-open
// effect is needed here.
export default function CreateVersionModal(props: CreateVersionModalProps) {
  if (!props.open) return null
  return <CreateVersionModalInner {...props} />
}

function CreateVersionModalInner({ datasetId, onClose, onSubmitted }: Omit<CreateVersionModalProps, 'open'>) {
  const { user } = useAuthContext()
  const { datasets } = useDatasetContext()
  const toast = useToast()

  const [versionTag, setVersionTag] = useState('')
  const [notes, setNotes] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const [format, setFormat] = useState<ImportFormat>('MLC')
  const [files, setFiles] = useState<File[]>([])
  const [declaredLabels, setDeclaredLabels] = useState<LabelDeclaration[]>([])

  // "Reuse labels from <source> <version>": a two-dropdown picker scoped to sibling sources in
  // the same project. The label set is structural (code, name, optional category), not free text.
  const [reuseSourceId, setReuseSourceId] = useState<number | ''>('')
  const [reuseVersions, setReuseVersions] = useState<DatasetVersion[]>([])
  const [reuseVersionId, setReuseVersionId] = useState<number | ''>('')
  const [reuseVersionsLoading, setReuseVersionsLoading] = useState(false)
  const [reuseApplying, setReuseApplying] = useState(false)
  const [reuseApplied, setReuseApplied] = useState(false)
  const [reuseError, setReuseError] = useState<string | null>(null)

  // "Import from JSON file": populates declaredLabels from the shape produced by VersionDetail's
  // "Export label set" button.
  const [labelsFileError, setLabelsFileError] = useState<string | null>(null)

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
      toast.push({
        variant: 'success',
        title: `${labels.length} label${labels.length === 1 ? '' : 's'} loaded`,
        detail: file.name,
      })
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to parse label set JSON file.'
      setLabelsFileError(message)
      toast.push({ variant: 'error', title: 'Label set upload failed', detail: message })
    }
  }

  const isBlank = format === 'BLANK'

  function handleClose() {
    onClose()
  }

  // Returns as soon as the request is accepted; the import processes in the background.
  async function handleSubmit() {
    setSubmitting(true)
    setSubmitError(null)
    const hasFiles = files.length > 0
    const toastId = hasFiles ? toast.push({ variant: 'progress', title: 'Uploading images...' }) : null
    try {
      if (!hasFiles) {
        // Bare manual snapshot: no files, no import job. Trigger reason is always MANUAL_SNAPSHOT.
        const request: CreateVersionRequest = {
          versionTag: versionTag.trim(),
          triggerReason: 'MANUAL_SNAPSHOT',
          createdBy: user?.username ?? '',
          notes: notes.trim() === '' ? null : notes.trim(),
        }
        await createVersion(datasetId, request)
      } else {
        await submitImport(
          datasetId, files, format, versionTag.trim(), user?.username ?? '', notes, isBlank ? declaredLabels : undefined,
        )
        if (toastId) {
          toast.update(toastId, {
            variant: 'success',
            title: `${files.length} image${files.length === 1 ? '' : 's'} uploaded`,
            detail: 'Import is processing in the background.',
          })
        }
      }
      onSubmitted()
    } catch (err) {
      const message = errorMessage(err)
      setSubmitError(message)
      if (toastId) {
        toast.update(toastId, { variant: 'error', title: 'Upload failed', detail: message })
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="modal-overlay active">
      <div className="modal" style={{ width: 680, maxWidth: '100%' }}>
        <div className="modal-header">
          <div>
            <div className="modal-title">Add Images</div>
            <div className="modal-subtitle">Importing images will trigger creation of a new dataset version</div>
          </div>
          <button className="modal-close" onClick={handleClose} aria-label="Close">
            <XMarkIcon style={{ width: 16, height: 16 }} />
          </button>
        </div>
        <div className="modal-body" style={{ maxHeight: '70vh', overflowY: 'auto' }}>
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
              {isBlank ? 'Images' : 'Dataset directory'}
            </label>
            {isBlank ? (
              <div
                onDragOver={(e) => e.preventDefault()}
                onDrop={(e) => {
                  e.preventDefault()
                  const dropped = Array.from(e.dataTransfer.files)
                  if (dropped.length > 0) setFiles((prev) => [...prev, ...dropped])
                }}
                style={{
                  border: '1px dashed var(--border-md)',
                  borderRadius: 6,
                  padding: 16,
                  textAlign: 'center',
                }}
              >
                <input
                  id="importFile"
                  type="file"
                  multiple
                  onChange={(e) => setFiles(e.target.files ? Array.from(e.target.files) : [])}
                />
                <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 8 }}>
                  Or drag and drop image files here
                </div>
              </div>
            ) : (
              <input
                id="importFile"
                type="file"
                ref={(el) => {
                  if (el) el.setAttribute('webkitdirectory', '')
                }}
                multiple
                onChange={(e) => setFiles(e.target.files ? Array.from(e.target.files) : [])}
              />
            )}
            {files.length > 0 && (
              <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 4 }}>
                {files.length} file{files.length === 1 ? '' : 's'} selected
              </div>
            )}
            {files.length > 0 && !isBlank && <FileTreePreview files={files} />}
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
                  <option value="">a source...</option>
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
                    {reuseVersionsLoading ? 'loading...' : 'a version...'}
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
                  {reuseApplying ? 'Loading...' : 'Use these labels'}
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
        </div>
        <div className="modal-footer">
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
            {submitting ? 'Creating...' : files.length === 0 ? 'Create version' : isBlank ? 'Upload' : 'Add Folder'}
          </button>
        </div>
      </div>
    </div>
  )
}

// Read-only, grouped-by-subfolder listing of a selected `webkitdirectory` file set, so the user
// can sanity-check what they are about to upload.
function FileTreePreview({ files }: { files: File[] }) {
  const groups = new Map<string, string[]>()
  for (const file of files) {
    const relativePath = (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name
    const slashIndex = relativePath.lastIndexOf('/')
    const folder = slashIndex === -1 ? '' : relativePath.slice(0, slashIndex)
    const list = groups.get(folder) ?? []
    list.push(relativePath.slice(slashIndex + 1))
    groups.set(folder, list)
  }

  return (
    <div
      style={{
        marginTop: 8,
        maxHeight: 180,
        overflowY: 'auto',
        border: '1px solid var(--border)',
        borderRadius: 6,
        fontSize: 12,
      }}
    >
      {[...groups.entries()].map(([folder, names]) => (
        <div key={folder} style={{ padding: '6px 10px', borderBottom: '1px solid var(--border)' }}>
          {folder && <div style={{ fontWeight: 600, marginBottom: 2 }}>{folder}/</div>}
          <div style={{ color: 'var(--text-secondary)' }}>
            {names.length} file{names.length === 1 ? '' : 's'}: {names.slice(0, 5).join(', ')}
            {names.length > 5 ? `, +${names.length - 5} more` : ''}
          </div>
        </div>
      ))}
    </div>
  )
}
