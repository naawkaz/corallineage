export type ChangeType = 'LABEL_ADDED' | 'LABEL_DEPRECATED' | 'LABEL_SPLIT' | 'LABEL_RENAMED'

export interface OntologyChange {
  id: number
  changeType: ChangeType
  oldLabelCode: string | null
  newLabelCodes: string[] | null
  affectedDatasetVersionId: number
  changedBy: string
  changedAt: string
  notes: string | null
  dirtyAnnotationsFlagged: number
  resolvedCount: number
}

export interface DirtyAnnotation {
  id: number
  imageId: number
  filename: string
  pointIndex: number
  currentLabelCode: string
  ontologyChangeId: number
  flaggedAt: string
  resolved: boolean
  resolvedAt: string | null
  resolvedBy: string | null
  newLabelCode: string | null
}

export interface RecordOntologyChangeRequest {
  changeType: ChangeType
  oldLabelCode?: string
  newLabelCodes: string[]
  affectedDatasetVersionId: number
  changedBy: string
  notes?: string
}

export interface ResolveDirtyAnnotationRequest {
  newLabelCode: string
  resolvedBy: string
}
