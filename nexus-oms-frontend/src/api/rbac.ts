import client from './client'
import { ApiResponse, RolePermission, UserRoleAssignment, Team } from '../types'
export type { RolePermission, UserRoleAssignment, Team }

export async function getPermissions(page: number, size: number): Promise<ApiResponse<{ content: RolePermission[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/rbac/permissions', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch permissions')
  }
}

export async function getPermissionsByRole(role: string): Promise<ApiResponse<RolePermission[]>> {
  try {
    const { data } = await client.get('/rbac/permissions/role', { params: { role } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch permissions by role')
  }
}

export async function setPermission(permissionData: Record<string, any>): Promise<ApiResponse<RolePermission>> {
  try {
    const { data } = await client.post('/rbac/permissions', permissionData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to set permission')
  }
}

export async function getUserRoles(page: number, size: number): Promise<ApiResponse<{ content: UserRole[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/rbac/user-roles', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch user roles')
  }
}

export async function assignRole(roleData: Record<string, any>): Promise<ApiResponse<UserRoleAssignment>> {
  try {
    const { data } = await client.post('/rbac/user-roles', roleData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to assign role')
  }
}

export async function removeRole(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.delete(`/rbac/user-roles/${id}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to remove role')
  }
}

export async function getTeams(page: number, size: number): Promise<ApiResponse<{ content: Team[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/rbac/teams', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch teams')
  }
}

export async function createTeam(teamData: Record<string, any>): Promise<ApiResponse<Team>> {
  try {
    const { data } = await client.post('/rbac/teams', teamData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create team')
  }
}

export async function checkPermission(role: string, resource: string, action: string): Promise<ApiResponse<{ granted: boolean }>> {
  try {
    const { data } = await client.get('/rbac/check-permission', { params: { role, resource, action } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to check permission')
  }
}
