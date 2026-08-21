import { useEffect, useState } from 'react'
import axios from 'axios'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeftIcon,
  ArrowTopRightOnSquareIcon,
  ArchiveBoxIcon,
  NoSymbolIcon,
  ArrowPathIcon,
} from '@heroicons/react/24/outline'
import { fetchMlflowDetails, fetchModel, updateModelStatus } from '../training/modelsApi'
import { errorMessage } from '../../shared/api/client'
import { useAuthContext } from '../../shared/lib/authContextCore'
import LossCurveChart from '../training/LossCurveChart'
import TitledCard from '../../shared/components/TitledCard'
import type { MlflowDetails, ModelStatus, ModelVersion } from '../training/modelsTypes'

const STATUS_BADGE: Record<ModelStatus, string> = {
  ACTIVE: 'badge-green',
  ARCHIVED: 'badge-gray',
  DEPRECATED: 'badge-red',
}

type DiagnosticsState =
  | { kind: 'loading' }
  | { kind: 'ready'; details: MlflowDetails }
  | { kind: 'not-found' }
  | { kind: 'unavailable' }
  | { kind: 'error'; message: string }

export default function ModelDetail() {
  const { id, sourceId: sourceIdParam } = useParams()
  const navigate = useNavigate()
  const modelId = Number(id)
  const modelsListPath = `/sources/${sourceIdParam}/models`
  const { user } = useAuthContext()

  const [model, setModel] = useState<ModelVersion | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [statusChanging, setStatusChanging] = useState(false)
  const [statusError, setStatusError] = useState<string | null>(null)

  const [diagnostics, setDiagnostics] = useState<DiagnosticsState>({ kind: 'loading' })

  function loadModel() {
    fetchModel(modelId)
      .then((res) => {
        setModel(res)
        setError(null)
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadModel()
    fetchMlflowDetails(modelId)
      .then((details) => setDiagnostics({ kind: 'ready', details }))
      .catch((err) => {
        if (axios.isAxiosError(err)) {
          if (err.response?.status === 404) {
            setDiagnostics({ kind: 'not-found' })
            return
          }
          if (err.response?.status === 503) {
            setDiagnostics({ kind: 'unavailable' })
            return
          }
        }
        setDiagnostics({ kind: 'error', message: errorMessage(err) })
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modelId])

  async function handleStatusChange(newStatus: ModelStatus) {
    setStatusChanging(true)
    setStatusError(null)
    try {
      const updated = await updateModelStatus(modelId, newStatus, user?.username ?? '')
      setModel(updated)
    } catch (err) {
      setStatusError(errorMessage(err))
    } finally {
      setStatusChanging(false)
    }
  }

  if (loading && !model) {
    return (
      <div className="content">
        <div className="loading-state">
          <span className="spinner" /> Loading model…
        </div>
      </div>
    )
  }

  if (error && !model) {
    return (
      <div className="content">
        <div className="alert alert-error">{error}</div>
      </div>
    )
  }

  if (!model) return null

  const perClass = model.performanceMetrics?.perClass ?? []

  return (
    <>
      <div className="content">
        <div className="page-back-row">
          <button className="btn btn-secondary" onClick={() => navigate(modelsListPath)} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <ArrowLeftIcon style={{ width: 14, height: 14 }} />
            Back
          </button>
        </div>

        <div className="page-title-row" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
          <div className="page-title">Model Version: {model.modelTag}</div>
          <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
            {model.status !== 'ARCHIVED' && (
              <button
                className="btn btn-secondary"
                disabled={statusChanging}
                onClick={() => handleStatusChange('ARCHIVED')}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
              >
                <ArchiveBoxIcon style={{ width: 14, height: 14 }} />
                Archive
              </button>
            )}
            {model.status !== 'DEPRECATED' && (
              <button
                className="btn"
                disabled={statusChanging}
                onClick={() => handleStatusChange('DEPRECATED')}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6, background: 'var(--danger-700)', color: '#fff', borderColor: 'var(--danger-700)' }}
              >
                <NoSymbolIcon style={{ width: 14, height: 14 }} />
                Deprecate
              </button>
            )}
            {model.status !== 'ACTIVE' && (
              <button
                className="btn btn-primary"
                disabled={statusChanging}
                onClick={() => handleStatusChange('ACTIVE')}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
              >
                <ArrowPathIcon style={{ width: 14, height: 14 }} />
                Reactivate
              </button>
            )}
          </div>
        </div>
        {statusError && (
          <div className="alert alert-error" style={{ marginBottom: 16 }}>
            {statusError}
          </div>
        )}

        <div className="grid-2" style={{ gap: 24, alignItems: 'start' }}>
          <TitledCard title="Model metadata">
              <div className="detail-grid">
                <div className="detail-row">
                  <div className="detail-key">Architecture</div>
                  <div>
                    <span className="badge badge-blue">{model.architecture}</span>
                  </div>
                </div>
                <div className="detail-row">
                  <div className="detail-key">Status</div>
                  <div>
                    <span className={`badge ${STATUS_BADGE[model.status]}`}>{model.status}</span>
                  </div>
                </div>
                <div className="detail-row">
                  <div className="detail-key">Accuracy</div>
                  <div>{model.accuracy != null ? `${(model.accuracy * 100).toFixed(1)}%` : '-'}</div>
                </div>
                <div className="detail-row">
                  <div className="detail-key">Dataset version</div>
                  <div>
                    {model.trainedOnDatasetVersion ? (
                      <Link
                        to={`/sources/${sourceIdParam}/versions/${model.trainedOnDatasetVersion.id}`}
                        className="mono"
                        style={{ color: 'var(--accent)' }}
                      >
                        {model.trainedOnDatasetVersion.versionTag}
                      </Link>
                    ) : (
                      '-'
                    )}
                  </div>
                </div>
                <div className="detail-row">
                  <div className="detail-key">Hyperparameters</div>
                  <div style={{ color: 'var(--text-secondary)' }}>
                    {model.hyperparameters
                      ? Object.entries(model.hyperparameters)
                          .map(([k, v]) => `${k}: ${v}`)
                          .join(', ')
                      : '-'}
                  </div>
                </div>
                <div className="detail-row">
                  <div className="detail-key">MLflow run</div>
                  <div>
                    <span className="mono">{model.mlflowRunId ?? '-'}</span>
                  </div>
                </div>
              </div>
          </TitledCard>

          <TitledCard title="Class mappings & per-class metrics" bodyClassName="no-padding">
              {model.classMappings && (
                <div style={{ padding: 16, borderBottom: '1px solid var(--border-md)' }}>
                  {Object.entries(model.classMappings).map(([idx, code]) => (
                    <span key={idx} className="tag" style={{ marginRight: 6 }}>
                      {idx}: {code}
                    </span>
                  ))}
                </div>
              )}
              {perClass.length > 0 ? (
                <table>
                  <thead>
                    <tr>
                      <th>Label</th>
                      <th className="num">Accuracy</th>
                      <th className="num">Samples</th>
                    </tr>
                  </thead>
                  <tbody>
                    {perClass.map((c) => (
                      <tr key={c.labelCode}>
                        <td>
                          <span className="mono">{c.labelCode}</span>
                        </td>
                        <td className="num">
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 8 }}>
                            <div
                              style={{
                                width: 80,
                                height: 6,
                                borderRadius: 3,
                                background: 'var(--bg-secondary)',
                                overflow: 'hidden',
                              }}
                            >
                              <div
                                style={{
                                  width: `${((c.accuracy ?? 0) * 100).toFixed(0)}%`,
                                  height: '100%',
                                  background: 'var(--accent)',
                                }}
                              />
                            </div>
                            <span>{c.accuracy != null ? `${(c.accuracy * 100).toFixed(0)}%` : '-'}</span>
                          </div>
                        </td>
                        <td className="num">{c.sampleCount ?? '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <div className="empty-state">
                  <p>No per-class metrics recorded.</p>
                </div>
              )}
          </TitledCard>
        </div>

        <TitledCard
          title="MLflow diagnostics"
          style={{ marginTop: 24 }}
          actions={
            diagnostics.kind === 'ready' && (
              <a
                className="btn btn-primary"
                href={diagnostics.details.mlflowUiUrl}
                target="_blank"
                rel="noreferrer"
                style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
              >
                Open in MLflow
                <ArrowTopRightOnSquareIcon style={{ width: 14, height: 14 }} />
              </a>
            )
          }
        >
            {diagnostics.kind === 'loading' && (
              <div className="loading-state">
                <span className="spinner" /> Loading MLflow details…
              </div>
            )}
            {diagnostics.kind === 'not-found' && (
              <div className="empty-state">
                <p>No MLflow run recorded for this model.</p>
              </div>
            )}
            {diagnostics.kind === 'unavailable' && (
              <div className="alert alert-error">
                MLflow tracking server is unreachable - training metadata is still shown above, but
                deep diagnostics (environment, loss curve, confusion matrix) can't be loaded right now.
              </div>
            )}
            {diagnostics.kind === 'error' && <div className="alert alert-error">{diagnostics.message}</div>}
            {diagnostics.kind === 'ready' && (
              <>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
                  {diagnostics.details.environment.pythonVersion && (
                    <span className="tag">Python {diagnostics.details.environment.pythonVersion}</span>
                  )}
                  {diagnostics.details.environment.pytorchVersion && (
                    <span className="tag">PyTorch {diagnostics.details.environment.pytorchVersion}</span>
                  )}
                  {diagnostics.details.environment.cudaVersion && (
                    <span className="tag">{diagnostics.details.environment.cudaVersion.toUpperCase()}</span>
                  )}
                  {diagnostics.details.environment.randomSeed && (
                    <span className="tag">Seed {diagnostics.details.environment.randomSeed}</span>
                  )}
                  {diagnostics.details.environment.totalDurationSeconds != null && (
                    <span className="tag">{diagnostics.details.environment.totalDurationSeconds}s</span>
                  )}
                </div>

                <LossCurveChart epochMetrics={diagnostics.details.epochMetrics} />

                {diagnostics.details.artifactUrls.confusionMatrixPng && (
                  <div style={{ marginBottom: 16 }}>
                    <div className="detail-key" style={{ marginBottom: 8 }}>Confusion matrix</div>
                    <img
                      src={diagnostics.details.artifactUrls.confusionMatrixPng}
                      alt="Confusion matrix"
                      style={{ maxWidth: 400, border: '1px solid var(--border-md)', borderRadius: 6 }}
                    />
                  </div>
                )}
              </>
            )}
        </TitledCard>
      </div>
    </>
  )
}
