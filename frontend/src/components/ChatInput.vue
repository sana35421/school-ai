<script setup lang="ts">
import { ref, computed } from 'vue'
import { Promotion, Link, Close } from '@element-plus/icons-vue'
import { recordUpload } from '@/api/upload'
import type { UploadRecordResult } from '@/api/upload'

interface Props {
  disabled?: boolean
  placeholder?: string
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  placeholder: '向校园助手提问…  (Ctrl + Enter 发送)',
})

const emit = defineEmits<{
  send: [content: string, attachmentIds: number[]]
}>()

const text = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const attachments = ref<UploadRecordResult[]>([])
const uploading = ref(false)

const canSend = computed(() => !props.disabled && text.value.trim().length > 0)

function handleSend() {
  if (!canSend.value) return
  const attachmentIds = attachments.value.map((a) => a.id)
  emit('send', text.value.trim(), attachmentIds)
  text.value = ''
  attachments.value = []
  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
  }
}

function autoResize(e: Event) {
  const target = e.target as HTMLTextAreaElement
  target.style.height = 'auto'
  target.style.height = Math.min(target.scrollHeight, 200) + 'px'
}

function handleKeydown(e: KeyboardEvent) {
  if (e.ctrlKey && e.key === 'Enter') {
    e.preventDefault()
    handleSend()
  }
}

function triggerFileInput() {
  fileInputRef.value?.click()
}

async function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return

  uploading.value = true
  try {
    for (const file of Array.from(files)) {
      const result = await recordUpload({
        fileName: file.name,
        fileType: file.type || 'application/octet-stream',
        fileSize: file.size,
      })
      attachments.value.push(result)
    }
  } catch {
    // 错误已在 request 拦截器中处理
  } finally {
    uploading.value = false
    if (input) input.value = ''
  }
}

function removeAttachment(id: number) {
  attachments.value = attachments.value.filter((a) => a.id !== id)
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / (1024 * 1024)).toFixed(1) + 'MB'
}
</script>

<template>
  <div class="bg-white border border-slate-200 rounded-2xl shadow-sm p-4 transition-all focus-within:border-blue-300 focus-within:shadow-md">
    <div v-if="attachments.length > 0" class="flex flex-wrap gap-2 mb-3 pb-3 border-b border-slate-100">
      <span
        v-for="att in attachments"
        :key="att.id"
        class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-blue-50 border border-blue-200 text-xs text-blue-700"
      >
        <span class="truncate max-w-[180px]">{{ att.fileName }}</span>
        <span class="text-blue-400 font-mono text-xs">({{ formatFileSize(att.fileSize) }})</span>
        <button
          class="p-0.5 rounded hover:bg-blue-200 transition-colors"
          @click="removeAttachment(att.id)"
          type="button"
        >
          <Close :size="12" />
        </button>
      </span>
    </div>

    <textarea
      ref="textareaRef"
      v-model="text"
      :placeholder="placeholder"
      :disabled="disabled"
      rows="1"
      class="w-full bg-transparent text-slate-700 placeholder:text-slate-400 resize-none focus:outline-none text-[0.95rem] leading-relaxed px-1"
      :style="{ minHeight: '24px', maxHeight: '200px' }"
      @input="autoResize"
      @keydown="handleKeydown"
    />
    <div class="flex items-center justify-between mt-3 pt-3 border-t border-slate-100">
      <div class="flex items-center gap-2">
        <button
          class="p-1.5 rounded-lg text-slate-400 hover:text-blue-500 hover:bg-blue-50 transition-colors"
          title="上传附件"
          type="button"
          :disabled="disabled || uploading"
          @click="triggerFileInput"
        >
          <Link :size="16" />
        </button>
        <input
          ref="fileInputRef"
          type="file"
          class="hidden"
          multiple
          @change="handleFileSelect"
        />
        <span class="text-xs text-slate-300 font-mono">
          {{ text.length }} / 2000
        </span>
      </div>
      <button
        class="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm font-medium transition-all"
        :class="canSend
          ? 'bg-blue-500 text-white hover:bg-blue-600 active:scale-[0.97]'
          : 'bg-slate-100 text-slate-400 cursor-not-allowed'"
        :disabled="!canSend"
        @click="handleSend"
      >
        <span>发送</span>
        <Promotion :size="14" />
      </button>
    </div>
  </div>
</template>