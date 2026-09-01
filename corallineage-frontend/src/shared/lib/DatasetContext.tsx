import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { useMatch } from 'react-router-dom'
import { fetchDataset, fetchDatasets } from '../../features/datasets/datasetsApi'
import { errorMessage } from '../api/client'
import { DatasetContext } from './datasetContextCore'
import type { Dataset } from '../../features/datasets/datasetsTypes'

/**
 * Holds every source in the same project as the currently-open source, resolved from the
 * `/sources/:sourceId/*` route: first the current source's parent projectId, then that
 * project's full source list. Backs `datasets.find(d => d.id === sourceId)` lookups and the
 * "reuse labels from a sibling source" dropdown.
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
