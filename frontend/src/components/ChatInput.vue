<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Paperclip, Promotion, Close } from '@element-plus/icons-vue'
import { uploadChatFile, type UploadRecord } from '@/api/upload'

const props = defineProps<{ disabled?: boolean }>()

const emit = defineEmits<{
  send: [
    content: string,
    attachmentIds: number[],
    attachments: UploadRecord[],
    accepted: () => void,
    complete: (success: boolean) => void,
  ]
}>()

const text = ref('')
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const textarea = ref<HTMLTextAreaElement | null>(null)
const attachments = ref<UploadRecord[]>([])
const maxLength = 2000
const maxFileSize = 10 * 1024 * 1024
const maxFiles = 3
const allowedExtensions = ['pdf', 'docx', 'txt', 'md', 'csv', 'xlsx']

const canSend = computed(() => !props.disabled && !uploading.value && text.value.trim().length > 0)

function handleSend() {
  if (!canSend.value) return
  const content = text.value.trim()
  const currentAttachments = [...attachments.value]
  let cleared = false
  emit(
    'send',
    content,
    currentAttachments.map(item => item.id),
    currentAttachments,
    () => {
      cleared = true
      text.value = ''
      attachments.value = []
    },
    (success) => {
      if (!success && cleared) {
        text.value = content
        attachments.value = currentAttachments
      }
    }
  )
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function resizeTextarea() {
  if (!textarea.value) return
  textarea.value.style.height = 'auto'
  textarea.value.style.height = `${Math.min(textarea.value.scrollHeight, 144)}px`
}

watch(text, async () => {
  await nextTick()
  resizeTextarea()
})

function handleAttach() {
  if (!props.disabled && !uploading.value) fileInput.value?.click()
}

function getExtension(fileName: string) {
  return fileName.split('.').pop()?.toLowerCase() || ''
}

async function handleFiles(event: Event) {
  const target = event.target as HTMLInputElement
  const files = Array.from(target.files || [])
  target.value = ''
  if (!files.length) return
  if (attachments.value.length + files.length > maxFiles) {
    ElMessage.warning(`单条消息最多上传${maxFiles}个文件`)
    return
  }
  if (files.some(file => !allowedExtensions.includes(getExtension(file.name)))) {
    ElMessage.warning('仅支持 PDF、DOCX、TXT、MD、CSV 和 XLSX 文件')
    return
  }
  const oversizedFile = files.find(file => file.size > maxFileSize)
  if (oversizedFile) {
    ElMessage.warning(`文件 ${oversizedFile.name} 超过10MB`)
    return
  }
  uploading.value = true
  try {
    for (const file of files) attachments.value.push(await uploadChatFile(file))
  } catch (error: any) {
    ElMessage.error(error?.message || '文件上传失败，请重试')
  } finally {
    uploading.value = false
  }
}

function removeAttachment(id: number) {
  attachments.value = attachments.value.filter(item => item.id !== id)
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <div class="overflow-hidden rounded-2xl border border-[#cfdbe7] bg-white shadow-[0_12px_34px_rgba(36,60,92,0.12)] transition-shadow focus-within:border-[#74b7aa] focus-within:shadow-[0_14px_38px_rgba(23,107,91,0.14)]">
    <div v-if="attachments.length || uploading" class="flex flex-wrap gap-2 px-3 pt-3">
      <div v-for="item in attachments" :key="item.id" class="flex max-w-full items-center gap-2 rounded-xl border border-[#cfe4df] bg-[#eff8f6] px-2.5 py-2 text-[#1a6659]">
        <Document :size="15" class="shrink-0" />
        <div class="min-w-0"><p class="truncate text-xs font-medium">{{ item.fileName }}</p><p class="mt-0.5 text-[11px] text-[#60968c]">{{ formatFileSize(item.fileSize) }}</p></div>
        <button class="grid h-6 w-6 shrink-0 place-items-center rounded-md hover:bg-[#dcedea]" type="button" title="移除附件" @click="removeAttachment(item.id)"><Close :size="14" /></button>
      </div>
      <span v-if="uploading" class="self-center text-xs text-[#718096]">正在上传并解析文件…</span>
    </div>
    <div class="flex items-end gap-2 p-3">
      <button type="button" class="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[#eef3f7] text-[#607189] transition-colors hover:bg-[#e1eaf1] hover:text-[#176b5b] disabled:cursor-not-allowed disabled:opacity-50" :disabled="disabled || uploading || attachments.length >= maxFiles" title="上传文档" @click="handleAttach"><Paperclip :size="18" /></button>
      <input ref="fileInput" type="file" multiple accept=".pdf,.docx,.txt,.md,.csv,.xlsx" class="hidden" @change="handleFiles" />
      <textarea ref="textarea" v-model="text" :disabled="disabled" :maxlength="maxLength" rows="1" placeholder="输入问题，或上传学习材料后提问…" class="chat-input-textarea max-h-36 min-h-10 flex-1 resize-none border-0 bg-transparent py-2 text-sm leading-6 text-[#26354a] outline-none placeholder:text-[#93a0b1]" @input="resizeTextarea" @keydown="handleKeydown" />
      <button type="button" class="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[#176b5b] text-white transition-all hover:bg-[#105448] disabled:cursor-not-allowed disabled:opacity-40" :disabled="!canSend" title="发送" @click="handleSend"><Promotion :size="18" /></button>
    </div>
    <div class="flex items-center justify-between gap-3 border-t border-[#edf1f5] px-4 py-2 text-[11px] text-[#8491a3]"><span class="hidden sm:inline">Enter 发送 · Shift + Enter 换行 · 支持附加学习材料</span><span class="sm:hidden">Enter 发送</span><span>{{ text.length }} / {{ maxLength }}</span></div>
  </div>
</template>
