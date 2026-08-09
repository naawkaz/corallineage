import { createContext, useContext } from 'react'
import type { Dataset } from '../types/dataset'

export interface DatasetContextValue {
  datasets: Dataset[]
  activeDataset: Dataset | null
  activeDatasetId: number | null
  setActiveDatasetId: (id: number) => void
  loading: boolean
  error: string | null
  refresh: () => void
}

export const DatasetContext = createContext<DatasetContextValue | null>(null)

export function useDatasetContext(): DatasetContextValue {
  const ctx = useContext(DatasetContext)
  if (!ctx) throw new Error('useDatasetContext must be used within a DatasetProvider')
  return ctx
}
