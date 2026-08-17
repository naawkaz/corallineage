export type ImportFormat = 'MLC' | 'CORALNET_CSV' | 'COCO_JSON' | 'BLANK'

// One label declared upfront for a Blank / Unlabeled Dataset import: code and name are both
// required (mirrors the backend's NOT NULL columns), category is optional. Populated either by
// the structured manual add form or by reusing a version's full label set wholesale.
export interface LabelDeclaration {
  labelCode: string
  labelName: string
  labelCategory?: string
}

export type ImportJobStatus = 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface ImportJob {
  importJobId: string
  datasetId: number
  format: ImportFormat
  versionTag: string
  status: ImportJobStatus
  submittedAt: string
  completedAt: string | null
  imagesImported: number | null
  annotationPointsImported: number | null
  labelsResolved: number | null
  labelsUnresolved: number | null
  createdDatasetVersionId: number | null
  createdVersionTag: string | null
  errorMessage: string | null
}
