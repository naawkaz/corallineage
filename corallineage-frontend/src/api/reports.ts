import { api } from './client'
import type { GenerateReportRequest, ReportListItem, ReproducibilityReport } from '../types/report'
import type { Page } from '../types/version'

export async function generateReport(request: GenerateReportRequest): Promise<ReproducibilityReport> {
  const { data } = await api.post<ReproducibilityReport>('/reports/generate', request)
  return data
}

export function exportReportUrl(reportId: string): string {
  return `${api.defaults.baseURL}/reports/${reportId}/export`
}

export async function fetchReports(page: number, size: number): Promise<Page<ReportListItem>> {
  const { data } = await api.get<Page<ReportListItem>>('/reports', { params: { page, size } })
  return data
}
