<script setup lang="ts">
import { Plus, ChatLineRound, Delete } from '@element-plus/icons-vue'
import type { Conversation } from '@/api/chat'

interface Props {
  conversations: Conversation[]
  activeId: string
  loading?: boolean
}

withDefaults(defineProps<Props>(), {
  loading: false,
})

const emit = defineEmits<{
  select: [conversationId: string]
  new: []
  delete: [conversationId: string]
}>()

function formatDate(dateStr?: string): string {
  if (!dateStr) return '时间未知'
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return '时间未知'
  const now = new Date()
  const sameDay = date.toDateString() === now.toDateString()
  if (sameDay) return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

function formatSummary(summary?: string): string {
  if (!summary?.trim()) return '暂无消息内容'
  return summary.replace(/\s+/g, ' ').trim()
}

function handleDelete(e: Event, conversationId: string) {
  e.stopPropagation()
  emit('delete', conversationId)
}
</script>

<template>
  <aside class="conversation-sidebar flex h-full w-72 shrink-0 flex-col border-r border-[#d6e0eb] bg-[#f8fafc] lg:w-80 max-md:absolute max-md:inset-y-0 max-md:left-0 max-md:z-10 max-md:hidden">
    <div class="border-b border-[#dce5ef] bg-white/80 p-4">
      <button class="btn-primary w-full" @click="emit('new')">
        <Plus :size="17" />
        <span>开启新对话</span>
      </button>
    </div>

    <div class="flex-1 overflow-y-auto">
      <div class="flex h-10 items-center justify-between border-b border-[#e2e8f0] px-4">
        <p class="text-[11px] font-semibold tracking-[0.12em] text-[#718096]">近期对话</p>
        <span v-if="!loading && conversations.length" class="text-[11px] font-medium text-[#8b98a9]">{{ conversations.length }}</span>
      </div>
      <div v-if="loading" class="px-4 py-4 text-sm text-[#758398]">正在加载…</div>
      <div v-else-if="conversations.length === 0" class="mx-3 my-4 rounded-lg border border-dashed border-[#d5e1ec] bg-white/60 px-4 py-12 text-center">
        <div class="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-[#edf5f3] text-[#398777]"><ChatLineRound :size="23" /></div>
        <p class="mt-3 text-sm font-medium text-[#40506a]">还没有对话记录</p>
        <p class="mt-1 text-xs leading-relaxed text-[#8795a8]">从一个问题开始，沉淀你的校园知识。</p>
      </div>
      <ul v-else>
        <li
          v-for="conv in conversations"
          :key="conv.conversationId"
          class="conversation-item group flex h-[72px] cursor-pointer items-start gap-3 border-b border-[#e7edf3] px-3 py-3 transition-colors"
          :class="activeId === conv.conversationId ? 'bg-[#eef7f5]' : 'hover:bg-white'"
          @click="emit('select', conv.conversationId)"
        >
          <span class="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-[#edf3f6] text-[#71879a]" :class="activeId === conv.conversationId ? 'bg-[#dcefe9] text-[#176b5b]' : ''">
            <ChatLineRound :size="17" />
          </span>
          <div class="relative min-w-0 flex-1 self-stretch pr-14">
            <p class="truncate text-[13px] font-semibold leading-5 text-[#334158]" :class="activeId === conv.conversationId ? '!text-[#0d6253]' : ''">{{ conv.title || '新对话' }}</p>
            <p class="conversation-summary truncate text-xs leading-5 text-[#7b899d]">{{ formatSummary(conv.summary) }}</p>
            <time class="absolute right-0 top-0 text-[10px] font-medium leading-5 text-[#9aa6b5]" :datetime="conv.lastActiveAt">{{ formatDate(conv.lastActiveAt) }}</time>
            <button class="absolute right-0 bottom-0 grid h-5 w-5 place-items-center rounded-md text-[#8b98a9] opacity-0 transition-opacity hover:bg-[#f1f4f7] hover:text-[#bd4350] group-hover:opacity-100 focus:opacity-100" title="删除对话" @click="handleDelete($event, conv.conversationId)"><Delete :size="13" /></button>
          </div>
        </li>
      </ul>
    </div>

    <div class="border-t border-[#dce5ef] bg-white/55 p-4">
      <div class="rounded-xl bg-[#eef4f8] px-3 py-2.5 text-xs leading-relaxed text-[#718096]">显示近 30 天对话，支持在提问时附加学习材料。</div>
    </div>
  </aside>
</template>
