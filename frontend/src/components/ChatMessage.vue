<script setup lang="ts">
import { computed } from 'vue'
import { marked, Renderer } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import { Document, User, MagicStick } from '@element-plus/icons-vue'
import type { Attachment, SourceItem } from '@/api/chat'

interface Props {
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  sources?: SourceItem[]
  attachments?: Attachment[]
}

const props = withDefaults(defineProps<Props>(), {
  streaming: false,
  sources: () => [],
  attachments: () => [],
})

const renderer = new Renderer()
renderer.code = function(code: string, lang?: string) {
  if (lang && hljs.getLanguage(lang)) {
    try {
      return `<pre><code class="hljs language-${lang}">${hljs.highlight(code, { language: lang }).value}</code></pre>`
    } catch {
      return `<pre><code>${code}</code></pre>`
    }
  }
  return `<pre><code class="hljs">${hljs.highlightAuto(code).value}</code></pre>`
}

marked.setOptions({ renderer })

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

const renderedHtml = computed(() => {
  if (props.role === 'user') return ''
  return marked.parse((props.content || '').replace(/<think>[\s\S]*?<\/think>/gi, '').replace(/\\n/g, '\n'), { breaks: true })
})
</script>

<template>
  <div class="flex gap-3 sm:gap-4" :class="role === 'user' ? 'flex-row-reverse' : ''">
    <div class="grid h-9 w-9 shrink-0 place-items-center rounded-xl" :class="role === 'user' ? 'bg-[#34445c] text-white' : 'bg-[#176b5b] text-white shadow-[0_8px_18px_rgba(23,107,91,0.2)]'">
      <User v-if="role === 'user'" :size="17" />
      <MagicStick v-else :size="17" />
    </div>
    <div class="min-w-0 flex-1" :class="role === 'user' ? 'flex flex-col items-end' : ''">
      <div class="mb-1.5 flex items-center gap-2" :class="role === 'user' ? 'flex-row-reverse' : ''">
        <span class="text-sm font-semibold text-[#344158]">{{ role === 'user' ? '我' : '校园知识助手' }}</span>
        <span v-if="role === 'assistant'" class="text-[11px] font-medium tracking-[0.08em] text-[#8391a5]">CAMPUS AI</span>
      </div>
      <div v-if="role === 'user'" class="max-w-[92%] rounded-2xl rounded-tr-sm bg-[#e8f4f1] px-4 py-3 text-sm leading-6 text-[#21433e] sm:max-w-[78%]">
        <div class="whitespace-pre-wrap">{{ content }}</div>
        <div v-if="attachments?.length" class="mt-3 grid gap-2 border-t border-[#cce4df] pt-3">
          <div v-for="attachment in attachments" :key="attachment.id" class="flex items-center gap-2 text-xs text-[#286e61]"><Document :size="15" /><span class="truncate">{{ attachment.fileName }}</span><span class="shrink-0 text-[#6d9f96]">{{ formatFileSize(attachment.fileSize) }}</span></div>
        </div>
      </div>
      <div v-else class="max-w-full rounded-2xl rounded-tl-sm border border-[#dce4ee] bg-white px-4 py-3.5 shadow-[0_5px_18px_rgba(36,60,92,0.05)] sm:px-5">
        <div v-if="!content && streaming" class="flex gap-1.5 py-1"><span class="h-2 w-2 animate-pulse rounded-full bg-[#53a899]"></span><span class="h-2 w-2 animate-pulse rounded-full bg-[#53a899] [animation-delay:150ms]"></span><span class="h-2 w-2 animate-pulse rounded-full bg-[#53a899] [animation-delay:300ms]"></span></div>
        <div v-else class="markdown-body text-[0.95rem] leading-7 text-[#3d4a5d]" v-html="renderedHtml"></div>
        <div v-if="sources?.length" class="mt-4 border-t border-[#e8edf3] pt-3"><p class="text-[11px] font-semibold tracking-[0.1em] text-[#8795a8]">参考来源</p><div class="mt-2 flex flex-wrap gap-2"><span v-for="(src, idx) in sources" :key="idx" class="inline-flex max-w-full items-center gap-1.5 rounded-lg border border-[#d8e8e4] bg-[#f2f9f7] px-2.5 py-1 text-xs text-[#296b60]"><span class="h-1.5 w-1.5 shrink-0 rounded-full bg-[#2d8d7a]"></span><span class="truncate">{{ src.documentName }}</span><span class="text-[#78a69d]">{{ (src.score * 100).toFixed(0) }}%</span></span></div></div>
      </div>
    </div>
  </div>
</template>
