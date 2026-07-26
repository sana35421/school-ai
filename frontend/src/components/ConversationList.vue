<script setup lang="ts">
import { Plus, ChatLineRound, Delete } from '@element-plus/icons-vue'
import type { Conversation } from '@/api/chat'

interface Props {
  conversations: Conversation[]
  activeId: string
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
})

const emit = defineEmits<{
  select: [conversationId: string]
  new: []
  delete: [conversationId: string]
}>()

function formatRelative(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  const diffHour = Math.floor(diffMin / 60)
  const diffDay = Math.floor(diffHour / 24)

  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  if (diffHour < 24) return `${diffHour} 小时前`
  if (diffDay < 7) return `${diffDay} 天前`
  return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

function handleDelete(e: Event, conversationId: string) {
  e.stopPropagation()
  emit('delete', conversationId)
}
</script>

<template>
  <aside class="w-72 flex-shrink-0 bg-cream border-r border-border flex flex-col h-full">
    <!-- 头部 -->
    <div class="p-5 border-b border-border">
      <button
        class="w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-md bg-ink text-white text-sm font-medium hover:bg-ink-soft transition-colors"
        @click="emit('new')"
      >
        <Plus :size="16" />
        <span>新建对话</span>
      </button>
    </div>

    <!-- 列表 -->
    <div class="flex-1 overflow-y-auto py-2">
      <div class="px-5 py-3 text-2xs font-mono uppercase tracking-wider text-ink-mute">
        最近 7 天
      </div>
      <div v-if="loading" class="px-5 py-3 text-sm text-ink-mute">加载中…</div>
      <div v-else-if="conversations.length === 0" class="px-5 py-8 text-center">
        <ChatLineRound :size="28" class="text-border-strong mx-auto mb-2" />
        <p class="text-sm text-ink-mute">还没有对话</p>
        <p class="text-2xs text-ink-mute mt-1">点击上方开始你的第一次提问</p>
      </div>
      <ul v-else class="px-2">
        <li
          v-for="conv in conversations"
          :key="conv.conversationId"
          class="group relative rounded-md cursor-pointer transition-colors mb-0.5"
          :class="activeId === conv.conversationId
            ? 'bg-terracotta-50'
            : 'hover:bg-paper'"
          @click="emit('select', conv.conversationId)"
        >
          <div class="px-3 py-2.5">
            <div class="flex items-start justify-between gap-2">
              <p
                class="text-sm text-ink font-medium leading-snug line-clamp-2 flex-1"
                :class="activeId === conv.conversationId ? 'text-terracotta-700' : ''"
              >
                {{ conv.title || '新对话' }}
              </p>
              <button
                class="opacity-0 group-hover:opacity-100 p-1 rounded text-ink-mute hover:text-warning transition-all"
                title="删除对话"
                @click="handleDelete($event, conv.conversationId)"
              >
                <Delete :size="14" />
              </button>
            </div>
            <p class="text-2xs text-ink-mute mt-1 font-mono">
              {{ formatRelative(conv.lastActiveAt) }}
            </p>
          </div>
          <!-- 选中时的左侧装饰条 -->
          <div
            v-if="activeId === conv.conversationId"
            class="absolute left-0 top-2 bottom-2 w-0.5 bg-terracotta rounded-r"
          ></div>
        </li>
      </ul>
    </div>

    <!-- 底部装饰 -->
    <div class="p-5 border-t border-border">
      <p class="text-2xs text-ink-mute leading-relaxed">
        对话记录仅保留 <span class="font-medium text-ink">7 天</span>，过期会自动清理
      </p>
    </div>
  </aside>
</template>