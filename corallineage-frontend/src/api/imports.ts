import { api } from './client'
import type { ImportFormat, ImportJob } from '../types/import'

export async function submitImport(
  datasetId: number,
  files: File[],
  format: ImportFormat,
  versionTag: string,
  importedBy: string,
  notes: string,
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

  const { data } = await api.post<ImportJob>(`/datasets/${datasetId}/import`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

export async function fetchImportJob(datasetId: number, importJobId: number): Promise<ImportJob> {
  const { data } = await api.get<ImportJob>(`/datasets/${datasetId}/import/${importJobId}`)
  return data
}
