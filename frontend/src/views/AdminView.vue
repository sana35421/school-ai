<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ChatLineRound, User, Document } from '@element-plus/icons-vue'
import AppHeader from '@/components/AppHeader.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import * as adminApi from '@/api/admin'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const students = ref<adminApi.StudentOverview[]>([])
const conversations = ref<any[]>([])
const messages = ref<any[]>([])
const selectedStudentId = ref<number | null>(null)
const selectedConversationId = ref('')
const loading = ref(false)
const searchKeyword = ref('')
const filteredStudents = computed(() => { const kw = searchKeyword.value.trim().toLowerCase(); return !kw ? students.value : students.value.filter(s => s.realName.toLowerCase().includes(kw) || s.studentId.includes(kw)) })
const filteredConversations = computed(() => !selectedStudentId.value ? conversations.value : conversations.value.filter(c => c.userId === selectedStudentId.value))
async function loadStudents() { loading.value = true; try { students.value = await adminApi.getClassStudents() } catch { ElMessage.error('加载学生列表失败') } finally { loading.value = false } }
async function loadConversations(studentId?: number) { loading.value = true; try { conversations.value = await adminApi.getClassConversations(studentId, 7) } catch { ElMessage.error('加载对话列表失败') } finally { loading.value = false } }
async function selectStudent(studentId: number | null) { selectedStudentId.value = studentId; selectedConversationId.value = ''; messages.value = []; await loadConversations(studentId ?? undefined) }
async function selectConversation(convId: string) { selectedConversationId.value = convId; try { messages.value = await adminApi.getClassMessages(convId) } catch { ElMessage.error('加载对话详情失败') } }
function formatTime(dateStr: string) { return new Date(dateStr).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) }
onMounted(async () => { await userStore.fetchProfile(); await loadStudents(); await loadConversations() })
</script>

<template>
  <div class="workspace-shell flex h-screen flex-col overflow-hidden"><AppHeader /><section class="border-b border-[#dce4ee] bg-white"><div class="page-wrap flex items-center justify-between gap-4 py-4 sm:py-5"><div class="flex min-w-0 items-center gap-3"><button class="btn-ghost !min-h-9 !px-3" @click="router.push('/chat')"><ArrowLeft :size="16" /><span class="hidden sm:inline">返回工作台</span></button><div class="min-w-0"><p class="text-xs font-semibold tracking-[0.12em] text-[#176b5b]">CONVERSATION REVIEW</p><h1 class="display-text truncate text-2xl font-semibold text-[#172033]">班级对话审计</h1><p class="mt-1 hidden text-xs text-[#7d8a9c] sm:block">{{ userStore.user?.classId ? '本班' : '全校' }} · {{ students.length }} 名学生 · {{ conversations.length }} 条对话</p></div></div><button v-if="userStore.isAdmin" class="btn-primary !min-h-9 !px-3" @click="router.push('/admin/knowledge')"><Document :size="16" />知识库管理</button></div></section>
    <main class="flex min-h-0 flex-1 overflow-hidden bg-[#f7f9fc]"><aside class="flex w-64 shrink-0 flex-col border-r border-[#dce4ee] bg-white max-md:w-52"><div class="border-b border-[#e6ecf3] p-3"><input v-model="searchKeyword" placeholder="搜索姓名或学号" class="input-field !min-h-9 !px-3 text-sm"></div><div class="flex-1 overflow-y-auto"><button class="w-full border-b border-[#edf1f5] px-4 py-3 text-left transition" :class="!selectedStudentId ? 'bg-[#e7f4f1]' : 'hover:bg-[#f6f8fb]'" @click="selectStudent(null)"><p class="text-sm font-semibold text-[#334158]">全部学生</p><p class="mt-1 text-[11px] text-[#8491a3]">{{ students.length }} 人</p></button><button v-for="s in filteredStudents" :key="s.id" class="flex w-full gap-2 border-b border-[#edf1f5] px-3 py-3 text-left transition" :class="selectedStudentId === s.id ? 'bg-[#e7f4f1]' : 'hover:bg-[#f6f8fb]'" @click="selectStudent(s.id)"><span class="grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-[#e4f1ee] text-xs font-bold text-[#176b5b]">{{ s.realName?.slice(0, 1) || s.studentId.slice(-2) }}</span><span class="min-w-0"><span class="block truncate text-sm font-semibold text-[#405069]">{{ s.realName }}</span><span class="mt-0.5 block text-[11px] text-[#8391a5]">{{ s.studentId }} · {{ s.conversationCount }} 条</span></span></button><div v-if="!students.length && !loading" class="p-8 text-center text-sm text-[#8592a4]"><User :size="26" class="mx-auto mb-2" />暂无学生数据</div></div></aside>
      <aside class="flex w-72 shrink-0 flex-col border-r border-[#dce4ee] bg-[#fbfcfe] max-md:hidden"><div class="border-b border-[#e6ecf3] px-4 py-4"><p class="text-sm font-semibold text-[#334158]">对话列表</p><p class="mt-1 text-[11px] text-[#8491a3]">{{ filteredConversations.length }} 条对话</p></div><div class="flex-1 overflow-y-auto"><button v-for="conv in filteredConversations" :key="conv.conversationId" class="w-full border-b border-[#edf1f5] px-4 py-3 text-left transition" :class="selectedConversationId === conv.conversationId ? 'bg-[#e7f4f1]' : 'hover:bg-white'" @click="selectConversation(conv.conversationId)"><p class="line-clamp-2 text-sm font-medium leading-5 text-[#405069]">{{ conv.title || '新对话' }}</p><p class="mt-1 text-[11px] text-[#8491a3]">{{ formatTime(conv.lastActiveAt) }}</p></button><div v-if="!filteredConversations.length" class="p-8 text-center text-sm text-[#8592a4]"><ChatLineRound :size="26" class="mx-auto mb-2" />暂无对话</div></div></aside>
      <section class="min-w-0 flex-1 overflow-y-auto"><div class="mx-auto max-w-4xl px-4 py-6 sm:px-8"><template v-if="selectedConversationId && messages.length"><div class="mb-6 border-b border-[#dce4ee] pb-4"><p class="text-xs font-semibold tracking-[0.12em] text-[#176b5b]">CONVERSATION DETAIL</p><h2 class="display-text mt-1 text-xl font-semibold text-[#26354a]">{{ messages.find(m => m.role === 'user')?.content?.slice(0, 40) || '对话内容' }}</h2></div><div class="space-y-7"><ChatMessage v-for="(m, idx) in messages" :key="idx" :role="m.role" :content="m.content" :attachments="m.attachments" /></div></template><div v-else class="grid min-h-[420px] place-items-center text-center"><div><span class="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-[#e8f4f1] text-[#398777]"><ChatLineRound :size="26" /></span><p class="display-text mt-4 text-xl font-semibold text-[#405069]">选择一条对话查看详情</p><p class="mt-2 text-sm text-[#8491a3]">先选择学生，再选择需要审阅的对话。</p></div></div></div></section>
    </main></div>
</template>
