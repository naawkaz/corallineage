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
  // 1-based permanent ordinal position among all versions of this source (oldest = 1)
  // distinct from the free-text versionTag
  versionNumber: number
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

  // Only set when this version was created by an import
  // importStatus mirrors the originating ImportJob's status
  // importFormat its format blankImportStarted is true once a PointAnnotationJob already 
  // exists for this version
  importStatus: string | null
  importFormat: string | null
  blankImportStarted: boolean

  // 'UNRESOLVED' when this version needs attention before it's usable
  // BLANK import not yet resolved into real points, at any stage
  // null once resolved or not applicable
  resolveStatus: 'UNRESOLVED' | null

  // 'START' - open the blank-import start dialog),
  // 'SEED_LABEL' - go straight to the point-annotation job's label/review page
  // 'ANNOTATION_REVIEW' - go straight to the warm-start job's review page)
  // Null when resolveStatus is null
  resolveRoute: 'START' | 'SEED_LABEL' | 'ANNOTATION_REVIEW' | null

  // null for 'START' or when resolveStatus is null
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
