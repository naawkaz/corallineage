import type { ProjectRole } from './membersTypes'

export interface Project {
  id: number
  projectName: string
  description: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
  sourceCount: number
  callerRole: ProjectRole | null
  ownerDisplayName: string | null
}

export interface CreateProjectRequest {
  projectName: string
  description?: string | null
}
