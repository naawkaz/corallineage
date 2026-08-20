import { useEffect, useRef, useState, type ReactNode } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { ArrowDownTrayIcon } from '@heroicons/react/24/outline'
import { exportReportUrl, fetchReports, generateReport } from '../reporting/reportsApi'
import { errorMessage } from '../../shared/api/client'
import { changeTypeBadge, changeTypeLabel, eventTypeBadge, eventTypeLabel } from '../../shared/constants'
import { useAuthContext } from '../../shared/lib/authContextCore'
import { formatCount, formatDate } from '../../shared/lib/format'
import type { ReportListItem, ReproducibilityReport } from '../reporting/reportsTypes'
import type { ReportChartImages } from '../reporting/ReportDocument'

const PIE_COLORS = ['#0d9488', '#1e3a5f', '#d97706', '#e05d44', '#7c3aed']

function defaultDateInput(daysAgo: number): string {
  const d = new Date()
  d.setDate(d.getDate() - daysAgo)
  return d.toISOString().slice(0, 10)
}

export default function ReportGenerator() {
  const { user } = useAuthContext()
  const [fromDate, setFromDate] = useState(defaultDateInput(365))
  const [toDate, setToDate] = useState(defaultDateInput(0))
  const [includeEventLog, setIncludeEventLog] = useState(true)
  const [includeOntologyChanges, setIncludeOntologyChanges] = useState(true)
  const [includeModelRegistry, setIncludeModelRegistry] = useState(true)
  const [includeDatasetVersionHistory, setIncludeDatasetVersionHistory] = useState(true)
  const [includeAnalytics, setIncludeAnalytics] = useState(true)
  const [includeMlflowDetails, setIncludeMlflowDetails] = useState(true)

  const [report, setReport] = useState<ReproducibilityReport | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [exportingPdf, setExportingPdf] = useState(false)

  const [previousReports, setPreviousReports] = useState<ReportListItem[]>([])
  const [previousReportsLoading, setPreviousReportsLoading] = useState(true)
  const [previousReportsError, setPreviousReportsError] = useState<string | null>(null)

  const reportRef = useRef<HTMLDivElement>(null)
  const classDistChartRef = useRef<HTMLDivElement>(null)
  const sourceSplitChartRef = useRef<HTMLDivElement>(null)
  const epochChartRefs = useRef<Record<string, HTMLDivElement | null>>({})

  function loadPreviousReports() {
    fetchReports(0, 10)
      .then((res) => {
        setPreviousReports(res.content)
        setPreviousReportsError(null)
      })
      .catch((err) => setPreviousReportsError(errorMessage(err)))
      .finally(() => setPreviousReportsLoading(false))
  }

  useEffect(() => {
    loadPreviousReports()
  }, [])

  async function handleGenerate() {
    setLoading(true)
    setError(null)
    try {
      const result = await generateReport({
        requestedBy: user?.username ?? '',
        fromDate: new Date(fromDate + 'T00:00:00Z').toISOString(),
        toDate: new Date(toDate + 'T23:59:59Z').toISOString(),
        includeEventLog,
        includeOntologyChanges,
        includeModelRegistry,
        includeDatasetVersionHistory,
        includeAnalytics,
        includeMlflowDetails,
      })
      setReport(result)
      setPreviousReportsLoading(true)
      loadPreviousReports()
    } catch (err) {
      setReport(null)
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  async function captureChart(el: HTMLDivElement | null): Promise<string | null> {
    if (!el) return null
    const html2canvas = (await import('html2canvas')).default
    const canvas = await html2canvas(el, { backgroundColor: '#ffffff', scale: 2 })
    return canvas.toDataURL('image/png')
  }

  async function handleExportPdf() {
    if (!report) return
    setExportingPdf(true)
    try {
      const epochLineByRunId: Record<string, string | null> = {}
      if (report.analytics) {
        for (const entry of report.analytics.mlflowDetails) {
          epochLineByRunId[entry.mlflowRunId] = await captureChart(epochChartRefs.current[entry.mlflowRunId] ?? null)
        }
      }
      const chartImages: ReportChartImages = {
        classDistribution: await captureChart(classDistChartRef.current),
        annotationSourceSplit: await captureChart(sourceSplitChartRef.current),
        epochLineByRunId,
      }

      const [{ pdf }, { default: ReportDocument }] = await Promise.all([
        import('@react-pdf/renderer'),
        import('./ReportDocument'),
      ])

      const blob = await pdf(
        <ReportDocument
          report={report}
          chartImages={chartImages}
          includeEventLog={includeEventLog}
          includeOntologyChanges={includeOntologyChanges}
          includeModelRegistry={includeModelRegistry}
          includeDatasetVersionHistory={includeDatasetVersionHistory}
        />,
      ).toBlob()

      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${report.reportId}.pdf`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(url)
    } finally {
      setExportingPdf(false)
    }
  }

  function handleExportJson() {
    if (!report) return
    window.open(exportReportUrl(report.reportId), '_blank')
  }

  const sourceSplitData = report?.analytics
    ? Object.entries(report.analytics.annotationSourceSplit).map(([source, count]) => ({ source, count }))
    : []

  return (
    <>
      <div className="content">
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 24, alignItems: 'start', marginBottom: 24 }}>
          <div className="card" style={{ padding: 16 }}>
            <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}>
              <div className="form-row" style={{ marginBottom: 0 }}>
                <label className="form-label">From date</label>
                <input
                  type="date"
                  className="form-input"
                  value={fromDate}
                  onChange={(e) => setFromDate(e.target.value)}
                />
              </div>
              <div className="form-row" style={{ marginBottom: 0 }}>
                <label className="form-label">To date</label>
                <input type="date" className="form-input" value={toDate} onChange={(e) => setToDate(e.target.value)} />
              </div>
            </div>

            <div className="form-row">
              <label className="form-label">Sections to include</label>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <input type="checkbox" checked={includeDatasetVersionHistory}
                    onChange={(e) => setIncludeDatasetVersionHistory(e.target.checked)} />
                  Dataset Version History
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <input type="checkbox" checked={includeModelRegistry}
                    onChange={(e) => setIncludeModelRegistry(e.target.checked)} />
                  Model Registry
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <input type="checkbox" checked={includeOntologyChanges}
                    onChange={(e) => setIncludeOntologyChanges(e.target.checked)} />
                  Ontology Changes
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <input type="checkbox" checked={includeEventLog}
                    onChange={(e) => setIncludeEventLog(e.target.checked)} />
                  Event Log
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <input type="checkbox" checked={includeAnalytics}
                    onChange={(e) => setIncludeAnalytics(e.target.checked)} />
                  Analytics Charts
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <input type="checkbox" checked={includeMlflowDetails}
                    onChange={(e) => setIncludeMlflowDetails(e.target.checked)} />
                  MLflow Details
                </label>
              </div>
            </div>

            <button className="btn btn-primary" onClick={handleGenerate} disabled={loading}>
              {loading ? 'Generating…' : 'Generate Report'}
            </button>

            {error && (
              <div className="alert alert-error" style={{ marginTop: 16 }}>
                {error}
              </div>
            )}
          </div>

          <div>
            <div className="section-header">
              <div className="section-title">Previous reports</div>
            </div>
            <div className="card" style={{ marginBottom: 16 }}>
              {previousReportsError && (
                <div className="alert alert-error" style={{ margin: 16 }}>
                  {previousReportsError}
                </div>
              )}
              {previousReportsLoading ? (
                <div className="loading-state">
                  <span className="spinner" /> Loading reports…
                </div>
              ) : previousReports.length === 0 ? (
                <div className="empty-state">
                  <p>No reports generated yet.</p>
                </div>
              ) : (
                <table>
                  <thead>
                    <tr>
                      <th>Report</th>
                      <th>Period</th>
                      <th>Requested by</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {previousReports.map((r) => (
                      <tr key={r.reportId}>
                        <td>
                          <span className="mono" style={{ fontSize: 12 }}>{r.reportId}</span>
                        </td>
                        <td style={{ color: 'var(--text-secondary)', fontSize: 12 }}>
                          {formatDate(r.fromDate)} – {formatDate(r.toDate)}
                        </td>
                        <td>{r.requestedBy}</td>
                        <td>
                          <button
                            className="btn btn-secondary btn-sm"
                            aria-label="Redownload report"
                            title="Redownload report"
                            onClick={() => window.open(exportReportUrl(r.reportId), '_blank')}
                          >
                            <ArrowDownTrayIcon style={{ width: 13, height: 13 }} />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>

        {report && (
          <>
            <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
              <button className="btn btn-secondary" onClick={handleExportPdf} disabled={exportingPdf}>
                {exportingPdf ? 'Exporting…' : 'Export PDF'}
              </button>
              <button className="btn btn-secondary" onClick={handleExportJson}>
                Export JSON
              </button>
            </div>

            <div ref={reportRef}>
              <div className="section-header">
                <div className="section-title">
                  <span className="mono">{report.reportId}</span> - generated {formatDate(report.generatedAt)} by{' '}
                  {report.requestedBy}
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 24 }}>
                <StatCard label="Dataset versions created" value={report.summary.datasetVersionsCreated} />
                <StatCard label="Models registered" value={report.summary.modelsRegistered} />
                <StatCard label="Ontology changes" value={report.summary.ontologyChanges} />
                <StatCard label="Total annotation events" value={report.summary.totalAnnotationEvents} />
              </div>

              {report.analytics && (
                <>
                  {report.analytics.classDistribution.length > 0 && (
                    <div style={{ marginBottom: 24 }}>
                      <div className="section-header">
                        <div className="section-title">Class Distribution</div>
                      </div>
                      <div className="card" style={{ padding: 16, height: 280 }} ref={classDistChartRef}>
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart data={report.analytics.classDistribution}>
                            <CartesianGrid strokeDasharray="3 3" />
                            <XAxis dataKey="labelCode" />
                            <YAxis />
                            <Tooltip />
                            <Bar dataKey="count" name="Count" fill="var(--navy, #1e3a5f)" />
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  )}

                  {sourceSplitData.length > 0 && (
                    <div style={{ marginBottom: 24 }}>
                      <div className="section-header">
                        <div className="section-title">Annotation Source Split</div>
                      </div>
                      <div className="card" style={{ padding: 16, height: 280 }} ref={sourceSplitChartRef}>
                        <ResponsiveContainer width="100%" height="100%">
                          <PieChart>
                            <Pie
                              data={sourceSplitData}
                              dataKey="count"
                              nameKey="source"
                              innerRadius={60}
                              outerRadius={90}
                              paddingAngle={2}
                            >
                              {sourceSplitData.map((entry, idx) => (
                                <Cell key={entry.source} fill={PIE_COLORS[idx % PIE_COLORS.length]} />
                              ))}
                            </Pie>
                            <Legend />
                            <Tooltip />
                          </PieChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  )}

                  {report.analytics.perClassMetrics.length > 0 && (
                    <div style={{ marginBottom: 24 }}>
                      <div className="section-header">
                        <div className="section-title">Per-Class Metrics</div>
                      </div>
                      <div className="card">
                        <table>
                          <thead>
                            <tr>
                              <th>Label</th>
                              <th className="num">Accuracy</th>
                              <th className="num">Samples</th>
                            </tr>
                          </thead>
                          <tbody>
                            {report.analytics.perClassMetrics.map((m) => (
                              <tr key={m.labelCode}>
                                <td>
                                  <span className="mono">{m.labelCode}</span>
                                </td>
                                <td className="num">
                                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 8 }}>
                                    <div
                                      style={{
                                        width: 80,
                                        height: 6,
                                        borderRadius: 3,
                                        background: 'var(--bg-secondary)',
                                        overflow: 'hidden',
                                      }}
                                    >
                                      <div
                                        style={{
                                          width: `${((m.accuracy ?? 0) * 100).toFixed(0)}%`,
                                          height: '100%',
                                          background: 'var(--accent)',
                                        }}
                                      />
                                    </div>
                                    <span>{m.accuracy != null ? `${(m.accuracy * 100).toFixed(0)}%` : '-'}</span>
                                  </div>
                                </td>
                                <td className="num">{m.sampleCount ?? '-'}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  )}

                  {report.analytics.mlflowDetails.map((entry) => (
                    <div key={entry.mlflowRunId} style={{ marginBottom: 24 }}>
                      <div className="section-header">
                        <div className="section-title">
                          MLflow Diagnostics - <span className="mono">{entry.modelTag}</span>
                        </div>
                      </div>
                      <div className="card" style={{ padding: 16 }}>
                        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
                          {entry.environment.pythonVersion && (
                            <span className="tag">Python {entry.environment.pythonVersion}</span>
                          )}
                          {entry.environment.pytorchVersion && (
                            <span className="tag">PyTorch {entry.environment.pytorchVersion}</span>
                          )}
                          {entry.environment.cudaVersion && (
                            <span className="tag">{entry.environment.cudaVersion.toUpperCase()}</span>
                          )}
                          {entry.environment.randomSeed && <span className="tag">Seed {entry.environment.randomSeed}</span>}
                        </div>
                        {entry.epochMetrics.length > 0 && (
                          <div
                            style={{ height: 220, marginBottom: 16 }}
                            ref={(el) => {
                              epochChartRefs.current[entry.mlflowRunId] = el
                            }}
                          >
                            <ResponsiveContainer width="100%" height="100%">
                              <LineChart data={entry.epochMetrics}>
                                <CartesianGrid strokeDasharray="3 3" />
                                <XAxis dataKey="epoch" />
                                <YAxis />
                                <Tooltip />
                                <Legend />
                                {entry.epochMetrics.some((p) => p.trainLoss != null || p.valLoss != null) ? (
                                  <>
                                    <Line type="monotone" dataKey="trainLoss" stroke="var(--navy, #1e3a5f)" name="Train loss" />
                                    <Line type="monotone" dataKey="valLoss" stroke="var(--warning, #d97706)" name="Validation loss" />
                                  </>
                                ) : (
                                  <Line type="monotone" dataKey="loss" stroke="var(--navy, #1e3a5f)" name="Loss" />
                                )}
                              </LineChart>
                            </ResponsiveContainer>
                          </div>
                        )}
                        {entry.confusionMatrixUrl && (
                          <img
                            src={entry.confusionMatrixUrl}
                            alt={`Confusion matrix for ${entry.modelTag}`}
                            style={{ maxWidth: '100%', borderRadius: 6 }}
                          />
                        )}
                      </div>
                    </div>
                  ))}
                </>
              )}

              {includeDatasetVersionHistory && report.datasetVersionHistory.length > 0 && (
                <ReportSection title="Dataset Version History">
                  <table>
                    <thead>
                      <tr>
                        <th>Version</th>
                        <th>Trigger</th>
                        <th className="num">Images</th>
                        <th className="num">Points</th>
                        <th>Created</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.datasetVersionHistory.map((v) => (
                        <tr key={v.id}>
                          <td>
                            <span className="mono">{v.versionTag}</span>
                          </td>
                          <td>{v.triggerReason}</td>
                          <td className="num">{formatCount(v.imageCount)}</td>
                          <td className="num">{formatCount(v.annotationCount)}</td>
                          <td style={{ color: 'var(--text-secondary)' }}>{formatDate(v.createdAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </ReportSection>
              )}

              {includeModelRegistry && report.modelRegistry.length > 0 && (
                <ReportSection title="Model Registry">
                  <table>
                    <thead>
                      <tr>
                        <th>Model</th>
                        <th>Architecture</th>
                        <th className="num">Accuracy</th>
                        <th>Status</th>
                        <th>Trained</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.modelRegistry.map((m) => (
                        <tr key={m.id}>
                          <td>
                            <span className="mono">{m.modelTag}</span>
                          </td>
                          <td>{m.architecture}</td>
                          <td className="num">{m.accuracy != null ? `${(m.accuracy * 100).toFixed(1)}%` : '-'}</td>
                          <td>{m.status}</td>
                          <td style={{ color: 'var(--text-secondary)' }}>{formatDate(m.trainedAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </ReportSection>
              )}

              {includeOntologyChanges && report.ontologyChanges.length > 0 && (
                <ReportSection title="Ontology Changes">
                  <table>
                    <thead>
                      <tr>
                        <th>Type</th>
                        <th>Old → New</th>
                        <th>Changed by</th>
                        <th>Changed at</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.ontologyChanges.map((c) => (
                        <tr key={c.id}>
                          <td>
                            <span className={`badge ${changeTypeBadge(c.changeType)}`}>{changeTypeLabel(c.changeType)}</span>
                          </td>
                          <td>
                            <span className="mono" style={{ fontSize: 12 }}>
                              {c.oldLabelCode ?? '-'} → {c.newLabelCodes?.join(', ') || '-'}
                            </span>
                          </td>
                          <td>{c.changedBy}</td>
                          <td style={{ color: 'var(--text-secondary)' }}>{formatDate(c.changedAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </ReportSection>
              )}

              {includeEventLog && report.eventLog.length > 0 && (
                <ReportSection title="Event Log">
                  <table>
                    <thead>
                      <tr>
                        <th>Event</th>
                        <th>Performed by</th>
                        <th>Occurred at</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.eventLog.map((e) => (
                        <tr key={e.id}>
                          <td>
                            <span className={`badge ${eventTypeBadge(e.eventType)}`}>{eventTypeLabel(e.eventType)}</span>
                          </td>
                          <td>{e.performedBy}</td>
                          <td style={{ color: 'var(--text-secondary)' }}>{formatDate(e.occurredAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </ReportSection>
              )}
            </div>
          </>
        )}
      </div>
    </>
  )
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="card" style={{ padding: 16 }}>
      <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginBottom: 4 }}>{label}</div>
      <div className="mono" style={{ fontSize: 22, fontWeight: 600 }}>
        {formatCount(value)}
      </div>
    </div>
  )
}

function ReportSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div style={{ marginBottom: 24 }}>
      <div className="section-header">
        <div className="section-title">{title}</div>
      </div>
      <div className="card">{children}</div>
    </div>
  )
}
