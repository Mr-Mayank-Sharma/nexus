import { readFileSync } from 'fs'
import { join } from 'path'
import { describe, expect, it } from 'vitest'
import { getModulesForRole, ROLE_WORKSPACES, SECTION_ORDER, type WorkspaceModule } from './useWorkspace'

const source = readFileSync(join(__dirname, 'useWorkspace.ts'), 'utf8')
const app = readFileSync(join(__dirname, '../App.tsx'), 'utf8')

const routes = new Set([...app.matchAll(/<Route\s+path="([^"]+)"/g)].map(m => '/' + m[1].replace(/^\//, '')))
routes.add('/')
const exists = (path: string) => routes.has(path) || [...routes].some(r => r.endsWith('/*') && (path === r.slice(0, -2) || path.startsWith(r.slice(0, -1))))

const adminGroups = getModulesForRole('ADMIN')
const children = adminGroups.flatMap(g => g.children ?? [])

describe('navigation', () => {
  it('every link goes to a page that exists', () => {
    const dead = children.filter(c => !exists(c.path)).map(c => `${c.id} -> ${c.path}`)
    expect(dead).toEqual([])
  })

  it('every group belongs to a known section, and groups come out in section order', () => {
    for (const g of adminGroups) expect(SECTION_ORDER, `${g.id} has section ${g.section}`).toContain(g.section)
    const ranks = adminGroups.map(g => SECTION_ORDER.indexOf(g.section!))
    expect(ranks).toEqual([...ranks].sort((a, b) => a - b))
  })

  it('ids are unique, so a role list can never switch on the wrong page', () => {
    const ids = [...adminGroups.map(g => g.id), ...children.map(c => c.id)]
    expect(ids.filter((id, i) => ids.indexOf(id) !== i)).toEqual([])
  })

  it('no two links in one group point at the same page', () => {
    for (const g of adminGroups) {
      const paths = (g.children ?? []).map(c => c.path)
      expect(paths.filter((p, i) => paths.indexOf(p) !== i), g.id).toEqual([])
    }
  })

  it('every id a role is granted exists (a typo would silently hide a page)', () => {
    const block = source.slice(source.indexOf('const roleModules'), source.indexOf('const allowedIds'))
    const known = new Set([...adminGroups.map(g => g.id), ...children.map(c => c.id)])
    const granted = [...block.matchAll(/'([a-z0-9-]+)'/g)].map(m => m[1]).filter(id => !Object.keys(ROLE_WORKSPACES).includes(id.toUpperCase().replace(/-/g, '_')))
    const unknown = [...new Set(granted)].filter(id => !known.has(id))
    expect(unknown).toEqual([])
  })

  it('integrations are in one place: Directory, Connections and the channel pages are all under Connect', () => {
    const connect = adminGroups.filter(g => g.section === 'Connect').map(g => g.label)
    expect(connect).toEqual(['Integrations', 'Sales Channels', 'Data Exchange'])
    const directory = children.find(c => c.id === 'integrations-directory')
    expect(directory?.path).toBe('/integrations/directory')
  })

  it('a store manager reaches the POS stock check; a picker does not see integrations', () => {
    const labels = (role: string) => getModulesForRole(role).flatMap((g: WorkspaceModule) => g.children ?? []).map(c => c.id)
    expect(labels('STORE_MANAGER')).toContain('store-operations')
    expect(labels('PICKER').filter(id => ['integration-hub', 'integrations-directory', 'edi'].includes(id))).toEqual([])
  })
})
