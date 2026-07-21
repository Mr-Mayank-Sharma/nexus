import { useState, useEffect } from 'react'
import PermissionGate from '../components/rbac/PermissionGate'
import Autocomplete from '../components/common/Autocomplete'
import { UserCog, Users, Shield, Plus, Search, X, Check, Trash2, Lock, ChevronDown, ChevronRight } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import * as rbacApi from '../api/rbac'
import { ROLE_WORKSPACES } from '../hooks/useWorkspace'
import StatusBadge from '../components/common/StatusBadge'

const roles = Object.keys(ROLE_WORKSPACES)

interface UserRoleItem {
  id: string
  userId: string
  userName: string
  userEmail: string
  role: string
  team: string
  department: string
  isActive: boolean
  grantedBy: string
  grantedAt: string
}

interface TeamItem {
  id: string
  name: string
  description: string
  managerId: string
  isActive: boolean
  createdAt: string
}

interface PermissionItem {
  id: string
  role: string
  permissionGroup: string
  permissionName: string
  canView: boolean
  canCreate: boolean
  canEdit: boolean
  canDelete: boolean
  canApprove: boolean
}

interface SecurityGroup {
  id: string
  name: string
  description: string
  parentGroupId?: string
  permissions: string[]
  memberCount: number
  isInherited: boolean
}

type Tab = 'user-roles' | 'teams' | 'security-groups'

export default function UsersPage() {
  const [tab, setTab] = useState<Tab>('user-roles')
  const [userRoles, setUserRoles] = useState<UserRoleItem[]>([])
  const [teams, setTeams] = useState<TeamItem[]>([])
  const [permissions, setPermissions] = useState<PermissionItem[]>([])
  const [loading, setLoading] = useState(true)
  const [showAssignModal, setShowAssignModal] = useState(false)
  const [showCreateTeamModal, setShowCreateTeamModal] = useState(false)
  const [showPermissions, setShowPermissions] = useState(false)
  const [saving, setSaving] = useState(false)
  const [search, setSearch] = useState('')
  const [expandedGroup, setExpandedGroup] = useState<string | null>(null)
  const { addToast } = useToast()

  const [roleForm, setRoleForm] = useState({ userId: '', role: 'VIEWER', team: '', department: '', isActive: true })
  const [teamForm, setTeamForm] = useState({ name: '', description: '', managerId: '' })
  const [permForm, setPermForm] = useState({ role: '', permissionGroup: '', permissionName: '', canView: false, canCreate: false, canEdit: false, canDelete: false, canApprove: false })

  useEffect(() => {
    if (tab === 'user-roles') fetchUserRoles()
    else if (tab === 'teams') fetchTeams()
  }, [tab])

  useEffect(() => { fetchPermissions() }, [])

  async function fetchUserRoles() {
    try {
      setLoading(true)
      const res = await rbacApi.getUserRoles(0, 100)
      const mapped: UserRoleItem[] = (res.data?.content ?? []).map((u: any) => ({
        id: u.id,
        userId: u.userId,
        userName: u.userName || u.userEmail || u.userId,
        userEmail: u.userEmail || '',
        role: u.role,
        team: u.team || '',
        department: u.department || '',
        isActive: u.isActive !== false,
        grantedBy: u.grantedBy || '',
        grantedAt: u.grantedAt || u.createdAt || '',
      }))
      setUserRoles(mapped)
    } catch {
      addToast({ type: 'error', title: 'Failed to load user roles' })
    } finally {
      setLoading(false)
    }
  }

  async function fetchTeams() {
    try {
      setLoading(true)
      const res = await rbacApi.getTeams(0, 100)
      const mapped: TeamItem[] = (res.data?.content ?? []).map((t: any) => ({
        id: t.id,
        name: t.name,
        description: t.description || '',
        managerId: t.managerId || '',
        isActive: t.isActive !== false,
        createdAt: t.createdAt || '',
      }))
      setTeams(mapped)
    } catch {
      addToast({ type: 'error', title: 'Failed to load teams' })
    } finally {
      setLoading(false)
    }
  }

  async function fetchPermissions() {
    try {
      const res = await rbacApi.getPermissions(0, 200)
      const mapped: PermissionItem[] = (res.data?.content ?? []).map((p: any) => ({
        id: p.id,
        role: p.role || p.resource?.split(':')[0] || '',
        permissionGroup: p.resource || '',
        permissionName: p.action || p.permissionName || '',
        canView: p.action === 'READ' || p.action === 'ALL' || p.canView,
        canCreate: p.action === 'CREATE' || p.action === 'ALL' || p.canCreate,
        canEdit: p.action === 'UPDATE' || p.action === 'ALL' || p.canEdit,
        canDelete: p.action === 'DELETE' || p.action === 'ALL' || p.canDelete,
        canApprove: p.canApprove || false,
      }))
      setPermissions(mapped)
    } catch {
      addToast({ type: 'error', title: 'Failed to load permissions' })
    }
  }

  async function handleAssignRole() {
    if (!roleForm.userId.trim() || !roleForm.role) { addToast({ type: 'warning', title: 'User ID and role are required' }); return }
    setSaving(true)
    try {
      await rbacApi.assignRole(roleForm)
      addToast({ type: 'success', title: 'Role assigned' })
      setShowAssignModal(false)
      await fetchUserRoles()
    } catch {
      addToast({ type: 'error', title: 'Failed to assign role' })
    } finally {
      setSaving(false)
    }
  }

  async function handleRemoveRole(id: string) {
    try {
      await rbacApi.removeRole(id)
      addToast({ type: 'success', title: 'Role removed' })
      await fetchUserRoles()
    } catch {
      addToast({ type: 'error', title: 'Failed to remove role' })
    }
  }

  async function handleCreateTeam() {
    if (!teamForm.name.trim()) { addToast({ type: 'warning', title: 'Team name is required' }); return }
    setSaving(true)
    try {
      await rbacApi.createTeam(teamForm)
      addToast({ type: 'success', title: 'Team created' })
      setShowCreateTeamModal(false)
      await fetchTeams()
    } catch {
      addToast({ type: 'error', title: 'Failed to create team' })
    } finally {
      setSaving(false)
    }
  }

  async function handleToggleTeam(team: TeamItem) {
    addToast({ type: 'warning', title: 'Team update API not available' })
  }

  async function handleSetPermission() {
    if (!permForm.role || !permForm.permissionGroup) { addToast({ type: 'warning', title: 'Role and permission group are required' }); return }
    setSaving(true)
    try {
      await rbacApi.setPermission(permForm)
      addToast({ type: 'success', title: 'Permission set' })
      setPermForm({ role: '', permissionGroup: '', permissionName: '', canView: false, canCreate: false, canEdit: false, canDelete: false, canApprove: false })
      await fetchPermissions()
    } catch {
      addToast({ type: 'error', title: 'Failed to set permission' })
    } finally {
      setSaving(false)
    }
  }

  const filteredRoles = userRoles.filter(r =>
    !search || r.userName.toLowerCase().includes(search.toLowerCase()) || r.userId.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><Users className="w-6 h-6" />Users & Roles</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Manage user roles, teams, and permissions</p>
        </div>
      </div>

      <div className="flex gap-1 border-b border-[var(--border-default)]">
        <button
          onClick={() => setTab('user-roles')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'user-roles' ? 'border-[var(--nexus-primary-600)] text-[var(--text-brand)]' : 'border-transparent text-[var(--text-secondary)] hover:text-[var(--text-secondary)]'
          }`}
        >
          <UserCog className="w-4 h-4 inline mr-1.5" /> User Roles
        </button>
        <button
          onClick={() => setTab('teams')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'teams' ? 'border-[var(--nexus-primary-600)] text-[var(--text-brand)]' : 'border-transparent text-[var(--text-secondary)] hover:text-[var(--text-secondary)]'
          }`}
        >
          <Users className="w-4 h-4 inline mr-1.5" /> Teams
        </button>
        <button
          onClick={() => setTab('security-groups')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'security-groups' ? 'border-[var(--nexus-primary-600)] text-[var(--text-brand)]' : 'border-transparent text-[var(--text-secondary)] hover:text-[var(--text-secondary)]'
          }`}
        >
          <Lock className="w-4 h-4 inline mr-1.5" /> Security Groups
        </button>
      </div>

      {tab === 'user-roles' && (
        <>
          <div className="flex items-center justify-between">
            <Autocomplete value={search} onChange={setSearch} placeholder="Search by user name or ID..." minChars={0} className="flex-1 max-w-xs" />
            <PermissionGate resource="users" action="create">
              <button onClick={() => { setRoleForm({ userId: '', role: 'VIEWER', team: '', department: '', isActive: true }); setShowAssignModal(true) }} className="btn-primary text-sm">
                <Plus className="w-4 h-4" /> Assign Role
              </button>
            </PermissionGate>
          </div>

          {loading ? (
            <div className="flex items-center justify-center h-64">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" />
            </div>
          ) : filteredRoles.length === 0 ? (
            <div className="text-center py-16 card">
              <Shield className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
              <p className="text-[var(--text-secondary)] text-sm">No user roles found.</p>
            </div>
          ) : (
            <div className="card overflow-hidden">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-[var(--border-default)] bg-[var(--surface-sunken)]/50">
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">User ID</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Role</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Team</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Department</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Status</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Assigned At</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {filteredRoles.map(r => (
                    <tr key={r.id} className="hover:bg-[var(--surface-sunken)]">
                      <td className="px-4 py-3">
                        <div className="text-sm font-medium text-[var(--text-primary)]">{r.userName}</div>
                        <div className="text-xs text-[var(--text-secondary)] font-mono">{r.userId}</div>
                      </td>
                      <td className="px-4 py-3">
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)]">
                          {r.role.replace(/_/g, ' ')}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{r.team || '-'}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{r.department || '-'}</td>
                      <td className="px-4 py-3">
                        <StatusBadge status={r.isActive ? 'ACTIVE' : 'INACTIVE'} size="sm" />
                      </td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{r.grantedAt ? new Date(r.grantedAt).toLocaleDateString() : '-'}</td>
                      <td className="px-4 py-3 text-right">
                        <PermissionGate resource="users" action="delete">
                          <button
                            onClick={() => handleRemoveRole(r.id)}
                            className="p-1.5 hover:bg-[var(--nexus-error-50)] rounded text-[var(--text-secondary)] hover:text-[var(--nexus-error-600)]"
                            title="Remove role"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </PermissionGate>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {tab === 'teams' && (
        <>
          <div className="flex items-center justify-end">
            <PermissionGate resource="users" action="create">
              <button onClick={() => { setTeamForm({ name: '', description: '', managerId: '' }); setShowCreateTeamModal(true) }} className="btn-primary text-sm">
                <Plus className="w-4 h-4" /> Create Team
              </button>
            </PermissionGate>
          </div>

          {loading ? (
            <div className="flex items-center justify-center h-64">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" />
            </div>
          ) : teams.length === 0 ? (
            <div className="text-center py-16 card">
              <Users className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
              <p className="text-[var(--text-secondary)] text-sm">No teams created yet.</p>
            </div>
          ) : (
            <div className="card overflow-hidden">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-[var(--border-default)] bg-[var(--surface-sunken)]/50">
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Name</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Description</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Manager</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Status</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Created</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {teams.map(t => (
                    <tr key={t.id} className="hover:bg-[var(--surface-sunken)]">
                      <td className="px-4 py-3 text-sm font-medium text-[var(--text-primary)]">{t.name}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)] max-w-[240px] truncate">{t.description || '-'}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)] font-mono">{t.managerId?.slice(0, 12) || '-'}</td>
                      <td className="px-4 py-3">
                        <StatusBadge status={t.isActive ? 'ACTIVE' : 'INACTIVE'} size="sm" />
                      </td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{t.createdAt ? new Date(t.createdAt).toLocaleDateString() : '-'}</td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => handleToggleTeam(t)}
                          className={`p-1.5 rounded text-[var(--text-secondary)] hover:text-[var(--text-secondary)] ${t.isActive ? 'hover:bg-orange-50' : 'hover:bg-[var(--nexus-success-50)]'}`}
                          title={t.isActive ? 'Deactivate' : 'Activate'}
                        >
                          {t.isActive ? <X className="w-4 h-4" /> : <Check className="w-4 h-4 text-[var(--nexus-success-600)]" />}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {tab === 'security-groups' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <p className="text-sm text-[var(--text-secondary)]">OFBiz-style security groups define permissions for users and roles</p>
            <PermissionGate resource="users" action="create"><button className="btn-primary text-sm"><Plus className="w-4 h-4" /> Create Group</button></PermissionGate>
          </div>

          {[
            {
              name: 'PICKING_ADMIN', description: 'Full picking operations management', memberCount: 3,
              permissions: ['picking:ALL', 'inventory:READ', 'orders:READ'],
              children: [
                { name: 'PICKING_OPERATOR', description: 'Picking execution', memberCount: 8, permissions: ['picking:EXECUTE', 'inventory:READ'] },
                { name: 'PICKING_VIEWER', description: 'View picking status', memberCount: 5, permissions: ['picking:READ'] },
              ],
            },
            {
              name: 'WAREHOUSE_OPERATIONS', description: 'Warehouse management', memberCount: 6,
              permissions: ['warehouse:ALL', 'inventory:ALL', 'receiving:ALL', 'cycle-count:EXECUTE'],
              children: [
                { name: 'RECEIVING_CLERK', description: 'Receive inbound shipments', memberCount: 4, permissions: ['receiving:EXECUTE', 'inventory:READ'] },
                { name: 'CYCLE_COUNTER', description: 'Perform cycle counts', memberCount: 3, permissions: ['cycle-count:EXECUTE', 'inventory:READ'] },
              ],
            },
            {
              name: 'PACKING_ADMIN', description: 'Packing operations', memberCount: 2,
              permissions: ['packing:ALL', 'shipping:READ'],
              children: [
                { name: 'PACKER', description: 'Pack orders', memberCount: 12, permissions: ['packing:EXECUTE'] },
                { name: 'PACKING_QC', description: 'Quality check packages', memberCount: 2, permissions: ['packing:QC', 'packing:READ'] },
              ],
            },
            {
              name: 'SHIPPING_ADMIN', description: 'Loading & dispatch management', memberCount: 2,
              permissions: ['shipping:ALL', 'carriers:ALL'],
              children: [
                { name: 'LOADER', description: 'Load & dispatch', memberCount: 6, permissions: ['shipping:LOAD', 'shipping:DISPATCH'] },
                { name: 'SHIPPING_CLERK', description: 'Shipping documentation', memberCount: 3, permissions: ['shipping:CREATE', 'carriers:READ'] },
              ],
            },
            {
              name: 'BOPIS_OPERATIONS', description: 'BOPIS fulfillment', memberCount: 4,
              permissions: ['bopis:ALL', 'orders:READ', 'inventory:READ'],
              children: [
                { name: 'BOPIS_PICKER', description: 'Pick BOPIS orders', memberCount: 3, permissions: ['bopis:PICK', 'inventory:READ'] },
                { name: 'BOPIS_HOST', description: 'Customer handoff', memberCount: 2, permissions: ['bopis:HANDOFF'] },
              ],
            },
          ].map(group => (
            <div key={group.name} className="card overflow-hidden">
              <button
                onClick={() => setExpandedGroup(expandedGroup === group.name ? null : group.name)}
                className="w-full flex items-center justify-between p-4 hover:bg-[var(--surface-sunken)] transition-colors"
              >
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-[var(--nexus-primary-50)] flex items-center justify-center">
                    <Lock className="w-5 h-5 text-[var(--nexus-primary-600)]" />
                  </div>
                  <div className="text-left">
                    <p className="text-sm font-semibold text-[var(--text-primary)]">{group.name}</p>
                    <p className="text-xs text-[var(--text-secondary)]">{group.description} · {group.memberCount} members</p>
                  </div>
                </div>
                {expandedGroup === group.name ? <ChevronDown className="w-4 h-4 text-[var(--text-tertiary)]" /> : <ChevronRight className="w-4 h-4 text-[var(--text-tertiary)]" />}
              </button>

              {expandedGroup === group.name && (
                <div className="border-t border-[var(--border-subtle)]">
                  <div className="px-4 py-3 bg-[var(--surface-sunken)]/50">
                    <p className="text-xs font-semibold text-[var(--text-secondary)] uppercase mb-2">Granted Permissions</p>
                    <div className="flex flex-wrap gap-1.5">
                      {group.permissions.map(p => (
                        <span key={p} className="px-2 py-0.5 text-[10px] font-medium bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)] rounded">{p}</span>
                      ))}
                    </div>
                  </div>

                  {group.children.map(child => (
                    <div key={child.name} className="px-4 py-3 border-t border-[var(--border-subtle)] hover:bg-[var(--surface-sunken)]">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <div className="w-2 h-2 rounded-full bg-[var(--nexus-primary-400)]" />
                          <div>
                            <p className="text-sm font-medium text-[var(--text-primary)]">{child.name}</p>
                            <p className="text-xs text-[var(--text-secondary)]">{child.description} · {child.memberCount} members</p>
                          </div>
                        </div>
                        <button className="text-xs text-[var(--text-brand)] hover:text-[var(--nexus-primary-800)] font-medium">Assign</button>
                      </div>
                      <div className="flex flex-wrap gap-1.5 mt-2 ml-5">
                        {child.permissions.map(p => (
                          <span key={p} className="px-2 py-0.5 text-[10px] font-medium bg-[var(--surface-muted)] text-[var(--text-secondary)] rounded">{p}</span>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      <div className="card">
        <button
          onClick={() => setShowPermissions(!showPermissions)}
          className="flex items-center justify-between w-full p-4 text-left"
        >
          <div className="flex items-center gap-2">
            <Shield className="w-5 h-5 text-[var(--text-tertiary)]" />
            <span className="text-sm font-semibold text-[var(--text-primary)]">Permissions</span>
            <span className="text-xs text-[var(--text-secondary)]">({permissions.length} entries)</span>
          </div>
          <svg className={`w-4 h-4 text-[var(--text-tertiary)] transition-transform ${showPermissions ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </button>
        {showPermissions && (
          <div className="p-4 border-t border-[var(--border-subtle)] space-y-4">
            {permissions.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-[var(--border-default)] bg-[var(--surface-sunken)]/50">
                      <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Role</th>
                      <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Permission Group</th>
                      <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Permission Name</th>
                      <th className="px-3 py-2 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">View</th>
                      <th className="px-3 py-2 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Create</th>
                      <th className="px-3 py-2 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Edit</th>
                      <th className="px-3 py-2 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Delete</th>
                      <th className="px-3 py-2 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Approve</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {permissions.map(p => (
                      <tr key={p.id} className="hover:bg-[var(--surface-sunken)]">
                        <td className="px-3 py-2 text-[var(--text-secondary)] font-medium">{p.role}</td>
                        <td className="px-3 py-2 text-[var(--text-secondary)]">{p.permissionGroup}</td>
                        <td className="px-3 py-2 text-[var(--text-secondary)]">{p.permissionName}</td>
                        <td className="px-3 py-2 text-center">{p.canView ? <Check className="w-4 h-4 text-[var(--nexus-success-500)] inline" /> : <span className="text-[var(--text-tertiary)]">&mdash;</span>}</td>
                        <td className="px-3 py-2 text-center">{p.canCreate ? <Check className="w-4 h-4 text-[var(--nexus-success-500)] inline" /> : <span className="text-[var(--text-tertiary)]">&mdash;</span>}</td>
                        <td className="px-3 py-2 text-center">{p.canEdit ? <Check className="w-4 h-4 text-[var(--nexus-success-500)] inline" /> : <span className="text-[var(--text-tertiary)]">&mdash;</span>}</td>
                        <td className="px-3 py-2 text-center">{p.canDelete ? <Check className="w-4 h-4 text-[var(--nexus-success-500)] inline" /> : <span className="text-[var(--text-tertiary)]">&mdash;</span>}</td>
                        <td className="px-3 py-2 text-center">{p.canApprove ? <Check className="w-4 h-4 text-[var(--nexus-success-500)] inline" /> : <span className="text-[var(--text-tertiary)]">&mdash;</span>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <div className="border-t border-[var(--border-subtle)] pt-4">
              <h4 className="text-sm font-semibold text-[var(--text-secondary)] mb-3">Set Permission</h4>
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Role</label>
                  <select value={permForm.role} onChange={e => setPermForm({ ...permForm, role: e.target.value })} className="input w-full">
                    <option value="">Select role</option>
                    {roles.map(r => <option key={r} value={r}>{r.replace(/_/g, ' ')}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Permission Group</label>
                  <input value={permForm.permissionGroup} onChange={e => setPermForm({ ...permForm, permissionGroup: e.target.value })} className="input w-full" placeholder="e.g. orders" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Permission Name</label>
                  <input value={permForm.permissionName} onChange={e => setPermForm({ ...permForm, permissionName: e.target.value })} className="input w-full" placeholder="e.g. manage_orders" />
                </div>
              </div>
              <div className="flex items-center gap-4 mt-3">
                {(['canView', 'canCreate', 'canEdit', 'canDelete', 'canApprove'] as const).map(perm => (
                  <label key={perm} className="flex items-center gap-1.5 text-xs text-[var(--text-secondary)] cursor-pointer">
                    <input
                      type="checkbox"
                      checked={permForm[perm]}
                      onChange={e => setPermForm({ ...permForm, [perm]: e.target.checked })}
                      className="rounded border-[var(--border-default)] text-[var(--text-brand)] focus:ring-[var(--nexus-primary-500)]"
                    />
                    {perm.replace('can', 'Can ')}
                  </label>
                ))}
                <PermissionGate resource="users" action="edit">
                  <button onClick={handleSetPermission} disabled={saving} className="btn-primary text-xs ml-auto">
                    {saving ? <div className="animate-spin rounded-full h-3.5 w-3.5 border-b-2 border-white" /> : <Shield className="w-3.5 h-3.5" />}
                    Set Permission
                  </button>
                </PermissionGate>
              </div>
            </div>
          </div>
        )}
      </div>

      {showAssignModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-md">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Assign Role</h2>
              <button onClick={() => setShowAssignModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">User ID</label>
                <input value={roleForm.userId} onChange={e => setRoleForm({ ...roleForm, userId: e.target.value })} className="input w-full" placeholder="User ID" />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Role</label>
                <select value={roleForm.role} onChange={e => setRoleForm({ ...roleForm, role: e.target.value })} className="input w-full">
                  {roles.map(r => <option key={r} value={r}>{r.replace(/_/g, ' ')}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Team</label>
                <input value={roleForm.team} onChange={e => setRoleForm({ ...roleForm, team: e.target.value })} className="input w-full" placeholder="Team name" />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Department</label>
                <input value={roleForm.department} onChange={e => setRoleForm({ ...roleForm, department: e.target.value })} className="input w-full" placeholder="Department" />
              </div>
              <label className="flex items-center gap-2 text-sm text-[var(--text-secondary)] cursor-pointer">
                <input
                  type="checkbox"
                  checked={roleForm.isActive}
                  onChange={e => setRoleForm({ ...roleForm, isActive: e.target.checked })}
                  className="rounded border-[var(--border-default)] text-[var(--text-brand)] focus:ring-[var(--nexus-primary-500)]"
                />
                Active
              </label>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowAssignModal(false)} className="btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="users" action="create">
                <button onClick={handleAssignRole} disabled={saving} className="btn-primary text-sm">
                  {saving ? <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white" /> : <Shield className="w-4 h-4" />}
                  Assign
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}

      {showCreateTeamModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-md">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Create Team</h2>
              <button onClick={() => setShowCreateTeamModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Team Name</label>
                <input value={teamForm.name} onChange={e => setTeamForm({ ...teamForm, name: e.target.value })} className="input w-full" placeholder="Team name" />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Description</label>
                <textarea value={teamForm.description} onChange={e => setTeamForm({ ...teamForm, description: e.target.value })} className="input w-full" rows={2} placeholder="Team description" />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Manager ID</label>
                <input value={teamForm.managerId} onChange={e => setTeamForm({ ...teamForm, managerId: e.target.value })} className="input w-full" placeholder="Manager user ID" />
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowCreateTeamModal(false)} className="btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="users" action="create">
                <button onClick={handleCreateTeam} disabled={saving} className="btn-primary text-sm">
                  {saving ? <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white" /> : <Users className="w-4 h-4" />}
                  Create
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
