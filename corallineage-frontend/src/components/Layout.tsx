import { NavLink, Outlet, useMatch, useNavigate } from 'react-router-dom'
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
  LayoutDashboard,
  LogOut,
} from 'lucide-react'
import { useAuthContext } from '../lib/authContextCore'
import { useDatasetContext } from '../lib/datasetContextCore'

const globalLinks: { to: string; icon: LucideIcon; label: string }[] = [
  { to: '/', icon: Home, label: 'Home' },
  { to: '/events', icon: ClipboardList, label: 'Global Event Log' },
  { to: '/report', icon: FileText, label: 'Report Generator' },
]

interface ProjectNavItem {
  to: string
  icon: LucideIcon
  label: string
  exact?: boolean
}

function projectSections(projectId: string): { label: string; items: ProjectNavItem[] }[] {
  return [
    {
      label: 'Overview',
      items: [
        { to: `/projects/${projectId}`, icon: LayoutDashboard, label: 'Dashboard', exact: true },
      ],
    },
    {
      label: 'Project',
      items: [
        { to: `/projects/${projectId}/versions`, icon: GitBranch, label: 'Dataset Versions' },
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
  const { user, logout } = useAuthContext()
  const navigate = useNavigate()
  const project = projectId ? datasets.find((d) => d.id === Number(projectId)) : null

  function handleSignOut() {
    logout()
    navigate('/login', { replace: true })
  }

  const initials = user
    ? user.displayName
        .trim()
        .split(/\s+/)
        .map((part) => part[0])
        .slice(0, 2)
        .join('')
        .toUpperCase()
    : ''

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
                      end={item.exact}
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
          <div className="user-avatar">{initials}</div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--sidebar-text-strong)', fontWeight: 500 }}>
              {user?.displayName}
            </div>
            <button
              onClick={handleSignOut}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 4,
                fontSize: 11,
                background: 'none',
                border: 'none',
                padding: 0,
                cursor: 'pointer',
                color: 'inherit',
              }}
            >
              <LogOut size={11} strokeWidth={1.5} />
              Sign out
            </button>
          </div>
        </div>
      </nav>

      <main className="main">
        <Outlet />
      </main>
    </>
  )
}
