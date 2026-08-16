import type { ProjectRole } from './member'

export interface Project {
  id: number
  projectName: string
  description: string | null
  createdBy: string
  createdAt: string
  sourceCount: number
  callerRole: ProjectRole | null
}

export interface CreateProjectRequest {
  projectName: string
  description?: string | null
}
