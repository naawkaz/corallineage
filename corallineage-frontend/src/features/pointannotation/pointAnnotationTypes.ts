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
  // Set once auto-annotate is triggered: the id of the real AnnotationJob (see the annotation
  // feature's AnnotationReview.tsx) this job's review delegates into.
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

// One row of the Unlabeled import queue - merges cold-start PointAnnotationJob rows with
// not-yet-applied, commitInPlace AnnotationJob rows (warm-start or delegated-from-cold-start),
// already sorted and with resolveRoute/resolveJobId fully resolved server-side. See
// PointAnnotationJobService.getUnlabeledImportQueue.
export interface UnlabeledImportQueueEntry {
  jobType: 'POINT_ANNOTATION' | 'ANNOTATION'
  jobId: number
  versionTag: string | null
  status: string
  seedLabeledPoints: number | null
  seedTotalPoints: number | null
  modelTag: string | null
  startedAt: string
  resolveRoute: 'SEED_LABEL' | 'ANNOTATION_REVIEW'
  resolveJobId: number
}
