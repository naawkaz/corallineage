import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeftIcon, ArrowRightIcon } from '@heroicons/react/24/outline'
import { fetchDataset } from '../datasets/datasetsApi'
import { fetchVersions } from '../datasets/versionsApi'
import { fetchModels } from '../training/modelsApi'
import { fetchUnlabeledImportQueue } from '../pointannotation/pointAnnotationApi'
import { fetchOntologyChanges } from '../ontology/ontologyApi'
import { fetchAnnotationJobs } from '../annotation/annotationApi'
import { errorMessage } from '../../shared/api/client'
import { formatDate } from '../../shared/lib/format'
import { changeTypeBadge, changeTypeLabel } from '../../shared/constants'
import { scrollToAndHighlightAll } from '../../shared/lib/highlight'
import { useJobPolling } from '../../shared/lib/jobPollingContextCore'
import VersionsList from '../datasets/VersionsList'
import StartBlankImportDialog from '../datasets/StartBlankImportDialog'
import TitledCard from '../../shared/components/TitledCard'
import StatRingCard from '../../shared/components/StatRingCard'
import type { Dataset } from '../datasets/datasetsTypes'
import type { UnlabeledImportQueueEntry } from '../pointannotation/pointAnnotationTypes'
import type { OntologyChange } from '../ontology/ontologyTypes'
import type { AnnotationJob } from '../annotation/annotationTypes'

function queueEntryStatusBadge(status: string): string {
  switch (status) {
    case 'COMPLETE':
    case 'COMPLETED':
      return 'badge-green'
    case 'FAILED':
      return 'badge-red'
    case 'TRAINING':
    case 'RUNNING':
    case 'REVIEW':
    case 'PENDING_START':
      return 'badge-amber'
    default:
      return 'badge-gray'
  }
}

const IN_PROGRESS_STATUSES = new Set(['GENERATING', 'TRAINING', 'RUNNING', 'PROCESSING'])

export default function DatasetDashboard() {
  const navigate = useNavigate()
  const { sourceId: sourceIdParam } = useParams()
  const sourceId = Number(sourceIdParam)
  const { refreshSignal } = useJobPolling()

  const [dataset, setDataset] = useState<Dataset | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [queueEntries, setQueueEntries] = useState<UnlabeledImportQueueEntry[]>([])
  const [queueLoading, setQueueLoading] = useState(true)
  const [startEntry, setStartEntry] = useState<UnlabeledImportQueueEntry | null>(null)
  const [queueError, setQueueError] = useState<string | null>(null)

  const [ontologyChanges, setOntologyChanges] = useState<OntologyChange[]>([])
  const [dirtyChanges, setDirtyChanges] = useState<OntologyChange[]>([])
  const [dirtyChangesLoading, setDirtyChangesLoading] = useState(true)
  const [dirtyChangesError, setDirtyChangesError] = useState<string | null>(null)

  const [annotationJobs, setAnnotationJobs] = useState<AnnotationJob[]>([])

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

  // Re-fetches whenever JobPollingProvider's shared refreshSignal changes (it polls this same
  // endpoint itself and bumps the signal on any observed status transition), so this card reflects
  // live progress without running its own poll interval.
  useEffect(() => {
    if (!sourceId) return
    let cancelled = false
    fetchUnlabeledImportQueue(sourceId, 0, 20)
      .then((res) => {
        if (!cancelled) {
          setQueueEntries(res.content)
          setQueueError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setQueueError(errorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setQueueLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [sourceId, refreshSignal])

  useEffect(() => {
    if (!sourceId) return
    let cancelled = false
    fetchOntologyChanges(sourceId, 0, 50)
      .then((res) => {
        if (!cancelled) {
          setOntologyChanges(res.content)
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

  useEffect(() => {
    if (!sourceId) return
    let cancelled = false
    fetchAnnotationJobs(sourceId, 0, 50)
      .then((res) => {
        if (!cancelled) setAnnotationJobs(res.content)
      })
      .catch(() => {
        if (!cancelled) setAnnotationJobs([])
      })
    return () => {
      cancelled = true
    }
  }, [sourceId])

  const annotationsFlagged = ontologyChanges.reduce((sum, c) => sum + c.dirtyAnnotationsFlagged, 0)
  const unresolvedFlags = ontologyChanges.reduce((sum, c) => sum + (c.dirtyAnnotationsFlagged - c.resolvedCount), 0)
  const unresolvedPct = annotationsFlagged > 0 ? (unresolvedFlags / annotationsFlagged) * 100 : 0

  const pointQueueEntries = queueEntries.filter((e) => e.jobType === 'POINT_ANNOTATION')
  const pendingSeedJobs = pointQueueEntries.filter((e) => e.status === 'SEED_LABELING')

  // a single cold-start job can carry hundreds of seed points, so counting jobs
  // this sums the same seedTotalPoints/seedLabeledPoints figures the queue table's own "Seed progress"
  // column already shows per row
  const totalSeedPoints = pointQueueEntries.reduce((sum, e) => sum + (e.seedTotalPoints ?? 0), 0)
  const labeledSeedPoints = pointQueueEntries.reduce((sum, e) => sum + (e.seedLabeledPoints ?? 0), 0)
  const pendingSeedPoints = totalSeedPoints - labeledSeedPoints
  const pendingSeedPct = totalSeedPoints > 0 ? (pendingSeedPoints / totalSeedPoints) * 100 : 0

  const totalPredictions = annotationJobs.reduce((sum, j) => sum + j.totalPredictions, 0)
  const pendingPredictions = annotationJobs.reduce((sum, j) => sum + (j.totalPredictions - j.reviewedPredictions), 0)
  const pendingPredictionsPct = totalPredictions > 0 ? (pendingPredictions / totalPredictions) * 100 : 0

  // Dataset versions carrying at least one prediction that landed below the auto-accept
  // confidence threshold and is still sitting in review (totalPredictions > reviewedPredictions).
  // Deduped since a version can have more than one annotation job against it
  const pendingPredictionVersionIds = [
    ...new Set(
      annotationJobs
        .filter((j) => j.totalPredictions > j.reviewedPredictions && j.datasetVersionId != null)
        .map((j) => j.datasetVersionId as number),
    ),
  ]

  function openQueueEntry(entry: UnlabeledImportQueueEntry) {
    if (entry.resolveRoute == null || IN_PROGRESS_STATUSES.has(entry.status)) return
    if (entry.resolveRoute === 'START') {
      setStartEntry(entry)
      return
    }
    const suffix = entry.resolveRoute === 'ANNOTATION_REVIEW' ? 'review' : 'label'
    navigate(`/sources/${sourceId}/annotation/jobs/${entry.resolveJobId}/${suffix}`)
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
            <span className="spinner" /> Loading source...
          </div>
        ) : (
          dataset && (
            <>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 24 }}>
                <StatRingCard
                  fraction={`${unresolvedFlags}/${annotationsFlagged}`}
                  subtitle="Unresolved label changes"
                  percent={unresolvedPct}
                  danger={unresolvedFlags > 0}
                  onGo={() => scrollToAndHighlightAll(dirtyChanges.map((c) => `ontology-change-row-${c.id}`))}
                />
                <StatRingCard
                  fraction={`${pendingSeedPoints}/${totalSeedPoints}`}
                  subtitle="Unlabeled images pending labels"
                  percent={pendingSeedPct}
                  danger={pendingSeedPoints > 0}
                  onGo={() =>
                    scrollToAndHighlightAll(pendingSeedJobs.map((j) => `blank-import-row-${j.jobId}`))
                  }
                />
                <StatRingCard
                  fraction={`${pendingPredictions}/${totalPredictions}`}
                  subtitle="Predicted annotations to resolve"
                  percent={pendingPredictionsPct}
                  danger={pendingPredictions > 0}
                  onGo={() =>
                    scrollToAndHighlightAll(pendingPredictionVersionIds.map((id) => `version-row-${id}`))
                  }
                />
              </div>

              <div style={{ marginTop: 24 }}>
                <VersionsList sourceId={sourceId} />
              </div>

              <div style={{ marginTop: 24, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 24, alignItems: 'start' }}>
                <TitledCard title="Unlabeled import queue" bodyClassName="no-padding">
                  {queueError && (
                    <div className="alert alert-error" style={{ margin: 16 }}>
                      {queueError}
                    </div>
                  )}
                  {queueLoading ? (
                    <div className="loading-state">
                      <span className="spinner" /> Loading jobs...
                    </div>
                  ) : queueEntries.length === 0 ? (
                    <div className="empty-state">
                      <p>No unlabeled imports yet.</p>
                    </div>
                  ) : (
                    <table>
                      <thead>
                        <tr>
                          <th>Status</th>
                          <th className="num">#</th>
                          <th>Version</th>
                          <th className="num">Seed progress</th>
                          <th>Model</th>
                          <th>Started</th>
                          <th></th>
                        </tr>
                      </thead>
                      <tbody>
                        {queueEntries.map((entry) => {
                          const inProgress = IN_PROGRESS_STATUSES.has(entry.status)
                          const resolvable = entry.resolveRoute != null && !inProgress
                          return (
                          <tr
                            key={`${entry.jobType}-${entry.jobId}`}
                            id={`blank-import-row-${entry.jobId}`}
                            className={resolvable ? 'clickable' : undefined}
                            onClick={() => openQueueEntry(entry)}
                          >
                            <td>
                              <span className={`badge ${queueEntryStatusBadge(entry.status)}`}>{entry.status}</span>
                            </td>
                            <td className="num" style={{ color: 'var(--text-secondary)' }}>{entry.versionNumber ?? '-'}</td>
                            <td>{entry.versionTag ?? '-'}</td>
                            <td className="num">
                              {entry.seedTotalPoints != null ? `${entry.seedLabeledPoints} / ${entry.seedTotalPoints}` : '-'}
                            </td>
                            <td>{entry.modelTag ?? '-'}</td>
                            <td style={{ color: 'var(--text-secondary)' }}>{formatDate(entry.startedAt)}</td>
                            <td>
                              {inProgress ? (
                                <span className="spinner spinner-gray" style={{ width: 14, height: 14 }} />
                              ) : resolvable ? (
                                <span style={{ color: 'var(--accent)', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 2 }}>
                                  Resolve <ArrowRightIcon style={{ width: 12, height: 12 }} />
                                </span>
                              ) : entry.status === 'FAILED' ? (
                                <span style={{ color: 'var(--danger)', fontSize: 12 }}>Failed</span>
                              ) : null}
                            </td>
                          </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  )}
                </TitledCard>

                <TitledCard title="Ontology changes queue" bodyClassName="no-padding">
                  {dirtyChangesError && (
                    <div className="alert alert-error" style={{ margin: 16 }}>
                      {dirtyChangesError}
                    </div>
                  )}
                  {dirtyChangesLoading ? (
                    <div className="loading-state">
                      <span className="spinner" /> Loading changes...
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
                          <th>Old -&gt; New</th>
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
                              id={`ontology-change-row-${c.id}`}
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
                                  {c.oldLabelCode ?? '(none)'} -&gt; {c.newLabelCodes?.join(', ') || (c.replacementLabelCode ? `(replacement: ${c.replacementLabelCode})` : '(none)')}
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

      {startEntry && (
        <StartBlankImportDialog
          sourceId={sourceId}
          datasetVersionId={startEntry.resolveJobId as number}
          imageCount={startEntry.imageCount ?? 0}
          open
          onClose={() => setStartEntry(null)}
        />
      )}
    </>
  )
}