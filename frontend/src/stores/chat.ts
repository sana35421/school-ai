import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Attachment, Conversation, Message, SourceItem, ChatProgress } from '@/api/chat'
import type { UploadRecord } from '@/api/upload'
import * as chatApi from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const historyDays = 30
  const conversations = ref<Conversation[]>([])
  const currentConversationId = ref<string>('')
  const messages = ref<Message[]>([])
  const isStreaming = ref(false)
  const streamingText = ref('')
  const streamingSources = ref<SourceItem[]>([])
  const streamingStatus = ref('')
  const competitionSelectionContext = ref('')

  async function loadHistory(days = historyDays) {
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
    streamingStatus.value = ''
    competitionSelectionContext.value = ''
  }

  function reset() {
    conversations.value = []
    clearCurrent()
  }

  function startAssistantMessage() {
    streamingText.value = ''
    isStreaming.value = true
    streamingStatus.value = '正在准备回答'
  }

  function appendStreamChunk(chunk: string) {
    streamingText.value += chunk
  }

  async function finishAssistantMessage(conversationId: string) {
    if (streamingText.value) {
      messages.value.push({ role: 'assistant', content: streamingText.value, sources: streamingSources.value })
    }
    streamingText.value = ''
    streamingSources.value = []
    streamingStatus.value = ''
    isStreaming.value = false
    if (conversationId) {
      currentConversationId.value = conversationId
    }
    await loadHistory()
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
    const selectionContext = competitionSelectionContext.value
    competitionSelectionContext.value = ''
    const userMessageIndex = messages.value.length
    messages.value.push(userMessage)
    startAssistantMessage()

    return new Promise<void>((resolve, reject) => {
      chatApi.sendMessageStream(
        { query, conversationId: currentConversationId.value, competitionSelectionContext: selectionContext, attachmentIds },
        onAccepted,
        appendStreamChunk,
        (sources) => { streamingSources.value = sources },
        (progress: ChatProgress) => { streamingStatus.value = progress.message },
        (originQuery) => { competitionSelectionContext.value = originQuery },
        async (conversationId) => {
          try {
            await finishAssistantMessage(conversationId)
            resolve()
          } catch (err) {
            reject(err)
          }
        },
        (err) => {
          isStreaming.value = false
          streamingText.value = ''
          streamingSources.value = []
          streamingStatus.value = ''
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
    streamingStatus,
    loadHistory,
    loadMessages,
    clearCurrent,
    reset,
    sendMessage,
    deleteConversation,
  }
})
