import { api } from '../../shared/api/client'
import type {
  DirtyAnnotation,
  OntologyChange,
  RecordOntologyChangeRequest,
  ResolveDirtyAnnotationRequest,
} from '../ontology/ontologyTypes'
import type { Page } from '../datasets/versionsTypes'

export async function fetchOntologyChanges(
  datasetId: number,
  page: number,
  size: number,
  datasetVersionId?: number,
): Promise<Page<OntologyChange>> {
  const { data } = await api.get<Page<OntologyChange>>('/ontology/changes', {
    params: { datasetId, page, size, datasetVersionId },
  })
  return data
}

export async function recordOntologyChange(request: RecordOntologyChangeRequest): Promise<OntologyChange> {
  const { data } = await api.post<OntologyChange>('/ontology/changes', request)
  return data
}

export async function fetchDirtyAnnotations(
  page: number,
  size: number,
  ontologyChangeId?: number,
  imageId?: number,
): Promise<Page<DirtyAnnotation>> {
  const { data } = await api.get<Page<DirtyAnnotation>>('/ontology/dirty', {
    params: { page, size, ontologyChangeId, imageId },
  })
  return data
}

export async function resolveDirtyAnnotation(
  id: number,
  request: ResolveDirtyAnnotationRequest,
): Promise<DirtyAnnotation> {
  const { data } = await api.patch<DirtyAnnotation>(`/ontology/dirty/${id}/resolve`, request)
  return data
}
