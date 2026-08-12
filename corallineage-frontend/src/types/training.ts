export type TrainingJobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface Architecture {
  id: string
  displayName: string
  description: string
}

export interface TrainingJob {
  jobId: number
  status: TrainingJobStatus
  architecture: string
  hyperparameters: string | null
  datasetVersionId: number
  datasetVersionTag: string
  initiatedBy: string
  initiatedAt: string
  completedAt: string | null
  durationSeconds: number | null
  modelVersionId: number | null
  modelTag: string | null
  errorMessage: string | null
}

export interface InitiateTrainingRequest {
  datasetId: number
  datasetVersionId?: number
  architecture?: string
  learningRate?: number
  batchSize?: number
  epochs?: number
  initiatedBy: string
}
