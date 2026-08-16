export type ProjectRole = 'OWNER' | 'COLLABORATOR'

export interface ProjectMember {
  userId: number
  username: string
  displayName: string
  role: ProjectRole
  addedAt: string
}

export interface AddMemberRequest {
  username: string
  role: ProjectRole
}
