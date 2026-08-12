export interface DatasetImage {
  id: number
  filename: string
  url: string
  dvcFileHash: string | null
  site: string | null
  surveyDate: string | null
  addedAt: string
}
