import { useState } from 'react'
import { createDataset } from '../api/datasets'
import { errorMessage } from '../api/client'
import { CURRENT_USER } from '../constants'
import type { CreateDatasetRequest, Dataset } from '../types/dataset'

interface CreateDatasetModalProps {
  open: boolean
  onClose: () => void
  onCreated: (dataset: Dataset) => void
}

export default function CreateDatasetModal({ open, onClose, onCreated }: CreateDatasetModalProps) {
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
      createdBy: CURRENT_USER,
    }
    try {
      const created = await createDataset(request)
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
            <div className="modal-title">Create new dataset</div>
            <div className="modal-subtitle">
              A named container for versioned snapshots of a coral annotation project
            </div>
          </div>
          <button className="modal-close" onClick={onClose} aria-label="Close">
            ✕
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
              Dataset name
            </label>
            <input
              id="datasetName"
              className="form-input"
              placeholder="e.g. Moorea Labeled Corals"
              value={datasetName}
              onChange={(e) => setDatasetName(e.target.value)}
            />
            <div className="form-hint">Must be unique across all datasets.</div>
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
            {submitting ? 'Creating…' : 'Create dataset'}
          </button>
        </div>
      </div>
    </div>
  )
}
