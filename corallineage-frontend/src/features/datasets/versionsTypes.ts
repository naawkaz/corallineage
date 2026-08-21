export type TriggerReason =
  | 'IMAGES_ADDED'
  | 'IMAGES_REMOVED'
  | 'ANNOTATIONS_MODIFIED'
  | 'LABEL_DEFINITION_CHANGED'
  | 'MANUAL_SNAPSHOT'

export interface LabelDefinition {
  labelCode: string
  labelName: string
  labelCategory: string | null
  deprecated: boolean
}

export interface DatasetVersion {
  id: number
  datasetId: number
  versionTag: string
  createdAt: string
  createdBy: string
  triggerReason: TriggerReason
  imageCount: number
  annotationCount: number
  dvcCommitHash: string
  notes: string | null
  labelDefinitions?: LabelDefinition[]
  complete: boolean
  unresolvedDirtyCount: number
  // Only set when this version was created by an import: importStatus mirrors the originating
  // ImportJob's status ('PROCESSING'/'COMPLETED'/'FAILED'), importFormat its format (e.g. 'BLANK').
  // blankImportStarted is true once a PointAnnotationJob already exists for this version.
  importStatus: string | null
  importFormat: string | null
  blankImportStarted: boolean
}

export interface CreateVersionRequest {
  versionTag: string
  triggerReason: TriggerReason
  createdBy: string
  notes?: string | null
}

export interface CreateLabelDefinitionRequest {
  labelCode: string
  labelName: string
  labelCategory?: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
