export interface VersionRef {
  id: number
  versionTag: string
}

export interface DiffSummary {
  imagesAdded: number
  imagesRemoved: number
  annotationsModified: number
  labelsAdded: number
  labelsDeprecated: number
}

export type LabelChangeType = 'ADDED' | 'DEPRECATED'

export interface LabelChangeEntry {
  changeType: LabelChangeType
  labelCode: string
  labelName: string
}

export interface ClassDistributionEntry {
  labelCode: string
  labelName: string
  countInV1: number
  countInV2: number
  delta: number
}

export interface ImageRef {
  imageId: number
  filename: string
}

export interface DatasetDiff {
  fromVersion: VersionRef
  toVersion: VersionRef
  summary: DiffSummary
  labelChanges: LabelChangeEntry[]
  classDistributionDiff: ClassDistributionEntry[]
  imagesAdded: ImageRef[]
  imagesRemoved: ImageRef[]
}
