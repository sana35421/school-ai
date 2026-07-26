<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import AppHeader from '@/components/AppHeader.vue'
import ConversationList from '@/components/ConversationList.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import EmptyState from '@/components/EmptyState.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const userStore = useUserStore()

const messagesContainer = ref<HTMLDivElement | null>(null)
const loading = ref(true)

const currentConvId = computed(() => {
  const routeId = route.params.conversationId as string
  return routeId || chatStore.currentConversationId || ''
})

async function loadAll() {
  loading.value = true
  try {
    await chatStore.loadHistory(7)
    if (currentConvId.value) {
      await chatStore.loadMessages(currentConvId.value)
    }
  } catch {
    ElMessage.error('加载历史对话失败')
  } finally {
    loading.value = false
  }
}

async function handleSelectConversation(id: string) {
  router.push(`/chat/${id}`)
  try {
    await chatStore.loadMessages(id)
  } catch {
    ElMessage.error('加载对话失败')
  }
}

function handleNewConversation() {
  chatStore.clearCurrent()
  router.push('/chat')
}

async function handleDelete(id: string) {
  try {
    await ElMessageBox.confirm('确定要删除这条对话吗？删除后无法恢复。', '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await chatStore.deleteConversation(id)
    ElMessage.success('已删除')
    if (currentConvId.value === id) {
      handleNewConversation()
    }
  } catch {
    // 取消
  }
}

async function handleSend(text: string, attachmentIds: number[] = []) {
  try {
    await chatStore.sendMessage(text, attachmentIds)
    await nextTick()
    scrollToBottom()
  } catch {
    // 错误已经在 store 中处理
  }
}

function scrollToBottom() {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

watch(
  () => chatStore.messages.length,
  () => {
    nextTick(scrollToBottom)
  }
)

watch(
  () => route.params.conversationId,
  async (id) => {
    if (id && typeof id === 'string') {
      try {
        await chatStore.loadMessages(id)
      } catch {
        ElMessage.error('加载对话失败')
      }
    } else if (!id) {
      chatStore.clearCurrent()
    }
  }
)

onMounted(async () => {
  await userStore.fetchProfile()
  await loadAll()
})
</script>

<template>
  <div class="h-screen flex flex-col">
    <AppHeader :show-admin-link="userStore.isStaff" />

    <div class="flex-1 flex overflow-hidden">
      <ConversationList
        :conversations="chatStore.conversations"
        :active-id="currentConvId"
        :loading="loading"
        @select="handleSelectConversation"
        @new="handleNewConversation"
        @delete="handleDelete"
      />

      <!-- 对话主区 -->
      <main class="flex-1 flex flex-col bg-paper">
        <!-- 消息列表 -->
        <div
          ref="messagesContainer"
          class="flex-1 overflow-y-auto px-8 py-8"
        >
          <div class="max-w-3xl mx-auto">
            <template v-if="chatStore.messages.length === 0">
              <EmptyState @pick="(t) => handleSend(t)" />
            </template>

            <template v-else>
              <div class="space-y-8 pb-4">
                <ChatMessage
                  v-for="(msg, idx) in chatStore.messages"
                  :key="idx"
                  :role="msg.role"
                  :content="msg.content"
                />
                <!-- 流式中 -->
                <ChatMessage
                  v-if="chatStore.isStreaming"
                  role="assistant"
                  :content="chatStore.streamingText"
                  streaming
                />
              </div>
            </template>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="border-t border-border bg-cream/60 backdrop-blur px-8 py-5">
          <div class="max-w-3xl mx-auto">
            <ChatInput
              :disabled="chatStore.isStreaming"
              @send="handleSend"
            />
            <p class="text-2xs text-ink-mute text-center mt-3 font-mono">
              AI 生成的内容仅供参考 · 重要政策请以学校官方文件为准
            </p>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>