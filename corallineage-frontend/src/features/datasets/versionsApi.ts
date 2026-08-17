import { api } from '../../shared/api/client'
import type {
  CreateLabelDefinitionRequest,
  CreateVersionRequest,
  DatasetVersion,
  LabelDefinition,
  Page,
} from '../datasets/versionsTypes'

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

export async function createLabelDefinition(
  datasetId: number,
  versionId: number,
  request: CreateLabelDefinitionRequest,
): Promise<LabelDefinition> {
  const { data } = await api.post<LabelDefinition>(
    `/datasets/${datasetId}/versions/${versionId}/labels`,
    request,
  )
  return data
}
