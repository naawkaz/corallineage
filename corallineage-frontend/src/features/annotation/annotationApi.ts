import { api } from '../../shared/api/client'
import type {
  AddToProjectRequest,
  AnnotationJob,
  AnnotationPrediction,
  InitiateAnnotationJobRequest,
  ReviewPredictionRequest,
  SubmitExistingVersionJobRequest,
  SubmitValidationJobRequest,
} from '../annotation/annotationTypes'
import type { Page } from '../datasets/versionsTypes'

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
  gridRows: number,
  gridCols: number,
  confidenceThreshold: number,
  files: File[],
): Promise<AnnotationJob> {
  const form = new FormData()
  form.append('modelVersionId', String(modelVersionId))
  form.append('datasetId', String(datasetId))
  form.append('initiatedBy', initiatedBy)
  form.append('gridRows', String(gridRows))
  form.append('gridCols', String(gridCols))
  form.append('confidenceThreshold', String(confidenceThreshold))
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

/** Warm-start blank-import: annotates an existing dataset version's already-present images with a chosen model. */
export async function submitExistingVersionJob(request: SubmitExistingVersionJobRequest): Promise<AnnotationJob> {
  const { data } = await api.post<AnnotationJob>('/annotation/jobs/existing-version', request)
  return data
}

/** Commits a fully-reviewed existing-version job's predictions directly onto its target dataset version. */
export async function applyToExistingVersion(jobId: number): Promise<AnnotationJob> {
  const { data } = await api.post<AnnotationJob>(`/annotation/jobs/${jobId}/apply-to-version`)
  return data
}

export async function fetchAnnotationJobs(datasetId: number, page: number, size: number): Promise<Page<AnnotationJob>> {
  const { data } = await api.get<Page<AnnotationJob>>('/annotation/jobs', { params: { datasetId, page, size } })
  return data
}

/** Scoped jobs list for a specific model's own detail page (replaces the old dataset-wide list page). */
export async function fetchAnnotationJobsByModel(modelVersionId: number, page: number, size: number): Promise<Page<AnnotationJob>> {
  const { data } = await api.get<Page<AnnotationJob>>('/annotation/jobs/by-model', { params: { modelVersionId, page, size } })
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

/** Bulk-accepts every still-pending prediction on a job at its current predicted label. */
export async function acceptAllPredictions(jobId: number, reviewedBy: string): Promise<AnnotationJob> {
  const { data } = await api.post<AnnotationJob>(`/annotation/jobs/${jobId}/predictions/accept-all`, { reviewedBy })
  return data
}
