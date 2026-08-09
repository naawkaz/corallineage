export interface Dataset {
  id: number
  datasetName: string
  description: string | null
  createdBy: string
  createdAt: string
  versionCount: number
  latestVersionTag: string | null
  imageCount?: number
  annotationCount?: number
}

export interface CreateDatasetRequest {
  datasetName: string
  description?: string | null
  createdBy: string
}
