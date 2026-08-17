export interface DatasetImage {
  id: number
  filename: string
  url: string
  dvcFileHash: string | null
  site: string | null
  surveyDate: string | null
  addedAt: string
}

export interface AnnotationPointSummary {
  id: number
  imageId: number
  pointIndex: number
  rowPosition: number
  colPosition: number
  labelCode: string
}
