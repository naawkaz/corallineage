import { api } from '../../shared/api/client'
import type { GenerateReportRequest, ReportListItem, ReproducibilityReport } from '../reporting/reportsTypes'
import type { Page } from '../datasets/versionsTypes'

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
