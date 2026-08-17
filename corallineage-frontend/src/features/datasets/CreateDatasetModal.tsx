import { useState } from 'react'
import { XMarkIcon } from '@heroicons/react/24/outline'
import { createDataset } from '../datasets/datasetsApi'
import { errorMessage } from '../../shared/api/client'
import { useAuthContext } from '../../shared/lib/authContextCore'
import type { CreateDatasetRequest, Dataset } from '../datasets/datasetsTypes'

interface CreateDatasetModalProps {
  open: boolean
  projectId: number
  onClose: () => void
  onCreated: (dataset: Dataset) => void
}

export default function CreateDatasetModal({ open, projectId, onClose, onCreated }: CreateDatasetModalProps) {
  const { user } = useAuthContext()
  const [datasetName, setDatasetName] = useState('')
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!open) return null

  async function handleSubmit() {
    setSubmitting(true)
    setError(null)
    const request: CreateDatasetRequest = {
      datasetName: datasetName.trim(),
      description: description.trim() === '' ? null : description.trim(),
      createdBy: user?.username ?? '',
    }
    try {
      const created = await createDataset(projectId, request)
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
            <div className="modal-title">Create new source</div>
            <div className="modal-subtitle">
              A named container for versioned snapshots of a coral annotation dataset
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
          <div className="form-row">
            <label className="form-label" htmlFor="datasetName">
              Source name
            </label>
            <input
              id="datasetName"
              className="form-input"
              placeholder="e.g. Moorea Labeled Corals"
              value={datasetName}
              onChange={(e) => setDatasetName(e.target.value)}
            />
            <div className="form-hint">Must be unique across all sources.</div>
          </div>
          <div className="form-row" style={{ marginBottom: 0 }}>
            <label className="form-label" htmlFor="description">
              Description
            </label>
            <textarea
              id="description"
              className="form-textarea"
              placeholder="Optional description of the dataset's subject and source"
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
            disabled={submitting || !datasetName.trim()}
          >
            {submitting ? 'Creating…' : 'Create source'}
          </button>
        </div>
      </div>
    </div>
  )
}
