import { api } from '../../shared/api/client'
import type { GenerateReportRequest, ReportListItem, ReproducibilityReport } from '../reporting/reportsTypes'
import type { Page } from '../datasets/versionsTypes'

export async function generateReport(request: GenerateReportRequest): Promise<ReproducibilityReport> {
  const { data } = await api.post<ReproducibilityReport>('/reports/generate', request)
  return data
}

// Every /api/v1/** endpoint except auth/register, auth/login, and the raw image-file endpoint
// requires a JWT (see SecurityConfig), so this must go through the authenticated `api` client
// rather than a bare URL opened with window.open()/window.location - a plain browser navigation
// carries no Authorization header (the token lives in localStorage, attached only by the axios
// interceptor below), so it would 401 instead of downloading anything.
export async function exportReport(reportId: string): Promise<ReproducibilityReport> {
  const { data } = await api.get<ReproducibilityReport>(`/reports/${reportId}/export`)
  return data
}

export async function fetchReports(page: number, size: number): Promise<Page<ReportListItem>> {
  const { data } = await api.get<Page<ReportListItem>>('/reports', { params: { page, size } })
  return data
}
