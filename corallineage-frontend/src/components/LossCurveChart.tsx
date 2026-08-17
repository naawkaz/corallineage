import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { MlflowEpochMetricPoint } from '../types/model'

interface LossCurveChartProps {
  epochMetrics: MlflowEpochMetricPoint[]
}

// Renders a training loss curve from a series of epoch metric points. Shared between the
// completed-model diagnostics view (fed a full, static series) and the live in-progress
// training view (fed a series that grows point by point as polling picks up new epochs).
export default function LossCurveChart({ epochMetrics }: LossCurveChartProps) {
  const chartData = epochMetrics.map((p) => ({ ...p, epochLabel: `Epoch ${p.epoch + 1}` }))
  // Models trained before the train/val split feature only have the old single loss
  // series (trainLoss/valLoss are null for every point), fall back to that series
  // instead of rendering a blank chart.
  const hasValidationSplit = chartData.some((p) => p.trainLoss != null || p.valLoss != null)

  if (chartData.length === 0) return null

  return (
    <>
      <div style={{ height: 260, marginBottom: 16 }}>
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="epochLabel" />
            <YAxis />
            <Tooltip />
            <Legend />
            {hasValidationSplit ? (
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
      {!hasValidationSplit && (
        <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: -8, marginBottom: 16 }}>
          This model was trained before the validation split feature, showing training loss only.
        </div>
      )}
    </>
  )
}
