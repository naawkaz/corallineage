export type EventType =
  | 'DATASET_VERSION_CREATED'
  | 'DATASET_IMPORT_COMPLETED'
  | 'IMAGE_ADDED'
  | 'IMAGE_REMOVED'
  | 'ANNOTATIONS_MODIFIED'
  | 'TRAINING_JOB_STARTED'
  | 'MODEL_TRAINED'
  | 'MODEL_STATUS_CHANGED'
  | 'ANNOTATION_JOB_STARTED'
  | 'MODEL_APPLIED'
  | 'HUMAN_CORRECTION'
  | 'ONTOLOGY_CHANGE_DETECTED'
  | 'DIRTY_FLAGS_RAISED'
  | 'DIRTY_FLAGS_RESOLVED'
  | 'REPORT_GENERATED'

export interface AnnotationEvent {
  id: number
  eventType: EventType
  datasetVersionId: number | null
  modelVersionId: number | null
  imageId: number | null
  performedBy: string
  occurredAt: string
  description: string | null
  metadata: string | null
}

export interface EventFilters {
  eventType?: EventType
  datasetVersionId?: number
  modelVersionId?: number
  datasetId?: number
  from?: string
  to?: string
}
