import client from './client'

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
}

export interface ChatRequest {
  systemPrompt?: string
  messages: ChatMessage[]
}

export async function sendChatMessage(request: ChatRequest): Promise<string> {
  try {
    const { data } = await client.post('/ai/chat', request)
    return data.data.content
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to send chat message'
    return { success: false, error: msg } as any
  }
}
