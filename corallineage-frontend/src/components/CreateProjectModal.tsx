import { useState } from 'react'
import { X } from 'lucide-react'
import { createProject } from '../api/projects'
import { errorMessage } from '../api/client'
import type { CreateProjectRequest, Project } from '../types/project'

interface CreateProjectModalProps {
  open: boolean
  onClose: () => void
  onCreated: (project: Project) => void
}

export default function CreateProjectModal({ open, onClose, onCreated }: CreateProjectModalProps) {
  const [projectName, setProjectName] = useState('')
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!open) return null

  async function handleSubmit() {
    setSubmitting(true)
    setError(null)
    const request: CreateProjectRequest = {
      projectName: projectName.trim(),
      description: description.trim() === '' ? null : description.trim(),
    }
    try {
      const created = await createProject(request)
      onCreated(created)
    } catch (err) {
      setError(errorMessage(err))
      setSubmitting(false)
    }
  }

  return (
    <div className="modal-overlay active">
      <div className="modal">
        <div className="modal-header">
          <div>
            <div className="modal-title">Create new project</div>
            <div className="modal-subtitle">
              A named container that groups one or more Sources and manages who can access them
            </div>
          </div>
          <button className="modal-close" onClick={onClose} aria-label="Close">
            <X size={16} strokeWidth={1.5} />
          </button>
        </div>
        <div className="modal-body">
          {error && (
            <div className="alert alert-error" style={{ marginBottom: 16 }}>
              {error}
            </div>
          )}
          <div className="form-row">
            <label className="form-label" htmlFor="projectName">
              Project name
            </label>
            <input
              id="projectName"
              className="form-input"
              placeholder="e.g. Moorea Reef Monitoring"
              value={projectName}
              onChange={(e) => setProjectName(e.target.value)}
            />
            <div className="form-hint">Must be unique across all projects.</div>
          </div>
          <div className="form-row" style={{ marginBottom: 0 }}>
            <label className="form-label" htmlFor="projectDescription">
              Description
            </label>
            <textarea
              id="projectDescription"
              className="form-textarea"
              placeholder="Optional description of the project's scope"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn btn-primary"
            onClick={handleSubmit}
            disabled={submitting || !projectName.trim()}
          >
            {submitting ? 'Creating…' : 'Create project'}
          </button>
        </div>
      </div>
    </div>
  )
}
