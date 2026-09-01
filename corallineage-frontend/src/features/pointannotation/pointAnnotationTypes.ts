export type PointAnnotationJobStatus =
  | 'GENERATING'
  | 'SEED_LABELING'
  | 'TRAINING'
  | 'COMPLETE'
  | 'FAILED'

export type GeneratedPointStatus = 'UNLABELED' | 'HUMAN_LABELED'

export interface PointAnnotationJob {
  jobId: number
  datasetVersionId: number
  versionTag: string
  gridRows: number
  gridCols: number
  confidenceThreshold: number
  seedImageCount: number
  status: PointAnnotationJobStatus
  totalImages: number
  seedTotalPoints: number
  seedLabeledPoints: number
  trainingJobId: number | null
  // Set once auto-annotate is triggered
  annotationJobId: number | null
  modelVersionId: number | null
  modelTag: string | null
  initiatedBy: string
  createdAt: string
  errorMessage: string | null
  autoAnnotationProcessedCount: number | null
  autoAnnotationTotalCount: number | null
  autoAnnotationCurrentPointId: number | null
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
  gridRows?: number
  gridCols?: number
  confidenceThreshold?: number
  seedImageCount?: number
  initiatedBy: string
}

export interface LabelPointRequest {
  labelCode: string
  annotatedBy: string
}

export interface TrainPointAnnotationJobRequest {
  architecture?: string
  learningRate?: number
  batchSize?: number
  epochs?: number
}

// One row of the unlabeled import queue
// commitInPlace AnnotationJob rows
// sorted and with resolveRoute/resolveJobId resolved server-side
export interface UnlabeledImportQueueEntry {
  jobType: 'POINT_ANNOTATION' | 'ANNOTATION' | 'IMPORT' | 'VERSION_START'
  jobId: number
  // null for an IMPORT entry
  // no version exists yet while it's still uploading/parsing
  versionNumber: number | null
  versionTag: string | null
  status: string
  seedLabeledPoints: number | null
  seedTotalPoints: number | null
  modelTag: string | null
  startedAt: string
  // Null for a still-processing IMPORT entry
  resolveRoute: 'SEED_LABEL' | 'ANNOTATION_REVIEW' | 'START' | null
  resolveJobId: number | null
  // Only set for a VERSION_START entry
  imageCount: number | null
}
