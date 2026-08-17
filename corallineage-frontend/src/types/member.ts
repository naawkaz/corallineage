export type ProjectRole = 'OWNER' | 'COLLABORATOR'

export interface ProjectMember {
  userId: number
  username: string
  displayName: string
  role: ProjectRole
  addedAt: string
  sourceAccessRestricted: boolean
}

export interface AddMemberRequest {
  username: string
  role: ProjectRole
  sourceIds?: number[]
}

export interface SourceAccess {
  restricted: boolean
  sourceIds: number[]
}
