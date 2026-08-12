export type ImportFormat = 'MLC' | 'CORALNET_CSV' | 'COCO_JSON'

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
