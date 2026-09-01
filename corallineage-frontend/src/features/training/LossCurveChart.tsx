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
import type { MlflowEpochMetricPoint } from '../training/modelsTypes'

interface LossCurveChartProps {
  epochMetrics: MlflowEpochMetricPoint[]
}

// Renders a training loss curve from a series of epoch metric points
export default function LossCurveChart({ epochMetrics }: LossCurveChartProps) {
  const chartData = epochMetrics.map((p) => ({ ...p, epochLabel: `Epoch ${p.epoch + 1}` }))
  // Some models only carry a single combined loss series (trainLoss/valLoss null) 
  // falls back to it rather than rendering a blank chart
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

    </>
  )
}
