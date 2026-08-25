import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthContext } from '../lib/authContextCore'
import { DatasetProvider } from '../lib/DatasetContext'
import { JobPollingProvider } from '../lib/JobPollingProvider'

export default function ProtectedRoute() {
  const { user, loading } = useAuthContext()
  const location = useLocation()

  if (loading) {
    return (
      <div className="loading-state" style={{ height: '100vh', alignItems: 'center', justifyContent: 'center' }}>
        <span className="spinner" /> Loading…
      </div>
    )
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return (
    <DatasetProvider>
      <JobPollingProvider>
        <Outlet />
      </JobPollingProvider>
    </DatasetProvider>
  )
}
