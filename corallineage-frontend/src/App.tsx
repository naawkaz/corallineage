import { BrowserRouter, Route, Routes } from 'react-router-dom'
import Layout from './shared/components/Layout'
import ProtectedRoute from './shared/components/ProtectedRoute'
import ComingSoon from './shared/components/ComingSoon'
import AnnotationReview from './features/annotation/AnnotationReview'
import DatasetDashboard from './features/datasets/DatasetDashboard'
import DatasetDiff from './features/datasets/DatasetDiff'
import Home from './features/projects/Home'
import EventLog from './features/events/EventLog'
import Login from './features/auth/Login'
import ModelDetail from './features/training/ModelDetail'
import ModelRegistry from './features/training/ModelRegistry'
import OntologyChanges from './features/ontology/OntologyChanges'
import OntologyChangeReview from './features/ontology/OntologyChangeReview'
import ProjectDetail from './features/projects/ProjectDetail'
import Register from './features/auth/Register'
import ReportGenerator from './features/reporting/ReportGenerator'
import SeedLabeling from './features/pointannotation/SeedLabeling'
import VersionDetail from './features/datasets/VersionDetail'
import { AuthProvider } from './shared/lib/AuthContext'
import { ToastProvider } from './shared/lib/ToastProvider'

function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route element={<ProtectedRoute />}>
              <Route element={<Layout />}>
                <Route index element={<Home />} />
                <Route path="/events" element={<EventLog />} />
                <Route path="/report" element={<ReportGenerator />} />
                <Route path="/projects/:projectId" element={<ProjectDetail />} />
                <Route path="/sources/:sourceId" element={<DatasetDashboard />} />
                <Route path="/sources/:sourceId/versions/:id" element={<VersionDetail />} />
                <Route path="/sources/:sourceId/diff" element={<DatasetDiff />} />
                <Route path="/sources/:sourceId/models" element={<ModelRegistry />} />
                <Route path="/sources/:sourceId/models/:id" element={<ModelDetail />} />
                <Route path="/sources/:sourceId/annotation/jobs/:id/review" element={<AnnotationReview />} />
                <Route path="/sources/:sourceId/annotation/jobs/:id/label" element={<SeedLabeling />} />
                <Route path="/sources/:sourceId/events" element={<EventLog />} />
                <Route path="/sources/:sourceId/ontology" element={<OntologyChanges />} />
                <Route path="/sources/:sourceId/ontology/:changeId" element={<OntologyChangeReview />} />
                <Route path="*" element={<ComingSoon />} />
              </Route>
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ToastProvider>
  )
}

export default App
