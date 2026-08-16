import { api } from './client'
import type { AddMemberRequest, ProjectMember } from '../types/member'

export async function fetchProjectMembers(datasetId: number): Promise<ProjectMember[]> {
  const { data } = await api.get<ProjectMember[]>(`/datasets/${datasetId}/members`)
  return data
}

export async function addProjectMember(datasetId: number, request: AddMemberRequest): Promise<ProjectMember> {
  const { data } = await api.post<ProjectMember>(`/datasets/${datasetId}/members`, request)
  return data
}

export async function removeProjectMember(datasetId: number, userId: number): Promise<void> {
  await api.delete(`/datasets/${datasetId}/members/${userId}`)
}
