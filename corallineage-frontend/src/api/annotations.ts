import { api } from './client'
import type {
  AnnotationJob,
  AnnotationPrediction,
  InitiateAnnotationJobRequest,
  ReviewPredictionRequest,
} from '../types/annotation'
import type { Page } from '../types/version'

export async function submitAnnotationJob(request: InitiateAnnotationJobRequest): Promise<AnnotationJob> {
  const { data } = await api.post<AnnotationJob>('/annotation/jobs', request)
  return data
}

export async function fetchAnnotationJobs(page: number, size: number): Promise<Page<AnnotationJob>> {
  const { data } = await api.get<Page<AnnotationJob>>('/annotation/jobs', { params: { page, size } })
  return data
}

export async function fetchAnnotationJob(jobId: number): Promise<AnnotationJob> {
  const { data } = await api.get<AnnotationJob>(`/annotation/jobs/${jobId}`)
  return data
}

export async function fetchPredictions(
  jobId: number,
  page: number,
  size: number,
  imageId?: number,
  accepted?: boolean,
): Promise<Page<AnnotationPrediction>> {
  const { data } = await api.get<Page<AnnotationPrediction>>(`/annotation/jobs/${jobId}/predictions`, {
    params: { page, size, imageId, accepted },
  })
  return data
}

export async function reviewPrediction(
  jobId: number,
  predictionId: number,
  request: ReviewPredictionRequest,
): Promise<AnnotationPrediction> {
  const { data } = await api.patch<AnnotationPrediction>(
    `/annotation/jobs/${jobId}/predictions/${predictionId}/review`,
    request,
  )
  return data
}
