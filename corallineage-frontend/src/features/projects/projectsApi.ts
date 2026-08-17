import { api } from '../../shared/api/client'
import type { CreateProjectRequest, Project } from '../projects/projectsTypes'

export async function fetchProjects(): Promise<Project[]> {
  const { data } = await api.get<Project[]>('/projects')
  return data
}

export async function fetchProject(id: number): Promise<Project> {
  const { data } = await api.get<Project>(`/projects/${id}`)
  return data
}

export async function createProject(request: CreateProjectRequest): Promise<Project> {
  const { data } = await api.post<Project>('/projects', request)
  return data
}
