import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { fetchDatasets } from '../api/datasets'
import { errorMessage } from '../api/client'
import { DatasetContext } from './datasetContextCore'
import type { Dataset } from '../types/dataset'

export function DatasetProvider({ children }: { children: ReactNode }) {
  const [datasets, setDatasets] = useState<Dataset[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    let cancelled = false
    fetchDatasets()
      .then((result) => {
        if (cancelled) return
        setDatasets(result)
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
  }, [reloadToken])

  const refresh = useCallback(() => {
    setLoading(true)
    setReloadToken((t) => t + 1)
  }, [])

  return (
    <DatasetContext.Provider value={{ datasets, loading, error, refresh }}>{children}</DatasetContext.Provider>
  )
}
