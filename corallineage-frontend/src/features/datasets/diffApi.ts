import { api } from '../../shared/api/client'
import type { DatasetDiff } from '../datasets/diffTypes'

export async function fetchDatasetDiff(datasetId: number, v1: number, v2: number): Promise<DatasetDiff> {
  const { data } = await api.get<DatasetDiff>(`/datasets/${datasetId}/diff`, { params: { v1, v2 } })
  return data
}
