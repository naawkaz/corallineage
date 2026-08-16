import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { fetchArchitectures, fetchTrainingJob, fetchTrainingJobs, submitTrainingJob } from '../api/training'
import { fetchVersions } from '../api/versions'
import { errorMessage } from '../api/client'
import { formatDate } from '../lib/format'
import { useAuthContext } from '../lib/authContextCore'
import { useDatasetContext } from '../lib/datasetContextCore'
import type { Architecture, TrainingJob } from '../types/training'
import type { DatasetVersion } from '../types/version'

function statusBadge(status: TrainingJob['status']): string {
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

export default function TrainingJobs() {
  const { sourceId: sourceIdParam } = useParams()
  const sourceId = Number(sourceIdParam)
  const { datasets } = useDatasetContext()
  const { user } = useAuthContext()
  const source = datasets.find((d) => d.id === sourceId) ?? null

  const [architectures, setArchitectures] = useState<Architecture[]>([])
  const [versions, setVersions] = useState<DatasetVersion[]>([])
  const [architecture, setArchitecture] = useState('efficientnet_b0')
  const [versionMode, setVersionMode] = useState<'latest' | 'specific'>('latest')
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null)
  const [learningRate, setLearningRate] = useState('0.001')
  const [batchSize, setBatchSize] = useState('32')
  const [epochs, setEpochs] = useState('10')

  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [activeJob, setActiveJob] = useState<TrainingJob | null>(null)

  const [jobs, setJobs] = useState<TrainingJob[]>([])
  const [jobsLoading, setJobsLoading] = useState(true)
  const [jobsError, setJobsError] = useState<string | null>(null)

  const pollingRef = useRef<number | null>(null)

  function loadJobs() {
    fetchTrainingJobs(sourceId, 0, 20)
      .then((res) => {
        setJobs(res.content)
        setJobsError(null)
      })
      .catch((err) => setJobsError(errorMessage(err)))
      .finally(() => setJobsLoading(false))
  }

  useEffect(() => {
    loadJobs()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceId])

  useEffect(() => {
    fetchArchitectures()
      .then((list) => {
        setArchitectures(list)
        if (list.length > 0) setArchitecture(list[0].id)
      })
      .catch(() => {
        // architecture dropdown just stays on the default; submit will still validate server-side
      })
  }, [])

  useEffect(() => {
    fetchVersions(sourceId, 0, 50)
      .then((res) => setVersions(res.content))
      .catch(() => setVersions([]))
  }, [sourceId])

  useEffect(() => {
    if (!activeJob || activeJob.status !== 'QUEUED' && activeJob.status !== 'RUNNING') {
      return
    }
    const intervalId = window.setInterval(() => {
      fetchTrainingJob(activeJob.jobId)
        .then((updated) => {
          setActiveJob(updated)
          if (updated.status !== 'QUEUED' && updated.status !== 'RUNNING' && pollingRef.current !== null) {
            window.clearInterval(pollingRef.current)
            pollingRef.current = null
            loadJobs()
          }
        })
        .catch(() => {
          // transient poll failure — try again next tick
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
    setSubmitting(true)
    setSubmitError(null)
    try {
      const created = await submitTrainingJob({
        datasetId: sourceId,
        datasetVersionId: versionMode === 'specific' && selectedVersionId ? selectedVersionId : undefined,
        architecture,
        learningRate: Number(learningRate),
        batchSize: Number(batchSize),
        epochs: Number(epochs),
        initiatedBy: user?.username ?? '',
      })
      setActiveJob(created)
      loadJobs()
    } catch (err) {
      setSubmitError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title">Training Jobs</div>
          <div className="topbar-sub">
            {source ? `Fine-tune a model on ${source.datasetName}` : 'Fine-tune a model'}
          </div>
        </div>
      </div>

      <div className="content">
        <div className="grid-2" style={{ gap: 24, alignItems: 'start' }}>
          <div>
            <div className="section-header">
              <div className="section-title">New Training Job</div>
            </div>
            <div className="card" style={{ padding: 16 }}>
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
                <label className="form-label">Dataset version</label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                  <input
                    type="radio"
                    name="versionMode"
                    checked={versionMode === 'latest'}
                    onChange={() => setVersionMode('latest')}
                  />
                  Latest version
                </label>
                {versionMode === 'latest' && versions.some((v) => !v.complete) && (
                  <div className="form-hint" style={{ marginBottom: 6 }}>
                    Some recent versions have unresolved ontology-change dirty flags and will be skipped —
                    training will silently fall back to the newest complete version.
                  </div>
                )}
                <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <input
                    type="radio"
                    name="versionMode"
                    checked={versionMode === 'specific'}
                    onChange={() => setVersionMode('specific')}
                  />
                  Specific version
                </label>
                {versionMode === 'specific' && (
                  <select
                    className="form-select"
                    style={{ marginTop: 8 }}
                    value={selectedVersionId ?? ''}
                    onChange={(e) => setSelectedVersionId(e.target.value ? Number(e.target.value) : null)}
                  >
                    <option value="">Select a version…</option>
                    {versions.map((v) => (
                      <option key={v.id} value={v.id}>
                        {v.versionTag}
                        {!v.complete ? ' (incomplete — training will be rejected)' : ''}
                      </option>
                    ))}
                  </select>
                )}
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
                <input
                  id="epochs"
                  className="form-input"
                  value={epochs}
                  onChange={(e) => setEpochs(e.target.value)}
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
                  disabled={submitting || (versionMode === 'specific' && !selectedVersionId)}
                >
                  {submitting ? 'Submitting…' : 'Start Training'}
                </button>
              </div>

              {activeJob && (
                <div style={{ marginTop: 16, borderTop: '1px solid var(--border-md)', paddingTop: 16 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                    <span className={`badge ${statusBadge(activeJob.status)}`}>{activeJob.status}</span>
                    <span className="mono" style={{ fontSize: 12 }}>job #{activeJob.jobId}</span>
                  </div>
                  {(activeJob.status === 'QUEUED' || activeJob.status === 'RUNNING') && (
                    <div className="loading-state">
                      <span className="spinner" /> Training in progress…
                    </div>
                  )}
                  {activeJob.status === 'COMPLETED' && (
                    <div>
                      Model registered: <span className="mono">{activeJob.modelTag}</span>
                    </div>
                  )}
                  {activeJob.status === 'FAILED' && (
                    <div className="alert alert-error">{activeJob.errorMessage}</div>
                  )}
                </div>
              )}
            </div>
          </div>

          <div>
            <div className="section-header">
              <div className="section-title">Job History</div>
            </div>
            <div className="card">
              {jobsError && (
                <div className="alert alert-error" style={{ margin: 16 }}>
                  {jobsError}
                </div>
              )}
              {jobsLoading ? (
                <div className="loading-state">
                  <span className="spinner" /> Loading jobs…
                </div>
              ) : jobs.length === 0 ? (
                <div className="empty-state">
                  <p>No training jobs yet.</p>
                </div>
              ) : (
                <table>
                  <thead>
                    <tr>
                      <th>Status</th>
                      <th>Architecture</th>
                      <th>Dataset version</th>
                      <th>Model</th>
                      <th>Started</th>
                    </tr>
                  </thead>
                  <tbody>
                    {jobs.map((job) => (
                      <tr key={job.jobId}>
                        <td>
                          <span className={`badge ${statusBadge(job.status)}`}>{job.status}</span>
                        </td>
                        <td>
                          <span className="mono">{job.architecture}</span>
                        </td>
                        <td>
                          <span className="mono">{job.datasetVersionTag}</span>
                        </td>
                        <td>{job.modelTag ?? '—'}</td>
                        <td style={{ color: 'var(--text-secondary)' }}>{formatDate(job.initiatedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
