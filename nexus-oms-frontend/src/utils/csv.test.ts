import { describe, expect, it } from 'vitest'
import { toCsv } from './csv'

describe('toCsv', () => {
  it('quotes every cell and escapes embedded quotes', () => {
    expect(toCsv(['a', 'b'], [['x "y"', 3], [null, undefined]])).toBe('"a","b"\r\n"x ""y""","3"\r\n"",""')
  })
})
