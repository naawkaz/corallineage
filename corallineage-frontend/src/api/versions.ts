import { api } from './client'
import type { CreateVersionRequest, DatasetVersion, Page } from '../types/version'

export async function fetchVersions(
  datasetId: number,
  page: number,
  size: number,
): Promise<Page<DatasetVersion>> {
  const { data } = await api.get<Page<DatasetVersion>>(`/datasets/${datasetId}/versions`, {
    params: { page, size },
  })
  return data
}

export async function fetchVersion(datasetId: number, id: number): Promise<DatasetVersion> {
  const { data } = await api.get<DatasetVersion>(`/datasets/${datasetId}/versions/${id}`)
  return data
}

export async function createVersion(
  datasetId: number,
  request: CreateVersionRequest,
): Promise<DatasetVersion> {
  const { data } = await api.post<DatasetVersion>(`/datasets/${datasetId}/versions`, request)
  return data
}
