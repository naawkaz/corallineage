import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useDatasetContext } from './datasetContextCore'
import { useToast } from './toastContextCore'
import { JobPollingContext } from './jobPollingContextCore'
import { fetchTrainingJobs } from '../../features/training/trainingApi'
import { fetchUnlabeledImportQueue } from '../../features/pointannotation/pointAnnotationApi'
import { fetchVersions } from '../../features/datasets/versionsApi'
import { fetchAnnotationJobs } from '../../features/annotation/annotationApi'

const POLL_INTERVAL_MS = 4000

/**
 * Source-scoped live job feedback: polls the two list endpoints that carry the app's async job
 * statuses (training jobs, the Unlabeled import queue) on a shared interval for as long as the
 * user stays anywhere under `/sources/:sourceId/*` (mounted once in ProtectedRoute, above the
 * router's Outlet, so it survives navigating between pages within the same source - the actual
 * fix for the orphaned-toast bug: a per-dialog poller dies when its dialog unmounts, this doesn't).
 *
 * Deliberately narrower than a full "every list reads pre-fetched data from this provider"
 * design: rather than migrating VersionsList/ModelRegistry/the queue card onto provider-owned
 * list data (a much larger refactor of each page's own pagination/filter state), this exposes a
 * `refreshSignal` (see jobPollingContextCore.ts) that increments on every observed status change.
 * Consumers keep their own fetch functions and pagination but add `refreshSignal` to their fetch
 * effect's dependency array, so they re-fetch themselves the moment something relevant changes.
 * Toasts fire here directly via the same shared `useToast()` every dialog already uses.
 */
export function JobPollingProvider({ children }: { children: ReactNode }) {
  const { sourceId } = useDatasetContext()
  const toast = useToast()
  const [refreshSignal, setRefreshSignal] = useState(0)
  const lastStatusRef = useRef(new Map<string, string>())
  const lastVersionIdsRef = useRef(new Set<number>())
  const lastQueueKeysRef = useRef(new Map<string, { versionTag: string | null; jobType: string }>())

  useEffect(() => {
    // New source (or leaving the source tree entirely): last-seen statuses from the previous
    // source no longer apply - reset so the first poll under the new source never fires a toast
    // for a job that was already in a terminal state before this page even loaded.
    lastStatusRef.current = new Map()
    lastVersionIdsRef.current = new Set()
    lastQueueKeysRef.current = new Map()
    if (!sourceId) return

    let cancelled = false

    function poll() {
      Promise.all([
        fetchTrainingJobs(sourceId as number, 0, 20),
        fetchUnlabeledImportQueue(sourceId as number, 0, 20),
        fetchVersions(sourceId as number, 0, 50),
        fetchAnnotationJobs(sourceId as number, 0, 50),
      ])
        .then(([trainingJobs, queue, versions, annotationJobs]) => {
          if (cancelled) return
          let changed = false
          const seen = lastStatusRef.current
          const seenVersionIds = lastVersionIdsRef.current
          const currentVersionIds = new Set<number>()

          for (const version of versions.content) {
            currentVersionIds.add(version.id)
            // A version this poll has never seen before (a brand new snapshot/manual version, or
            // one that just became resolved and is no longer filtered out of this list - see
            // DatasetVersionService.getVersionHistory's UNRESOLVED filter) needs a refresh even
            // when it has no importStatus to compare (a manual snapshot, or a version created
            // synchronously with no ImportJob at all): otherwise a list that was already mounted
            // when the version first appeared never learns about it until something else happens
            // to bump refreshSignal.
            if (!seenVersionIds.has(version.id)) {
              changed = true
            }
            if (version.importStatus == null) continue
            const key = `import-${version.id}`
            const previous = seen.get(key)
            if (previous !== version.importStatus) {
              changed = true
              if (previous === 'PROCESSING') {
                if (version.importStatus === 'COMPLETED') {
                  toast.push({ variant: 'success', title: 'Import complete', detail: version.versionTag })
                } else if (version.importStatus === 'FAILED') {
                  toast.push({ variant: 'error', title: 'Import failed', detail: version.versionTag })
                }
              }
              seen.set(key, version.importStatus)
            }
          }
          lastVersionIdsRef.current = currentVersionIds

          for (const job of trainingJobs.content) {
            const key = `training-${job.jobId}`
            const previous = seen.get(key)
            if (previous !== job.status) {
              changed = true
              // A job that fails almost immediately (e.g. the ML service isn't reachable at all,
              // so training never gets past a connection error) can go QUEUED -> RUNNING -> FAILED
              // faster than this poll's own interval, so the very first tick that ever sees this
              // job already finds it terminal - previous is null, not because it's some old job
              // that finished before this session started (the case this "never toast on first
              // sight" rule exists to suppress), but because it started and died between two polls.
              // Recent-initiatedAt is what tells the two apart.
              const startedRecently = Date.now() - new Date(job.initiatedAt).getTime() < 15000
              if ((previous != null && previous !== 'COMPLETED' && previous !== 'FAILED') || (previous == null && startedRecently)) {
                if (job.status === 'COMPLETED') {
                  toast.push({ variant: 'success', title: 'Training complete', detail: job.modelTag ?? undefined })
                } else if (job.status === 'FAILED') {
                  toast.push({ variant: 'error', title: 'Training failed', detail: job.errorMessage ?? undefined })
                }
              }
              seen.set(key, job.status)
            }
            // A RUNNING job's epoch count advances without its status ever changing - without
            // tracking this separately, a live "epoch X/Y" readout (e.g. ModelRegistry.tsx's
            // in-progress row) would only ever update on the status transitions above, i.e. never
            // while actually training.
            if (job.status === 'RUNNING') {
              const epochKey = `training-epoch-${job.jobId}`
              const epochValue = String(job.currentEpoch ?? '')
              if (seen.get(epochKey) !== epochValue) {
                changed = true
                seen.set(epochKey, epochValue)
              }
            }
          }

          // VALIDATION jobs and plain "Upload & Annotate New Images" jobs (commitInPlace=false -
          // the warm-start blank-import/cold-start-delegated flavor is already covered above via
          // the queue's own REVIEW/"Dataset version ready" toasts) run from dialogs on
          // ModelDetail.tsx. Those dialogs used to own a bespoke poll that both drove their own
          // in-dialog progress UI and updated a 'progress' toast to its final state - closing the
          // dialog (or navigating away) killed that poll and orphaned the toast forever, since a
          // 'progress' toast never auto-dismisses on its own. Tracking these jobs here instead,
          // the same way training/queue jobs already are, means the completion toast survives the
          // dialog's lifetime.
          for (const job of annotationJobs.content) {
            if (job.jobType !== 'VALIDATION' && !(job.jobType === 'NEW_IMAGES' && !job.commitInPlace)) continue
            const key = `dialog-annotation-${job.jobId}`
            const previous = seen.get(key)
            if (previous !== job.status) {
              changed = true
              const startedRecently = Date.now() - new Date(job.initiatedAt).getTime() < 15000
              if ((previous != null && previous !== 'COMPLETED' && previous !== 'FAILED') || (previous == null && startedRecently)) {
                const isValidation = job.jobType === 'VALIDATION'
                if (job.status === 'COMPLETED') {
                  toast.push({
                    variant: 'success',
                    title: isValidation ? 'Validation complete' : 'Auto-annotate complete',
                    detail: isValidation ? `${job.totalPredictions} point(s) checked.` : `${job.totalPredictions} point(s) ready.`,
                  })
                } else if (job.status === 'FAILED') {
                  toast.push({
                    variant: 'error',
                    title: isValidation ? 'Validation failed' : 'Auto-annotate failed',
                    detail: job.errorMessage ?? undefined,
                  })
                }
              }
              seen.set(key, job.status)
            }
          }

          const currentQueueKeys = new Map<string, { versionTag: string | null; jobType: string }>()
          for (const entry of queue.content) {
            const key = `${entry.jobType}-${entry.jobId}`
            currentQueueKeys.set(key, { versionTag: entry.versionTag, jobType: entry.jobType })
            const previous = seen.get(key)
            if (previous !== entry.status) {
              changed = true
              if (previous != null && previous !== entry.status) {
                if (entry.jobType === 'ANNOTATION' && entry.status === 'REVIEW') {
                  toast.push({
                    variant: 'success',
                    title: 'Ready for review',
                    detail: entry.versionTag ?? undefined,
                  })
                } else if (entry.status === 'FAILED') {
                  toast.push({
                    variant: 'error',
                    title: entry.jobType === 'ANNOTATION' ? 'Annotation job failed' : 'Blank import failed',
                    detail: entry.versionTag ?? undefined,
                  })
                }
              }
              seen.set(key, entry.status)
            }
          }

          // A ready-for-review ANNOTATION entry that vanished from the queue between two polls (as
          // opposed to never having been seen, e.g. right after mount) left because every prediction
          // cleared the confidence threshold and the backend auto-registered its dataset version
          // (see AnnotationJobExecutor/PointAnnotationJobStatusService) - the human never got a
          // "Ready for review" toast for it since it skipped review entirely, so tell them it's done
          // wherever they currently are instead of leaving that silent.
          const previousQueueKeys = lastQueueKeysRef.current
          if (previousQueueKeys.size > 0) {
            for (const [key, info] of previousQueueKeys) {
              if (currentQueueKeys.has(key)) continue
              const lastKnownStatus = seen.get(key)
              seen.delete(key)
              if (info.jobType === 'ANNOTATION' && lastKnownStatus !== 'FAILED') {
                changed = true
                toast.push({
                  variant: 'success',
                  title: 'Dataset version ready',
                  detail: info.versionTag ?? undefined,
                })
              }
            }
          }
          lastQueueKeysRef.current = currentQueueKeys

          if (changed) setRefreshSignal((n) => n + 1)
        })
        .catch(() => {
          // transient poll failure - try again next tick
        })
    }

    poll()
    const intervalId = window.setInterval(poll, POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      window.clearInterval(intervalId)
    }
  }, [sourceId, toast])

  return <JobPollingContext.Provider value={{ refreshSignal }}>{children}</JobPollingContext.Provider>
}
