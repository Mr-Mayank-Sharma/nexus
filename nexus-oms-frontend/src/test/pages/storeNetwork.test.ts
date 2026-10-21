import { describe, expect, it } from 'vitest'
import { transferProblems, transferStage } from '../../pages/StoreNetworkPage'

const line = (statusId: string) => ({ inventoryTransferId: '1', productId: 'P', productName: 'P', quantity: 1, statusId })

describe('transferStage', () => {
  it('follows the least advanced open line', () => {
    expect(transferStage({ lines: [line('IXF_REQUESTED'), line('IXF_REQUESTED')] })).toBe('REQUESTED')
    expect(transferStage({ lines: [line('IXF_SCHEDULED')] })).toBe('APPROVED')
    expect(transferStage({ lines: [line('IXF_EN_ROUTE'), line('IXF_COMPLETE')] })).toBe('IN_TRANSIT')
    expect(transferStage({ lines: [line('IXF_COMPLETE'), line('IXF_RECEIVED_SHORT')] })).toBe('NEEDS_DECISION')
    expect(transferStage({ lines: [line('IXF_COMPLETE')] })).toBe('DONE')
    expect(transferStage({ lines: [line('IXF_CANCELLED')] })).toBe('CANCELLED')
  })
})

describe('transferProblems', () => {
  it('names every problem before sending', () => {
    expect(transferProblems('', '', [])).toEqual(['Choose where the stock comes from.', 'Choose where it goes.', 'Add at least one product.'])
    expect(transferProblems('A', 'A', [{ productId: 'p', label: 'Tee', quantity: 1, max: 5 }])).toEqual(['The two locations must be different.'])
    expect(transferProblems('A', 'B', [{ productId: 'p', label: 'Tee', quantity: 6, max: 5 }])).toEqual(['Tee: only 5 can be sold at the sending location.'])
    expect(transferProblems('A', 'B', [{ productId: 'p', label: 'Tee', quantity: 0, max: 5 }])).toEqual(['Tee: enter a quantity above zero.'])
    expect(transferProblems('A', 'B', [{ productId: 'p', label: 'Tee', quantity: 2, max: 5 }])).toEqual([])
  })
})
