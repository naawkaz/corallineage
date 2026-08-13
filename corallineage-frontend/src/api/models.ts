import { api } from './client'
import type { MlflowDetails, ModelVersion } from '../types/model'
import type { Page } from '../types/version'

export async function fetchModels(
  datasetId: number,
  page: number,
  size: number,
  status?: string,
): Promise<Page<ModelVersion>> {
  const { data } = await api.get<Page<ModelVersion>>('/models', {
    params: { datasetId, page, size, status: status || undefined },
  })
  return data
}

export async function fetchModel(id: number): Promise<ModelVersion> {
  const { data } = await api.get<ModelVersion>(`/models/${id}`)
  return data
}

export async function fetchMlflowDetails(id: number): Promise<MlflowDetails> {
  const { data } = await api.get<MlflowDetails>(`/models/${id}/mlflow-details`)
  return data
}

export async function updateModelStatus(id: number, status: string, changedBy: string): Promise<ModelVersion> {
  const { data } = await api.patch<ModelVersion>(`/models/${id}/status`, { status, changedBy })
  return data
}
