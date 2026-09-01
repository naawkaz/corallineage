import type { Dataset } from '../datasets/datasetsTypes'

interface SourceChecklistProps {
  sources: Dataset[]
  checked: Set<number>
  onToggle: (sourceId: number) => void
}

/** Checklist of a project's sources, shared by the add-collaborator form and the
 * manage-access dialog. */
export default function SourceChecklist({ sources, checked, onToggle }: SourceChecklistProps) {
  if (sources.length === 0) {
    return <div className="form-hint">This project has no sources yet.</div>
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxHeight: 220, overflowY: 'auto' }}>
      {sources.map((s) => (
        <label
          key={s.id}
          style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, cursor: 'pointer' }}
        >
          <input type="checkbox" checked={checked.has(s.id)} onChange={() => onToggle(s.id)} />
          {s.datasetName}
        </label>
      ))}
    </div>
  )
}
