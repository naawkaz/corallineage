import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  ArrowRight,
  Box,
  Cpu,
  Crosshair,
  GitBranch,
  GitCompare,
  Layers,
  Tag,
  Upload,
} from 'lucide-react'
import { fetchDataset } from '../api/datasets'
import { fetchVersions } from '../api/versions'
import { errorMessage } from '../api/client'
import { formatCount, formatDate } from '../lib/format'
import { triggerReasonBadge, triggerReasonLabel } from '../constants'
import type { Dataset } from '../types/dataset'
import type { DatasetVersion } from '../types/version'

interface QuickLink {
  to: string
  icon: typeof Box
  label: string
  description: string
}

export default function DatasetDashboard() {
  const navigate = useNavigate()
  const { projectId: projectIdParam } = useParams()
  const projectId = Number(projectIdParam)

  const [dataset, setDataset] = useState<Dataset | null>(null)
  const [latestVersion, setLatestVersion] = useState<DatasetVersion | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!projectId) return
    let cancelled = false
    Promise.resolve()
      .then(() => {
        if (!cancelled) setLoading(true)
      })
    Promise.all([fetchDataset(projectId), fetchVersions(projectId, 0, 1)])
      .then(([datasetRes, versionsRes]) => {
        if (cancelled) return
        setDataset(datasetRes)
        setLatestVersion(versionsRes.content[0] ?? null)
        setError(null)
      })
      .catch((err) => {
        if (!cancelled) setError(errorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [projectId])

  const quickLinks: QuickLink[] = [
    {
      to: `/projects/${projectId}/import`,
      icon: Upload,
      label: 'Import Data',
      description: 'Bring in new images and annotations',
    },
    {
      to: `/projects/${projectId}/diff`,
      icon: GitCompare,
      label: 'Compare Versions',
      description: 'Diff two dataset snapshots',
    },
    {
      to: `/projects/${projectId}/models`,
      icon: Box,
      label: 'Model Registry',
      description: 'Browse trained and registered models',
    },
    {
      to: `/projects/${projectId}/training`,
      icon: Cpu,
      label: 'Training Jobs',
      description: 'Fine-tune a model on this project',
    },
    {
      to: `/projects/${projectId}/annotation`,
      icon: Tag,
      label: 'Annotation Jobs',
      description: 'Whole-image inference and review',
    },
    {
      to: `/projects/${projectId}/point-annotation`,
      icon: Crosshair,
      label: 'Point Annotation',
      description: 'CoralNet-style point labeling jobs',
    },
    {
      to: `/projects/${projectId}/ontology`,
      icon: Layers,
      label: 'Ontology Changes',
      description: 'Label vocabulary history and dirty flags',
    },
  ]

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title">{dataset ? dataset.datasetName : 'Project'}</div>
          <div className="topbar-sub">
            {dataset ? dataset.description || 'No description' : 'Project overview'}
          </div>
        </div>
        <button
          className="btn btn-primary"
          onClick={() => navigate(`/projects/${projectId}/versions`)}
          style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
        >
          <GitBranch size={14} strokeWidth={1.5} />
          Dataset Versions
        </button>
      </div>

      <div className="content">
        {error && (
          <div className="alert alert-error" style={{ marginBottom: 16 }}>
            {error}
          </div>
        )}

        {loading ? (
          <div className="loading-state">
            <span className="spinner" /> Loading project…
          </div>
        ) : (
          dataset && (
            <>
              {latestVersion && !latestVersion.complete && (
                <div className="impact-box" style={{ marginBottom: 20 }}>
                  <span>
                    The latest version has {formatCount(latestVersion.unresolvedDirtyCount)} unresolved
                    dirty annotation{latestVersion.unresolvedDirtyCount === 1 ? '' : 's'} and cannot be
                    trained against until resolved.
                  </span>
                  <Link
                    to={`/projects/${projectId}/ontology?datasetVersionId=${latestVersion.id}`}
                    className="badge badge-amber"
                    style={{ textDecoration: 'none', flexShrink: 0 }}
                  >
                    Resolve
                  </Link>
                </div>
              )}

              <div className="grid-4" style={{ marginBottom: 24 }}>
                <div className="stat-card">
                  <div className="stat-label">Versions</div>
                  <div className="stat-value">{formatCount(dataset.versionCount)}</div>
                  <div className="stat-sub">
                    {dataset.latestVersionTag ? (
                      <span className="mono">{dataset.latestVersionTag}</span>
                    ) : (
                      'No versions yet'
                    )}
                  </div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Images</div>
                  <div className="stat-value">
                    {dataset.imageCount != null ? formatCount(dataset.imageCount) : '—'}
                  </div>
                  <div className="stat-sub">Latest version</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Annotation Points</div>
                  <div className="stat-value">
                    {dataset.annotationCount != null ? formatCount(dataset.annotationCount) : '—'}
                  </div>
                  <div className="stat-sub">Latest version</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Label Classes</div>
                  <div className="stat-value">{latestVersion?.labelDefinitions?.length ?? '—'}</div>
                  <div className="stat-sub">Latest version</div>
                </div>
              </div>

              <div className="section-header">
                <div className="section-title">Latest version</div>
              </div>
              <div className="card" style={{ padding: 16, marginBottom: 24 }}>
                {latestVersion ? (
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      flexWrap: 'wrap',
                      gap: 12,
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <span className="mono" style={{ fontSize: 14 }}>
                        {latestVersion.versionTag}
                      </span>
                      <span className={`badge ${triggerReasonBadge(latestVersion.triggerReason)}`}>
                        {triggerReasonLabel(latestVersion.triggerReason)}
                      </span>
                      {!latestVersion.complete && (
                        <span className="badge badge-amber">Incomplete</span>
                      )}
                      <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                        Created {formatDate(latestVersion.createdAt)} by {latestVersion.createdBy}
                      </span>
                    </div>
                    <button
                      className="btn btn-secondary btn-sm"
                      onClick={() => navigate(`/projects/${projectId}/versions/${latestVersion.id}`)}
                      style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
                    >
                      Open version <ArrowRight size={12} strokeWidth={1.5} />
                    </button>
                  </div>
                ) : (
                  <div className="empty-state">
                    <p>No versions yet. Import data or create a snapshot to get started.</p>
                  </div>
                )}
              </div>

              <div className="section-header">
                <div className="section-title">Quick links</div>
              </div>
              <div className="grid-3" style={{ gap: 16 }}>
                {quickLinks.map((link) => {
                  const Icon = link.icon
                  return (
                    <div
                      key={link.to}
                      className="card"
                      style={{ padding: 16, cursor: 'pointer' }}
                      onClick={() => navigate(link.to)}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                        <Icon size={16} strokeWidth={1.5} color="var(--accent-500)" />
                        <div style={{ fontWeight: 600, fontSize: 13 }}>{link.label}</div>
                      </div>
                      <div style={{ color: 'var(--text-secondary)', fontSize: 12 }}>
                        {link.description}
                      </div>
                    </div>
                  )
                })}
              </div>
            </>
          )
        )}
      </div>
    </>
  )
}
