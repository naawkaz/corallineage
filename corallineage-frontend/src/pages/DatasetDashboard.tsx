import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeftIcon } from '@heroicons/react/24/outline'
import { fetchDataset } from '../api/datasets'
import { fetchProject } from '../api/projects'
import { fetchVersions } from '../api/versions'
import { fetchModels } from '../api/models'
import { errorMessage } from '../api/client'
import { formatCount } from '../lib/format'
import VersionsList from '../components/VersionsList'
import type { Dataset } from '../types/dataset'
import type { DatasetVersion } from '../types/version'
import type { Project } from '../types/project'

export default function DatasetDashboard() {
  const navigate = useNavigate()
  const { sourceId: sourceIdParam } = useParams()
  const sourceId = Number(sourceIdParam)

  const [dataset, setDataset] = useState<Dataset | null>(null)
  const [parentProject, setParentProject] = useState<Project | null>(null)
  const [latestVersion, setLatestVersion] = useState<DatasetVersion | null>(null)
  const [modelCount, setModelCount] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!sourceId) return
    let cancelled = false
    Promise.resolve().then(() => {
      if (!cancelled) setLoading(true)
    })
    Promise.all([fetchDataset(sourceId), fetchVersions(sourceId, 0, 1), fetchModels(sourceId, 0, 1)])
      .then(([datasetRes, versionsRes, modelsRes]) => {
        if (cancelled) return
        setDataset(datasetRes)
        setLatestVersion(versionsRes.content[0] ?? null)
        setModelCount(modelsRes.totalElements)
        setError(null)
        return datasetRes.projectId != null ? fetchProject(datasetRes.projectId) : null
      })
      .then((projectRes) => {
        if (!cancelled) setParentProject(projectRes ?? null)
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
  }, [sourceId])

  const dirtyBanner = latestVersion && !latestVersion.complete

  return (
    <>
      {/* item 28: edge-to-edge, no page padding, red with light text, no border */}
      {dirtyBanner && (
        <div
          style={{
            width: '100%',
            padding: '10px 28px',
            background: 'var(--danger-bg)',
            color: 'var(--danger)',
            fontWeight: 400,
            fontSize: 12.5,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            flexShrink: 0,
          }}
        >
          <span>
            The latest version has {formatCount(latestVersion!.unresolvedDirtyCount)} unresolved dirty
            annotation{latestVersion!.unresolvedDirtyCount === 1 ? '' : 's'} and cannot be trained against
            until resolved.
          </span>
          <Link to={`/sources/${sourceId}/ontology?datasetVersionId=${latestVersion!.id}`} style={{ color: 'inherit', flexShrink: 0 }}>
            Resolve
          </Link>
        </div>
      )}

      {/* item 26: reserved top-nav space, single button back to the parent project */}
      <div className="page-actions with-back">
        <button
          className="btn btn-secondary btn-sm"
          onClick={() => navigate(dataset?.projectId != null ? `/projects/${dataset.projectId}` : '/')}
          style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
        >
          <ArrowLeftIcon style={{ width: 12, height: 12 }} />
          {parentProject ? parentProject.projectName : 'Back to project'}
        </button>
        <div style={{ fontSize: 14, fontWeight: 600 }}>{dataset?.datasetName}</div>
      </div>

      <div className="content">
        {error && (
          <div className="alert alert-error" style={{ marginBottom: 16 }}>
            {error}
          </div>
        )}

        {loading ? (
          <div className="loading-state">
            <span className="spinner" /> Loading source…
          </div>
        ) : (
          dataset && (
            <>
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
                    {dataset.imageCount != null ? formatCount(dataset.imageCount) : 0}
                  </div>
                  <div className="stat-sub">Latest version</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Label Classes</div>
                  <div className="stat-value">{latestVersion?.labelDefinitions?.length ?? 0}</div>
                  <div className="stat-sub">Latest version</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Number of models</div>
                  <div className="stat-value">{modelCount != null ? formatCount(modelCount) : 0}</div>
                  <div className="stat-sub">In the model registry</div>
                </div>
              </div>

              <VersionsList sourceId={sourceId} />
            </>
          )
        )}
      </div>
    </>
  )
}
