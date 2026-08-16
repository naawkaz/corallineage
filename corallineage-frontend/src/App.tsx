import { BrowserRouter, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import ProtectedRoute from './components/ProtectedRoute'
import AnnotationJobs from './pages/AnnotationJobs'
import AnnotationReview from './pages/AnnotationReview'
import ComingSoon from './pages/ComingSoon'
import DatasetDashboard from './pages/DatasetDashboard'
import DatasetDiff from './pages/DatasetDiff'
import Home from './pages/Home'
import EventLog from './pages/EventLog'
import Import from './pages/Import'
import Login from './pages/Login'
import ModelDetail from './pages/ModelDetail'
import ModelRegistry from './pages/ModelRegistry'
import OntologyChanges from './pages/OntologyChanges'
import PointAnnotationJobs from './pages/PointAnnotationJobs'
import PointAnnotationReview from './pages/PointAnnotationReview'
import Register from './pages/Register'
import ReportGenerator from './pages/ReportGenerator'
import SeedLabeling from './pages/SeedLabeling'
import TrainingJobs from './pages/TrainingJobs'
import VersionBrowser from './pages/VersionBrowser'
import VersionDetail from './pages/VersionDetail'
import { AuthProvider } from './lib/AuthContext'

function App() {
  return (
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
              <Route path="/projects/:projectId" element={<DatasetDashboard />} />
              <Route path="/projects/:projectId/versions" element={<VersionBrowser />} />
              <Route path="/projects/:projectId/versions/:id" element={<VersionDetail />} />
              <Route path="/projects/:projectId/import" element={<Import />} />
              <Route path="/projects/:projectId/diff" element={<DatasetDiff />} />
              <Route path="/projects/:projectId/models" element={<ModelRegistry />} />
              <Route path="/projects/:projectId/models/:id" element={<ModelDetail />} />
              <Route path="/projects/:projectId/training" element={<TrainingJobs />} />
              <Route path="/projects/:projectId/annotation" element={<AnnotationJobs />} />
              <Route path="/projects/:projectId/annotation/jobs/:id/review" element={<AnnotationReview />} />
              <Route path="/projects/:projectId/point-annotation" element={<PointAnnotationJobs />} />
              <Route path="/projects/:projectId/point-annotation/jobs/:id/label" element={<SeedLabeling />} />
              <Route path="/projects/:projectId/point-annotation/jobs/:id/review" element={<PointAnnotationReview />} />
              <Route path="/projects/:projectId/events" element={<EventLog />} />
              <Route path="/projects/:projectId/ontology" element={<OntologyChanges />} />
              <Route path="*" element={<ComingSoon />} />
            </Route>
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}

export default App
