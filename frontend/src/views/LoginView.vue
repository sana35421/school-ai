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
const form = reactive({ studentId: '', password: '' })
const loading = ref(false)
const yibanLoading = ref(false)
const errorMsg = ref('')

async function handleYibanLogin() {
  errorMsg.value = ''
  yibanLoading.value = true
  window.open(import.meta.env.VITE_YIBAN_LOGIN_URL, '_blank', 'noopener,noreferrer')
  try {
    await new Promise((resolve) => window.setTimeout(resolve, 5000))
    chatStore.reset()
    await userStore.loginWithYibanTestUser()
    ElMessage.success(`欢迎，${userStore.user?.realName || '同学'}`)
    await router.push(normalizeRedirect(route.query.redirect))
  } catch (err: any) { errorMsg.value = err.message || '易班登录失败，请重试' } finally { yibanLoading.value = false }
}
function normalizeRedirect(value: unknown) { return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : '/chat' }
async function handleLogin() {
  if (!form.studentId.trim() || !form.password.trim()) { errorMsg.value = '请输入学号和密码'; return }
  errorMsg.value = ''
  loading.value = true
  try {
    chatStore.reset()
    await userStore.login(form.studentId.trim(), form.password)
    ElMessage.success(`欢迎回来，${userStore.user?.realName || '同学'}`)
    router.push(normalizeRedirect(route.query.redirect))
  } catch (err: any) { errorMsg.value = err.message || '登录失败，请检查学号密码' } finally { loading.value = false }
}
function handleKeydown(e: KeyboardEvent) { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleLogin() } }
</script>

<template>
  <main class="relative grid min-h-screen place-items-center overflow-hidden bg-[#f3f7f8] px-4 py-8">
    <div class="absolute -left-32 -top-32 h-96 w-96 rounded-full bg-[#bce3db] blur-3xl opacity-60"></div><div class="absolute -bottom-36 -right-24 h-96 w-96 rounded-full bg-[#cddcf1] blur-3xl opacity-70"></div>
    <div class="relative grid w-full max-w-5xl overflow-hidden rounded-3xl border border-white/80 bg-white/85 shadow-[0_28px_80px_rgba(31,56,82,0.16)] backdrop-blur md:grid-cols-[1.05fr_0.95fr]">
      <section class="hidden bg-[#176b5b] p-10 text-white md:flex md:flex-col"><div class="grid h-12 w-12 place-items-center rounded-2xl bg-white/15 text-xl font-bold">知</div><div class="my-auto"><p class="text-xs font-semibold tracking-[0.18em] text-[#b8dfd8]">CAMPUS KNOWLEDGE WORKSPACE</p><h1 class="display-text mt-5 text-4xl font-semibold leading-tight">让每一次提问，<br>都成为更好的学习。</h1><p class="mt-5 max-w-sm text-sm leading-7 text-[#d6eeea]">面向校园学习、竞赛备赛与事务咨询的一体化知识工作台。</p></div><p class="text-xs text-[#b8dfd8]">可信知识 · 即问即答 · 学习沉淀</p></section>
      <section class="p-6 sm:p-10"><div class="mb-8 flex items-center gap-3 md:hidden"><div class="grid h-10 w-10 place-items-center rounded-xl bg-[#176b5b] font-bold text-white">知</div><div><h1 class="display-text text-xl font-semibold text-[#172033]">校园知识工作台</h1><p class="text-[11px] tracking-[0.1em] text-[#8492a4]">CAMPUS KNOWLEDGE WORKSPACE</p></div></div><div><p class="text-sm font-semibold text-[#176b5b]">欢迎回来</p><h2 class="display-text mt-2 text-3xl font-semibold text-[#172033]">登录你的工作台</h2><p class="mt-2 text-sm leading-6 text-[#718096]">使用易班或校内账户，继续你的学习探索。</p></div>
        <button type="button" class="mt-7 flex w-full items-center justify-center rounded-xl bg-[#16a6e0] px-4 py-3 text-sm font-semibold text-white transition hover:bg-[#1195ca] disabled:cursor-not-allowed disabled:opacity-50" :disabled="yibanLoading || loading" @click="handleYibanLogin">{{ yibanLoading ? '易班登录中…' : '易班账号登录' }}</button>
        <div class="my-6 flex items-center gap-3"><span class="h-px flex-1 bg-[#e4ebf1]"></span><span class="text-xs text-[#8996a7]">或使用校内账户</span><span class="h-px flex-1 bg-[#e4ebf1]"></span></div>
        <form class="space-y-4" @submit.prevent="handleLogin" @keydown="handleKeydown"><label class="block text-sm font-medium text-[#405069]">学号 / 工号<input v-model="form.studentId" type="text" placeholder="例如 20220101" class="input-field mt-2" autocomplete="username" autofocus></label><label class="block text-sm font-medium text-[#405069]">密码<input v-model="form.password" type="password" placeholder="请输入密码" class="input-field mt-2" autocomplete="current-password"></label><p v-if="errorMsg" class="rounded-xl border border-[#f2ccd0] bg-[#fff5f5] px-3 py-2.5 text-sm text-[#b74752]">{{ errorMsg }}</p><button type="submit" class="btn-primary mt-2 w-full" :disabled="loading || yibanLoading">{{ loading ? '登录中…' : '进入工作台' }}</button></form>
        <p class="mt-6 border-t border-[#e8edf2] pt-4 text-xs leading-5 text-[#8491a3]">忘记密码？请联系所在班级导员或系统管理员重置。</p>
      </section>
    </div><p class="relative mt-6 text-center text-xs text-[#8b98a9]">Powered by Dify · DeepSeek · Vue 3</p>
  </main>
</template>
