import { api } from './client'
import type { DatasetImage } from '../types/image'
import type { Page } from '../types/version'

export async function fetchImages(
  datasetId: number,
  versionId: number,
  page: number,
  size: number,
): Promise<Page<DatasetImage>> {
  const { data } = await api.get<Page<DatasetImage>>(
    `/datasets/${datasetId}/versions/${versionId}/images`,
    { params: { page, size } },
  )
  return data
}

export function imageFileUrl(image: DatasetImage): string {
  return `${api.defaults.baseURL}${image.url}`
}
