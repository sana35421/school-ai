import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Conversation, Message } from '@/api/chat'
import * as chatApi from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const currentConversationId = ref<string>('')
  const messages = ref<Message[]>([])
  const isStreaming = ref(false)
  const streamingText = ref('')

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
  }

  function reset() {
    conversations.value = []
    clearCurrent()
  }

  function appendUserMessage(content: string) {
    messages.value.push({ role: 'user', content })
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
      messages.value.push({ role: 'assistant', content: streamingText.value })
    }
    streamingText.value = ''
    isStreaming.value = false
    if (conversationId && conversationId !== currentConversationId.value) {
      currentConversationId.value = conversationId
      loadHistory().catch(() => {})
    }
  }

  function failAssistantMessage(err: Error) {
    messages.value.push({
      role: 'assistant',
      content: `⚠️ 出错了：${err.message}。请稍后再试。`,
    })
    isStreaming.value = false
    streamingText.value = ''
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

  async function sendMessage(query: string, attachmentIds: number[] = []) {
    if (isStreaming.value) return
    appendUserMessage(query)
    startAssistantMessage()

    return new Promise<void>((resolve, reject) => {
      chatApi.sendMessageStream(
        { query, conversationId: currentConversationId.value, attachmentIds },
        appendStreamChunk,
        (conversationId) => {
          finishAssistantMessage(conversationId)
          resolve()
        },
        (err) => {
          failAssistantMessage(err)
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