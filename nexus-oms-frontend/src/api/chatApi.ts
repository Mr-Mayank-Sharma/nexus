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

export async function sendChatMessage(request: ChatRequest): Promise<ChatResponse> {
  try {
    const { data } = await client.post('/ai/chat', request)
    return { success: true, content: data.data.content }
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to send chat message'
    return { success: false, error: msg }
  }
}
