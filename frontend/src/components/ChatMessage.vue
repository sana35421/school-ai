<script setup lang="ts">
import { computed } from 'vue'
import { marked, Renderer } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import { User, MagicStick } from '@element-plus/icons-vue'

interface Props {
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  sources?: Array<{ documentName: string; score: number }>
}

const props = withDefaults(defineProps<Props>(), {
  streaming: false,
  sources: () => [],
})

const renderer = new Renderer()
renderer.code = function(code: string, lang?: string) {
  if (lang && hljs.getLanguage(lang)) {
    try {
      const highlighted = hljs.highlight(code, { language: lang }).value
      return `<pre><code class="hljs language-${lang}">${highlighted}</code></pre>`
    } catch {
      return `<pre><code>${code}</code></pre>`
    }
  }
  const { value } = hljs.highlightAuto(code)
  return `<pre><code class="hljs">${value}</code></pre>`
}

marked.setOptions({ renderer })

const renderedHtml = computed(() => {
  if (props.role === 'user') return ''
  // 转换编码后的换行，并在 Markdown 渲染前隐藏模型内部思考内容。
  const content = (props.content || '')
    .replace(/<think>[\s\S]*?<\/think>/gi, '')
    .replace(/\\n/g, '\n')
  return marked.parse(content, { breaks: true })
})
</script>

<template>
  <div class="flex gap-4 animate-fade-up" :class="role === 'user' ? 'flex-row-reverse' : ''">
    <div class="flex-shrink-0">
      <div
        v-if="role === 'user'"
        class="w-9 h-9 rounded-xl bg-slate-700 text-white flex items-center justify-center"
      >
        <User :size="18" />
      </div>
      <div
        v-else
        class="w-9 h-9 rounded-xl bg-blue-500 text-white flex items-center justify-center"
      >
        <MagicStick :size="18" />
      </div>
    </div>

    <div class="flex-1 max-w-3xl min-w-0" :class="role === 'user' ? 'flex justify-end' : ''">
      <div class="flex items-baseline gap-2 mb-1.5" :class="role === 'user' ? 'justify-end' : ''">
        <span class="text-sm font-medium text-slate-700">
          {{ role === 'user' ? '你' : '校园助手' }}
        </span>
        <span class="text-xs text-slate-400 font-mono">
          {{ role === 'assistant' ? 'Campus AI' : '' }}
        </span>
      </div>

      <div
        v-if="role === 'user'"
        class="inline-block bg-white border border-slate-200 rounded-xl rounded-tr-sm px-4 py-2.5 max-w-full text-slate-700 leading-relaxed shadow-sm"
      >
        {{ content }}
      </div>

      <div
        v-else
        class="bg-white border border-slate-200 rounded-xl rounded-tl-sm px-5 py-3.5 max-w-full shadow-sm"
      >
        <div v-if="!content && streaming" class="flex items-center gap-1.5 py-1">
          <span class="w-2 h-2 rounded-full bg-blue-400 animate-pulse-dot" style="animation-delay: 0s"></span>
          <span class="w-2 h-2 rounded-full bg-blue-400 animate-pulse-dot" style="animation-delay: 0.2s"></span>
          <span class="w-2 h-2 rounded-full bg-blue-400 animate-pulse-dot" style="animation-delay: 0.4s"></span>
        </div>
        <div v-else class="markdown-body text-slate-700 leading-relaxed text-[0.95rem]" v-html="renderedHtml"></div>

        <div
          v-if="sources && sources.length > 0"
          class="mt-4 pt-3 border-t border-slate-100"
        >
          <p class="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2">
            参考来源
          </p>
          <div class="flex flex-wrap gap-2">
            <span
              v-for="(src, idx) in sources"
              :key="idx"
              class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-slate-50 border border-slate-200 text-xs text-slate-600"
            >
              <span class="w-1 h-1 rounded-full bg-blue-500"></span>
              {{ src.documentName }}
              <span class="text-slate-400 font-mono text-xs">
                {{ (src.score * 100).toFixed(0) }}%
              </span>
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>