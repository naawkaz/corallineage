export type ModelStatus = 'ACTIVE' | 'ARCHIVED' | 'DEPRECATED'

export interface DatasetVersionSummary {
  id: number
  versionTag: string
  datasetName: string | null
}

export interface PerClassMetric {
  labelCode: string
  accuracy?: number
  precision?: number
  recall?: number
  f1?: number
  sampleCount?: number
}

export interface ModelVersion {
  id: number
  modelTag: string
  trainedAt: string
  trainedBy: string
  architecture: string
  hyperparameters: Record<string, unknown> | null
  classMappings: Record<string, string> | null
  accuracy: number | null
  performanceMetrics: { perClass?: PerClassMetric[] } | null
  mlflowRunId: string | null
  weightsPath: string | null
  status: ModelStatus
  trainedOnDatasetVersion: DatasetVersionSummary | null
}

export interface MlflowEnvironment {
  pythonVersion: string | null
  pytorchVersion: string | null
  cudaVersion: string | null
  randomSeed: string | null
  totalDurationSeconds: number | null
}

export interface MlflowEpochMetricPoint {
  epoch: number
  trainLoss: number | null
  valLoss: number | null
  valAccuracy: number | null
  // fallback when trainLoss/valLoss are both null
  loss: number | null
  accuracy: number | null
}

export interface MlflowArtifactUrls {
  confusionMatrixPng: string | null
  weightsFile: string | null
}

export interface MlflowDetails {
  modelId: number
  mlflowRunId: string
  environment: MlflowEnvironment
  epochMetrics: MlflowEpochMetricPoint[]
  artifactUrls: MlflowArtifactUrls
  mlflowUiUrl: string
}
