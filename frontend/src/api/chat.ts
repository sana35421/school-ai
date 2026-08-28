import request from './request'

export interface Conversation {
  conversationId: string
  title: string
  summary?: string
  createdAt: string
  lastActiveAt: string
  messageCount?: number
}

export interface Attachment {
  id: number
  fileName: string
  fileType: string
  fileSize: number
  status: string
}

export interface SourceItem {
  documentName: string
  score: number
  content: string
}

export interface Message {
  id?: number
  role: 'user' | 'assistant'
  content: string
  createdAt?: string
  sources?: SourceItem[]
  attachments?: Attachment[]
}

interface ChatSendPayload {
  query: string
  conversationId?: string
  attachmentIds?: number[]
}

export interface ChatSendResult {
  answer: string
  conversationId: string
  sources?: SourceItem[]
}

export const getHistory = (days = 7) =>
  request.get<Conversation[], Conversation[]>(`/chat/history?days=${days}`)

export const getMessages = (conversationId: string) =>
  request.get<Message[], Message[]>(`/chat/messages/${conversationId}`)

export const deleteConversation = (conversationId: string) =>
  request.delete(`/chat/${conversationId}`)

export const sendMessageStream = (
  payload: ChatSendPayload,
  onAccepted: () => void,
  onChunk: (text: string) => void,
  onSources: (sources: SourceItem[]) => void,
  onDone: (conversationId: string) => void,
  onError: (err: Error) => void
) => {
  const token = localStorage.getItem('token') || ''
  const sseBaseURL = import.meta.env.VITE_API_BASE || '/api'

  fetch(`${sseBaseURL}/chat/send`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(payload),
  })
    .then(async (response) => {
      if (!response.ok || !response.body) {
        const errorText = await response.text()
        throw new Error(errorText || `HTTP ${response.status}`)
      }
      onAccepted()
      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      let conversationId = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (!line.startsWith('data:')) continue
          const eventPayload = line.slice(5).trim()
          if (!eventPayload) continue
          let evt: any
          try {
            evt = JSON.parse(eventPayload)
          } catch {
            continue
          }
          if (evt.type === 'message' && evt.answer) {
            onChunk(evt.answer)
          } else if (evt.type === 'sources' && Array.isArray(evt.sources)) {
            onSources(evt.sources)
          } else if (evt.type === 'done') {
            conversationId = evt.conversation_id || conversationId
          } else if (evt.type === 'error') {
            throw new Error(evt.message || '流式错误')
          }
        }
      }
      onDone(conversationId)
    })
    .catch((err) => onError(err))
}
