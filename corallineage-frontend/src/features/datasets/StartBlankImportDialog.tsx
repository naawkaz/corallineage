import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { XMarkIcon } from '@heroicons/react/24/outline'
import { startPointAnnotationJob } from '../pointannotation/pointAnnotationApi'
import { submitExistingVersionJob } from '../annotation/annotationApi'
import { fetchImages } from './imagesApi'
import { fetchModels } from '../training/modelsApi'
import { errorMessage } from '../../shared/api/client'
import { useAuthContext } from '../../shared/lib/authContextCore'
import { useToast } from '../../shared/lib/toastContextCore'
import type { ModelVersion } from '../training/modelsTypes'

interface StartBlankImportDialogProps {
  sourceId: number
  datasetVersionId: number
  imageCount: number
  open: boolean
  onClose: () => void
}

/**
 * "Resolve" dialog opened from the dataset versions list for a completed BLANK import.
 *
 * Forks on whether the source already has a trained model: warm start (model exists) goes
 * straight to auto-annotate then review; cold start falls back to the seed-then-train pipeline.
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
  const toast = useToast()

  const [modelsLoading, setModelsLoading] = useState(true)
  const [activeModels, setActiveModels] = useState<ModelVersion[]>([])
  const [selectedModelId, setSelectedModelId] = useState<number | null>(null)

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
    setModelsLoading(true)
    fetchModels(sourceId, 0, 50, 'ACTIVE')
      .then((page) => {
        setActiveModels(page.content)
        setSelectedModelId(page.content[0]?.id ?? null)
      })
      .catch(() => setActiveModels([]))
      .finally(() => setModelsLoading(false))
  }, [open, sourceId])

  if (!open) return null

  const warmStart = activeModels.length > 0

  async function handleStartColdStart() {
    setSubmitting(true)
    setError(null)
    const toastId = toast.push({ variant: 'progress', title: 'Generating points...' })
    try {
      const pointJob = await startPointAnnotationJob({
        datasetVersionId,
        seedImageCount,
        confidenceThreshold,
        gridRows,
        gridCols,
        initiatedBy: user?.username ?? '',
      })
      toast.update(toastId, { variant: 'success', title: 'Points generated', detail: 'Ready for seed labeling.' })
      onClose()
      navigate(`/sources/${sourceId}/annotation/jobs/${pointJob.jobId}/label`)
    } catch (err) {
      const message = errorMessage(err)
      setError(message)
      toast.update(toastId, { variant: 'error', title: 'Failed to start blank import', detail: message })
    } finally {
      setSubmitting(false)
    }
  }

  async function handleStartWarmStart() {
    if (!selectedModelId) return
    setSubmitting(true)
    setError(null)
    const toastId = toast.push({ variant: 'progress', title: 'Starting auto-annotate...' })
    try {
      const imagesPage = await fetchImages(sourceId, datasetVersionId, 0, Math.max(imageCount, 1))
      const imageIds = imagesPage.content.map((image) => image.id)
      const job = await submitExistingVersionJob({
        modelVersionId: selectedModelId,
        datasetVersionId,
        imageIds,
        gridRows,
        gridCols,
        confidenceThreshold,
        initiatedBy: user?.username ?? '',
      })
      toast.update(toastId, { variant: 'success', title: 'Auto-annotate started', detail: 'Review predictions once it finishes.' })
      onClose()
      navigate(`/sources/${sourceId}/annotation/jobs/${job.jobId}/review`)
    } catch (err) {
      const message = errorMessage(err)
      setError(message)
      toast.update(toastId, { variant: 'error', title: 'Failed to start auto-annotate', detail: message })
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
            {!modelsLoading && (
              <div className="modal-subtitle">
                {warmStart
                  ? 'A trained model already exists for this source - auto-annotate with it, then review.'
                  : 'No trained model yet for this source - label seed images to train one.'}
              </div>
            )}
          </div>
          <button className="modal-close" onClick={onClose} aria-label="Close">
            <XMarkIcon style={{ width: 16, height: 16 }} />
          </button>
        </div>
        <div className="modal-body">
          {modelsLoading ? (
            <div className="loading-state">
              <span className="spinner" /> Checking for a trained model...
            </div>
          ) : warmStart ? (
            <>
              <div className="form-row">
                <label className="form-label" htmlFor="resolveModelSelect">
                  Model
                </label>
                <select
                  id="resolveModelSelect"
                  className="form-input"
                  value={selectedModelId ?? ''}
                  onChange={(e) => setSelectedModelId(Number(e.target.value))}
                >
                  {activeModels.map((model) => (
                    <option key={model.id} value={model.id}>
                      {model.modelTag}
                    </option>
                  ))}
                </select>
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
                  Predicted points above this confidence threshold auto-confirm.
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
            </>
          ) : (
            <>
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
                  Predicted points above this confidence threshold auto-confirm.
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
            </>
          )}
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
          <button
            className="btn btn-primary"
            onClick={warmStart ? handleStartWarmStart : handleStartColdStart}
            disabled={submitting || modelsLoading || (warmStart && !selectedModelId)}
          >
            {submitting ? 'Starting...' : warmStart ? 'Start Auto-Annotate' : 'Generate Points & Start Labeling'}
          </button>
        </div>
      </div>
    </div>
  )
}
