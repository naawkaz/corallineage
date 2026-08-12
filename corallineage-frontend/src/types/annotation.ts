export type AnnotationJobStatus = 'RUNNING' | 'COMPLETED' | 'FAILED'
export type AnnotationReviewStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED'

export interface AnnotationJob {
  jobId: number
  status: AnnotationJobStatus
  reviewStatus: AnnotationReviewStatus
  modelVersionId: number
  modelTag: string | null
  imageCount: number
  initiatedBy: string
  initiatedAt: string
  completedAt: string | null
  totalPredictions: number
  reviewedPredictions: number
  errorMessage: string | null
}

export interface AnnotationPrediction {
  predictionId: number
  imageId: number
  filename: string
  pointIndex: number
  predictedLabelCode: string
  confidenceScore: number
  accepted: boolean | null
  correctedLabelCode: string | null
  reviewedBy: string | null
}

export interface InitiateAnnotationJobRequest {
  modelVersionId: number
  imageIds: number[]
  initiatedBy: string
}

export interface ReviewPredictionRequest {
  accepted: boolean
  correctedLabelCode?: string
  reviewedBy: string
}
