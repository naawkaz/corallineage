import { api } from '../../shared/api/client'
import type {
  GeneratedPoint,
  LabelPointRequest,
  PointAnnotationJob,
  StartPointAnnotationJobRequest,
  TrainPointAnnotationJobRequest,
  UnlabeledImportQueueEntry,
} from '../pointannotation/pointAnnotationTypes'
import type { Page } from '../datasets/versionsTypes'

export async function startPointAnnotationJob(request: StartPointAnnotationJobRequest): Promise<PointAnnotationJob> {
  const { data } = await api.post<PointAnnotationJob>('/point-annotation/jobs', request)
  return data
}

export async function fetchPointAnnotationJobs(datasetId: number, page: number, size: number): Promise<Page<PointAnnotationJob>> {
  const { data } = await api.get<Page<PointAnnotationJob>>('/point-annotation/jobs', {
    params: { datasetId, page, size },
  })
  return data
}

export async function fetchPointAnnotationJob(jobId: number): Promise<PointAnnotationJob> {
  const { data } = await api.get<PointAnnotationJob>(`/point-annotation/jobs/${jobId}`)
  return data
}

export async function fetchGeneratedPoints(
  jobId: number,
  seed: boolean,
  page: number,
  size: number,
): Promise<Page<GeneratedPoint>> {
  const { data } = await api.get<Page<GeneratedPoint>>(`/point-annotation/jobs/${jobId}/points`, {
    params: { seed, page, size },
  })
  return data
}

export async function labelPoint(pointId: number, request: LabelPointRequest): Promise<GeneratedPoint> {
  const { data } = await api.patch<GeneratedPoint>(`/point-annotation/points/${pointId}/label`, request)
  return data
}

export async function trainPointAnnotationJob(
  jobId: number,
  request?: TrainPointAnnotationJobRequest,
): Promise<PointAnnotationJob> {
  const { data } = await api.post<PointAnnotationJob>(`/point-annotation/jobs/${jobId}/train`, request)
  return data
}

export async function autoAnnotatePointAnnotationJob(jobId: number): Promise<PointAnnotationJob> {
  const { data } = await api.post<PointAnnotationJob>(`/point-annotation/jobs/${jobId}/auto-annotate`)
  return data
}

export async function fetchUnlabeledImportQueue(
  datasetId: number,
  page: number,
  size: number,
): Promise<Page<UnlabeledImportQueueEntry>> {
  const { data } = await api.get<Page<UnlabeledImportQueueEntry>>('/point-annotation/unlabeled-queue', {
    params: { datasetId, page, size },
  })
  return data
}
