import { api } from './client'
import type { ImportFormat, ImportJob, LabelDeclaration } from '../types/import'

export async function submitImport(
  datasetId: number,
  files: File[],
  format: ImportFormat,
  versionTag: string,
  importedBy: string,
  notes: string,
  labels?: LabelDeclaration[],
): Promise<ImportJob> {
  const form = new FormData()
  for (const file of files) {
    form.append('files', file)
    form.append('relativePaths', (file.webkitRelativePath || file.name))
  }
  form.append('format', format)
  form.append('versionTag', versionTag)
  form.append('importedBy', importedBy)
  if (notes.trim() !== '') form.append('notes', notes)
  // Parallel arrays (one entry per declared label, category sent as an empty string rather than
  // omitted so array indices line up on the backend) since this endpoint is multipart form data,
  // not a JSON body.
  for (const label of labels ?? []) {
    form.append('labelCodes', label.labelCode)
    form.append('labelNames', label.labelName)
    form.append('labelCategories', label.labelCategory ?? '')
  }

  const { data } = await api.post<ImportJob>(`/datasets/${datasetId}/import`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

export async function fetchImportJob(datasetId: number, importJobId: number): Promise<ImportJob> {
  const { data } = await api.get<ImportJob>(`/datasets/${datasetId}/import/${importJobId}`)
  return data
}
