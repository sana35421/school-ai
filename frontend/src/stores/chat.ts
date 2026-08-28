import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Attachment, Conversation, Message, SourceItem } from '@/api/chat'
import type { UploadRecord } from '@/api/upload'
import * as chatApi from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const currentConversationId = ref<string>('')
  const messages = ref<Message[]>([])
  const isStreaming = ref(false)
  const streamingText = ref('')
  const streamingSources = ref<SourceItem[]>([])

  async function loadHistory(days = 7) {
    conversations.value = await chatApi.getHistory(days)
    return conversations.value
  }

  async function loadMessages(conversationId: string) {
    currentConversationId.value = conversationId
    messages.value = await chatApi.getMessages(conversationId)
    return messages.value
  }

  function clearCurrent() {
    currentConversationId.value = ''
    messages.value = []
    streamingText.value = ''
    streamingSources.value = []
  }

  function reset() {
    conversations.value = []
    clearCurrent()
  }

  function startAssistantMessage() {
    streamingText.value = ''
    isStreaming.value = true
  }

  function appendStreamChunk(chunk: string) {
    streamingText.value += chunk
  }

  function finishAssistantMessage(conversationId: string) {
    if (streamingText.value) {
      messages.value.push({ role: 'assistant', content: streamingText.value, sources: streamingSources.value })
    }
    streamingText.value = ''
    streamingSources.value = []
    isStreaming.value = false
    if (conversationId && conversationId !== currentConversationId.value) {
      currentConversationId.value = conversationId
      loadHistory().catch(() => {})
    }
  }

  async function deleteConversation(conversationId: string) {
    await chatApi.deleteConversation(conversationId)
    conversations.value = conversations.value.filter(
      (c) => c.conversationId !== conversationId
    )
    if (currentConversationId.value === conversationId) {
      clearCurrent()
    }
  }

  async function sendMessage(
    query: string,
    attachmentIds: number[] = [],
    uploadedFiles: UploadRecord[] = [],
    onAccepted: () => void = () => {}
  ) {
    if (isStreaming.value || !query.trim()) return
    const attachments: Attachment[] = uploadedFiles.map(file => ({
      id: file.id,
      fileName: file.fileName,
      fileType: file.fileType,
      fileSize: file.fileSize,
      status: file.status,
    }))
    const userMessage: Message = { role: 'user', content: query, attachments }
    const userMessageIndex = messages.value.length
    messages.value.push(userMessage)
    startAssistantMessage()

    return new Promise<void>((resolve, reject) => {
      chatApi.sendMessageStream(
        { query, conversationId: currentConversationId.value, attachmentIds },
        onAccepted,
        appendStreamChunk,
        (sources) => { streamingSources.value = sources },
        (conversationId) => {
          finishAssistantMessage(conversationId)
          resolve()
        },
        (err) => {
          isStreaming.value = false
          streamingText.value = ''
          streamingSources.value = []
          if (messages.value[userMessageIndex] === userMessage) {
            messages.value.splice(userMessageIndex, 1)
          }
          reject(err)
        }
      )
    })
  }

  return {
    conversations,
    currentConversationId,
    messages,
    isStreaming,
    streamingText,
    loadHistory,
    loadMessages,
    clearCurrent,
    reset,
    sendMessage,
    deleteConversation,
  }
})
