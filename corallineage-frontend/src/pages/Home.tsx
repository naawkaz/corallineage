import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ClipboardList, FileText, Shell } from 'lucide-react'
import { fetchProjects } from '../api/projects'
import { errorMessage } from '../api/client'
import { formatCount, formatDate } from '../lib/format'
import CreateProjectModal from '../components/CreateProjectModal'
import type { Project } from '../types/project'

export default function Home() {
  const [projects, setProjects] = useState<Project[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const navigate = useNavigate()

  function load() {
    setLoading(true)
    fetchProjects()
      .then((result) => {
        setProjects(result)
        setError(null)
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    Promise.resolve().then(() => load())
  }, [])

  return (
    <>
      <div className="topbar">
        <div>
          <div className="topbar-title">Home</div>
          <div className="topbar-sub">Named containers that group Sources and manage collaborator access</div>
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
        ) : projects.length === 0 ? (
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
            {projects.map((p) => (
              <div
                key={p.id}
                className="card"
                style={{ padding: 16, cursor: 'pointer' }}
                onClick={() => navigate(`/projects/${p.id}`)}
              >
                <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 4 }}>
                  {p.projectName}
                </div>
                <div style={{ color: 'var(--text-secondary)', fontSize: 12, marginBottom: 12 }}>
                  {p.description || 'No description'}
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
                  <span className={`badge ${p.callerRole === 'OWNER' ? 'badge-blue' : 'badge-gray'}`}>
                    {p.callerRole === 'OWNER' ? 'Owner' : 'Collaborator'}
                  </span>
                  <span style={{ color: 'var(--text-muted)' }}>
                    {formatCount(p.sourceCount)} source{p.sourceCount === 1 ? '' : 's'}
                  </span>
                </div>
                <div style={{ color: 'var(--text-muted)', fontSize: 11, marginTop: 8 }}>
                  Created {formatDate(p.createdAt)} by {p.createdBy}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <CreateProjectModal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onCreated={(p) => {
          setShowCreate(false)
          load()
          navigate(`/projects/${p.id}`)
        }}
      />
    </>
  )
}
