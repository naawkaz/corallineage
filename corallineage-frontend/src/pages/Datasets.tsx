import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useDatasetContext } from '../lib/datasetContextCore'
import { formatCount, formatDate } from '../lib/format'
import CreateDatasetModal from '../components/CreateDatasetModal'

export default function Datasets() {
  const navigate = useNavigate()
  const { datasets, activeDatasetId, setActiveDatasetId, loading, error, refresh } = useDatasetContext()
  const [showCreate, setShowCreate] = useState(false)

  function selectDataset(id: number) {
    setActiveDatasetId(id)
    navigate('/versions')
  }

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title">Datasets</div>
          <div className="topbar-sub">Named containers for versioned annotation projects</div>
        </div>
        <button className="btn btn-primary" onClick={() => setShowCreate(true)}>
          ＋ New Dataset
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
            <span className="spinner" /> Loading datasets…
          </div>
        ) : datasets.length === 0 ? (
          <div className="card">
            <div className="empty-state">
              <div className="empty-icon">🪸</div>
              <p>No datasets yet. Create your first dataset to get started.</p>
            </div>
          </div>
        ) : (
          <div className="grid-3" style={{ gap: 16 }}>
            {datasets.map((d) => (
              <div
                key={d.id}
                className="card"
                style={{
                  padding: 16,
                  cursor: 'pointer',
                  outline: d.id === activeDatasetId ? '2px solid var(--accent)' : 'none',
                }}
                onClick={() => selectDataset(d.id)}
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
          selectDataset(d.id)
        }}
      />
    </>
  )
}
