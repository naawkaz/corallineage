export type AnnotationJobStatus = 'RUNNING' | 'COMPLETED' | 'FAILED'
export type AnnotationReviewStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED'
export type AnnotationJobType = 'VALIDATION' | 'NEW_IMAGES'

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
  jobType: AnnotationJobType | null
  datasetVersionId: number | null
  correctPredictions: number | null
  accuracyPercent: number | null
  addedToProjectVersionId: number | null
  confidenceThreshold: number | null
  commitInPlace: boolean
}

export interface SubmitExistingVersionJobRequest {
  modelVersionId: number
  datasetVersionId: number
  imageIds: number[]
  gridRows?: number
  gridCols?: number
  confidenceThreshold?: number
  initiatedBy: string
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
  rowPosition: number | null
  colPosition: number | null
  agreesWithHuman: boolean | null
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

export interface SubmitValidationJobRequest {
  modelVersionId: number
  datasetVersionId: number
  imageIds: number[]
  initiatedBy: string
}

export interface AddToProjectRequest {
  versionTag: string
  notes?: string
  createdBy: string
}
