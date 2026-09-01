import { api } from '../../shared/api/client'
import type { GenerateReportRequest, ReportListItem, ReproducibilityReport } from '../reporting/reportsTypes'
import type { Page } from '../datasets/versionsTypes'

export async function generateReport(request: GenerateReportRequest): Promise<ReproducibilityReport> {
  const { data } = await api.post<ReproducibilityReport>('/reports/generate', request)
  return data
}

// This endpoint requires a JWT, so it must go through the authenticated `api` client. A plain
// browser navigation (window.open / window.location) carries no Authorization header and 401s.
export async function exportReport(reportId: string): Promise<ReproducibilityReport> {
  const { data } = await api.get<ReproducibilityReport>(`/reports/${reportId}/export`)
  return data
}

export async function fetchReports(page: number, size: number): Promise<Page<ReportListItem>> {
  const { data } = await api.get<Page<ReportListItem>>('/reports', { params: { page, size } })
  return data
}
