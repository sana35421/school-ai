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
async function loadDocuments() { loading.value = true; try { documents.value = await adminApi.getKbDocuments() } catch { ElMessage.error('加载文档列表失败') } finally { loading.value = false } }
async function handleUpload(e: Event) { const target = e.target as HTMLInputElement; if (!target.files?.length) return; const file = target.files[0]; uploading.value = true; try { await adminApi.uploadKbDocument(file); ElMessage.success(`已上传 "${file.name}"`); await loadDocuments() } catch { ElMessage.error('上传失败') } finally { uploading.value = false; target.value = '' } }
async function handleDelete(docId: number, name: string) { try { await ElMessageBox.confirm(`确定要删除文档 "${name}" 吗？删除后该文档的内容将不再被 AI 引用。`, '删除确认', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }); await adminApi.deleteKbDocument(docId); ElMessage.success('已删除'); await loadDocuments() } catch {} }
function formatSize(bytes: number) { if (bytes < 1024) return `${bytes} B`; if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`; return `${(bytes / 1024 / 1024).toFixed(1)} MB` }
function formatDate(dateStr: string) { return new Date(dateStr).toLocaleString('zh-CN', { year: 'numeric', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) }
function statusLabel(status: string) { return status === 'failed' ? { text: '上传失败', cls: 'border-[#f1c7cc] bg-[#fff5f5] text-[#b74752]' } : { text: status === 'indexing' ? '处理中' : '已就绪', cls: 'border-[#cce4df] bg-[#eff8f6] text-[#1a7564]' } }
onMounted(loadDocuments)
</script>

<template>
  <div class="workspace-shell flex h-screen flex-col overflow-hidden"><AppHeader /><main class="flex-1 overflow-y-auto"><div class="page-wrap py-7 sm:py-10"><div class="mb-7 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between"><div class="flex gap-3"><button class="btn-ghost !min-h-9 !px-3" @click="router.push('/admin')">返回</button><div><p class="text-xs font-semibold tracking-[0.12em] text-[#176b5b]">KNOWLEDGE BASE</p><h1 class="display-text mt-1 text-3xl font-semibold text-[#172033]">知识库管理</h1><p class="mt-2 max-w-2xl text-sm leading-6 text-[#718096]">上传并维护校园资料，让知识助手基于可信文档提供回答。</p></div></div><button class="btn-ghost self-start" @click="loadDocuments">刷新列表</button></div>
    <section class="surface mb-6 overflow-hidden"><div class="border-b border-[#e5ebf2] px-5 py-4 sm:px-6"><h2 class="text-base font-semibold text-[#26354a]">添加知识文档</h2><p class="mt-1 text-xs text-[#8491a3]">支持 PDF / TXT / MD / DOCX，单个文件最大 50MB</p></div><label class="m-4 block cursor-pointer rounded-2xl border-2 border-dashed border-[#b9d8d2] bg-[#f4fbf9] px-5 py-10 text-center transition hover:border-[#54a997] hover:bg-[#edf8f5] sm:m-6"><p class="text-sm font-semibold text-[#24685c]">{{ uploading ? '文档上传中…' : '点击选择或拖拽文件至此处' }}</p><p class="mt-2 text-xs text-[#6f8e8a]">建议将 PPT 转换为 PDF，以获得更稳定的文档解析效果</p><input type="file" class="hidden" accept=".pdf,.txt,.md,.docx,.doc" :disabled="uploading" @change="handleUpload"></label></section>
    <section class="surface overflow-hidden"><div class="flex items-center justify-between border-b border-[#e5ebf2] px-5 py-4 sm:px-6"><h2 class="text-base font-semibold text-[#26354a]">已收录文档</h2><span class="rounded-lg bg-[#f1f5f9] px-2.5 py-1 text-xs text-[#718096]">{{ documents.length }} 个文档</span></div><div v-if="loading" class="p-12 text-center text-sm text-[#718096]">加载中…</div><div v-else-if="!documents.length" class="p-14 text-center"><p class="text-sm font-medium text-[#526174]">还没有上传任何文档</p><p class="mt-1 text-xs text-[#8a97a8]">上传第一份资料，帮助助手建立可信知识来源。</p></div><div v-else class="divide-y divide-[#e8edf3]"><div v-for="doc in documents" :key="doc.id" class="flex flex-col gap-3 px-5 py-4 transition hover:bg-[#f8fafc] sm:flex-row sm:items-center sm:px-6"><div class="min-w-0 flex-1"><p class="truncate text-sm font-semibold text-[#344158]" :title="doc.name">{{ doc.name }}</p><div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-[#7a889b]"><span>{{ doc.type || '未知类型' }}</span><span>{{ formatSize(doc.size) }}</span><span>{{ formatDate(doc.uploadedAt) }}</span></div></div><div class="flex items-center gap-2"><span class="rounded-lg border px-2 py-1 text-xs" :class="statusLabel(doc.status).cls">{{ statusLabel(doc.status).text }}</span><button class="btn-ghost !min-h-8 !px-3 !text-xs hover:!border-[#efbdc3] hover:!text-[#b74752]" @click="handleDelete(doc.id, doc.name)">删除</button></div></div></div></section>
  </div></main></div>
</template>
