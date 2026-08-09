import { api } from './client'
import type { CreateDatasetRequest, Dataset } from '../types/dataset'

export async function fetchDatasets(): Promise<Dataset[]> {
  const { data } = await api.get<Dataset[]>('/datasets')
  return data
}

export async function fetchDataset(id: number): Promise<Dataset> {
  const { data } = await api.get<Dataset>(`/datasets/${id}`)
  return data
}

export async function createDataset(request: CreateDatasetRequest): Promise<Dataset> {
  const { data } = await api.post<Dataset>('/datasets', request)
  return data
}
