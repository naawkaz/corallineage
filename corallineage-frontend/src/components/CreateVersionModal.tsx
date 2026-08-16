import { useState } from 'react'
import { X } from 'lucide-react'
import { createVersion } from '../api/versions'
import { errorMessage } from '../api/client'
import { CURRENT_USER, TRIGGER_REASONS } from '../constants'
import type { CreateVersionRequest, DatasetVersion, TriggerReason } from '../types/version'

interface CreateVersionModalProps {
  datasetId: number
  open: boolean
  onClose: () => void
  onCreated: (version: DatasetVersion) => void
}

export default function CreateVersionModal({ datasetId, open, onClose, onCreated }: CreateVersionModalProps) {
  const [versionTag, setVersionTag] = useState('')
  const [triggerReason, setTriggerReason] = useState<TriggerReason>('MANUAL_SNAPSHOT')
  const [notes, setNotes] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!open) return null

  async function handleSubmit() {
    setSubmitting(true)
    setError(null)
    const request: CreateVersionRequest = {
      versionTag: versionTag.trim(),
      triggerReason,
      createdBy: CURRENT_USER,
      notes: notes.trim() === '' ? null : notes.trim(),
    }
    try {
      const created = await createVersion(datasetId, request)
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
            <div className="modal-title">Create new dataset version</div>
            <div className="modal-subtitle">
              Snapshots the current dataset state via DVC and records it in the version history
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
            <label className="form-label" htmlFor="versionTag">
              Version tag
            </label>
            <input
              id="versionTag"
              className="form-input"
              placeholder="e.g. v1.1"
              value={versionTag}
              onChange={(e) => setVersionTag(e.target.value)}
            />
            <div className="form-hint">Must be unique within this dataset.</div>
          </div>
          <div className="form-row">
            <label className="form-label" htmlFor="triggerReason">
              Trigger reason
            </label>
            <select
              id="triggerReason"
              className="form-select"
              value={triggerReason}
              onChange={(e) => setTriggerReason(e.target.value as TriggerReason)}
            >
              {TRIGGER_REASONS.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>
          <div className="form-row">
            <label className="form-label" htmlFor="notes">
              Notes
            </label>
            <textarea
              id="notes"
              className="form-textarea"
              placeholder="Optional description of what changed in this version"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </div>
          <div className="form-row" style={{ marginBottom: 0 }}>
            <label className="form-label">Created by</label>
            <input className="form-input" value={CURRENT_USER} disabled />
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn btn-primary"
            onClick={handleSubmit}
            disabled={submitting || !versionTag.trim()}
          >
            {submitting ? 'Creating…' : 'Create version'}
          </button>
        </div>
      </div>
    </div>
  )
}
