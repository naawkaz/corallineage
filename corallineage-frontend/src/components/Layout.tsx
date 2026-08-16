import { NavLink, Outlet, useMatch } from 'react-router-dom'
import type { LucideIcon } from 'lucide-react'
import {
  Home,
  ClipboardList,
  FileText,
  GitBranch,
  Upload,
  GitCompare,
  Box,
  Cpu,
  Tag,
  Crosshair,
  Layers,
  Waves,
  ArrowLeft,
} from 'lucide-react'
import { CURRENT_USER } from '../constants'
import { useDatasetContext } from '../lib/datasetContextCore'

const globalLinks: { to: string; icon: LucideIcon; label: string }[] = [
  { to: '/', icon: Home, label: 'Home' },
  { to: '/events', icon: ClipboardList, label: 'Global Event Log' },
  { to: '/report', icon: FileText, label: 'Report Generator' },
]

function projectSections(projectId: string): { label: string; items: { to: string; icon: LucideIcon; label: string }[] }[] {
  return [
    {
      label: 'Project',
      items: [
        { to: `/projects/${projectId}/versions`, icon: GitBranch, label: 'Version History' },
        { to: `/projects/${projectId}/import`, icon: Upload, label: 'Import Data' },
        { to: `/projects/${projectId}/diff`, icon: GitCompare, label: 'Compare Versions' },
      ],
    },
    {
      label: 'Models',
      items: [
        { to: `/projects/${projectId}/models`, icon: Box, label: 'Model Registry' },
        { to: `/projects/${projectId}/training`, icon: Cpu, label: 'Training Jobs' },
      ],
    },
    {
      label: 'Annotation',
      items: [
        { to: `/projects/${projectId}/annotation`, icon: Tag, label: 'Annotation Jobs' },
        { to: `/projects/${projectId}/point-annotation`, icon: Crosshair, label: 'Point Annotation' },
      ],
    },
    {
      label: 'Provenance',
      items: [
        { to: `/projects/${projectId}/events`, icon: ClipboardList, label: 'Event Log' },
        { to: `/projects/${projectId}/ontology`, icon: Layers, label: 'Ontology Changes' },
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
          <Waves size={18} strokeWidth={1.5} color="var(--accent-400)" />
          <div>
            <div className="wordmark">CORALLINEAGE</div>
            <div className="tagline">Annotation reproducibility</div>
          </div>
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
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 4,
                  marginBottom: 8,
                }}
              >
                <ArrowLeft size={12} strokeWidth={1.5} />
                All Projects
              </NavLink>
              <div style={{ fontWeight: 600, fontSize: 14, color: 'var(--sidebar-text-strong)' }}>
                {project ? project.datasetName : `Project ${projectId}`}
              </div>
            </div>
            {sections!.map((section) => (
              <div className="sidebar-section" key={section.label}>
                <div className="sidebar-section-label">{section.label}</div>
                {section.items.map((item) => {
                  const Icon = item.icon
                  return (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      className={({ isActive }) => `sidebar-item${isActive ? ' active' : ''}`}
                    >
                      <Icon className="icon" strokeWidth={1.5} />
                      {item.label}
                    </NavLink>
                  )
                })}
              </div>
            ))}
          </>
        ) : (
          <div className="sidebar-section">
            {globalLinks.map((item) => {
              const Icon = item.icon
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/'}
                  className={({ isActive }) => `sidebar-item${isActive ? ' active' : ''}`}
                >
                  <Icon className="icon" strokeWidth={1.5} />
                  {item.label}
                </NavLink>
              )
            })}
          </div>
        )}

        <div className="sidebar-footer">
          <div className="user-avatar">R1</div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--sidebar-text-strong)', fontWeight: 500 }}>
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
