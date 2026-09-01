import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeftIcon, InboxIcon, UserPlusIcon } from '@heroicons/react/24/outline'
import { fetchProject } from '../projects/projectsApi'
import { fetchDatasets } from '../datasets/datasetsApi'
import { fetchProjectMembers, removeProjectMember } from '../projects/membersApi'
import { errorMessage } from '../../shared/api/client'
import { formatCount, formatDate } from '../../shared/lib/format'
import { useAuthContext } from '../../shared/lib/authContextCore'
import CreateDatasetModal from '../datasets/CreateDatasetModal'
import AddCollaboratorModal from '../projects/AddCollaboratorModal'
import ManageAccessModal from '../projects/ManageAccessModal'
import ConfirmDialog from '../projects/ConfirmDialog'
import RowMenu from '../projects/RowMenu'
import TitledCard from '../../shared/components/TitledCard'
import type { Project } from '../projects/projectsTypes'
import type { Dataset } from '../datasets/datasetsTypes'
import type { ProjectMember } from '../projects/membersTypes'

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

  const [showAddCollaborator, setShowAddCollaborator] = useState(false)
  const [manageAccessTarget, setManageAccessTarget] = useState<ProjectMember | null>(null)
  const [removeTarget, setRemoveTarget] = useState<ProjectMember | null>(null)
  const [removing, setRemoving] = useState(false)

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

  async function confirmRemove() {
    if (!removeTarget) return
    setRemoving(true)
    setMembersError(null)
    try {
      await removeProjectMember(projectId, removeTarget.userId)
      setRemoveTarget(null)
      loadMembers()
    } catch (err) {
      setMembersError(errorMessage(err))
    } finally {
      setRemoving(false)
    }
  }

  return (
    <>
      <div className="content">
        <div className="page-back-row">
          <button
            onClick={() => navigate('/')}
            className="btn btn-secondary"
            style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
          >
            <ArrowLeftIcon style={{ width: 14, height: 14 }} />
            All Projects
          </button>
        </div>

        {error && (
          <div className="alert alert-error" style={{ marginBottom: 16 }}>
            {error}
          </div>
        )}

        {loading ? (
          <div className="loading-state">
            <span className="spinner" /> Loading project...
          </div>
        ) : (
          <>
          {project && (
            <div className="page-title-row">
              <div className="page-title">{project.projectName}</div>
              {project.description && (
                <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 4 }}>{project.description}</div>
              )}
            </div>
          )}
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 32, alignItems: 'start' }}>
            {/* Sources: 2/3 width, list-row layout matching the Home projects list */}
            <TitledCard
              title="Sources"
              actions={
                <button className="btn btn-primary" onClick={() => setShowCreate(true)}>
                  + New Source
                </button>
              }
            >
              {sources.length === 0 ? (
                <div className="empty-state">
                  <div className="empty-icon">
                    <InboxIcon style={{ width: 32, height: 32 }} />
                  </div>
                  <p>No sources yet. Create your first source to get started.</p>
                </div>
              ) : (
                <div className="list-rows">
                  {sources.map((d) => (
                    <div key={d.id} className="list-row clickable" onClick={() => navigate(`/sources/${d.id}`)}>
                      <div className="list-row-main">
                        <div className="list-row-name">{d.datasetName}</div>
                        <div className="list-row-desc">{d.description || 'No description'}</div>
                      </div>
                      <div className="list-row-meta">
                        <div className="list-row-owner mono">{d.latestVersionTag ?? 'no versions'}</div>
                        <div className="list-row-date">
                          {formatCount(d.versionCount)} version{d.versionCount === 1 ? '' : 's'}
                        </div>
                        <div className="list-row-sub">Created {formatDate(d.createdAt)}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </TitledCard>

            {/* Collaborators: 1/3 width, to the right */}
            <TitledCard
              title="Collaborators"
              actions={
                isOwner && (
                  <button
                    className="btn btn-primary btn-sm"
                    onClick={() => setShowAddCollaborator(true)}
                    style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
                  >
                    <UserPlusIcon style={{ width: 13, height: 13 }} />
                    Add
                  </button>
                )
              }
            >
              {membersError && (
                <div className="alert alert-error" style={{ marginBottom: 12 }}>
                  {membersError}
                </div>
              )}
              {membersLoading ? (
                <div className="loading-state">
                  <span className="spinner" /> Loading collaborators...
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  {members.map((member) => (
                    <div
                      key={member.userId}
                      style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 }}
                    >
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontSize: 13, fontWeight: 500 }}>{member.displayName}</div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
                          {member.role === 'OWNER'
                            ? 'Owner'
                            : member.sourceAccessRestricted
                              ? 'Collaborator, limited access'
                              : 'Collaborator, full access'}
                        </div>
                      </div>
                      {isOwner && member.role !== 'OWNER' && (
                        <RowMenu
                          items={[
                            { label: 'Manage access', onClick: () => setManageAccessTarget(member) },
                            { label: 'Remove', danger: true, onClick: () => setRemoveTarget(member) },
                          ]}
                        />
                      )}
                    </div>
                  ))}
                </div>
              )}
            </TitledCard>
          </div>
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

      <AddCollaboratorModal
        open={showAddCollaborator}
        projectId={projectId}
        sources={sources}
        onClose={() => setShowAddCollaborator(false)}
        onAdded={() => {
          setShowAddCollaborator(false)
          loadMembers()
        }}
      />

      <ManageAccessModal
        open={manageAccessTarget != null}
        projectId={projectId}
        member={manageAccessTarget}
        sources={sources}
        onClose={() => setManageAccessTarget(null)}
        onSaved={() => {
          setManageAccessTarget(null)
          loadMembers()
        }}
      />

      <ConfirmDialog
        open={removeTarget != null}
        title="Remove collaborator"
        message={`Remove ${removeTarget?.displayName ?? ''} from this project? They will lose access to every Source in it.`}
        confirmLabel="Remove"
        danger
        busy={removing}
        onConfirm={confirmRemove}
        onCancel={() => setRemoveTarget(null)}
      />
    </>
  )
}
