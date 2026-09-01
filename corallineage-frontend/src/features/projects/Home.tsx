import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { InboxIcon } from '@heroicons/react/24/outline'
import { fetchProjects } from '../projects/projectsApi'
import { errorMessage } from '../../shared/api/client'
import { formatCount, formatDate } from '../../shared/lib/format'
import CreateProjectModal from '../projects/CreateProjectModal'
import TitledCard from '../../shared/components/TitledCard'
import type { Project } from '../projects/projectsTypes'

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
      <div className="content">
        {error && (
          <div className="alert alert-error" style={{ marginBottom: 16 }}>
            {error}
          </div>
        )}

        <TitledCard
          title="Projects"
          actions={
            <button className="btn btn-primary" onClick={() => setShowCreate(true)}>
              + New Project
            </button>
          }
        >
          {loading ? (
            <div className="loading-state">
              <span className="spinner" /> Loading projects...
            </div>
          ) : projects.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon">
                <InboxIcon style={{ width: 32, height: 32 }} />
              </div>
              <p>No projects yet. Create your first project to get started.</p>
            </div>
          ) : (
            <div className="list-rows">
              {projects.map((p) => (
                <div key={p.id} className="list-row clickable" onClick={() => navigate(`/projects/${p.id}`)}>
                  <div className="list-row-main">
                    <div className="list-row-name">{p.projectName}</div>
                    <div className="list-row-desc">{p.description || 'No description'}</div>
                  </div>
                  <div className="list-row-meta">
                    <div className="list-row-owner">{p.ownerDisplayName ?? p.createdBy}</div>
                    <div className="list-row-date">Updated {formatDate(p.updatedAt)}</div>
                    <div className="list-row-sub">
                      {formatCount(p.sourceCount)} source{p.sourceCount === 1 ? '' : 's'}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </TitledCard>
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
