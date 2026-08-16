import { api } from './client'
import type { AnnotationPointSummary, DatasetImage } from '../types/image'
import type { Page } from '../types/version'

export async function fetchImages(
  datasetId: number,
  versionId: number,
  page: number,
  size: number,
  humanLabeled?: boolean,
): Promise<Page<DatasetImage>> {
  const { data } = await api.get<Page<DatasetImage>>(
    `/datasets/${datasetId}/versions/${versionId}/images`,
    { params: { page, size, humanLabeled } },
  )
  return data
}

/**
 * Every annotation point on every image in a dataset version, for the Dataset Versions image
 * grid's optional points overlay. Only meant to be called when the user switches the overlay
 * toggle on, since it returns the whole version's points in one request rather than paging.
 */
export async function fetchAnnotationPoints(
  datasetId: number,
  versionId: number,
): Promise<AnnotationPointSummary[]> {
  const { data } = await api.get<AnnotationPointSummary[]>(
    `/datasets/${datasetId}/versions/${versionId}/points`,
  )
  return data
}

export function imageFileUrl(image: DatasetImage): string {
  return `${api.defaults.baseURL}${image.url}`
}

export function imageFileUrlById(imageId: number): string {
  return `${api.defaults.baseURL}/images/${imageId}/file`
}
