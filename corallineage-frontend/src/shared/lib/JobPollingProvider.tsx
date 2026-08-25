import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useDatasetContext } from './datasetContextCore'
import { useToast } from './toastContextCore'
import { JobPollingContext } from './jobPollingContextCore'
import { fetchTrainingJobs } from '../../features/training/trainingApi'
import { fetchUnlabeledImportQueue } from '../../features/pointannotation/pointAnnotationApi'
import { fetchVersions } from '../../features/datasets/versionsApi'

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

  useEffect(() => {
    // New source (or leaving the source tree entirely): last-seen statuses from the previous
    // source no longer apply - reset so the first poll under the new source never fires a toast
    // for a job that was already in a terminal state before this page even loaded.
    lastStatusRef.current = new Map()
    if (!sourceId) return

    let cancelled = false

    function poll() {
      Promise.all([
        fetchTrainingJobs(sourceId as number, 0, 20),
        fetchUnlabeledImportQueue(sourceId as number, 0, 20),
        fetchVersions(sourceId as number, 0, 50),
      ])
        .then(([trainingJobs, queue, versions]) => {
          if (cancelled) return
          let changed = false
          const seen = lastStatusRef.current

          for (const version of versions.content) {
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

          for (const job of trainingJobs.content) {
            const key = `training-${job.jobId}`
            const previous = seen.get(key)
            if (previous !== job.status) {
              changed = true
              if (previous != null && previous !== 'COMPLETED' && previous !== 'FAILED') {
                if (job.status === 'COMPLETED') {
                  toast.push({ variant: 'success', title: 'Training complete', detail: job.modelTag ?? undefined })
                } else if (job.status === 'FAILED') {
                  toast.push({ variant: 'error', title: 'Training failed', detail: job.errorMessage ?? undefined })
                }
              }
              seen.set(key, job.status)
            }
          }

          for (const entry of queue.content) {
            const key = `${entry.jobType}-${entry.jobId}`
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
