import { createContext, useContext } from 'react'
import type { Dataset } from '../../features/datasets/datasetsTypes'

export interface DatasetContextValue {
  datasets: Dataset[]
  loading: boolean
  error: string | null
  refresh: () => void
  // The current Source id resolved from the `/sources/:sourceId/*` route, or null outside that
  // tree - already computed internally to load `datasets`, exposed here so other providers (e.g.
  // JobPollingProvider) don't need to re-derive it with their own `useMatch` call.
  sourceId: number | null
}

export const DatasetContext = createContext<DatasetContextValue | null>(null)

export function useDatasetContext(): DatasetContextValue {
  const ctx = useContext(DatasetContext)
  if (!ctx) throw new Error('useDatasetContext must be used within a DatasetProvider')
  return ctx
}
