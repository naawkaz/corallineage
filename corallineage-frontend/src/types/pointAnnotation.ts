export type PointAnnotationJobStatus =
  | 'GENERATING'
  | 'SEED_LABELING'
  | 'TRAINING'
  | 'AUTO_ANNOTATING'
  | 'REVIEW'
  | 'COMPLETE'
  | 'FAILED'

export type GeneratedPointStatus = 'UNLABELED' | 'HUMAN_LABELED' | 'MODEL_PREDICTED' | 'CONFIRMED'

export interface PointAnnotationJob {
  jobId: number
  datasetVersionId: number
  versionTag: string
  pointsPerImage: number
  confidenceThreshold: number
  seedImageCount: number
  status: PointAnnotationJobStatus
  totalImages: number
  seedTotalPoints: number
  seedLabeledPoints: number
  trainingJobId: number | null
  modelVersionId: number | null
  modelTag: string | null
  initiatedBy: string
  createdAt: string
  errorMessage: string | null
}

export interface GeneratedPoint {
  id: number
  imageId: number
  filename: string
  pointIndex: number
  rowPosition: number
  colPosition: number
  seed: boolean
  status: GeneratedPointStatus
  predictedLabelCode: string | null
  confidenceScore: number | null
  finalLabelCode: string | null
}

export interface StartPointAnnotationJobRequest {
  datasetVersionId: number
  pointsPerImage?: number
  confidenceThreshold?: number
  seedImageCount?: number
  initiatedBy: string
}

export interface LabelPointRequest {
  labelCode: string
  annotatedBy: string
}

export interface ReviewGeneratedPointRequest {
  accepted: boolean
  correctedLabelCode?: string
  reviewedBy: string
}
