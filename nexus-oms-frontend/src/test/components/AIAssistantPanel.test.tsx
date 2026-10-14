import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import AIAssistantPanel from '../../components/enterprise/AIAssistantPanel'
import * as chat from '../../api/chatApi'

vi.mock('../../api/chatApi', () => ({ sendChatMessage: vi.fn() }))
const send = vi.mocked(chat.sendChatMessage)

async function ask(text: string) {
  render(<AIAssistantPanel open onClose={() => {}} />)
  const box = screen.getByRole('textbox')
  fireEvent.change(box, { target: { value: text } })
  fireEvent.keyDown(box, { key: 'Enter' })
}

describe('AIAssistantPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Element.prototype.scrollIntoView = vi.fn()   // not implemented by jsdom
  })

  it('shows the model\'s answer as text, not as an object', async () => {
    send.mockResolvedValue({ success: true, content: 'Two SKUs are below their reorder point.' })
    await ask('what is low?')
    expect(await screen.findByText('Two SKUs are below their reorder point.')).toBeInTheDocument()
    expect(screen.queryByText(/\[object Object\]/)).toBeNull()
  })

  it('tells the user why when the assistant is unavailable, instead of a generic apology', async () => {
    send.mockResolvedValue({ success: false, error: 'AI Service error: The AI assistant is not available: no model is configured (set nexus.ai.openai.api-key)' })
    await ask('hello')
    expect(await screen.findByText(/no model is configured/)).toBeInTheDocument()
  })

  it('still copes with a thrown network error', async () => {
    send.mockRejectedValue(new Error('network'))
    await ask('hello')
    await waitFor(() => expect(screen.getByText(/couldn't process your request/i)).toBeInTheDocument())
  })
})
