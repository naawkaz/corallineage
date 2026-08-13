import { api } from './client'
import type { Architecture, InitiateTrainingRequest, TrainingJob } from '../types/training'
import type { Page } from '../types/version'

export async function fetchArchitectures(): Promise<Architecture[]> {
  const { data } = await api.get<{ architectures: Architecture[] }>('/training/architectures')
  return data.architectures
}

export async function submitTrainingJob(request: InitiateTrainingRequest): Promise<TrainingJob> {
  const { data } = await api.post<TrainingJob>('/training/jobs', request)
  return data
}

export async function fetchTrainingJob(jobId: number): Promise<TrainingJob> {
  const { data } = await api.get<TrainingJob>(`/training/jobs/${jobId}`)
  return data
}

export async function fetchTrainingJobs(datasetId: number, page: number, size: number): Promise<Page<TrainingJob>> {
  const { data } = await api.get<Page<TrainingJob>>('/training/jobs', { params: { datasetId, page, size } })
  return data
}
