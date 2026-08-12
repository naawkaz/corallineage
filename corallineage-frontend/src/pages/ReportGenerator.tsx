import { useRef, useState, type ReactNode } from 'react'
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
import { exportReportUrl, generateReport } from '../api/reports'
import { errorMessage } from '../api/client'
import { CURRENT_USER, changeTypeBadge, changeTypeLabel, eventTypeBadge, eventTypeLabel } from '../constants'
import { formatCount, formatDate } from '../lib/format'
import type { ReproducibilityReport } from '../types/report'

const PIE_COLORS = ['#0d9488', '#1e3a5f', '#d97706', '#e05d44', '#7c3aed']

function defaultDateInput(daysAgo: number): string {
  const d = new Date()
  d.setDate(d.getDate() - daysAgo)
  return d.toISOString().slice(0, 10)
}

export default function ReportGenerator() {
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

  const reportRef = useRef<HTMLDivElement>(null)

  async function handleGenerate() {
    setLoading(true)
    setError(null)
    try {
      const result = await generateReport({
        requestedBy: CURRENT_USER,
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
    } catch (err) {
      setReport(null)
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  async function handleExportPdf() {
    if (!reportRef.current || !report) return
    setExportingPdf(true)
    try {
      const html2pdf = (await import('html2pdf.js')).default
      await html2pdf().from(reportRef.current).save(`${report.reportId}.pdf`)
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
      <div className="topbar">
        <div>
          <div className="topbar-title">Report Generator</div>
          <div className="topbar-sub">Aggregate provenance and analytics into a reproducibility report</div>
        </div>
      </div>

      <div className="content">
        <div className="card" style={{ padding: 16, marginBottom: 24 }}>
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

          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <input type="checkbox" checked={includeDatasetVersionHistory}
                onChange={(e) => setIncludeDatasetVersionHistory(e.target.checked)} />
              Dataset Version History
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <input type="checkbox" checked={includeModelRegistry}
                onChange={(e) => setIncludeModelRegistry(e.target.checked)} />
              Model Registry
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <input type="checkbox" checked={includeOntologyChanges}
                onChange={(e) => setIncludeOntologyChanges(e.target.checked)} />
              Ontology Changes
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <input type="checkbox" checked={includeEventLog}
                onChange={(e) => setIncludeEventLog(e.target.checked)} />
              Event Log
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <input type="checkbox" checked={includeAnalytics}
                onChange={(e) => setIncludeAnalytics(e.target.checked)} />
              Include Analytics Charts
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <input type="checkbox" checked={includeMlflowDetails}
                onChange={(e) => setIncludeMlflowDetails(e.target.checked)} />
              Include MLflow Details
            </label>
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
                  <span className="mono">{report.reportId}</span> — generated {formatDate(report.generatedAt)} by{' '}
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
                      <div className="card" style={{ padding: 16, height: 280 }}>
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
                      <div className="card" style={{ padding: 16, height: 280 }}>
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
                              <th>Accuracy</th>
                              <th>Samples</th>
                            </tr>
                          </thead>
                          <tbody>
                            {report.analytics.perClassMetrics.map((m) => (
                              <tr key={m.labelCode}>
                                <td>
                                  <span className="mono">{m.labelCode}</span>
                                </td>
                                <td>
                                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
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
                                    <span>{m.accuracy != null ? `${(m.accuracy * 100).toFixed(0)}%` : '—'}</span>
                                  </div>
                                </td>
                                <td>{m.sampleCount ?? '—'}</td>
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
                          MLflow Diagnostics — <span className="mono">{entry.modelTag}</span>
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
                          <div style={{ height: 220, marginBottom: 16 }}>
                            <ResponsiveContainer width="100%" height="100%">
                              <LineChart data={entry.epochMetrics}>
                                <CartesianGrid strokeDasharray="3 3" />
                                <XAxis dataKey="epoch" />
                                <YAxis />
                                <Tooltip />
                                <Line type="monotone" dataKey="loss" stroke="var(--navy, #1e3a5f)" name="Loss" />
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
                        <th>Images</th>
                        <th>Points</th>
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
                          <td>{formatCount(v.imageCount)}</td>
                          <td>{formatCount(v.annotationCount)}</td>
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
                        <th>Accuracy</th>
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
                          <td>{m.accuracy != null ? `${(m.accuracy * 100).toFixed(1)}%` : '—'}</td>
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
                              {c.oldLabelCode ?? '—'} → {c.newLabelCodes?.join(', ') || '—'}
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
