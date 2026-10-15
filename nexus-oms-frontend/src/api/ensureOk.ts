/**
 * Many API wrappers turn a failed request into `{ success: false, error }` instead of throwing, so a caller that
 * only wraps the call in try/catch announces success for a request that failed. Pass the result through this
 * before reporting anything: it throws the wrapper's own message when the call did not succeed.
 */
export function ensureOk<T>(result: T): T {
  const r = result as { success?: boolean; error?: string; message?: string } | null | undefined
  if (r && r.success === false) throw new Error(r.error ?? r.message ?? 'The request failed')
  return result
}
