<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Setting, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

interface Props {
  showAdminLink?: boolean
}

withDefaults(defineProps<Props>(), {
  showAdminLink: false,
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出工作台', {
      confirmButtonText: '退出登录',
      cancelButtonText: '暂不退出',
      type: 'info',
    })
    userStore.logout()
    router.push('/login')
  } catch {
  }
}

function goToAdmin() {
  router.push('/admin')
}

const roleLabel = (role?: string) => {
  switch (role) {
    case 'student': return '学生'
    case 'counselor': return '导员'
    case 'admin': return '管理员'
    case 'super_admin': return '超级管理员'
    default: return '访客'
  }
}
</script>

<template>
  <header class="relative z-20 min-h-16 border-b border-[#dce4ee] bg-white/90 backdrop-blur-xl">
    <div class="page-wrap flex min-h-16 items-center justify-between gap-3">
      <RouterLink to="/chat" class="flex min-w-0 items-center gap-3 text-inherit no-underline">
        <div class="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[#176b5b] text-lg font-bold text-white shadow-[0_8px_20px_rgba(23,107,91,0.24)]">知</div>
        <div class="min-w-0">
          <h1 class="display-text truncate text-lg font-semibold text-[#172033]">校园知识工作台</h1>
          <p class="hidden truncate pt-0.5 text-[11px] font-medium tracking-[0.12em] text-[#7b899d] sm:block">CAMPUS KNOWLEDGE WORKSPACE</p>
        </div>
      </RouterLink>

      <div class="flex shrink-0 items-center gap-1.5 sm:gap-3">
        <button
          v-if="showAdminLink"
          class="btn-ghost !min-h-9 !px-3"
          @click="goToAdmin"
        >
          <Setting :size="16" />
          <span class="hidden sm:inline">管理中心</span>
        </button>
        <div class="flex items-center gap-2 border-l border-[#e1e8f0] pl-2 sm:pl-3">
          <div class="hidden text-right md:block">
            <p class="text-sm font-semibold leading-none text-[#26354a]">{{ userStore.user?.realName || userStore.user?.username }}</p>
            <p class="mt-1 text-[11px] text-[#758398]">{{ roleLabel(userStore.user?.role) }} · {{ userStore.user?.studentId }}</p>
          </div>
          <button
            class="grid h-9 w-9 place-items-center rounded-full bg-[#e2f1ee] text-sm font-bold text-[#116052] transition-colors hover:bg-[#cde7e1]"
            @click="handleLogout"
            title="退出登录"
          >
            {{ (userStore.user?.realName || userStore.user?.username || '?').slice(0, 1) }}
          </button>
          <button
            class="grid h-9 w-9 place-items-center rounded-xl text-[#6e7d92] transition-colors hover:bg-[#f1f5f9] hover:text-[#26354a]"
            @click="handleLogout"
            title="退出登录"
          >
            <SwitchButton :size="17" />
          </button>
        </div>
      </div>
    </div>
  </header>
</template>
