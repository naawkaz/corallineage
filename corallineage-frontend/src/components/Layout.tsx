import { NavLink, Outlet } from 'react-router-dom'
import { CURRENT_USER } from '../constants'
import { useDatasetContext } from '../lib/datasetContextCore'

const sections: { label: string; items: { to: string; icon: string; label: string }[] }[] = [
  {
    label: 'Dataset',
    items: [
      { to: '/datasets', icon: '🪸', label: 'Datasets' },
      { to: '/versions', icon: '🗂', label: 'Version History' },
      { to: '/import', icon: '⬆', label: 'Import Data' },
      { to: '/diff', icon: '⇄', label: 'Compare Versions' },
    ],
  },
  {
    label: 'Models',
    items: [
      { to: '/models', icon: '📦', label: 'Model Registry' },
      { to: '/training', icon: '⚙️', label: 'Training Jobs' },
    ],
  },
  {
    label: 'Annotation',
    items: [
      { to: '/annotation', icon: '🏷', label: 'Annotation Jobs' },
      { to: '/review', icon: '✔️', label: 'Review Predictions' },
    ],
  },
  {
    label: 'Provenance',
    items: [
      { to: '/events', icon: '📋', label: 'Event Log' },
      { to: '/ontology', icon: '🔖', label: 'Ontology Changes' },
      { to: '/dirty', icon: '⚠️', label: 'Dirty Annotations' },
    ],
  },
  {
    label: 'Reports',
    items: [{ to: '/report', icon: '📄', label: 'Report Generator' }],
  },
]

function DatasetSelector() {
  const { datasets, activeDatasetId, setActiveDatasetId, loading } = useDatasetContext()

  if (loading || datasets.length === 0) return null

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '8px 24px',
        borderBottom: '1px solid var(--border)',
      }}
    >
      <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Active dataset</span>
      <select
        className="form-select"
        style={{ width: 260 }}
        value={activeDatasetId ?? ''}
        onChange={(e) => setActiveDatasetId(Number(e.target.value))}
      >
        {datasets.map((d) => (
          <option key={d.id} value={d.id}>
            {d.datasetName}
          </option>
        ))}
      </select>
    </div>
  )
}

export default function Layout() {
  return (
    <>
      <nav className="sidebar">
        <div className="sidebar-logo">
          <div className="wordmark">🪸 CoralLineage</div>
          <div className="tagline">Annotation reproducibility</div>
        </div>

        {sections.map((section) => (
          <div className="sidebar-section" key={section.label}>
            <div className="sidebar-section-label">{section.label}</div>
            {section.items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) => `sidebar-item${isActive ? ' active' : ''}`}
              >
                <span className="icon">{item.icon}</span>
                {item.label}
              </NavLink>
            ))}
          </div>
        ))}

        <div className="sidebar-footer">
          <div className="user-avatar">R1</div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-primary)', fontWeight: 500 }}>
              {CURRENT_USER}
            </div>
            <div style={{ fontSize: 11 }}>Sign out</div>
          </div>
        </div>
      </nav>

      <main className="main">
        <DatasetSelector />
        <Outlet />
      </main>
    </>
  )
}
