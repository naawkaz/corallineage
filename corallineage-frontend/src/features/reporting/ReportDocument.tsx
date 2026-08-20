import { Document, Image, Page, StyleSheet, Text, View } from '@react-pdf/renderer'
import { changeTypeLabel, eventTypeLabel, triggerReasonLabel } from '../../shared/constants'
import { formatCount, formatDate } from '../../shared/lib/format'
import type { ReproducibilityReport } from './reportsTypes'

// Chart images are captured client-side (Recharts rendered off-screen, snapshotted with
// html2canvas) since react-pdf has no native chart primitive. classDistribution and
// annotationSourceSplit are single images; epochLineByRunId is keyed per MLflow run since
// each model gets its own loss curve.
export interface ReportChartImages {
  classDistribution: string | null
  annotationSourceSplit: string | null
  epochLineByRunId: Record<string, string | null>
}

interface ReportDocumentProps {
  report: ReproducibilityReport
  chartImages: ReportChartImages
  includeEventLog: boolean
  includeOntologyChanges: boolean
  includeModelRegistry: boolean
  includeDatasetVersionHistory: boolean
}

const styles = StyleSheet.create({
  page: {
    paddingTop: 32,
    paddingBottom: 40,
    paddingHorizontal: 32,
    fontSize: 9,
    fontFamily: 'Helvetica',
    color: '#1a1a1a',
  },
  headerBlock: {
    marginBottom: 20,
    borderBottom: '2 solid #1e3a5f',
    paddingBottom: 10,
  },
  title: {
    fontSize: 16,
    fontFamily: 'Helvetica-Bold',
    color: '#1e3a5f',
    marginBottom: 4,
  },
  headerMeta: {
    fontSize: 9,
    color: '#555555',
  },
  section: {
    marginBottom: 18,
  },
  sectionTitle: {
    fontSize: 12,
    fontFamily: 'Helvetica-Bold',
    color: '#1e3a5f',
    marginBottom: 8,
    borderBottom: '1 solid #d0d0d0',
    paddingBottom: 3,
  },
  subsectionTitle: {
    fontSize: 10,
    fontFamily: 'Helvetica-Bold',
    color: '#1e3a5f',
    marginTop: 10,
    marginBottom: 6,
  },
  statRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 4,
  },
  statTile: {
    flex: 1,
    border: '1 solid #d0d0d0',
    borderRadius: 4,
    padding: 8,
  },
  statLabel: {
    fontSize: 7.5,
    color: '#666666',
    marginBottom: 3,
  },
  statValue: {
    fontSize: 15,
    fontFamily: 'Helvetica-Bold',
    color: '#1a1a1a',
  },
  table: {
    border: '1 solid #d0d0d0',
    borderRadius: 3,
  },
  tableRowHeader: {
    flexDirection: 'row',
    backgroundColor: '#eef2f5',
    borderBottom: '1 solid #d0d0d0',
  },
  tableRow: {
    flexDirection: 'row',
    borderBottom: '1 solid #e5e5e5',
  },
  tableCellHeader: {
    padding: 5,
    fontFamily: 'Helvetica-Bold',
    fontSize: 8,
    color: '#333333',
  },
  tableCell: {
    padding: 5,
    fontSize: 8,
    color: '#1a1a1a',
  },
  chartImage: {
    width: '100%',
    maxHeight: 220,
    objectFit: 'contain',
    marginBottom: 8,
  },
  tag: {
    fontSize: 7.5,
    border: '1 solid #c8c8c8',
    borderRadius: 3,
    paddingVertical: 2,
    paddingHorizontal: 5,
    marginRight: 4,
    marginBottom: 4,
    color: '#333333',
  },
  tagRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginBottom: 8,
  },
  emptyNote: {
    fontSize: 8.5,
    color: '#888888',
    fontStyle: 'italic',
  },
  pageNumber: {
    position: 'absolute',
    bottom: 16,
    right: 32,
    fontSize: 8,
    color: '#888888',
  },
})

function colWidth(pct: number) {
  return { width: `${pct}%` as const }
}

function Table({
  columns,
  rows,
}: {
  columns: { label: string; width: number }[]
  rows: (string | number)[][]
}) {
  if (rows.length === 0) {
    return <Text style={styles.emptyNote}>No data for this section.</Text>
  }
  return (
    <View style={styles.table} wrap>
      <View style={styles.tableRowHeader} fixed>
        {columns.map((col, i) => (
          <Text key={i} style={[styles.tableCellHeader, colWidth(col.width)]}>
            {col.label}
          </Text>
        ))}
      </View>
      {rows.map((row, ri) => (
        <View style={styles.tableRow} key={ri} wrap={false}>
          {row.map((cell, ci) => (
            <Text key={ci} style={[styles.tableCell, colWidth(columns[ci].width)]}>
              {cell === '' || cell == null ? '-' : String(cell)}
            </Text>
          ))}
        </View>
      ))}
    </View>
  )
}

export default function ReportDocument({
  report,
  chartImages,
  includeEventLog,
  includeOntologyChanges,
  includeModelRegistry,
  includeDatasetVersionHistory,
}: ReportDocumentProps) {
  const sourceSplitEntries = report.analytics
    ? Object.entries(report.analytics.annotationSourceSplit)
    : []

  return (
    <Document title={report.reportId} author={report.requestedBy}>
      <Page size="A4" style={styles.page}>
        <View style={styles.headerBlock}>
          <Text style={styles.title}>Reproducibility Report</Text>
          <Text style={styles.headerMeta}>Report ID: {report.reportId}</Text>
          <Text style={styles.headerMeta}>Generated: {formatDate(report.generatedAt)}</Text>
          <Text style={styles.headerMeta}>Requested by: {report.requestedBy}</Text>
          <Text style={styles.headerMeta}>
            Period: {formatDate(report.period.from)} to {formatDate(report.period.to)}
          </Text>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Summary</Text>
          <View style={styles.statRow}>
            <View style={styles.statTile}>
              <Text style={styles.statLabel}>Dataset versions created</Text>
              <Text style={styles.statValue}>{formatCount(report.summary.datasetVersionsCreated)}</Text>
            </View>
            <View style={styles.statTile}>
              <Text style={styles.statLabel}>Models registered</Text>
              <Text style={styles.statValue}>{formatCount(report.summary.modelsRegistered)}</Text>
            </View>
            <View style={styles.statTile}>
              <Text style={styles.statLabel}>Ontology changes</Text>
              <Text style={styles.statValue}>{formatCount(report.summary.ontologyChanges)}</Text>
            </View>
            <View style={styles.statTile}>
              <Text style={styles.statLabel}>Total annotation events</Text>
              <Text style={styles.statValue}>{formatCount(report.summary.totalAnnotationEvents)}</Text>
            </View>
          </View>
        </View>

        {report.analytics && report.analytics.classDistribution.length > 0 && (
          <View style={styles.section} wrap={false}>
            <Text style={styles.sectionTitle}>Class Distribution</Text>
            {chartImages.classDistribution && (
              // eslint-disable-next-line jsx-a11y/alt-text
              <Image src={chartImages.classDistribution} style={styles.chartImage} />
            )}
            <Table
              columns={[
                { label: 'Label', width: 25 },
                { label: 'Name', width: 40 },
                { label: 'Count', width: 15 },
                { label: 'Percentage', width: 20 },
              ]}
              rows={report.analytics.classDistribution.map((c) => [
                c.labelCode,
                c.labelName,
                formatCount(c.count),
                `${c.percentage.toFixed(1)}%`,
              ])}
            />
          </View>
        )}

        {report.analytics && sourceSplitEntries.length > 0 && (
          <View style={styles.section} wrap={false}>
            <Text style={styles.sectionTitle}>Annotation Source Split</Text>
            {chartImages.annotationSourceSplit && (
              // eslint-disable-next-line jsx-a11y/alt-text
              <Image src={chartImages.annotationSourceSplit} style={styles.chartImage} />
            )}
            <Table
              columns={[
                { label: 'Source', width: 60 },
                { label: 'Count', width: 40 },
              ]}
              rows={sourceSplitEntries.map(([source, count]) => [source, formatCount(count)])}
            />
          </View>
        )}

        {report.analytics && report.analytics.perClassMetrics.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Per-Class Metrics</Text>
            <Table
              columns={[
                { label: 'Label', width: 34 },
                { label: 'Accuracy', width: 33 },
                { label: 'Samples', width: 33 },
              ]}
              rows={report.analytics.perClassMetrics.map((m) => [
                m.labelCode,
                m.accuracy != null ? `${(m.accuracy * 100).toFixed(0)}%` : '-',
                m.sampleCount ?? '-',
              ])}
            />
          </View>
        )}

        {report.analytics &&
          report.analytics.mlflowDetails.map((entry) => (
            <View key={entry.mlflowRunId} style={styles.section}>
              <Text style={styles.sectionTitle}>MLflow Diagnostics - {entry.modelTag}</Text>
              <View style={styles.tagRow}>
                {entry.environment.pythonVersion && (
                  <Text style={styles.tag}>Python {entry.environment.pythonVersion}</Text>
                )}
                {entry.environment.pytorchVersion && (
                  <Text style={styles.tag}>PyTorch {entry.environment.pytorchVersion}</Text>
                )}
                {entry.environment.cudaVersion && (
                  <Text style={styles.tag}>{entry.environment.cudaVersion.toUpperCase()}</Text>
                )}
                {entry.environment.randomSeed && (
                  <Text style={styles.tag}>Seed {entry.environment.randomSeed}</Text>
                )}
                {entry.environment.totalDurationSeconds != null && (
                  <Text style={styles.tag}>Duration {entry.environment.totalDurationSeconds}s</Text>
                )}
              </View>

              {entry.epochMetrics.length > 0 && (
                <>
                  <Text style={styles.subsectionTitle}>Epoch Metrics</Text>
                  {chartImages.epochLineByRunId[entry.mlflowRunId] && (
                    // eslint-disable-next-line jsx-a11y/alt-text
                    <Image src={chartImages.epochLineByRunId[entry.mlflowRunId] as string} style={styles.chartImage} />
                  )}
                  <Table
                    columns={[
                      { label: 'Epoch', width: 12 },
                      { label: 'Train loss', width: 22 },
                      { label: 'Val loss', width: 22 },
                      { label: 'Val accuracy', width: 22 },
                      { label: 'Loss (legacy)', width: 22 },
                    ]}
                    rows={entry.epochMetrics.map((p) => [
                      p.epoch,
                      p.trainLoss != null ? p.trainLoss.toFixed(4) : '-',
                      p.valLoss != null ? p.valLoss.toFixed(4) : '-',
                      p.valAccuracy != null ? `${(p.valAccuracy * 100).toFixed(1)}%` : '-',
                      p.loss != null ? p.loss.toFixed(4) : '-',
                    ])}
                  />
                </>
              )}

              {entry.confusionMatrixUrl && (
                <>
                  <Text style={styles.subsectionTitle}>Confusion Matrix</Text>
                  {/* eslint-disable-next-line jsx-a11y/alt-text */}
                  <Image src={entry.confusionMatrixUrl} style={styles.chartImage} />
                </>
              )}
            </View>
          ))}

        {includeDatasetVersionHistory && report.datasetVersionHistory.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Dataset Version History</Text>
            <Table
              columns={[
                { label: 'Version', width: 16 },
                { label: 'Trigger', width: 22 },
                { label: 'Images', width: 14 },
                { label: 'Points', width: 14 },
                { label: 'Created', width: 34 },
              ]}
              rows={report.datasetVersionHistory.map((v) => [
                v.versionTag,
                triggerReasonLabel(v.triggerReason),
                formatCount(v.imageCount),
                formatCount(v.annotationCount),
                formatDate(v.createdAt),
              ])}
            />
          </View>
        )}

        {includeModelRegistry && report.modelRegistry.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Model Registry</Text>
            <Table
              columns={[
                { label: 'Model', width: 20 },
                { label: 'Architecture', width: 22 },
                { label: 'Accuracy', width: 16 },
                { label: 'Status', width: 14 },
                { label: 'Trained', width: 28 },
              ]}
              rows={report.modelRegistry.map((m) => [
                m.modelTag,
                m.architecture,
                m.accuracy != null ? `${(m.accuracy * 100).toFixed(1)}%` : '-',
                m.status,
                formatDate(m.trainedAt),
              ])}
            />
          </View>
        )}

        {includeOntologyChanges && report.ontologyChanges.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Ontology Changes</Text>
            <Table
              columns={[
                { label: 'Type', width: 18 },
                { label: 'Old to New', width: 34 },
                { label: 'Changed by', width: 22 },
                { label: 'Changed at', width: 26 },
              ]}
              rows={report.ontologyChanges.map((c) => [
                changeTypeLabel(c.changeType),
                `${c.oldLabelCode ?? '-'} -> ${c.newLabelCodes?.join(', ') || '-'}`,
                c.changedBy,
                formatDate(c.changedAt),
              ])}
            />
          </View>
        )}

        {includeEventLog && report.eventLog.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Event Log</Text>
            <Table
              columns={[
                { label: 'Event', width: 34 },
                { label: 'Performed by', width: 30 },
                { label: 'Occurred at', width: 36 },
              ]}
              rows={report.eventLog.map((e) => [
                eventTypeLabel(e.eventType),
                e.performedBy,
                formatDate(e.occurredAt),
              ])}
            />
          </View>
        )}

        <Text
          style={styles.pageNumber}
          render={({ pageNumber, totalPages }) => `${pageNumber} / ${totalPages}`}
          fixed
        />
      </Page>
    </Document>
  )
}
