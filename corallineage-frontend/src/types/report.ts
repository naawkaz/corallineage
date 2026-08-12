import type { AnnotationEvent } from './event'
import type { OntologyChange } from './ontology'
import type { ModelVersion, MlflowEpochMetricPoint, MlflowEnvironment } from './model'
import type { DatasetVersion } from './version'

export interface GenerateReportRequest {
  requestedBy: string
  fromDate: string
  toDate: string
  includeEventLog: boolean
  includeOntologyChanges: boolean
  includeModelRegistry: boolean
  includeDatasetVersionHistory: boolean
  includeAnalytics: boolean
  includeMlflowDetails: boolean
}

export interface ReportPeriod {
  from: string
  to: string
}

export interface ReportSummary {
  datasetVersionsCreated: number
  modelsRegistered: number
  ontologyChanges: number
  totalAnnotationEvents: number
}

export interface ClassDistributionItem {
  labelCode: string
  labelName: string
  count: number
  percentage: number
}

export interface PerClassMetric {
  labelCode: string
  accuracy: number | null
  sampleCount: number | null
}

export interface MlflowReportEntry {
  modelTag: string
  mlflowRunId: string
  environment: MlflowEnvironment
  epochMetrics: MlflowEpochMetricPoint[]
  confusionMatrixUrl: string | null
}

export interface ReportAnalytics {
  classDistribution: ClassDistributionItem[]
  annotationSourceSplit: Record<string, number>
  perClassMetrics: PerClassMetric[]
  mlflowDetails: MlflowReportEntry[]
}

export interface ReproducibilityReport {
  reportId: string
  generatedAt: string
  requestedBy: string
  period: ReportPeriod
  summary: ReportSummary
  datasetVersionHistory: DatasetVersion[]
  modelRegistry: ModelVersion[]
  ontologyChanges: OntologyChange[]
  eventLog: AnnotationEvent[]
  analytics: ReportAnalytics | null
}
