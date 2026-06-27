import { useState, useRef, useEffect } from 'react'
import { X, MessageSquare, Send, Bot, Sparkles, Brain, Loader2, ChevronRight } from 'lucide-react'
import { clsx } from 'clsx'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
  suggestions?: string[]
}

const quickActions = [
  { label: 'Search order', query: 'Find order #' },
  { label: 'Inventory check', query: 'Check inventory for ' },
  { label: 'Sales report', query: 'Show sales report for this month' },
  { label: 'Stock alert', query: 'Any low stock items?' },
  { label: 'Top customers', query: 'Who are my top 10 customers?' },
  { label: 'Order issues', query: 'Any failed orders today?' },
]

interface Props {
  open: boolean
  onClose: () => void
}

export default function AIAssistantPanel({ open, onClose }: Props) {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'welcome',
      role: 'assistant',
      content: "Hi! I'm your AI Operations Assistant. I can help you find orders, check inventory, generate reports, and more. Try one of the suggestions below or ask me anything!",
      timestamp: new Date(),
      suggestions: quickActions.map(a => a.label),
    },
  ])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  useEffect(() => {
    if (open) setTimeout(() => inputRef.current?.focus(), 100)
  }, [open])

  const sendMessage = (text: string) => {
    if (!text.trim() || loading) return

    const userMsg: Message = {
      id: Date.now().toString(),
      role: 'user',
      content: text,
      timestamp: new Date(),
    }
    setMessages(prev => [...prev, userMsg])
    setInput('')
    setLoading(true)

    setTimeout(() => {
      const responses = [
        "I've checked the system. Here's what I found: Order OMS-2024-5821 is currently being processed and is expected to ship within 2 hours.",
        "Sure! Inventory levels look healthy. Top-moving SKU is NEXUS-PRO-X1 with 1,247 units in stock across 3 warehouses.",
        "Based on current data, sales are up 18% this month compared to last. Your top-performing channel is Shopify.",
        "I've identified 3 orders that require attention due to payment verification delays. Would you like me to list them?",
        "The warehouse is operating at 72% capacity. I recommend redistributing inventory from Warehouse A (88% full) to Warehouse C (45% full).",
      ]
      const aiMsg: Message = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: responses[Math.floor(Math.random() * responses.length)],
        timestamp: new Date(),
      }
      setMessages(prev => [...prev, aiMsg])
      setLoading(false)
    }, 1500)
  }

  if (!open) return null

  return (
    <>
      <div className="fixed inset-0 bg-black/20 z-40 lg:hidden" onClick={onClose} />
      <div className={clsx(
        'fixed right-0 top-0 h-full bg-[var(--bg-card)] border-l border-[var(--border-color)] shadow-xl z-50 flex flex-col',
        'w-[var(--ai-panel-width)] animate-[slideInRight_250ms_ease-out]'
      )}>
        <div className="flex items-center justify-between px-4 h-14 border-b border-[var(--border-color)] shrink-0">
          <div className="flex items-center gap-2">
            <Brain className="w-5 h-5 text-[var(--color-primary-500)]" />
            <span className="font-semibold text-sm text-[var(--text-primary)]">AI Assistant</span>
            <span className="text-[10px] bg-[var(--color-primary-50)] text-[var(--color-primary-700)] px-1.5 py-0.5 rounded-full font-medium">BETA</span>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-md hover:bg-[var(--bg-tertiary)] text-[var(--text-secondary)]">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {messages.map(msg => (
            <div key={msg.id} className={clsx('flex gap-3', msg.role === 'user' ? 'justify-end' : 'justify-start')}>
              {msg.role === 'assistant' && (
                <div className="w-8 h-8 rounded-full bg-[var(--color-primary-100)] flex items-center justify-center shrink-0">
                  <Bot className="w-4 h-4 text-[var(--color-primary-600)]" />
                </div>
              )}
              <div className={clsx(
                'max-w-[85%] rounded-xl px-4 py-2.5 text-sm',
                msg.role === 'user'
                  ? 'bg-[var(--color-primary-500)] text-white rounded-tr-sm'
                  : 'bg-[var(--bg-tertiary)] text-[var(--text-primary)] rounded-tl-sm'
              )}>
                <p className="whitespace-pre-wrap">{msg.content}</p>
                {msg.suggestions && (
                  <div className="flex flex-wrap gap-1.5 mt-2 pt-2 border-t border-[var(--border-color)]">
                    {msg.suggestions.map(s => (
                      <button key={s}
                        className="text-xs px-2 py-1 rounded-md bg-[var(--bg-card)] border border-[var(--border-color)] hover:bg-[var(--color-primary-50)] hover:border-[var(--color-primary-200)] transition-colors"
                        onClick={() => sendMessage(s)}
                      >{s}</button>
                    ))}
                  </div>
                )}
              </div>
              {msg.role === 'user' && (
                <div className="w-8 h-8 rounded-full bg-[var(--color-primary-600)] flex items-center justify-center shrink-0">
                  <span className="text-xs font-medium text-white">U</span>
                </div>
              )}
            </div>
          ))}
          {loading && (
            <div className="flex gap-3">
              <div className="w-8 h-8 rounded-full bg-[var(--color-primary-100)] flex items-center justify-center shrink-0">
                <Bot className="w-4 h-4 text-[var(--color-primary-600)]" />
              </div>
              <div className="bg-[var(--bg-tertiary)] rounded-xl rounded-tl-sm px-4 py-3">
                <Loader2 className="w-4 h-4 animate-spin text-[var(--text-tertiary)]" />
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        <div className="px-4 py-2 border-t border-[var(--border-color)] bg-[var(--bg-secondary)]">
          <div className="flex gap-1.5 overflow-x-auto pb-1 scrollbar-hide">
            {quickActions.slice(0, 4).map(a => (
              <button key={a.label}
                className="flex items-center gap-1 text-xs px-2.5 py-1.5 rounded-md bg-[var(--bg-card)] border border-[var(--border-color)] whitespace-nowrap hover:bg-[var(--color-primary-50)] transition-colors text-[var(--text-secondary)]"
                onClick={() => sendMessage(a.query)}
              >
                <Sparkles className="w-3 h-3" />
                {a.label}
              </button>
            ))}
          </div>
        </div>

        <div className="p-4 border-t border-[var(--border-color)] shrink-0">
          <div className="flex items-center gap-2 bg-[var(--bg-secondary)] rounded-xl border border-[var(--border-color)] px-3 py-2 focus-within:border-[var(--color-primary-400)] focus-within:ring-2 focus-within:ring-[var(--color-primary-100)] transition-all">
            <input ref={inputRef}
              className="flex-1 bg-transparent border-none outline-none text-sm text-[var(--text-primary)] placeholder:text-[var(--text-tertiary)]"
              placeholder="Ask AI anything..."
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && sendMessage(input)}
            />
            <button
              className={clsx('p-1.5 rounded-lg transition-colors',
                input.trim() ? 'text-[var(--color-primary-500)] hover:bg-[var(--color-primary-50)]' : 'text-[var(--text-tertiary)]'
              )}
              onClick={() => sendMessage(input)}
              disabled={!input.trim() || loading}
            >
              <Send className="w-4 h-4" />
            </button>
          </div>
          <p className="text-[10px] text-[var(--text-tertiary)] mt-1.5 text-center">
            AI responses are simulated. May include inaccuracies.
          </p>
        </div>
      </div>
    </>
  )
}
