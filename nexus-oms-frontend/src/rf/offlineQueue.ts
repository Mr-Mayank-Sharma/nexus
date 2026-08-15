export interface OfflineAction {
  id: string
  type: string
  payload: Record<string, unknown>
  createdAt: string
}

const STORAGE_KEY = 'nexus_rf_offline_queue'
const QUEUE_EVENT = 'nexus:rf-queue-changed'

function loadActions(): OfflineAction[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? (parsed as OfflineAction[]) : []
  } catch {
    return []
  }
}

function persistActions(actions: OfflineAction[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(actions))
  } catch {
    // storage full/unavailable — keep queue in memory for the session
  }
  window.dispatchEvent(new CustomEvent(QUEUE_EVENT))
}

function newId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

export function enqueueAction(type: string, payload: Record<string, unknown>): OfflineAction {
  const action: OfflineAction = {
    id: newId(),
    type,
    payload,
    createdAt: new Date().toISOString(),
  }
  const next = [...loadActions(), action]
  persistActions(next)
  return action
}

export function pendingCount(): number {
  return loadActions().length
}

export function listActions(): OfflineAction[] {
  return loadActions()
}

export function clearQueue(): void {
  persistActions([])
}

export async function flushQueue(
  executor: (action: OfflineAction) => Promise<void>,
): Promise<{ replayed: number; remaining: number }> {
  let replayed = 0
  const actions = loadActions()
  if (actions.length === 0) {
    return { replayed: 0, remaining: 0 }
  }
  for (const action of actions) {
    try {
      await executor(action)
      replayed += 1
      persistActions(loadActions().filter((a) => a.id !== action.id))
    } catch {
      // Stop flushing on first failure — remaining actions stay queued for the next retry.
      break
    }
  }
  return { replayed, remaining: pendingCount() }
}

export function subscribeQueue(listener: () => void): () => void {
  const handler = () => listener()
  window.addEventListener(QUEUE_EVENT, handler)
  return () => window.removeEventListener(QUEUE_EVENT, handler)
}
