import { useEffect, useState } from 'react'
import { XMarkIcon } from '@heroicons/react/24/outline'
import { addProjectMember } from '../projects/membersApi'
import { errorMessage } from '../../shared/api/client'
import SourceChecklist from './SourceChecklist'
import type { Dataset } from '../datasets/datasetsTypes'
import type { ProjectMember, ProjectRole } from '../projects/membersTypes'

interface AddCollaboratorModalProps {
  open: boolean
  projectId: number
  sources: Dataset[]
  onClose: () => void
  onAdded: (member: ProjectMember) => void
}

export default function AddCollaboratorModal({ open, projectId, sources, onClose, onAdded }: AddCollaboratorModalProps) {
  const [username, setUsername] = useState('')
  const [role, setRole] = useState<ProjectRole>('COLLABORATOR')
  const [checked, setChecked] = useState<Set<number>>(new Set())
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (open) {
      // All sources checked on by default (item 21: matches the full-access-by-default semantics).
      // Deferred via .then so this isn't a synchronous setState call in the effect body.
      Promise.resolve().then(() => {
        setChecked(new Set(sources.map((s) => s.id)))
        setUsername('')
        setRole('COLLABORATOR')
        setError(null)
      })
    }
  }, [open, sources])

  if (!open) return null

  function toggle(sourceId: number) {
    setChecked((prev) => {
      const next = new Set(prev)
      if (next.has(sourceId)) next.delete(sourceId)
      else next.add(sourceId)
      return next
    })
  }

  async function handleSubmit() {
    if (!username.trim()) return
    setSubmitting(true)
    setError(null)
    try {
      const member = await addProjectMember(projectId, {
        username: username.trim(),
        role,
        sourceIds: Array.from(checked),
      })
      onAdded(member)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="modal-overlay active">
      <div className="modal">
        <div className="modal-header">
          <div>
            <div className="modal-title">Add collaborator</div>
            <div className="modal-subtitle">Grant a user access to this project</div>
          </div>
          <button className="modal-close" onClick={onClose} aria-label="Close">
            <XMarkIcon style={{ width: 16, height: 16 }} />
          </button>
        </div>
        <div className="modal-body">
          {error && (
            <div className="alert alert-error" style={{ marginBottom: 16 }}>
              {error}
            </div>
          )}
          <div className="form-row">
            <label className="form-label" htmlFor="addCollabUsername">
              Username
            </label>
            <input
              id="addCollabUsername"
              className="form-input"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>
          <div className="form-row">
            <label className="form-label" htmlFor="addCollabRole">
              Role
            </label>
            <select
              id="addCollabRole"
              className="form-select"
              value={role}
              onChange={(e) => setRole(e.target.value as ProjectRole)}
            >
              <option value="COLLABORATOR">Collaborator</option>
              <option value="OWNER">Owner</option>
            </select>
          </div>
          {role === 'COLLABORATOR' && (
            <div className="form-row" style={{ marginBottom: 0 }}>
              <label className="form-label">Source access</label>
              <SourceChecklist sources={sources} checked={checked} onToggle={toggle} />
              <div className="form-hint">Unchecked sources will not be visible to this collaborator.</div>
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" onClick={handleSubmit} disabled={submitting || !username.trim()}>
            {submitting ? 'Adding…' : 'Add collaborator'}
          </button>
        </div>
      </div>
    </div>
  )
}
