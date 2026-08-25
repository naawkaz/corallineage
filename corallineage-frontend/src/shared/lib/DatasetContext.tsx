import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { useMatch } from 'react-router-dom'
import { fetchDataset, fetchDatasets } from '../../features/datasets/datasetsApi'
import { errorMessage } from '../api/client'
import { DatasetContext } from './datasetContextCore'
import type { Dataset } from '../../features/datasets/datasetsTypes'

/**
 * Holds every Source (the renamed Dataset entity) belonging to the same Project as the
 * currently-open Source, resolved from the `/sources/:sourceId/*` route. Source listing
 * moved one level up under a Project when the Project grouping layer was introduced, so
 * this first resolves the current Source's parent projectId, then loads that Project's
 * full Source list, exposed the same way the old global "every dataset I can reach" list
 * was: existing `datasets.find((d) => d.id === sourceId)` lookups and the Import page's
 * "reuse labels from a sibling Source" dropdown both keep working unchanged.
 */
export function DatasetProvider({ children }: { children: ReactNode }) {
  const match = useMatch('/sources/:sourceId/*')
  const sourceId = match?.params.sourceId ? Number(match.params.sourceId) : null

  const [datasets, setDatasets] = useState<Dataset[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    let cancelled = false
    if (!sourceId) {
      Promise.resolve().then(() => {
        if (!cancelled) {
          setDatasets([])
          setLoading(false)
          setError(null)
        }
      })
      return () => {
        cancelled = true
      }
    }
    async function load() {
      setLoading(true)
      const current = await fetchDataset(sourceId!)
      const siblings = current.projectId != null ? await fetchDatasets(current.projectId) : [current]
      if (cancelled) return
      setDatasets(siblings)
      setError(null)
    }

    Promise.resolve().then(load)
      .catch((err) => {
        if (!cancelled) setError(errorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [sourceId, reloadToken])

  const refresh = useCallback(() => {
    setLoading(true)
    setReloadToken((t) => t + 1)
  }, [])

  return (
    <DatasetContext.Provider value={{ datasets, loading, error, refresh, sourceId }}>{children}</DatasetContext.Provider>
  )
}
