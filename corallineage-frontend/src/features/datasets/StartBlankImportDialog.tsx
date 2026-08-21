import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { XMarkIcon } from '@heroicons/react/24/outline'
import { startPointAnnotationJob } from '../pointannotation/pointAnnotationApi'
import { errorMessage } from '../../shared/api/client'
import { useAuthContext } from '../../shared/lib/authContextCore'

interface StartBlankImportDialogProps {
  sourceId: number
  datasetVersionId: number
  imageCount: number
  open: boolean
  onClose: () => void
}

/**
 * "Resolve" dialog opened from the Dataset Versions list for a completed BLANK import that hasn't
 * had blank import started yet, standalone rather than nested in CreateVersionModal's own
 * post-import step (see CreateVersionModal.tsx's identical "Start Blank Import" fields) since it
 * needs to be reachable independently, after the import modal has long since been closed.
 */
export default function StartBlankImportDialog({
  sourceId,
  datasetVersionId,
  imageCount,
  open,
  onClose,
}: StartBlankImportDialogProps) {
  const navigate = useNavigate()
  const { user } = useAuthContext()

  const [seedImageCount, setSeedImageCount] = useState(20)
  const [confidenceThreshold, setConfidenceThreshold] = useState(0.85)
  const [gridRows, setGridRows] = useState(7)
  const [gridCols, setGridCols] = useState(7)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Dialog shouldn't remember state from a previous open.
  useEffect(() => {
    if (!open) return
    setSeedImageCount(20)
    setConfidenceThreshold(0.85)
    setGridRows(7)
    setGridCols(7)
    setError(null)
  }, [open])

  if (!open) return null

  async function handleStart() {
    setSubmitting(true)
    setError(null)
    try {
      const pointJob = await startPointAnnotationJob({
        datasetVersionId,
        seedImageCount,
        confidenceThreshold,
        gridRows,
        gridCols,
        initiatedBy: user?.username ?? '',
      })
      onClose()
      navigate(`/sources/${sourceId}/blank-import/jobs/${pointJob.jobId}/label`)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="modal-overlay active">
      <div className="modal">
        <div className="modal-header">
          <div>
            <div className="modal-title">Start Blank Import</div>
          </div>
          <button className="modal-close" onClick={onClose} aria-label="Close">
            <XMarkIcon style={{ width: 16, height: 16 }} />
          </button>
        </div>
        <div className="modal-body">
          <div className="form-row">
            <label className="form-label" htmlFor="resolveSeedImageCount">
              Seed images to label manually
            </label>
            <input
              id="resolveSeedImageCount"
              type="number"
              className="form-input"
              min={1}
              max={imageCount || undefined}
              value={seedImageCount}
              onChange={(e) => setSeedImageCount(Number(e.target.value))}
            />
          </div>
          <div className="form-row">
            <label className="form-label" htmlFor="resolveConfidenceThreshold">
              Confidence threshold ({Math.round(confidenceThreshold * 100)}%)
            </label>
            <input
              id="resolveConfidenceThreshold"
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
          {error && (
            <div className="alert alert-error" style={{ marginTop: 16 }}>
              {error}
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" onClick={handleStart} disabled={submitting}>
            {submitting ? 'Generating points…' : 'Generate Points & Start Labeling'}
          </button>
        </div>
      </div>
    </div>
  )
}
