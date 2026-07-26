<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import * as adminApi from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const documents = ref<adminApi.KbDocument[]>([])
const loading = ref(false)
const uploading = ref(false)

async function loadDocuments() {
  loading.value = true
  try {
    documents.value = await adminApi.getKbDocuments()
  } catch {
    ElMessage.error('加载文档列表失败')
  } finally {
    loading.value = false
  }
}

async function handleUpload(e: Event) {
  const target = e.target as HTMLInputElement
  if (!target.files || target.files.length === 0) return
  const file = target.files[0]
  uploading.value = true
  try {
    await adminApi.uploadKbDocument(file)
    ElMessage.success(`已上传 "${file.name}"`)
    await loadDocuments()
  } catch {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
    target.value = ''
  }
}

async function handleDelete(docId: number, name: string) {
  try {
    await ElMessageBox.confirm(
      `确定要删除文档 "${name}" 吗？删除后该文档的内容将不再被 AI 引用。`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await adminApi.deleteKbDocument(docId)
    ElMessage.success('已删除')
    await loadDocuments()
  } catch {
    // 取消
  }
}

function goBack() {
  router.push('/admin')
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const statusLabel = (status: string) => {
  if (status === 'completed' || status === 'pending' || status === 'uploaded') {
    return { text: '已上传', cls: 'text-sage-700 border-sage-200 bg-sage-50' }
  }
  switch (status) {
    case 'indexing': return { text: '已上传', cls: 'text-sage-700 border-sage-200 bg-sage-50' }
    case 'failed': return { text: '上传失败', cls: 'text-warning border-warning/30 bg-warning/5' }
    default: return { text: '已上传', cls: 'text-sage-700 border-sage-200 bg-sage-50' }
  }
}

onMounted(async () => {
  await loadDocuments()
})
</script>

<template>
  <div class="h-screen flex flex-col">
    <AppHeader />

    <div class="flex-1 overflow-y-auto bg-paper">
      <div class="max-w-5xl mx-auto px-8 py-10">
        <!-- 顶部 -->
        <div class="flex items-center gap-4 mb-8">
          <button class="p-2 rounded-md hover:bg-paper text-ink-soft" @click="goBack">
            返回
          </button>
          <div class="flex-1">
            <h1 class="display-text text-2xl font-semibold text-ink">知识库管理</h1>
            <p class="text-sm text-ink-mute mt-0.5">
              上传 PPT（建议先转 PDF）、TXT、DOCX 等文档。系统会自动建立索引，AI 将基于这些文档回答学生问题。
            </p>
          </div>
          <button class="btn-ghost" @click="loadDocuments">
            刷新
          </button>
        </div>

        <!-- 上传区 -->
        <div class="card p-6 mb-6">
          <h2 class="display-text text-lg font-semibold text-ink mb-1">上传新文档</h2>
          <p class="text-xs text-ink-mute mb-4">支持 PDF / TXT / MD / DOCX，单个文件最大 50MB</p>
          <label
            class="block border-2 border-dashed border-border-strong rounded-lg p-10 text-center cursor-pointer hover:border-terracotta-300 hover:bg-terracotta-50/30 transition-colors"
          >
            <p class="text-sm font-medium text-ink">
              {{ uploading ? '上传中…' : '点击或拖拽文件到这里' }}
            </p>
            <p class="text-xs text-ink-mute mt-1">PPT 建议先另存为 PDF 再上传，以获得最佳识别效果</p>
            <input
              type="file"
              class="hidden"
              accept=".pdf,.txt,.md,.docx,.doc"
              :disabled="uploading"
              @change="handleUpload"
            />
          </label>
        </div>

        <!-- 文档列表 -->
        <div class="card overflow-hidden">
          <div class="px-6 py-4 border-b border-border flex items-center justify-between">
            <h2 class="display-text text-lg font-semibold text-ink">文档列表</h2>
            <span class="text-xs text-ink-mute font-mono">共 {{ documents.length }} 个</span>
          </div>
          <div v-if="loading" class="p-12 text-center text-sm text-ink-mute">
            加载中…
          </div>
          <div v-else-if="documents.length === 0" class="p-16 text-center">
            <p class="text-sm text-ink-soft">还没有上传任何文档</p>
            <p class="text-xs text-ink-mute mt-1">上传第一个文档，AI 就能开始基于它回答问题</p>
          </div>
          <div v-else class="divide-y divide-border">
            <div
              v-for="doc in documents"
              :key="doc.id"
              class="document-row flex flex-col gap-3 sm:flex-row sm:items-start px-4 sm:px-6 py-4 hover:bg-paper/50 transition-colors"
            >
                <div class="min-w-0 flex-1">
                  <span class="document-name block min-w-0 max-w-full truncate text-sm text-ink font-medium" :title="doc.name">{{ doc.name }}</span>
                <div class="document-meta mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-ink-mute">
                  <span class="truncate max-w-full">类型：{{ doc.type || '未知' }}</span>
                  <span>大小：{{ formatSize(doc.size) }}</span>
                  <span>上传：{{ formatDate(doc.uploadedAt) }}</span>
                </div>
              </div>
              <div class="document-actions flex items-center justify-end gap-3 flex-shrink-0 sm:ml-3">
                <span
                  class="inline-flex items-center px-2 py-0.5 rounded-sm border text-2xs font-medium whitespace-nowrap"
                  :class="statusLabel(doc.status).cls"
                >
                  {{ statusLabel(doc.status).text }}
                </span>
                <button
                  class="delete-button min-h-7 px-2.5 rounded border border-border text-xs text-ink-mute hover:text-warning hover:border-warning/40 transition-colors"
                  @click="handleDelete(doc.id, doc.name)"
                  title="删除"
                  aria-label="删除文档"
                >
                  删除
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>