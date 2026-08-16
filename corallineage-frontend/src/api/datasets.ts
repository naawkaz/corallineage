import { api } from './client'
import type { CreateDatasetRequest, Dataset } from '../types/dataset'

export async function fetchDatasets(projectId: number): Promise<Dataset[]> {
  const { data } = await api.get<Dataset[]>(`/projects/${projectId}/datasets`)
  return data
}

export async function fetchDataset(id: number): Promise<Dataset> {
  const { data } = await api.get<Dataset>(`/datasets/${id}`)
  return data
}

export async function createDataset(projectId: number, request: CreateDatasetRequest): Promise<Dataset> {
  const { data } = await api.post<Dataset>(`/projects/${projectId}/datasets`, request)
  return data
}
