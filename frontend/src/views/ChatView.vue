<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import AppHeader from '@/components/AppHeader.vue'
import ConversationList from '@/components/ConversationList.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import type { UploadRecord } from '@/api/upload'
import EmptyState from '@/components/EmptyState.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const userStore = useUserStore()
const messagesContainer = ref<HTMLDivElement | null>(null)
const loading = ref(true)
const currentConvId = computed(() => (route.params.conversationId as string) || chatStore.currentConversationId || '')

async function loadAll() {
  loading.value = true
  try {
    await chatStore.loadHistory()
    if (currentConvId.value) await chatStore.loadMessages(currentConvId.value)
  } catch { ElMessage.error('加载历史对话失败') } finally { loading.value = false }
}
async function handleSelectConversation(id: string) {
  if (chatStore.isStreaming) return ElMessage.warning('请等待当前回答完成后再切换对话')
  router.push(`/chat/${id}`)
  try { await chatStore.loadMessages(id) } catch { ElMessage.error('加载对话失败') }
}
function handleNewConversation() {
  if (chatStore.isStreaming) return ElMessage.warning('请等待当前回答完成后再新建对话')
  chatStore.clearCurrent()
  router.push('/chat')
}
async function handleDelete(id: string) {
  try {
    await ElMessageBox.confirm('确定要删除这条对话吗？删除后无法恢复。', '删除对话', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await chatStore.deleteConversation(id)
    ElMessage.success('已删除')
    if (currentConvId.value === id) handleNewConversation()
  } catch {}
}
function handleSend(
  text: string,
  attachmentIds: number[] = [],
  attachments: UploadRecord[] = [],
  accepted: () => void,
  complete: (success: boolean) => void
) {
  chatStore.sendMessage(text, attachmentIds, attachments, accepted)
    .then(async () => {
      if (chatStore.currentConversationId && !route.params.conversationId) {
        await router.replace(`/chat/${chatStore.currentConversationId}`)
      }
      complete(true)
      await nextTick()
      scrollToBottom()
    })
    .catch((error: any) => {
      complete(false)
      ElMessage.error(error?.message || '发送失败，请重试')
    })
}
function scrollToBottom() { if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight }
watch(() => chatStore.messages.length, () => nextTick(scrollToBottom))
watch(() => route.params.conversationId, async (id) => { if (id && typeof id === 'string') { try { await chatStore.loadMessages(id) } catch { ElMessage.error('加载对话失败') } } else chatStore.clearCurrent() })
onMounted(async () => { await userStore.fetchProfile(); await loadAll() })
</script>

<template>
  <div class="workspace-shell flex h-dvh flex-col overflow-hidden">
    <AppHeader :show-admin-link="userStore.isStaff" />
    <div class="relative flex min-h-0 flex-1 overflow-hidden">
      <ConversationList :conversations="chatStore.conversations" :active-id="currentConvId" :loading="loading" @select="handleSelectConversation" @new="handleNewConversation" @delete="handleDelete" />
      <main class="flex min-w-0 flex-1 flex-col bg-[#f7f9fc]">
        <div ref="messagesContainer" class="flex-1 overflow-y-auto px-4 py-6 sm:px-8 sm:py-8">
          <div class="mx-auto max-w-4xl">
            <template v-if="chatStore.messages.length === 0"><div class="py-4 sm:py-10"><EmptyState @pick="(t) => handleSend(t, [], [], () => {}, () => {})" /></div></template>
            <div v-else class="space-y-7 pb-6 sm:space-y-8"><ChatMessage v-for="(msg, idx) in chatStore.messages" :key="idx" :role="msg.role" :content="msg.content" :sources="msg.sources" :attachments="msg.attachments" /><ChatMessage v-if="chatStore.isStreaming" role="assistant" :content="chatStore.streamingText" :status="chatStore.streamingStatus" streaming /></div>
          </div>
        </div>
        <div class="border-t border-[#dce4ee] bg-white/90 px-4 py-4 backdrop-blur sm:px-8 sm:py-5"><div class="mx-auto max-w-4xl"><ChatInput :disabled="chatStore.isStreaming" @send="handleSend" /><p class="mt-2.5 text-center text-[11px] text-[#8592a4]">AI 内容仅供学习参考，重要政策请以学校官方文件为准</p></div></div>
      </main>
    </div>
  </div>
</template>
