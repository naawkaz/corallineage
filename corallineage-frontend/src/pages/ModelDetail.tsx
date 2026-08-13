import { useEffect, useState } from 'react'
import axios from 'axios'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { fetchMlflowDetails, fetchModel, updateModelStatus } from '../api/models'
import { errorMessage } from '../api/client'
import { CURRENT_USER } from '../constants'
import { formatDate } from '../lib/format'
import type { MlflowDetails, ModelStatus, ModelVersion } from '../types/model'

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
  const { id, projectId: projectIdParam } = useParams()
  const navigate = useNavigate()
  const modelId = Number(id)
  const modelsListPath = `/projects/${projectIdParam}/models`

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
      const updated = await updateModelStatus(modelId, newStatus, CURRENT_USER)
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
  const chartData = diagnostics.kind === 'ready'
    ? diagnostics.details.epochMetrics.map((p) => ({ ...p, epochLabel: `Epoch ${p.epoch + 1}` }))
    : []

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Link to={modelsListPath} style={{ color: 'var(--text-muted)', fontWeight: 400, textDecoration: 'none' }}>
              Model Registry
            </Link>
            <span style={{ color: 'var(--border-md)' }}>/</span>
            <span className="mono" style={{ fontSize: 14 }}>{model.modelTag}</span>
          </div>
          <div className="topbar-sub">
            Trained {formatDate(model.trainedAt)} by {model.trainedBy}
          </div>
        </div>
        <button className="btn btn-secondary btn-sm" onClick={() => navigate(modelsListPath)}>
          ← Back
        </button>
      </div>

      <div className="content">
        <div className="grid-2" style={{ gap: 24, alignItems: 'start' }}>
          <div>
            <div className="section-header">
              <div className="section-title">Model metadata</div>
            </div>
            <div className="card" style={{ padding: 16 }}>
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
                  <div>{model.accuracy != null ? `${(model.accuracy * 100).toFixed(1)}%` : '—'}</div>
                </div>
                <div className="detail-row">
                  <div className="detail-key">Dataset version</div>
                  <div>
                    {model.trainedOnDatasetVersion ? (
                      <Link
                        to={`/projects/${projectIdParam}/versions/${model.trainedOnDatasetVersion.id}`}
                        className="mono"
                        style={{ color: 'var(--accent)' }}
                      >
                        {model.trainedOnDatasetVersion.versionTag}
                      </Link>
                    ) : (
                      '—'
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
                      : '—'}
                  </div>
                </div>
                <div className="detail-row">
                  <div className="detail-key">MLflow run</div>
                  <div>
                    <span className="mono">{model.mlflowRunId ?? '—'}</span>
                  </div>
                </div>
              </div>

              <div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
                {model.status !== 'ARCHIVED' && (
                  <button
                    className="btn btn-secondary"
                    disabled={statusChanging}
                    onClick={() => handleStatusChange('ARCHIVED')}
                  >
                    Archive
                  </button>
                )}
                {model.status !== 'DEPRECATED' && (
                  <button
                    className="btn btn-secondary"
                    disabled={statusChanging}
                    onClick={() => handleStatusChange('DEPRECATED')}
                  >
                    Deprecate
                  </button>
                )}
                {model.status !== 'ACTIVE' && (
                  <button
                    className="btn btn-primary"
                    disabled={statusChanging}
                    onClick={() => handleStatusChange('ACTIVE')}
                  >
                    Reactivate
                  </button>
                )}
              </div>
              {statusError && (
                <div className="alert alert-error" style={{ marginTop: 12 }}>
                  {statusError}
                </div>
              )}
            </div>
          </div>

          <div>
            <div className="section-header">
              <div className="section-title">Class mappings &amp; per-class metrics</div>
            </div>
            <div className="card">
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
                      <th>Accuracy</th>
                      <th>Samples</th>
                    </tr>
                  </thead>
                  <tbody>
                    {perClass.map((c) => (
                      <tr key={c.labelCode}>
                        <td>
                          <span className="mono">{c.labelCode}</span>
                        </td>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
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
                            <span>{c.accuracy != null ? `${(c.accuracy * 100).toFixed(0)}%` : '—'}</span>
                          </div>
                        </td>
                        <td>{c.sampleCount ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <div className="empty-state">
                  <p>No per-class metrics recorded.</p>
                </div>
              )}
            </div>
          </div>
        </div>

        <div style={{ marginTop: 24 }}>
          <div className="section-header">
            <div className="section-title">MLflow diagnostics</div>
          </div>
          <div className="card" style={{ padding: 16 }}>
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
                MLflow tracking server is unreachable — training metadata is still shown above, but
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

                {chartData.length > 0 && (
                  <div style={{ height: 260, marginBottom: 16 }}>
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={chartData}>
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis dataKey="epochLabel" />
                        <YAxis />
                        <Tooltip />
                        <Line type="monotone" dataKey="loss" stroke="var(--navy, #1e3a5f)" name="Loss" />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                )}

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

                <a
                  className="btn btn-secondary"
                  href={diagnostics.details.mlflowUiUrl}
                  target="_blank"
                  rel="noreferrer"
                >
                  Open in MLflow ↗
                </a>
              </>
            )}
          </div>
        </div>
      </div>
    </>
  )
}
