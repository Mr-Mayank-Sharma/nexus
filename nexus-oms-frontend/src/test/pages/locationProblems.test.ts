import { describe, expect, it } from 'vitest'
import { locationProblems } from '../../pages/LocationsPage'

const base = { code: 'DC-EAST', name: 'East DC', type: 'WAREHOUSE' }

describe('locationProblems', () => {
  it('accepts a complete location', () => {
    expect(locationProblems({ ...base, latitude: 40.7, longitude: -74 }, [])).toEqual([])
  })
  it('asks for a code and a name', () => {
    expect(locationProblems({ code: ' ', name: '' }, [])).toHaveLength(2)
  })
  it('refuses a code already used by another location, ignoring case', () => {
    expect(locationProblems(base, [{ id: 'x', code: 'dc-east', name: 'Other' }])).toContain('The code DC-EAST is already used.')
  })
  it('lets a location keep its own code when edited', () => {
    expect(locationProblems({ ...base, id: 'x' }, [{ id: 'x', code: 'DC-EAST', name: 'East DC' }])).toEqual([])
  })
  it('needs both coordinates and keeps them in range', () => {
    expect(locationProblems({ ...base, latitude: 10 }, [])).toContain('Enter both latitude and longitude, or neither.')
    expect(locationProblems({ ...base, latitude: 95, longitude: 0 }, [])).toContain('Latitude must be between -90 and 90.')
  })
})
