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
  // 'UNRESOLVED' when this version needs attention before it's usable (a BLANK import not yet
  // resolved into real points, at any stage); null once resolved or not applicable.
  resolveStatus: 'UNRESOLVED' | null
  // Where clicking "Resolve" should navigate: 'START' (open the blank-import start dialog),
  // 'SEED_LABEL' (go straight to the point-annotation job's label/review page), 'ANNOTATION_REVIEW'
  // (go straight to the warm-start job's review page). Null when resolveStatus is null.
  resolveRoute: 'START' | 'SEED_LABEL' | 'ANNOTATION_REVIEW' | null
  // The job id resolveRoute's URL is built from; null for 'START' or when resolveStatus is null.
  resolveJobId: number | null
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
