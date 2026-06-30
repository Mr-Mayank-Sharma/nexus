import { useState, useEffect } from 'react'
import { UserCog, Users, Shield, Plus, Search, X, Check, Trash2 } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import * as rbacApi from '../api/rbac'
import StatusBadge from '../components/common/StatusBadge'

const roles = ['ADMIN', 'OPS', 'WAREHOUSE', 'VIEWER', 'SALES_EXEC', 'CUSTOMER_SUPPORT', 'WAREHOUSE_OPERATOR', 'WAREHOUSE_MANAGER', 'PROCUREMENT_MANAGER', 'FINANCE', 'LOGISTICS_MANAGER', 'CEO']

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

type Tab = 'user-roles' | 'teams'

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
  const { addToast } = useToast()

  const [roleForm, setRoleForm] = useState({ userId: '', role: 'VIEWER', team: '', department: '', isActive: true })
  const [teamForm, setTeamForm] = useState({ name: '', description: '', managerId: '' })
  const [permForm, setPermForm] = useState({ role: '', permissionGroup: '', permissionName: '', canView: false, canCreate: false, canEdit: false, canDelete: false, canApprove: false })

  useEffect(() => {
    if (tab === 'user-roles') fetchUserRoles()
    else fetchTeams()
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
          <h1 className="text-2xl font-bold text-gray-900">Users & Roles</h1>
          <p className="text-sm text-gray-500 mt-1">Manage user roles, teams, and permissions</p>
        </div>
      </div>

      <div className="flex gap-1 border-b border-gray-200">
        <button
          onClick={() => setTab('user-roles')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'user-roles' ? 'border-primary-600 text-primary-600' : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <UserCog className="w-4 h-4 inline mr-1.5" /> User Roles
        </button>
        <button
          onClick={() => setTab('teams')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'teams' ? 'border-primary-600 text-primary-600' : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <Users className="w-4 h-4 inline mr-1.5" /> Teams
        </button>
      </div>

      {tab === 'user-roles' && (
        <>
          <div className="flex items-center justify-between">
            <div className="relative flex-1 max-w-xs">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
              <input value={search} onChange={e => setSearch(e.target.value)} className="input w-full pl-9" placeholder="Search by user name or ID..." />
            </div>
            <button onClick={() => { setRoleForm({ userId: '', role: 'VIEWER', team: '', department: '', isActive: true }); setShowAssignModal(true) }} className="btn-primary text-sm">
              <Plus className="w-4 h-4" /> Assign Role
            </button>
          </div>

          {loading ? (
            <div className="flex items-center justify-center h-64">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
            </div>
          ) : filteredRoles.length === 0 ? (
            <div className="text-center py-16 card">
              <Shield className="w-12 h-12 mx-auto mb-3 text-gray-300" />
              <p className="text-gray-500 text-sm">No user roles found.</p>
            </div>
          ) : (
            <div className="card overflow-hidden">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-gray-200 bg-gray-50/50">
                    <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">User ID</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Role</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Team</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Department</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Status</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Assigned At</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {filteredRoles.map(r => (
                    <tr key={r.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3">
                        <div className="text-sm font-medium text-gray-900">{r.userName}</div>
                        <div className="text-xs text-gray-500 font-mono">{r.userId}</div>
                      </td>
                      <td className="px-4 py-3">
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-indigo-100 text-indigo-700">
                          {r.role.replace(/_/g, ' ')}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-600">{r.team || '-'}</td>
                      <td className="px-4 py-3 text-sm text-gray-600">{r.department || '-'}</td>
                      <td className="px-4 py-3">
                        <StatusBadge status={r.isActive ? 'ACTIVE' : 'INACTIVE'} size="sm" />
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-600">{r.grantedAt ? new Date(r.grantedAt).toLocaleDateString() : '-'}</td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => handleRemoveRole(r.id)}
                          className="p-1.5 hover:bg-red-50 rounded text-gray-500 hover:text-red-600"
                          title="Remove role"
                        >
                          <Trash2 className="w-4 h-4" />
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

      {tab === 'teams' && (
        <>
          <div className="flex items-center justify-end">
            <button onClick={() => { setTeamForm({ name: '', description: '', managerId: '' }); setShowCreateTeamModal(true) }} className="btn-primary text-sm">
              <Plus className="w-4 h-4" /> Create Team
            </button>
          </div>

          {loading ? (
            <div className="flex items-center justify-center h-64">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
            </div>
          ) : teams.length === 0 ? (
            <div className="text-center py-16 card">
              <Users className="w-12 h-12 mx-auto mb-3 text-gray-300" />
              <p className="text-gray-500 text-sm">No teams created yet.</p>
            </div>
          ) : (
            <div className="card overflow-hidden">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-gray-200 bg-gray-50/50">
                    <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Name</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Description</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Manager</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Status</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Created</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {teams.map(t => (
                    <tr key={t.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3 text-sm font-medium text-gray-900">{t.name}</td>
                      <td className="px-4 py-3 text-sm text-gray-600 max-w-[240px] truncate">{t.description || '-'}</td>
                      <td className="px-4 py-3 text-sm text-gray-600 font-mono">{t.managerId?.slice(0, 12) || '-'}</td>
                      <td className="px-4 py-3">
                        <StatusBadge status={t.isActive ? 'ACTIVE' : 'INACTIVE'} size="sm" />
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-600">{t.createdAt ? new Date(t.createdAt).toLocaleDateString() : '-'}</td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => handleToggleTeam(t)}
                          className={`p-1.5 rounded text-gray-500 hover:text-gray-700 ${t.isActive ? 'hover:bg-orange-50' : 'hover:bg-green-50'}`}
                          title={t.isActive ? 'Deactivate' : 'Activate'}
                        >
                          {t.isActive ? <X className="w-4 h-4" /> : <Check className="w-4 h-4 text-green-600" />}
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

      <div className="card">
        <button
          onClick={() => setShowPermissions(!showPermissions)}
          className="flex items-center justify-between w-full p-4 text-left"
        >
          <div className="flex items-center gap-2">
            <Shield className="w-5 h-5 text-gray-400" />
            <span className="text-sm font-semibold text-gray-900">Permissions</span>
            <span className="text-xs text-gray-500">({permissions.length} entries)</span>
          </div>
          <svg className={`w-4 h-4 text-gray-400 transition-transform ${showPermissions ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </button>
        {showPermissions && (
          <div className="p-4 border-t border-gray-100 space-y-4">
            {permissions.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200 bg-gray-50/50">
                      <th className="px-3 py-2 text-left text-xs font-semibold text-gray-500 uppercase">Role</th>
                      <th className="px-3 py-2 text-left text-xs font-semibold text-gray-500 uppercase">Permission Group</th>
                      <th className="px-3 py-2 text-left text-xs font-semibold text-gray-500 uppercase">Permission Name</th>
                      <th className="px-3 py-2 text-center text-xs font-semibold text-gray-500 uppercase">View</th>
                      <th className="px-3 py-2 text-center text-xs font-semibold text-gray-500 uppercase">Create</th>
                      <th className="px-3 py-2 text-center text-xs font-semibold text-gray-500 uppercase">Edit</th>
                      <th className="px-3 py-2 text-center text-xs font-semibold text-gray-500 uppercase">Delete</th>
                      <th className="px-3 py-2 text-center text-xs font-semibold text-gray-500 uppercase">Approve</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {permissions.map(p => (
                      <tr key={p.id} className="hover:bg-gray-50">
                        <td className="px-3 py-2 text-gray-700 font-medium">{p.role}</td>
                        <td className="px-3 py-2 text-gray-600">{p.permissionGroup}</td>
                        <td className="px-3 py-2 text-gray-600">{p.permissionName}</td>
                        <td className="px-3 py-2 text-center">{p.canView ? <Check className="w-4 h-4 text-green-500 inline" /> : <span className="text-gray-300">&mdash;</span>}</td>
                        <td className="px-3 py-2 text-center">{p.canCreate ? <Check className="w-4 h-4 text-green-500 inline" /> : <span className="text-gray-300">&mdash;</span>}</td>
                        <td className="px-3 py-2 text-center">{p.canEdit ? <Check className="w-4 h-4 text-green-500 inline" /> : <span className="text-gray-300">&mdash;</span>}</td>
                        <td className="px-3 py-2 text-center">{p.canDelete ? <Check className="w-4 h-4 text-green-500 inline" /> : <span className="text-gray-300">&mdash;</span>}</td>
                        <td className="px-3 py-2 text-center">{p.canApprove ? <Check className="w-4 h-4 text-green-500 inline" /> : <span className="text-gray-300">&mdash;</span>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <div className="border-t border-gray-100 pt-4">
              <h4 className="text-sm font-semibold text-gray-700 mb-3">Set Permission</h4>
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Role</label>
                  <select value={permForm.role} onChange={e => setPermForm({ ...permForm, role: e.target.value })} className="input w-full">
                    <option value="">Select role</option>
                    {roles.map(r => <option key={r} value={r}>{r.replace(/_/g, ' ')}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Permission Group</label>
                  <input value={permForm.permissionGroup} onChange={e => setPermForm({ ...permForm, permissionGroup: e.target.value })} className="input w-full" placeholder="e.g. orders" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Permission Name</label>
                  <input value={permForm.permissionName} onChange={e => setPermForm({ ...permForm, permissionName: e.target.value })} className="input w-full" placeholder="e.g. manage_orders" />
                </div>
              </div>
              <div className="flex items-center gap-4 mt-3">
                {(['canView', 'canCreate', 'canEdit', 'canDelete', 'canApprove'] as const).map(perm => (
                  <label key={perm} className="flex items-center gap-1.5 text-xs text-gray-700 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={permForm[perm]}
                      onChange={e => setPermForm({ ...permForm, [perm]: e.target.checked })}
                      className="rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                    />
                    {perm.replace('can', 'Can ')}
                  </label>
                ))}
                <button onClick={handleSetPermission} disabled={saving} className="btn-primary text-xs ml-auto">
                  {saving ? <div className="animate-spin rounded-full h-3.5 w-3.5 border-b-2 border-white" /> : <Shield className="w-3.5 h-3.5" />}
                  Set Permission
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {showAssignModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Assign Role</h2>
              <button onClick={() => setShowAssignModal(false)} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">User ID</label>
                <input value={roleForm.userId} onChange={e => setRoleForm({ ...roleForm, userId: e.target.value })} className="input w-full" placeholder="User ID" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Role</label>
                <select value={roleForm.role} onChange={e => setRoleForm({ ...roleForm, role: e.target.value })} className="input w-full">
                  {roles.map(r => <option key={r} value={r}>{r.replace(/_/g, ' ')}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Team</label>
                <input value={roleForm.team} onChange={e => setRoleForm({ ...roleForm, team: e.target.value })} className="input w-full" placeholder="Team name" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Department</label>
                <input value={roleForm.department} onChange={e => setRoleForm({ ...roleForm, department: e.target.value })} className="input w-full" placeholder="Department" />
              </div>
              <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
                <input
                  type="checkbox"
                  checked={roleForm.isActive}
                  onChange={e => setRoleForm({ ...roleForm, isActive: e.target.checked })}
                  className="rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                />
                Active
              </label>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowAssignModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleAssignRole} disabled={saving} className="btn-primary text-sm">
                {saving ? <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white" /> : <Shield className="w-4 h-4" />}
                Assign
              </button>
            </div>
          </div>
        </div>
      )}

      {showCreateTeamModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Create Team</h2>
              <button onClick={() => setShowCreateTeamModal(false)} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Team Name</label>
                <input value={teamForm.name} onChange={e => setTeamForm({ ...teamForm, name: e.target.value })} className="input w-full" placeholder="Team name" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
                <textarea value={teamForm.description} onChange={e => setTeamForm({ ...teamForm, description: e.target.value })} className="input w-full" rows={2} placeholder="Team description" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Manager ID</label>
                <input value={teamForm.managerId} onChange={e => setTeamForm({ ...teamForm, managerId: e.target.value })} className="input w-full" placeholder="Manager user ID" />
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowCreateTeamModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleCreateTeam} disabled={saving} className="btn-primary text-sm">
                {saving ? <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white" /> : <Users className="w-4 h-4" />}
                Create
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
