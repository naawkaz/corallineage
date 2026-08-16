import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { X } from 'lucide-react'
import { fetchImportJob, submitImport } from '../api/imports'
import { startPointAnnotationJob } from '../api/pointAnnotation'
import { fetchVersion, fetchVersions } from '../api/versions'
import { errorMessage } from '../api/client'
import { useAuthContext } from '../lib/authContextCore'
import { useDatasetContext } from '../lib/datasetContextCore'
import type { ImportFormat, ImportJob, LabelDeclaration } from '../types/import'
import type { DatasetVersion } from '../types/version'

const FORMATS: { value: ImportFormat; label: string; hint: string }[] = [
  { value: 'MLC', label: 'MLC', hint: 'One .txt per image, lines of "row col label_code"' },
  { value: 'CORALNET_CSV', label: 'CoralNet CSV', hint: 'Single CSV: image_filename, row, col, label_code' },
  { value: 'COCO_JSON', label: 'COCO JSON', hint: 'annotations.json with images/annotations/categories' },
  { value: 'BLANK', label: 'Blank / Unlabeled Dataset', hint: 'Just images, declare your label set below, then label points afterward' },
]

function jobNumericId(job: ImportJob): number {
  return Number(job.importJobId.replace('IMP-', ''))
}

export default function Import() {
  const navigate = useNavigate()
  const { projectId: projectIdParam } = useParams()
  const projectId = Number(projectIdParam)
  const { datasets } = useDatasetContext()
  const { user } = useAuthContext()
  const project = datasets.find((d) => d.id === projectId) ?? null

  const [format, setFormat] = useState<ImportFormat>('MLC')
  const [files, setFiles] = useState<File[]>([])
  const [versionTag, setVersionTag] = useState('')
  const [notes, setNotes] = useState('')
  const [declaredLabels, setDeclaredLabels] = useState<LabelDeclaration[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [job, setJob] = useState<ImportJob | null>(null)
  const [pollError, setPollError] = useState<string | null>(null)

  // "Reuse labels from <project> <version>", a two-dropdown sentence rather than a free-text
  // vocabulary field, since the label set is structural (code, name, optional category), not
  // prose. Global scope is intentional: any project's version can be reused, not just this one's.
  const [reuseProjectId, setReuseProjectId] = useState<number | ''>('')
  const [reuseVersions, setReuseVersions] = useState<DatasetVersion[]>([])
  const [reuseVersionId, setReuseVersionId] = useState<number | ''>('')
  const [reuseVersionsLoading, setReuseVersionsLoading] = useState(false)
  const [reuseApplying, setReuseApplying] = useState(false)
  const [reuseError, setReuseError] = useState<string | null>(null)

  const [newLabelCode, setNewLabelCode] = useState('')
  const [newLabelName, setNewLabelName] = useState('')
  const [newLabelCategory, setNewLabelCategory] = useState('')

  const [seedImageCount, setSeedImageCount] = useState(20)
  const [confidenceThreshold, setConfidenceThreshold] = useState(0.85)
  const [startingPointAnnotation, setStartingPointAnnotation] = useState(false)
  const [pointAnnotationError, setPointAnnotationError] = useState<string | null>(null)

  const pollingRef = useRef<number | null>(null)

  useEffect(() => {
    if (!job || job.status !== 'PROCESSING') {
      return
    }
    const intervalId = window.setInterval(() => {
      fetchImportJob(projectId, jobNumericId(job))
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
  }, [job, projectId])

  // When a project is picked in the "Reuse labels from" sentence, load its versions and default
  // to the latest one (versions.content[0], same "most recent first" ordering the rest of the
  // app already relies on for a project's version list).
  useEffect(() => {
    if (reuseProjectId === '') {
      setReuseVersions([])
      setReuseVersionId('')
      return
    }
    let cancelled = false
    setReuseVersionsLoading(true)
    setReuseError(null)
    fetchVersions(reuseProjectId, 0, 100)
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
  }, [reuseProjectId])

  // Once both a project and a version are picked, pull that version's full label definitions
  // (code, name, category, not just bare codes) and replace the local declared-label list. A
  // subsequent manual add on top of a reused vocabulary is expected, per the local list below.
  async function applyReuseSelection() {
    if (reuseProjectId === '' || reuseVersionId === '') return
    setReuseApplying(true)
    setReuseError(null)
    try {
      const full = await fetchVersion(reuseProjectId, reuseVersionId)
      const labels: LabelDeclaration[] = (full.labelDefinitions ?? [])
        .filter((l) => !l.deprecated)
        .map((l) => ({
          labelCode: l.labelCode,
          labelName: l.labelName,
          labelCategory: l.labelCategory ?? undefined,
        }))
      setDeclaredLabels(labels)
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

  const isBlank = format === 'BLANK'

  async function handleSubmit() {
    if (files.length === 0) return
    setSubmitting(true)
    setSubmitError(null)
    setPollError(null)
    try {
      const created = await submitImport(
        projectId, files, format, versionTag.trim(), user?.username ?? '', notes, isBlank ? declaredLabels : undefined,
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
        initiatedBy: user?.username ?? '',
      })
      navigate(`/projects/${projectId}/point-annotation/jobs/${pointJob.jobId}/label`)
    } catch (err) {
      setPointAnnotationError(errorMessage(err))
    } finally {
      setStartingPointAnnotation(false)
    }
  }

  function reset() {
    setJob(null)
    setSubmitError(null)
    setPollError(null)
    setPointAnnotationError(null)
    setFiles([])
    setVersionTag('')
    setNotes('')
    setDeclaredLabels([])
    setReuseProjectId('')
    setReuseVersions([])
    setReuseVersionId('')
    setReuseError(null)
    setNewLabelCode('')
    setNewLabelName('')
    setNewLabelCategory('')
  }

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title">Import Data</div>
          <div className="topbar-sub">
            {project ? `Bulk-import images and annotations into ${project.datasetName}` : 'Bulk-import images and annotations'}
          </div>
        </div>
      </div>

      <div className="content">
        {!job && (
          <div className="card" style={{ padding: 16, maxWidth: 560 }}>
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

            {isBlank && (
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
                    value={reuseProjectId}
                    onChange={(e) => setReuseProjectId(e.target.value === '' ? '' : Number(e.target.value))}
                  >
                    <option value="">a project…</option>
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
                    disabled={reuseProjectId === '' || reuseVersionsLoading || reuseVersions.length === 0}
                    onChange={(e) => setReuseVersionId(e.target.value === '' ? '' : Number(e.target.value))}
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
                    className="btn btn-secondary btn-sm"
                    onClick={applyReuseSelection}
                    disabled={reuseProjectId === '' || reuseVersionId === '' || reuseApplying}
                  >
                    {reuseApplying ? 'Loading…' : 'Use these labels'}
                  </button>
                </div>
                {reuseError && (
                  <div className="alert alert-error" style={{ marginBottom: 10 }}>
                    {reuseError}
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
                          <X size={14} strokeWidth={1.5} />
                        </button>
                      </div>
                    ))}
                  </div>
                )}

                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'flex-end' }}>
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
                </div>
                <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 6 }}>
                  {declaredLabels.length > 0
                    ? `${declaredLabels.length} label(s) declared`
                    : 'Declare the label set upfront, reused from another version or added by hand'}
                </div>
              </div>
            )}

            <div className="form-row">
              <label className="form-label" htmlFor="versionTag">
                Version tag for the resulting snapshot
              </label>
              <input
                id="versionTag"
                className="form-input"
                placeholder="e.g. v1.0"
                value={versionTag}
                onChange={(e) => setVersionTag(e.target.value)}
              />
            </div>

            <div className="form-row" style={{ marginBottom: 0 }}>
              <label className="form-label" htmlFor="importNotes">
                Notes
              </label>
              <textarea
                id="importNotes"
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

            <div style={{ marginTop: 16 }}>
              <button
                className="btn btn-primary"
                onClick={handleSubmit}
                disabled={
                  submitting || files.length === 0 || !versionTag.trim() || (isBlank && declaredLabels.length === 0)
                }
              >
                {submitting ? 'Submitting…' : 'Start Import'}
              </button>
            </div>
          </div>
        )}

        {job && job.status === 'PROCESSING' && (
          <div className="card" style={{ padding: 16, maxWidth: 560 }}>
            <div className="loading-state">
              <span className="spinner" /> Processing import ({job.importJobId})…
            </div>
            {pollError && (
              <div className="alert alert-error" style={{ marginTop: 12 }}>
                {pollError}
              </div>
            )}
          </div>
        )}

        {job && job.status === 'COMPLETED' && (
          <div className="card" style={{ padding: 16, maxWidth: 560 }}>
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

            {job.format === 'BLANK' ? (
              <>
                <div style={{ borderTop: '1px solid var(--border)', margin: '16px 0' }} />
                <div style={{ fontWeight: 600, marginBottom: 8 }}>Start Point Annotation</div>
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
                <div className="form-row" style={{ marginBottom: 0 }}>
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
                    Points the trained model predicts above this confidence auto-confirm; the rest go to a human
                    review queue. Grid is fixed at 50 points/image.
                  </div>
                </div>

                {pointAnnotationError && (
                  <div className="alert alert-error" style={{ marginTop: 12 }}>
                    {pointAnnotationError}
                  </div>
                )}

                <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
                  <button
                    className="btn btn-primary"
                    onClick={handleStartPointAnnotation}
                    disabled={startingPointAnnotation}
                  >
                    {startingPointAnnotation ? 'Generating points…' : 'Generate Points & Start Labeling'}
                  </button>
                  <button className="btn btn-secondary" onClick={reset}>
                    Import another
                  </button>
                </div>
              </>
            ) : (
              <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
                <button
                  className="btn btn-primary"
                  onClick={() => navigate(`/projects/${projectId}/versions/${job.createdDatasetVersionId}`)}
                >
                  View version
                </button>
                <button className="btn btn-secondary" onClick={reset}>
                  Import another
                </button>
              </div>
            )}
          </div>
        )}

        {job && job.status === 'FAILED' && (
          <div className="card" style={{ padding: 16, maxWidth: 560 }}>
            <div style={{ fontWeight: 600, marginBottom: 12, color: 'var(--red)' }}>Import failed</div>
            <div className="alert alert-error">{job.errorMessage}</div>
            <div style={{ marginTop: 16 }}>
              <button className="btn btn-secondary" onClick={reset}>
                Try again
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  )
}
