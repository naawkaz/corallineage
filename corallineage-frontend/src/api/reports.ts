import { api } from './client'
import type { GenerateReportRequest, ReproducibilityReport } from '../types/report'

export async function generateReport(request: GenerateReportRequest): Promise<ReproducibilityReport> {
  const { data } = await api.post<ReproducibilityReport>('/reports/generate', request)
  return data
}

export function exportReportUrl(reportId: string): string {
  return `${api.defaults.baseURL}/reports/${reportId}/export`
}
