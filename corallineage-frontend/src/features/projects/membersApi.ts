import { api } from '../../shared/api/client'
import type { AddMemberRequest, ProjectMember, SourceAccess } from '../projects/membersTypes'

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

export async function fetchMemberSourceAccess(projectId: number, userId: number): Promise<SourceAccess> {
  const { data } = await api.get<SourceAccess>(`/projects/${projectId}/members/${userId}/source-access`)
  return data
}

export async function setMemberSourceAccess(
  projectId: number,
  userId: number,
  sourceIds: number[],
): Promise<SourceAccess> {
  const { data } = await api.put<SourceAccess>(`/projects/${projectId}/members/${userId}/source-access`, { sourceIds })
  return data
}
