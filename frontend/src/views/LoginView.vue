<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const chatStore = useChatStore()

const form = reactive({
  studentId: '',
  password: '',
})
const loading = ref(false)
const errorMsg = ref('')

async function handleLogin() {
  if (!form.studentId.trim() || !form.password.trim()) {
    errorMsg.value = '请输入学号和密码'
    return
  }
  errorMsg.value = ''
  loading.value = true
  try {
    chatStore.reset()
    await userStore.login(form.studentId.trim(), form.password)
    ElMessage.success(`欢迎回来，${userStore.user?.realName || '同学'}`)
    const redirect = (route.query.redirect as string) || '/chat'
    router.push(redirect)
  } catch (err: any) {
    errorMsg.value = err.message || '登录失败，请检查学号密码'
  } finally {
    loading.value = false
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleLogin()
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center px-4 bg-slate-50">
    <div class="w-full max-w-sm">
      <div class="text-center mb-8">
        <div class="w-14 h-14 mx-auto mb-4 rounded-2xl bg-blue-500 flex items-center justify-center shadow-lg shadow-blue-200">
          <span class="text-white text-2xl font-bold">问</span>
        </div>
        <h1 class="text-2xl font-semibold text-slate-800">
          校园智能问答助手
        </h1>
        <p class="text-sm text-slate-400 mt-1">
          Campus AI Assistant
        </p>
      </div>

      <div class="bg-white rounded-2xl shadow-sm border border-slate-200 p-6">
        <h2 class="text-lg font-semibold text-slate-700 mb-1">登录</h2>
        <p class="text-sm text-slate-400 mb-6">使用学号或工号进入系统</p>

        <form class="space-y-4" @submit.prevent="handleLogin" @keydown="handleKeydown">
          <div>
            <label class="block text-sm font-medium text-slate-600 mb-1.5">
              学号 / 工号
            </label>
            <input
              v-model="form.studentId"
              type="text"
              placeholder="例如 20220101"
              class="w-full rounded-xl border border-slate-200 px-4 py-2.5 text-sm text-slate-700 placeholder:text-slate-400 focus:outline-none focus:border-blue-400 focus:ring-1 focus:ring-blue-100 transition-all"
              autocomplete="username"
              autofocus
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-slate-600 mb-1.5">
              密码
            </label>
            <input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              class="w-full rounded-xl border border-slate-200 px-4 py-2.5 text-sm text-slate-700 placeholder:text-slate-400 focus:outline-none focus:border-blue-400 focus:ring-1 focus:ring-blue-100 transition-all"
              autocomplete="current-password"
            />
          </div>

          <div
            v-if="errorMsg"
            class="text-sm text-red-500 bg-red-50 border border-red-100 rounded-xl px-4 py-2.5"
          >
            {{ errorMsg }}
          </div>

          <button
            type="submit"
            class="w-full rounded-xl bg-blue-500 text-white font-medium text-sm py-2.5 hover:bg-blue-600 active:scale-[0.98] transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            :disabled="loading"
          >
            <span v-if="!loading">登录</span>
            <span v-else>登录中…</span>
          </button>
        </form>

        <div class="mt-6 pt-4 border-t border-slate-100">
          <p class="text-xs text-slate-400 leading-relaxed">
            忘记密码？请联系所在班级的<span class="font-medium text-slate-600">导员</span>或<span class="font-medium text-slate-600">系统管理员</span>重置。
          </p>
        </div>
      </div>

      <p class="text-center text-xs text-slate-300 mt-6">
        Powered by Dify · DeepSeek · Vue 3
      </p>
    </div>
  </div>
</template>