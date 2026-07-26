<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ChatLineRound, User, Document, Delete } from '@element-plus/icons-vue'
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
const selectedConversationId = ref<string>('')
const loading = ref(false)
const searchKeyword = ref('')

const filteredStudents = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return students.value
  return students.value.filter(
    (s) =>
      s.realName.toLowerCase().includes(kw) ||
      s.studentId.includes(kw)
  )
})

const filteredConversations = computed(() => {
  if (!selectedStudentId.value) return conversations.value
  return conversations.value.filter((c) => c.userId === selectedStudentId.value)
})

async function loadStudents() {
  loading.value = true
  try {
    students.value = await adminApi.getClassStudents()
  } catch {
    ElMessage.error('加载学生列表失败')
  } finally {
    loading.value = false
  }
}

async function loadConversations(studentId?: number) {
  loading.value = true
  try {
    conversations.value = await adminApi.getClassConversations(studentId, 7)
  } catch {
    ElMessage.error('加载对话列表失败')
  } finally {
    loading.value = false
  }
}

async function selectStudent(studentId: number | null) {
  selectedStudentId.value = studentId
  selectedConversationId.value = ''
  messages.value = []
  await loadConversations(studentId ?? undefined)
}

async function selectConversation(convId: string) {
  selectedConversationId.value = convId
  try {
    messages.value = await adminApi.getClassMessages(convId)
  } catch {
    ElMessage.error('加载对话详情失败')
  }
}

function formatTime(dateStr: string): string {
  return new Date(dateStr).toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function goBack() {
  router.push('/chat')
}

onMounted(async () => {
  await userStore.fetchProfile()
  await loadStudents()
  await loadConversations()
})
</script>

<template>
  <div class="h-screen flex flex-col">
    <AppHeader />

    <div class="flex-1 flex flex-col overflow-hidden bg-paper">
      <!-- 顶部 Tab 区 -->
      <div class="bg-cream border-b border-border px-8 py-5">
        <div class="max-w-7xl mx-auto flex items-center justify-between">
          <div class="flex items-center gap-4">
            <button class="p-2 rounded-md hover:bg-paper text-ink-soft" @click="goBack">
              <ArrowLeft :size="18" />
            </button>
            <div>
              <h1 class="display-text text-2xl font-semibold text-ink">班级对话审计</h1>
              <p class="text-xs text-ink-mute mt-0.5 font-mono">
                班级：<span class="text-ink">{{ userStore.user?.classId ? '本班' : '全校' }}</span>
                · 学生数：<span class="text-ink">{{ students.length }}</span>
                · 对话数：<span class="text-ink">{{ conversations.length }}</span>
              </p>
            </div>
          </div>
          <button
            v-if="userStore.isAdmin"
            class="btn-primary"
            @click="router.push('/admin/knowledge')"
          >
            <Document :size="14" class="mr-1.5" />
            管理知识库
          </button>
        </div>
      </div>

      <!-- 三栏布局 -->
      <div class="flex-1 flex overflow-hidden">
        <!-- 左：学生列表 -->
        <div class="w-72 border-r border-border bg-cream flex flex-col">
          <div class="p-4 border-b border-border">
            <input
              v-model="searchKeyword"
              placeholder="搜索姓名或学号…"
              class="input-field text-sm"
            />
          </div>
          <div class="flex-1 overflow-y-auto">
            <button
              class="w-full text-left px-4 py-3 border-b border-border transition-colors"
              :class="!selectedStudentId ? 'bg-terracotta-50' : 'hover:bg-paper'"
              @click="selectStudent(null)"
            >
              <p class="text-sm font-medium text-ink" :class="!selectedStudentId ? 'text-terracotta-700' : ''">
                全部学生
              </p>
              <p class="text-2xs text-ink-mute mt-0.5 font-mono">{{ students.length }} 人</p>
            </button>
            <button
              v-for="s in filteredStudents"
              :key="s.id"
              class="w-full text-left px-4 py-3 border-b border-border/60 transition-colors"
              :class="selectedStudentId === s.id ? 'bg-terracotta-50' : 'hover:bg-paper'"
              @click="selectStudent(s.id)"
            >
              <div class="flex items-start gap-2.5">
                <div class="w-8 h-8 rounded-full bg-sage-100 text-sage-700 flex items-center justify-center font-semibold text-xs flex-shrink-0">
                  {{ s.realName?.slice(0, 1) || s.studentId.slice(-2) }}
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-sm text-ink font-medium truncate" :class="selectedStudentId === s.id ? 'text-terracotta-700' : ''">
                    {{ s.realName }}
                  </p>
                  <p class="text-2xs text-ink-mute font-mono mt-0.5">{{ s.studentId }}</p>
                  <p class="text-2xs text-ink-mute mt-0.5">
                    {{ s.conversationCount }} 条对话 · 最近 {{ s.lastActiveAt ? formatTime(s.lastActiveAt) : '未活跃' }}
                  </p>
                </div>
              </div>
            </button>
            <div v-if="students.length === 0 && !loading" class="p-8 text-center">
              <User :size="28" class="text-border-strong mx-auto mb-2" />
              <p class="text-sm text-ink-mute">暂无学生数据</p>
            </div>
          </div>
        </div>

        <!-- 中：对话列表 -->
        <div class="w-80 border-r border-border bg-paper flex flex-col">
          <div class="px-5 py-4 border-b border-border">
            <h3 class="display-text font-medium text-ink">对话列表</h3>
            <p class="text-2xs text-ink-mute mt-0.5 font-mono">{{ filteredConversations.length }} 条对话</p>
          </div>
          <div class="flex-1 overflow-y-auto">
            <button
              v-for="conv in filteredConversations"
              :key="conv.conversationId"
              class="w-full text-left px-5 py-3 border-b border-border/60 transition-colors"
              :class="selectedConversationId === conv.conversationId ? 'bg-terracotta-50' : 'hover:bg-paper'"
              @click="selectConversation(conv.conversationId)"
            >
              <p class="text-sm text-ink font-medium line-clamp-2 leading-snug" :class="selectedConversationId === conv.conversationId ? 'text-terracotta-700' : ''">
                {{ conv.title || '新对话' }}
              </p>
              <p class="text-2xs text-ink-mute mt-1 font-mono">
                {{ formatTime(conv.lastActiveAt) }}
              </p>
            </button>
            <div v-if="filteredConversations.length === 0" class="p-8 text-center">
              <ChatLineRound :size="28" class="text-border-strong mx-auto mb-2" />
              <p class="text-sm text-ink-mute">一周内暂无对话</p>
            </div>
          </div>
        </div>

        <!-- 右：对话详情 -->
        <div class="flex-1 bg-cream overflow-y-auto">
          <div class="max-w-3xl mx-auto px-8 py-8">
            <template v-if="selectedConversationId && messages.length > 0">
              <div class="mb-6 pb-4 border-b border-border">
                <p class="text-2xs text-ink-mute font-mono uppercase tracking-wider">对话内容</p>
                <h2 class="display-text text-xl font-medium text-ink mt-1">
                  {{ messages.find((m) => m.role === 'user')?.content?.slice(0, 40) || '对话' }}
                </h2>
              </div>
              <div class="space-y-6">
                <ChatMessage
                  v-for="(m, idx) in messages"
                  :key="idx"
                  :role="m.role"
                  :content="m.content"
                />
              </div>
            </template>
            <div v-else class="flex items-center justify-center h-full min-h-[400px]">
              <div class="text-center">
                <ChatLineRound :size="48" class="text-border-strong mx-auto mb-4" />
                <p class="display-text text-lg text-ink-soft">选择一条对话查看详情</p>
                <p class="text-sm text-ink-mute mt-1">左侧选择学生，中间选择对话</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>