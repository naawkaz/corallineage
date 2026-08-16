import { api } from './client'
import type { AddMemberRequest, ProjectMember } from '../types/member'

export async function fetchProjectMembers(projectId: number): Promise<ProjectMember[]> {
  const { data } = await api.get<ProjectMember[]>(`/projects/${projectId}/members`)
  return data
}

export async function addProjectMember(projectId: number, request: AddMemberRequest): Promise<ProjectMember> {
  const { data } = await api.post<ProjectMember>(`/projects/${projectId}/members`, request)
  return data
}

export async function removeProjectMember(projectId: number, userId: number): Promise<void> {
  await api.delete(`/projects/${projectId}/members/${userId}`)
}
