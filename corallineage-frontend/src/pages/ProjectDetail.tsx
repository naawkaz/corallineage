import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Shell, UserPlus, X } from 'lucide-react'
import { fetchProject } from '../api/projects'
import { fetchDatasets } from '../api/datasets'
import { addProjectMember, fetchProjectMembers, removeProjectMember } from '../api/members'
import { errorMessage } from '../api/client'
import { formatCount, formatDate } from '../lib/format'
import { useAuthContext } from '../lib/authContextCore'
import CreateDatasetModal from '../components/CreateDatasetModal'
import type { Project } from '../types/project'
import type { Dataset } from '../types/dataset'
import type { ProjectMember, ProjectRole } from '../types/member'

export default function ProjectDetail() {
  const navigate = useNavigate()
  const { projectId: projectIdParam } = useParams()
  const projectId = Number(projectIdParam)
  const { user } = useAuthContext()

  const [project, setProject] = useState<Project | null>(null)
  const [sources, setSources] = useState<Dataset[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)

  const [members, setMembers] = useState<ProjectMember[]>([])
  const [membersLoading, setMembersLoading] = useState(true)
  const [membersError, setMembersError] = useState<string | null>(null)
  const [newUsername, setNewUsername] = useState('')
  const [newRole, setNewRole] = useState<ProjectRole>('COLLABORATOR')
  const [addingMember, setAddingMember] = useState(false)
  const [addMemberError, setAddMemberError] = useState<string | null>(null)

  const currentMembership = members.find((m) => m.username === user?.username) ?? null
  const isOwner = currentMembership?.role === 'OWNER'

  function loadProjectAndSources() {
    if (!projectId) return
    setLoading(true)
    Promise.all([fetchProject(projectId), fetchDatasets(projectId)])
      .then(([projectRes, sourcesRes]) => {
        setProject(projectRes)
        setSources(sourcesRes)
        setError(null)
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }

  function loadMembers() {
    if (!projectId) return
    setMembersLoading(true)
    fetchProjectMembers(projectId)
      .then((result) => {
        setMembers(result)
        setMembersError(null)
      })
      .catch((err) => setMembersError(errorMessage(err)))
      .finally(() => setMembersLoading(false))
  }

  useEffect(() => {
    Promise.resolve().then(() => {
      loadProjectAndSources()
      loadMembers()
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId])

  async function handleAddMember() {
    if (!newUsername.trim()) return
    setAddingMember(true)
    setAddMemberError(null)
    try {
      await addProjectMember(projectId, { username: newUsername.trim(), role: newRole })
      setNewUsername('')
      setNewRole('COLLABORATOR')
      loadMembers()
    } catch (err) {
      setAddMemberError(errorMessage(err))
    } finally {
      setAddingMember(false)
    }
  }

  async function handleRemoveMember(userId: number) {
    setMembersError(null)
    try {
      await removeProjectMember(projectId, userId)
      loadMembers()
    } catch (err) {
      setMembersError(errorMessage(err))
    }
  }

  return (
    <>
      <div className="topbar">
        <div>
          <button
            onClick={() => navigate('/')}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
              fontSize: 12,
              color: 'var(--text-muted)',
              background: 'none',
              border: 'none',
              padding: 0,
              cursor: 'pointer',
              marginBottom: 6,
            }}
          >
            <ArrowLeft size={12} strokeWidth={1.5} />
            All Projects
          </button>
          <div className="topbar-title">{project ? project.projectName : 'Project'}</div>
          <div className="topbar-sub">
            {project ? project.description || 'No description' : 'Project overview'}
          </div>
        </div>
        <button className="btn btn-primary" onClick={() => setShowCreate(true)}>
          ＋ New Source
        </button>
      </div>

      <div className="content">
        {error && (
          <div className="alert alert-error" style={{ marginBottom: 16 }}>
            {error}
          </div>
        )}

        {loading ? (
          <div className="loading-state">
            <span className="spinner" /> Loading project…
          </div>
        ) : (
          <>
            <div className="section-header">
              <div className="section-title">Collaborators</div>
            </div>
            <div className="card" style={{ padding: 16, marginBottom: 24 }}>
              {membersError && (
                <div className="alert alert-error" style={{ marginBottom: 12 }}>
                  {membersError}
                </div>
              )}
              {membersLoading ? (
                <div className="loading-state">
                  <span className="spinner" /> Loading collaborators…
                </div>
              ) : (
                <>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: isOwner ? 16 : 0 }}>
                    {members.map((member) => (
                      <div
                        key={member.userId}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          gap: 12,
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <span style={{ fontSize: 13, fontWeight: 500 }}>{member.displayName}</span>
                          <span className="mono" style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                            {member.username}
                          </span>
                          <span className={`badge ${member.role === 'OWNER' ? 'badge-blue' : 'badge-gray'}`}>
                            {member.role === 'OWNER' ? 'Owner' : 'Collaborator'}
                          </span>
                        </div>
                        {isOwner && !(member.role === 'OWNER' && members.filter((m) => m.role === 'OWNER').length <= 1) && (
                          <button
                            className="btn btn-secondary btn-sm"
                            onClick={() => handleRemoveMember(member.userId)}
                            style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
                          >
                            <X size={12} strokeWidth={1.5} />
                            Remove
                          </button>
                        )}
                      </div>
                    ))}
                  </div>

                  {isOwner && (
                    <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', flexWrap: 'wrap' }}>
                      <div className="form-row" style={{ marginBottom: 0, flex: 1, minWidth: 160 }}>
                        <label className="form-label" htmlFor="newMemberUsername">
                          Add collaborator by username
                        </label>
                        <input
                          id="newMemberUsername"
                          className="form-input"
                          placeholder="username"
                          value={newUsername}
                          onChange={(e) => setNewUsername(e.target.value)}
                        />
                      </div>
                      <div className="form-row" style={{ marginBottom: 0 }}>
                        <label className="form-label" htmlFor="newMemberRole">
                          Role
                        </label>
                        <select
                          id="newMemberRole"
                          className="form-select"
                          value={newRole}
                          onChange={(e) => setNewRole(e.target.value as ProjectRole)}
                        >
                          <option value="COLLABORATOR">Collaborator</option>
                          <option value="OWNER">Owner</option>
                        </select>
                      </div>
                      <button
                        className="btn btn-primary"
                        onClick={handleAddMember}
                        disabled={addingMember || !newUsername.trim()}
                        style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
                      >
                        <UserPlus size={14} strokeWidth={1.5} />
                        {addingMember ? 'Adding…' : 'Add'}
                      </button>
                    </div>
                  )}
                  {addMemberError && (
                    <div className="alert alert-error" style={{ marginTop: 12 }}>
                      {addMemberError}
                    </div>
                  )}
                </>
              )}
            </div>

            <div className="section-header">
              <div className="section-title">Sources</div>
            </div>

            {sources.length === 0 ? (
              <div className="card">
                <div className="empty-state">
                  <div className="empty-icon">
                    <Shell size={32} strokeWidth={1.5} />
                  </div>
                  <p>No sources yet. Create your first source to get started.</p>
                </div>
              </div>
            ) : (
              <div className="grid-3" style={{ gap: 16 }}>
                {sources.map((d) => (
                  <div
                    key={d.id}
                    className="card"
                    style={{ padding: 16, cursor: 'pointer' }}
                    onClick={() => navigate(`/sources/${d.id}`)}
                  >
                    <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 4 }}>
                      {d.datasetName}
                    </div>
                    <div style={{ color: 'var(--text-secondary)', fontSize: 12, marginBottom: 12 }}>
                      {d.description || 'No description'}
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
                      <span className="mono">
                        {d.latestVersionTag ?? 'no versions'}
                      </span>
                      <span style={{ color: 'var(--text-muted)' }}>
                        {formatCount(d.versionCount)} version{d.versionCount === 1 ? '' : 's'}
                      </span>
                    </div>
                    <div style={{ color: 'var(--text-muted)', fontSize: 11, marginTop: 8 }}>
                      Created {formatDate(d.createdAt)} by {d.createdBy}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </div>

      <CreateDatasetModal
        open={showCreate}
        projectId={projectId}
        onClose={() => setShowCreate(false)}
        onCreated={(d) => {
          setShowCreate(false)
          loadProjectAndSources()
          navigate(`/sources/${d.id}`)
        }}
      />
    </>
  )
}
