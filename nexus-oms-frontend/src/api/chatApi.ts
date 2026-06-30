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
  const { data } = await client.post('/api/ai/chat', request)
  return data.data.content
}
