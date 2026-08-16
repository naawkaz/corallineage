import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ClipboardList, FileText, Shell } from 'lucide-react'
import { useDatasetContext } from '../lib/datasetContextCore'
import { formatCount, formatDate } from '../lib/format'
import CreateDatasetModal from '../components/CreateDatasetModal'

export default function Home() {
  const { datasets, loading, error, refresh } = useDatasetContext()
  const [showCreate, setShowCreate] = useState(false)
  const navigate = useNavigate()

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title">Home</div>
          <div className="topbar-sub">Named containers for versioned annotation projects</div>
        </div>
        <button className="btn btn-primary" onClick={() => setShowCreate(true)}>
          ＋ New Project
        </button>
      </div>

      <div className="content">
        <div style={{ display: 'flex', gap: 8, marginBottom: 20 }}>
          <button className="btn btn-secondary" onClick={() => navigate('/events')} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <ClipboardList size={14} strokeWidth={1.5} />
            Global Event Log
          </button>
          <button className="btn btn-secondary" onClick={() => navigate('/report')} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <FileText size={14} strokeWidth={1.5} />
            Report Generator
          </button>
        </div>

        {error && (
          <div className="alert alert-error" style={{ marginBottom: 16 }}>
            {error}
          </div>
        )}

        <div className="section-header">
          <div className="section-title">Projects</div>
        </div>

        {loading ? (
          <div className="loading-state">
            <span className="spinner" /> Loading projects…
          </div>
        ) : datasets.length === 0 ? (
          <div className="card">
            <div className="empty-state">
              <div className="empty-icon">
                <Shell size={32} strokeWidth={1.5} />
              </div>
              <p>No projects yet. Create your first project to get started.</p>
            </div>
          </div>
        ) : (
          <div className="grid-3" style={{ gap: 16 }}>
            {datasets.map((d) => (
              <div
                key={d.id}
                className="card"
                style={{ padding: 16, cursor: 'pointer' }}
                onClick={() => navigate(`/projects/${d.id}/versions`)}
              >
                <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 4 }}>
                  {d.datasetName}
                </div>
                <div style={{ color: 'var(--text-secondary)', fontSize: 12, marginBottom: 12 }}>
                  {d.description || 'No description'}
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
                  <span className="mono">
                    {d.latestVersionTag ?? 'no versions'}
                  </span>
                  <span style={{ color: 'var(--text-muted)' }}>
                    {formatCount(d.versionCount)} version{d.versionCount === 1 ? '' : 's'}
                  </span>
                </div>
                <div style={{ color: 'var(--text-muted)', fontSize: 11, marginTop: 8 }}>
                  Created {formatDate(d.createdAt)} by {d.createdBy}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <CreateDatasetModal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onCreated={(d) => {
          setShowCreate(false)
          refresh()
          navigate(`/projects/${d.id}/versions`)
        }}
      />
    </>
  )
}
