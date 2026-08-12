import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchImportJob, submitImport } from '../api/imports'
import { errorMessage } from '../api/client'
import { CURRENT_USER } from '../constants'
import { useDatasetContext } from '../lib/datasetContextCore'
import type { ImportFormat, ImportJob } from '../types/import'

const FORMATS: { value: ImportFormat; label: string; hint: string }[] = [
  { value: 'MLC', label: 'MLC', hint: 'One .txt per image, lines of "row col label_code"' },
  { value: 'CORALNET_CSV', label: 'CoralNet CSV', hint: 'Single CSV: image_filename, row, col, label_code' },
  { value: 'COCO_JSON', label: 'COCO JSON', hint: 'annotations.json with images/annotations/categories' },
]

function jobNumericId(job: ImportJob): number {
  return Number(job.importJobId.replace('IMP-', ''))
}

export default function Import() {
  const navigate = useNavigate()
  const { activeDataset, activeDatasetId, loading: datasetsLoading } = useDatasetContext()

  const [format, setFormat] = useState<ImportFormat>('MLC')
  const [files, setFiles] = useState<File[]>([])
  const [versionTag, setVersionTag] = useState('')
  const [notes, setNotes] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [job, setJob] = useState<ImportJob | null>(null)
  const [pollError, setPollError] = useState<string | null>(null)

  const pollingRef = useRef<number | null>(null)

  useEffect(() => {
    if (!job || !activeDatasetId || job.status !== 'PROCESSING') {
      return
    }
    const intervalId = window.setInterval(() => {
      fetchImportJob(activeDatasetId, jobNumericId(job))
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
  }, [job, activeDatasetId])

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

  async function handleSubmit() {
    if (!activeDatasetId || files.length === 0) return
    setSubmitting(true)
    setSubmitError(null)
    setPollError(null)
    try {
      const created = await submitImport(activeDatasetId, files, format, versionTag.trim(), CURRENT_USER, notes)
      setJob(created)
    } catch (err) {
      setSubmitError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  function reset() {
    setJob(null)
    setSubmitError(null)
    setPollError(null)
    setFiles([])
    setVersionTag('')
    setNotes('')
  }

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title">Import Dataset</div>
          <div className="topbar-sub">
            {activeDataset ? `Bulk-import images and annotations into ${activeDataset.datasetName}` : 'Bulk-import images and annotations'}
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
                disabled={submitting || files.length === 0 || !versionTag.trim()}
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
                <div className="detail-key">Labels resolved</div>
                <div>{job.labelsResolved}</div>
              </div>
              <div className="detail-row">
                <div className="detail-key">New version</div>
                <div>
                  <span className="mono">{job.createdVersionTag}</span>
                </div>
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
              <button
                className="btn btn-primary"
                onClick={() => navigate(`/versions/${job.createdDatasetVersionId}`)}
              >
                View version
              </button>
              <button className="btn btn-secondary" onClick={reset}>
                Import another
              </button>
            </div>
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
