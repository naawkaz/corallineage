import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { fetchDatasets } from '../api/datasets'
import { errorMessage } from '../api/client'
import { DatasetContext } from './datasetContextCore'
import type { Dataset } from '../types/dataset'

const STORAGE_KEY = 'coral-lineage:active-dataset-id'

export function DatasetProvider({ children }: { children: ReactNode }) {
  const [datasets, setDatasets] = useState<Dataset[]>([])
  const [activeDatasetId, setActiveDatasetIdState] = useState<number | null>(() => {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored ? Number(stored) : null
  })
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
        setActiveDatasetIdState((current) => {
          if (current && result.some((d) => d.id === current)) return current
          return result[0]?.id ?? null
        })
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

  const setActiveDatasetId = useCallback((id: number) => {
    setActiveDatasetIdState(id)
    localStorage.setItem(STORAGE_KEY, String(id))
  }, [])

  const refresh = useCallback(() => {
    setLoading(true)
    setReloadToken((t) => t + 1)
  }, [])

  const activeDataset = useMemo(
    () => datasets.find((d) => d.id === activeDatasetId) ?? null,
    [datasets, activeDatasetId],
  )

  const value = useMemo(
    () => ({ datasets, activeDataset, activeDatasetId, setActiveDatasetId, loading, error, refresh }),
    [datasets, activeDataset, activeDatasetId, setActiveDatasetId, loading, error, refresh],
  )

  return <DatasetContext.Provider value={value}>{children}</DatasetContext.Provider>
}
