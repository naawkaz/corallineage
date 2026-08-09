import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import ComingSoon from './pages/ComingSoon'
import Datasets from './pages/Datasets'
import VersionBrowser from './pages/VersionBrowser'
import VersionDetail from './pages/VersionDetail'
import { DatasetProvider } from './lib/DatasetContext'

function App() {
  return (
    <DatasetProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<Navigate to="/datasets" replace />} />
            <Route path="/datasets" element={<Datasets />} />
            <Route path="/versions" element={<VersionBrowser />} />
            <Route path="/versions/:id" element={<VersionDetail />} />
            <Route path="*" element={<ComingSoon />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </DatasetProvider>
  )
}

export default App
