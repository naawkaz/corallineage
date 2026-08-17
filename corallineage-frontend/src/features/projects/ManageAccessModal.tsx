import { useEffect, useState } from 'react'
import { XMarkIcon } from '@heroicons/react/24/outline'
import { fetchMemberSourceAccess, setMemberSourceAccess } from '../projects/membersApi'
import { errorMessage } from '../../shared/api/client'
import SourceChecklist from './SourceChecklist'
import type { Dataset } from '../datasets/datasetsTypes'
import type { ProjectMember } from '../projects/membersTypes'

interface ManageAccessModalProps {
  open: boolean
  projectId: number
  member: ProjectMember | null
  sources: Dataset[]
  onClose: () => void
  onSaved: () => void
}

export default function ManageAccessModal({ open, projectId, member, sources, onClose, onSaved }: ManageAccessModalProps) {
  const [checked, setChecked] = useState<Set<number>>(new Set())
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (open && member) {
      Promise.resolve().then(() => {
        setLoading(true)
        setError(null)
      })
      fetchMemberSourceAccess(projectId, member.userId)
        .then((access) => {
          setChecked(access.restricted ? new Set(access.sourceIds) : new Set(sources.map((s) => s.id)))
        })
        .catch((err) => setError(errorMessage(err)))
        .finally(() => setLoading(false))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, member])

  if (!open || !member) return null

  function toggle(sourceId: number) {
    setChecked((prev) => {
      const next = new Set(prev)
      if (next.has(sourceId)) next.delete(sourceId)
      else next.add(sourceId)
      return next
    })
  }

  async function handleSave() {
    if (!member) return
    setSaving(true)
    setError(null)
    try {
      await setMemberSourceAccess(projectId, member.userId, Array.from(checked))
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="modal-overlay active">
      <div className="modal">
        <div className="modal-header">
          <div>
            <div className="modal-title">Manage access</div>
            <div className="modal-subtitle">
              Which sources {member.displayName} can see in this project
            </div>
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
          {loading ? (
            <div className="loading-state">
              <span className="spinner" /> Loading access…
            </div>
          ) : (
            <SourceChecklist sources={sources} checked={checked} onToggle={toggle} />
          )}
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" onClick={handleSave} disabled={saving || loading}>
            {saving ? 'Saving…' : 'Save access'}
          </button>
        </div>
      </div>
    </div>
  )
}
