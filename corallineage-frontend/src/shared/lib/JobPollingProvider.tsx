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
 * Shared source-scoped poller for async job status (training jobs, the unlabeled import queue,
 * annotation jobs). Mounted once above the router's Outlet so it outlives any single page or
 * dialog. Surfaces completion and failure toasts, and increments `refreshSignal` on every
 * observed status change so consuming lists add it to their fetch effect's deps and re-fetch.
 */
export function JobPollingProvider({ children }: { children: ReactNode }) {
  const { sourceId } = useDatasetContext()
  const toast = useToast()
  const toastRef = useRef(toast)
  const [refreshSignal, setRefreshSignal] = useState(0)
  const lastStatusRef = useRef(new Map<string, string>())
  const lastVersionIdsRef = useRef(new Set<number>())
  const lastQueueKeysRef = useRef(new Map<string, { versionTag: string | null; jobType: string }>())

  useEffect(() => {
    toastRef.current = toast
  }, [toast])

  useEffect(() => {
    // Reset last-seen statuses on source change so the first poll under a new source never
    // toasts for a job that was already terminal before this page loaded.
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
            // A version not seen before may have no importStatus to compare (a manual snapshot,
            // or one created synchronously with no import job), so trigger a refresh on first
            // sight or an already-mounted list never learns it appeared.
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
                  toastRef.current.push({ variant: 'success', title: 'Import complete', detail: version.versionTag })
                } else if (version.importStatus === 'FAILED') {
                  toastRef.current.push({ variant: 'error', title: 'Import failed', detail: version.versionTag })
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
              // A job can go QUEUED -> RUNNING -> FAILED faster than the poll interval, so the
              // first tick already finds it terminal with previous == null. A recent initiatedAt
              // distinguishes that from a job that finished before this session started (which the
              // "never toast on first sight" rule exists to suppress).
              const startedRecently = Date.now() - new Date(job.initiatedAt).getTime() < 15000
              if ((previous != null && previous !== 'COMPLETED' && previous !== 'FAILED') || (previous == null && startedRecently)) {
                if (job.status === 'COMPLETED') {
                  toastRef.current.push({ variant: 'success', title: 'Training complete', detail: job.modelTag ?? undefined })
                } else if (job.status === 'FAILED') {
                  toastRef.current.push({ variant: 'error', title: 'Training failed', detail: job.errorMessage ?? undefined })
                }
              }
              seen.set(key, job.status)
            }
            // A RUNNING job's epoch count advances without a status change; track it separately
            // so a live "epoch X/Y" readout updates during training, not only on transitions.
            if (job.status === 'RUNNING') {
              const epochKey = `training-epoch-${job.jobId}`
              const epochValue = String(job.currentEpoch ?? '')
              if (seen.get(epochKey) !== epochValue) {
                changed = true
                seen.set(epochKey, epochValue)
              }
            }
          }

          // VALIDATION jobs and plain "Upload & Annotate New Images" jobs (commitInPlace=false)
          // run from dialogs on ModelDetail. Tracking them here means their completion toast
          // survives the dialog closing or the user navigating away.
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
                  toastRef.current.push({
                    variant: 'success',
                    title: isValidation ? 'Validation complete' : 'Auto-annotate complete',
                    detail: isValidation ? `${job.totalPredictions} point(s) checked.` : `${job.totalPredictions} point(s) ready.`,
                  })
                } else if (job.status === 'FAILED') {
                  toastRef.current.push({
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
                  toastRef.current.push({
                    variant: 'success',
                    title: 'Ready for review',
                    detail: entry.versionTag ?? undefined,
                  })
                } else if (entry.status === 'FAILED') {
                  toastRef.current.push({
                    variant: 'error',
                    title: entry.jobType === 'ANNOTATION' ? 'Annotation job failed' : 'Blank import failed',
                    detail: entry.versionTag ?? undefined,
                  })
                }
              }
              seen.set(key, entry.status)
            }
          }

          // An ANNOTATION entry that vanished from the queue between two polls left because every
          // prediction cleared the confidence threshold and the backend auto-registered its
          // dataset version, skipping review. Toast that completion since review never fired one.
          const previousQueueKeys = lastQueueKeysRef.current
          if (previousQueueKeys.size > 0) {
            for (const [key, info] of previousQueueKeys) {
              if (currentQueueKeys.has(key)) continue
              const lastKnownStatus = seen.get(key)
              seen.delete(key)
              if (info.jobType === 'ANNOTATION' && lastKnownStatus !== 'FAILED') {
                changed = true
                toastRef.current.push({
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
  }, [sourceId])

  return <JobPollingContext.Provider value={{ refreshSignal }}>{children}</JobPollingContext.Provider>
}
