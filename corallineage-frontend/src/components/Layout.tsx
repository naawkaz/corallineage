import { NavLink, Outlet, useMatch } from 'react-router-dom'
import { CURRENT_USER } from '../constants'
import { useDatasetContext } from '../lib/datasetContextCore'

const globalLinks: { to: string; icon: string; label: string }[] = [
  { to: '/', icon: '🏠', label: 'Home' },
  { to: '/events', icon: '📋', label: 'Global Event Log' },
  { to: '/report', icon: '📄', label: 'Report Generator' },
]

function projectSections(projectId: string): { label: string; items: { to: string; icon: string; label: string }[] }[] {
  return [
    {
      label: 'Project',
      items: [
        { to: `/projects/${projectId}/versions`, icon: '🪸', label: 'Version History' },
        { to: `/projects/${projectId}/import`, icon: '⬆', label: 'Import Data' },
        { to: `/projects/${projectId}/diff`, icon: '⇄', label: 'Compare Versions' },
      ],
    },
    {
      label: 'Models',
      items: [
        { to: `/projects/${projectId}/models`, icon: '📦', label: 'Model Registry' },
        { to: `/projects/${projectId}/training`, icon: '⚙️', label: 'Training Jobs' },
      ],
    },
    {
      label: 'Annotation',
      items: [{ to: `/projects/${projectId}/annotation`, icon: '🏷', label: 'Annotation Jobs' }],
    },
    {
      label: 'Provenance',
      items: [
        { to: `/projects/${projectId}/events`, icon: '📋', label: 'Event Log' },
        { to: `/projects/${projectId}/ontology`, icon: '🔖', label: 'Ontology Changes' },
      ],
    },
  ]
}

export default function Layout() {
  const match = useMatch('/projects/:projectId/*')
  const projectId = match?.params.projectId
  const { datasets } = useDatasetContext()
  const project = projectId ? datasets.find((d) => d.id === Number(projectId)) : null

  const sections = projectId ? projectSections(projectId) : null

  return (
    <>
      <nav className="sidebar">
        <div className="sidebar-logo">
          <div className="wordmark">🪸 CoralLineage</div>
          <div className="tagline">Annotation reproducibility</div>
        </div>

        {projectId ? (
          <>
            <div style={{ padding: '0 24px 12px' }}>
              <NavLink
                to="/"
                style={{
                  fontSize: 12,
                  color: 'var(--text-muted)',
                  textDecoration: 'none',
                  display: 'inline-block',
                  marginBottom: 8,
                }}
              >
                ← All Projects
              </NavLink>
              <div style={{ fontWeight: 600, fontSize: 14 }}>
                {project ? project.datasetName : `Project ${projectId}`}
              </div>
            </div>
            {sections!.map((section) => (
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
          </>
        ) : (
          <div className="sidebar-section">
            {globalLinks.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) => `sidebar-item${isActive ? ' active' : ''}`}
              >
                <span className="icon">{item.icon}</span>
                {item.label}
              </NavLink>
            ))}
          </div>
        )}

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
        <Outlet />
      </main>
    </>
  )
}
