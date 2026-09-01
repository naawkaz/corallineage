import { useEffect, useRef, useState } from 'react'
import { XMarkIcon } from '@heroicons/react/24/outline'
import { fetchTrainingJob, fetchTrainingJobEpochMetrics, submitTrainingJob } from '../training/trainingApi'
import { errorMessage } from '../../shared/api/client'
import LossCurveChart from '../training/LossCurveChart'
import type { MlflowEpochMetricPoint } from '../training/modelsTypes'
import type { Architecture, TrainingJob } from '../training/trainingTypes'
import type { DatasetVersion } from '../datasets/versionsTypes'

export function trainingStatusBadge(status: TrainingJob['status']): string {
  switch (status) {
    case 'COMPLETED':
      return 'badge-green'
    case 'FAILED':
      return 'badge-red'
    case 'RUNNING':
      return 'badge-amber'
    default:
      return 'badge-gray'
  }
}

export interface TrainModelDialogForm {
  architecture: string
  datasetVersionId: number
  learningRate: number
  batchSize: number
  epochs: number
}

interface TrainModelDialogProps {
  sourceId: number
  initiatedBy: string
  architectures: Architecture[]
  versions: DatasetVersion[]
  /** Preselects a specific dataset version instead of defaulting to versions[0] */
  initialVersionId?: number
  /** Overrides the submit action 
   * defaults to the standalone training-job endpoint */
  onSubmit?: (form: TrainModelDialogForm) => Promise<TrainingJob>
  onClose: () => void
  onCompleted: () => void
}

// Owns "Train New Model" form state 
// Mounted only while its parent's open flag is true
export default function TrainModelDialog({
  sourceId,
  initiatedBy,
  architectures,
  versions,
  initialVersionId,
  onSubmit,
  onClose,
  onCompleted,
}: TrainModelDialogProps) {
  const [architecture, setArchitecture] = useState('efficientnet_b0')
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(initialVersionId ?? null)
  const [learningRate, setLearningRate] = useState('0.001')
  const [batchSize, setBatchSize] = useState('32')
  const [epochs, setEpochs] = useState('10')

  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [activeJob, setActiveJob] = useState<TrainingJob | null>(null)
  const [activeJobEpochMetrics, setActiveJobEpochMetrics] = useState<MlflowEpochMetricPoint[]>([])

  const pollingRef = useRef<number | null>(null)

  useEffect(() => {
    if (architectures.length > 0) setArchitecture(architectures[0].id)
  }, [architectures])

  useEffect(() => {
    if (initialVersionId == null && versions.length > 0) setSelectedVersionId(versions[0].id)
  }, [versions, initialVersionId])

  // Polls the active job every 3s while QUEUED/RUNNING to drive epoch-progress bar
  // and loss curve
  useEffect(() => {
    if (!activeJob || (activeJob.status !== 'QUEUED' && activeJob.status !== 'RUNNING')) {
      return
    }
    const intervalId = window.setInterval(() => {
      fetchTrainingJob(activeJob.jobId)
        .then((updated) => {
          setActiveJob(updated)
          if (updated.mlflowRunId) {
            fetchTrainingJobEpochMetrics(updated.jobId)
              .then((points) => setActiveJobEpochMetrics(points))
              .catch(() => {
              })
          }
          if (updated.status !== 'QUEUED' && updated.status !== 'RUNNING' && pollingRef.current !== null) {
            window.clearInterval(pollingRef.current)
            pollingRef.current = null
            onCompleted()
            if (updated.status === 'COMPLETED') {
              onClose()
            }
          }
        })
        .catch(() => {
        })
    }, 3000)
    pollingRef.current = intervalId
    return () => {
      window.clearInterval(intervalId)
      pollingRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeJob])

  async function handleSubmit() {
    if (selectedVersionId == null) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      const form: TrainModelDialogForm = {
        architecture,
        datasetVersionId: selectedVersionId,
        learningRate: Number(learningRate),
        batchSize: Number(batchSize),
        epochs: Number(epochs),
      }
      const created = onSubmit
        ? await onSubmit(form)
        : await submitTrainingJob({
            datasetId: sourceId,
            datasetVersionId: form.datasetVersionId,
            architecture: form.architecture,
            learningRate: form.learningRate,
            batchSize: form.batchSize,
            epochs: form.epochs,
            initiatedBy,
          })
      setActiveJob(created)
      setActiveJobEpochMetrics([])
      onCompleted()
    } catch (err) {
      setSubmitError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="modal-overlay active">
      <div className="modal">
        <div className="modal-header">
          <div>
            <div className="modal-title">Train New Model</div>
          </div>
          <button className="modal-close" onClick={onClose} aria-label="Close">
            <XMarkIcon style={{ width: 16, height: 16 }} />
          </button>
        </div>
        <div className="modal-body">
          <div className="form-row">
            <label className="form-label">Architecture</label>
            <select className="form-select" value={architecture} onChange={(e) => setArchitecture(e.target.value)}>
              {(architectures.length > 0
                ? architectures
                : [{ id: 'efficientnet_b0', displayName: 'EfficientNet-B0', description: '' }]
              ).map((a) => (
                <option key={a.id} value={a.id}>
                  {a.displayName}
                </option>
              ))}
            </select>
          </div>

          <div className="form-row">
            <label className="form-label" htmlFor="datasetVersion">
              Dataset version
            </label>
            <select
              id="datasetVersion"
              className="form-select"
              value={selectedVersionId ?? ''}
              onChange={(e) => setSelectedVersionId(e.target.value ? Number(e.target.value) : null)}
            >
              <option value="" disabled>
                Select a version...
              </option>
              {versions.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.versionTag}
                  {!v.complete ? ' (incomplete - training will be rejected)' : ''}
                </option>
              ))}
            </select>
          </div>

          <div className="form-row">
            <label className="form-label" htmlFor="learningRate">
              Learning rate
            </label>
            <input
              id="learningRate"
              className="form-input"
              value={learningRate}
              onChange={(e) => setLearningRate(e.target.value)}
            />
          </div>
          <div className="form-row">
            <label className="form-label" htmlFor="batchSize">
              Batch size
            </label>
            <input
              id="batchSize"
              className="form-input"
              value={batchSize}
              onChange={(e) => setBatchSize(e.target.value)}
            />
          </div>
          <div className="form-row" style={{ marginBottom: 0 }}>
            <label className="form-label" htmlFor="epochs">
              Epochs
            </label>
            <input id="epochs" className="form-input" value={epochs} onChange={(e) => setEpochs(e.target.value)} />
          </div>

          {submitError && (
            <div className="alert alert-error" style={{ marginTop: 16 }}>
              {submitError}
            </div>
          )}

          {activeJob && (
            <div style={{ marginTop: 16, borderTop: '1px solid var(--border-md)', paddingTop: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                <span className={`badge ${trainingStatusBadge(activeJob.status)}`}>{activeJob.status}</span>
                <span className="mono" style={{ fontSize: 12 }}>job #{activeJob.jobId}</span>
                {activeJob.mlflowRunId && (
                  <span className="mono" style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                    run {activeJob.mlflowRunId}
                  </span>
                )}
              </div>
              {activeJob.status === 'RUNNING' && activeJob.currentEpoch != null ? (
                <div>
                  <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 6 }}>
                    Epoch {activeJob.currentEpoch} / {activeJob.totalEpochs}
                  </div>
                  <div style={{ height: 4, background: 'var(--border)', marginBottom: 16 }}>
                    <div
                      style={{
                        height: 4,
                        width: `${activeJob.totalEpochs ? (activeJob.currentEpoch / activeJob.totalEpochs) * 100 : 0}%`,
                        background: '#0d9488',
                        transition: 'width 0.2s',
                      }}
                    />
                  </div>
                  {/* Fed by GET /training/jobs/{id}/epoch-metrics which reads the job's own
                      mlflowRunId rather than requiring a registered ModelVersion, so the same
                      loss curve used for a completed model can grow live while still RUNNING. */}
                  <LossCurveChart epochMetrics={activeJobEpochMetrics} />
                </div>
              ) : (
                (activeJob.status === 'QUEUED' || activeJob.status === 'RUNNING') && (
                  <div className="loading-state">
                    <span className="spinner" /> Training in progress...
                  </div>
                )
              )}
              {activeJob.status === 'COMPLETED' && (
                <div>
                  Model registered: <span className="mono">{activeJob.modelTag}</span>
                </div>
              )}
              {activeJob.status === 'FAILED' && <div className="alert alert-error">{activeJob.errorMessage}</div>}
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button className="btn btn-primary" onClick={handleSubmit} disabled={submitting || selectedVersionId == null}>
            {submitting ? 'Submitting...' : 'Start Training'}
          </button>
        </div>
      </div>
    </div>
  )
}
