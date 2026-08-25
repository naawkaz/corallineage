import { useState } from 'react'
import type { ComponentType, SVGProps } from 'react'
import { NavLink, Outlet, useMatch, useNavigate } from 'react-router-dom'
import {
  HomeIcon,
  ClipboardDocumentListIcon,
  DocumentTextIcon,
  ArrowRightStartOnRectangleIcon,
  ChevronDoubleLeftIcon,
  ChevronDoubleRightIcon,
  Squares2X2Icon,
  ArrowsRightLeftIcon,
  CubeIcon,
  Square3Stack3DIcon,
  ArrowLeftIcon,
} from '@heroicons/react/24/outline'
import { useAuthContext } from '../lib/authContextCore'
import { useDatasetContext } from '../lib/datasetContextCore'


type IconType = ComponentType<SVGProps<SVGSVGElement>>

const globalLinks: { to: string; icon: IconType; label: string }[] = [
  { to: '/', icon: HomeIcon, label: 'Projects' },
  { to: '/events', icon: ClipboardDocumentListIcon, label: 'Global Event Log' },
  { to: '/report', icon: DocumentTextIcon, label: 'Report Generator' },
]

interface SourceNavItem {
  to: string
  icon: IconType
  label: string
  exact?: boolean
}

function sourceNavItems(sourceId: string): SourceNavItem[] {
  return [
    { to: `/sources/${sourceId}`, icon: Squares2X2Icon, label: 'Overview', exact: true },
    { to: `/sources/${sourceId}/models`, icon: CubeIcon, label: 'Models' },
    { to: `/sources/${sourceId}/ontology`, icon: Square3Stack3DIcon, label: 'Ontology Changes' },
    { to: `/sources/${sourceId}/diff`, icon: ArrowsRightLeftIcon, label: 'Compare Versions' },
    { to: `/sources/${sourceId}/events`, icon: ClipboardDocumentListIcon, label: 'Event Log' },
  ]
}

export default function Layout() {
  const match = useMatch('/sources/:sourceId/*')
  const sourceId = match?.params.sourceId
  const { user, logout } = useAuthContext()
  const { datasets } = useDatasetContext()
  const navigate = useNavigate()
  const [collapsed, setCollapsed] = useState(false)

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

  const sourceItems = sourceId ? sourceNavItems(sourceId) : null
  const currentDataset = sourceId ? datasets.find((d) => String(d.id) === sourceId) : undefined
  const backToProjectPath = currentDataset?.projectId != null ? `/projects/${currentDataset.projectId}` : '/'

  return (
    <>
      <nav className={`sidebar${collapsed ? ' collapsed' : ''}`}>
        <div className="sidebar-logo">
          <img src="/logo.png" alt="" width={18} height={18} style={{ filter: 'invert(1)', flexShrink: 0 }} />
          {!collapsed && <div className="wordmark">CORALLINEAGE</div>}
          <button
            className="sidebar-collapse-toggle"
            onClick={() => setCollapsed((c) => !c)}
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? (
              <ChevronDoubleRightIcon style={{ width: 15, height: 15 }} />
            ) : (
              <ChevronDoubleLeftIcon style={{ width: 15, height: 15 }} />
            )}
          </button>
        </div>

        {sourceItems && (
          <NavLink to={backToProjectPath} className="sidebar-back-link" title={collapsed ? 'Sources' : undefined}>
            <ArrowLeftIcon style={{ width: 13, height: 13 }} />
            <span>Sources</span>
          </NavLink>
        )}

        {sourceItems ? (
          <div className="sidebar-section">
            {sourceItems.map((item) => {
              const Icon = item.icon
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.exact}
                  className={({ isActive }) => `sidebar-item${isActive ? ' active' : ''}`}
                  title={collapsed ? item.label : undefined}
                >
                  <Icon className="icon" />
                  <span>{item.label}</span>
                </NavLink>
              )
            })}
          </div>
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
                  title={collapsed ? item.label : undefined}
                >
                  <Icon className="icon" />
                  <span>{item.label}</span>
                </NavLink>
              )
            })}
          </div>
        )}

        <div className="sidebar-footer">
          <div className="user-avatar">{initials}</div>
          {!collapsed && (
            <div>
              <div className="sidebar-footer-name" style={{ fontSize: 12, color: 'var(--sidebar-text-strong)', fontWeight: 500 }}>
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
                <ArrowRightStartOnRectangleIcon style={{ width: 11, height: 11 }} />
                Sign out
              </button>
            </div>
          )}
        </div>
      </nav>

      <main className="main">
        <Outlet />
      </main>
    </>
  )
}
