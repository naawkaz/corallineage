import { api } from './client'
import type {
  AddToProjectRequest,
  AnnotationJob,
  AnnotationPrediction,
  InitiateAnnotationJobRequest,
  ReviewPredictionRequest,
  SubmitValidationJobRequest,
} from '../types/annotation'
import type { Page } from '../types/version'

export async function submitAnnotationJob(request: InitiateAnnotationJobRequest): Promise<AnnotationJob> {
  const { data } = await api.post<AnnotationJob>('/annotation/jobs', request)
  return data
}

export async function submitValidationJob(request: SubmitValidationJobRequest): Promise<AnnotationJob> {
  const { data } = await api.post<AnnotationJob>('/annotation/jobs/validate', request)
  return data
}

export async function submitNewImagesJob(
  modelVersionId: number,
  datasetId: number,
  initiatedBy: string,
  pointsPerImage: number,
  files: File[],
): Promise<AnnotationJob> {
  const form = new FormData()
  form.append('modelVersionId', String(modelVersionId))
  form.append('datasetId', String(datasetId))
  form.append('initiatedBy', initiatedBy)
  form.append('pointsPerImage', String(pointsPerImage))
  for (const file of files) {
    form.append('files', file)
  }
  const { data } = await api.post<AnnotationJob>('/annotation/jobs/new-images', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

export async function addToProject(jobId: number, request: AddToProjectRequest): Promise<AnnotationJob> {
  const { data } = await api.post<AnnotationJob>(`/annotation/jobs/${jobId}/add-to-project`, request)
  return data
}

export async function fetchAnnotationJobs(datasetId: number, page: number, size: number): Promise<Page<AnnotationJob>> {
  const { data } = await api.get<Page<AnnotationJob>>('/annotation/jobs', { params: { datasetId, page, size } })
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
