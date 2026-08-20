import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeftIcon, ArrowRightIcon } from '@heroicons/react/24/outline'
import { fetchDataset } from '../datasets/datasetsApi'
import { fetchVersions } from '../datasets/versionsApi'
import { fetchModels } from '../training/modelsApi'
import { fetchPointAnnotationJobs } from '../pointannotation/pointAnnotationApi'
import { fetchOntologyChanges } from '../ontology/ontologyApi'
import { errorMessage } from '../../shared/api/client'
import { formatDate } from '../../shared/lib/format'
import { changeTypeBadge, changeTypeLabel } from '../../shared/constants'
import VersionsList from '../datasets/VersionsList'
import TitledCard from '../../shared/components/TitledCard'
import type { Dataset } from '../datasets/datasetsTypes'
import type { PointAnnotationJob, PointAnnotationJobStatus } from '../pointannotation/pointAnnotationTypes'
import type { OntologyChange } from '../ontology/ontologyTypes'

function pointJobStatusBadge(status: PointAnnotationJobStatus): string {
  switch (status) {
    case 'COMPLETE':
      return 'badge-green'
    case 'REVIEW':
    case 'AUTO_ANNOTATING':
      return 'badge-amber'
    default:
      return 'badge-gray'
  }
}

export default function DatasetDashboard() {
  const navigate = useNavigate()
  const { sourceId: sourceIdParam } = useParams()
  const sourceId = Number(sourceIdParam)

  const [dataset, setDataset] = useState<Dataset | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [pointJobs, setPointJobs] = useState<PointAnnotationJob[]>([])
  const [pointJobsLoading, setPointJobsLoading] = useState(true)
  const [pointJobsError, setPointJobsError] = useState<string | null>(null)

  const [dirtyChanges, setDirtyChanges] = useState<OntologyChange[]>([])
  const [dirtyChangesLoading, setDirtyChangesLoading] = useState(true)
  const [dirtyChangesError, setDirtyChangesError] = useState<string | null>(null)

  useEffect(() => {
    if (!sourceId) return
    let cancelled = false
    Promise.resolve().then(() => {
      if (!cancelled) setLoading(true)
    })
    Promise.all([fetchDataset(sourceId), fetchVersions(sourceId, 0, 1), fetchModels(sourceId, 0, 1)])
      .then(([datasetRes]) => {
        if (cancelled) return
        setDataset(datasetRes)
        // setLatestVersion(versionsRes.content[0] ?? null)
        // setModelCount(modelsRes.totalElements)
        setError(null)
      })
      .catch((err) => {
        if (!cancelled) setError(errorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [sourceId])

  useEffect(() => {
    if (!sourceId) return
    let cancelled = false
    fetchPointAnnotationJobs(sourceId, 0, 20)
      .then((res) => {
        if (!cancelled) {
          setPointJobs(res.content)
          setPointJobsError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setPointJobsError(errorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setPointJobsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [sourceId])

  useEffect(() => {
    if (!sourceId) return
    let cancelled = false
    fetchOntologyChanges(sourceId, 0, 50)
      .then((res) => {
        if (!cancelled) {
          setDirtyChanges(res.content.filter((c) => c.dirtyAnnotationsFlagged - c.resolvedCount > 0))
          setDirtyChangesError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setDirtyChangesError(errorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setDirtyChangesLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [sourceId])

  function openPointJob(job: PointAnnotationJob) {
    const target = job.status === 'REVIEW' || job.status === 'COMPLETE' ? 'review' : 'label'
    navigate(`/sources/${sourceId}/blank-import/jobs/${job.jobId}/${target}`)
  }

  return (
    <>
      <div className="content">
        <div className="page-back-row">
          <button
            className="btn btn-secondary"
            onClick={() => navigate(dataset?.projectId != null ? `/projects/${dataset.projectId}` : '/')}
            style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
          >
            <ArrowLeftIcon style={{ width: 14, height: 14 }} />
            Sources
          </button>
        </div>

        <div className="page-title-row">
          <div className="page-title">{dataset?.datasetName}</div>
        </div>

        {error && (
          <div className="alert alert-error" style={{ marginBottom: 16 }}>
            {error}
          </div>
        )}

        {loading ? (
          <div className="loading-state">
            <span className="spinner" /> Loading source…
          </div>
        ) : (
          dataset && (
            <>

              <VersionsList sourceId={sourceId} />

              <div style={{ marginTop: 24, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 24, alignItems: 'start' }}>
                <TitledCard title="Ongoing Blank Import Jobs" bodyClassName="no-padding">
                  {pointJobsError && (
                    <div className="alert alert-error" style={{ margin: 16 }}>
                      {pointJobsError}
                    </div>
                  )}
                  {pointJobsLoading ? (
                    <div className="loading-state">
                      <span className="spinner" /> Loading jobs…
                    </div>
                  ) : pointJobs.length === 0 ? (
                    <div className="empty-state">
                      <p>No blank import jobs yet.</p>
                    </div>
                  ) : (
                    <table>
                      <thead>
                        <tr>
                          <th>Status</th>
                          <th>Version</th>
                          <th className="num">Seed progress</th>
                          <th>Model</th>
                          <th>Started</th>
                          <th></th>
                        </tr>
                      </thead>
                      <tbody>
                        {pointJobs.map((job) => (
                          <tr key={job.jobId} className="clickable" onClick={() => openPointJob(job)}>
                            <td>
                              <span className={`badge ${pointJobStatusBadge(job.status)}`}>{job.status}</span>
                            </td>
                            <td>{job.versionTag}</td>
                            <td className="num">
                              {job.seedLabeledPoints} / {job.seedTotalPoints}
                            </td>
                            <td>{job.modelTag ?? '-'}</td>
                            <td style={{ color: 'var(--text-secondary)' }}>{formatDate(job.createdAt)}</td>
                            <td>
                              <span style={{ color: 'var(--accent)', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 2 }}>
                                View <ArrowRightIcon style={{ width: 12, height: 12 }} />
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </TitledCard>

                <TitledCard title="Dirty Annotations Queue" bodyClassName="no-padding">
                  {dirtyChangesError && (
                    <div className="alert alert-error" style={{ margin: 16 }}>
                      {dirtyChangesError}
                    </div>
                  )}
                  {dirtyChangesLoading ? (
                    <div className="loading-state">
                      <span className="spinner" /> Loading changes…
                    </div>
                  ) : dirtyChanges.length === 0 ? (
                    <div className="empty-state">
                      <p>No unresolved dirty annotations.</p>
                    </div>
                  ) : (
                    <table>
                      <thead>
                        <tr>
                          <th>Type</th>
                          <th>Old → New</th>
                          <th className="num">Remaining</th>
                          <th></th>
                        </tr>
                      </thead>
                      <tbody>
                        {dirtyChanges.map((c) => {
                          const remaining = c.dirtyAnnotationsFlagged - c.resolvedCount
                          return (
                            <tr
                              key={c.id}
                              className="clickable"
                              onClick={() => navigate(`/sources/${sourceId}/ontology/${c.id}`)}
                            >
                              <td>
                                <span className={`badge ${changeTypeBadge(c.changeType)}`}>
                                  {changeTypeLabel(c.changeType)}
                                </span>
                              </td>
                              <td>
                                <span className="mono" style={{ fontSize: 12 }}>
                                  {c.oldLabelCode ?? '(none)'} → {c.newLabelCodes?.join(', ') || (c.replacementLabelCode ? `(replacement: ${c.replacementLabelCode})` : '(none)')}
                                </span>
                              </td>
                              <td className="num">
                                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 8 }}>
                                  <div className="progress-bar-bg" style={{ width: 60 }}>
                                    <div
                                      className="progress-bar-fill"
                                      style={{
                                        width: `${c.dirtyAnnotationsFlagged > 0 ? (remaining / c.dirtyAnnotationsFlagged) * 100 : 0}%`,
                                        background: 'var(--danger)',
                                      }}
                                    />
                                  </div>
                                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                                    {remaining}/{c.dirtyAnnotationsFlagged}
                                  </span>
                                </div>
                              </td>
                              <td>
                                <span style={{ color: 'var(--accent)', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 2 }}>
                                  View <ArrowRightIcon style={{ width: 12, height: 12 }} />
                                </span>
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  )}
                </TitledCard>
              </div>
            </>
          )
        )}
      </div>
    </>
  )
}
