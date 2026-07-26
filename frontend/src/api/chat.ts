import request from './request'

export interface Conversation {
  conversationId: string
  title: string
  createdAt: string
  lastActiveAt: string
  messageCount?: number
}

export interface Message {
  id?: number
  role: 'user' | 'assistant'
  content: string
  createdAt?: string
}

export interface ChatSendPayload {
  query: string
  conversationId?: string
  attachmentIds?: number[]
}

export interface ChatSendResult {
  answer: string
  conversationId: string
  sources?: Array<{
    documentName: string
    score: number
    content: string
  }>
}

export const getHistory = (days = 7) =>
  request.get<Conversation[], Conversation[]>(`/chat/history?days=${days}`)

export const getMessages = (conversationId: string) =>
  request.get<Message[], Message[]>(`/chat/messages/${conversationId}`)

export const deleteConversation = (conversationId: string) =>
  request.delete(`/chat/${conversationId}`)

/**
 * 流式对话（SSE）
 */
export const sendMessageStream = (
  payload: ChatSendPayload,
  onChunk: (text: string) => void,
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
        throw new Error(`HTTP ${response.status}`)
      }
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
          const payload = line.slice(5).trim()
          if (!payload) continue
          try {
            const evt = JSON.parse(payload)
            if (evt.type === 'message' && evt.answer) {
              onChunk(evt.answer)
            } else if (evt.type === 'done') {
              conversationId = evt.conversation_id || conversationId
            } else if (evt.type === 'error') {
              throw new Error(evt.message || '流式错误')
            }
          } catch (e) {
            // 忽略非 JSON 行
          }
        }
      }
      onDone(conversationId)
    })
    .catch((err) => onError(err))
}