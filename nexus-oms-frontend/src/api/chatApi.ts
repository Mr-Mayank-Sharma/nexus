import client from './client'

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
}

export interface ChatRequest {
  systemPrompt?: string
  messages: ChatMessage[]
}

export interface ChatResponse {
  success: boolean
  content?: string
  error?: string
}

/**
 * Asks the core operations assistant (POST /core/ai/assistant, doc 25). It answers one question at a time from the
 * OMS's own tools, so only the latest user message is sent; the backend keeps its own system prompt.
 */
export async function sendChatMessage(request: ChatRequest): Promise<ChatResponse> {
  const latestQuestion = [...request.messages].reverse().find(message => message.role === 'user')?.content ?? ''
  try {
    const { data } = await client.post('/core/ai/assistant', { question: latestQuestion }, { timeout: 300_000 })
    const answer = data?.data
    const toolsUsed: string[] = answer?.toolsUsed ?? []
    const source = toolsUsed.length > 0 ? `\n\n— from: ${toolsUsed.join(', ')}` : ''
    return { success: true, content: `${answer?.answer ?? ''}${source}` }
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to send chat message'
    return { success: false, error: msg }
  }
}
