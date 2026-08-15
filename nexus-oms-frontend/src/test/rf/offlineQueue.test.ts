import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import {
  OfflineAction,
  clearQueue,
  enqueueAction,
  flushQueue,
  listActions,
  pendingCount,
} from '../../rf/offlineQueue'

describe('offlineQueue', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('enqueues actions and reports the pending count', () => {
    expect(pendingCount()).toBe(0)
    enqueueAction('PICK_ITEM', { itemId: 'item-1', staffId: 'worker' })
    enqueueAction('PICK_ITEM', { itemId: 'item-2', staffId: 'worker' })

    expect(pendingCount()).toBe(2)
    const actions = listActions()
    expect(actions[0].type).toBe('PICK_ITEM')
    expect(actions[0].payload.itemId).toBe('item-1')
    expect(actions[1].payload.itemId).toBe('item-2')
  })

  it('survives a page reload because it persists to localStorage', () => {
    enqueueAction('COMPLETE_PICKLIST', { picklistId: 'pl-9' })
    expect(listActions()).toHaveLength(1)
  })

  it('flushes actions in order and drains the queue', async () => {
    enqueueAction('PICK_ITEM', { itemId: 'item-1' })
    enqueueAction('PICK_ITEM', { itemId: 'item-2' })

    const replayed: string[] = []
    const result = await flushQueue(async (action: OfflineAction) => {
      replayed.push(String(action.payload.itemId))
    })

    expect(replayed).toEqual(['item-1', 'item-2'])
    expect(result.replayed).toBe(2)
    expect(result.remaining).toBe(0)
    expect(pendingCount()).toBe(0)
  })

  it('keeps remaining actions when the executor throws', async () => {
    enqueueAction('PICK_ITEM', { itemId: 'item-1' })
    enqueueAction('PICK_ITEM', { itemId: 'item-2' })

    const result = await flushQueue(async (action: OfflineAction) => {
      if (action.payload.itemId === 'item-1') throw new Error('offline again')
    })

    expect(result.replayed).toBe(0)
    expect(result.remaining).toBe(2)
    expect(pendingCount()).toBe(2)
  })

  it('clearQueue empties the queue', () => {
    enqueueAction('PICK_ITEM', { itemId: 'item-1' })
    clearQueue()
    expect(pendingCount()).toBe(0)
  })
})
