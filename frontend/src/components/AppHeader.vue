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
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'info',
    })
    userStore.logout()
    router.push('/login')
  } catch {
    // 用户取消
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
  <header class="h-14 px-6 flex items-center justify-between bg-cream/80 backdrop-blur border-b border-border sticky top-0 z-10">
    <div class="flex items-center gap-3">
      <div class="w-8 h-8 rounded bg-ink flex items-center justify-center">
        <span class="display-text text-terracotta font-semibold text-lg italic">问</span>
      </div>
      <div>
        <h1 class="display-text text-base font-semibold text-ink leading-none">校园智能问答助手</h1>
        <p class="text-2xs text-ink-mute font-mono mt-0.5 leading-none">Campus AI · powered by Dify + DeepSeek</p>
      </div>
    </div>

    <div class="flex items-center gap-2">
      <button
        v-if="showAdminLink"
        class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm text-ink-soft hover:bg-paper transition-colors"
        @click="goToAdmin"
      >
        <Setting :size="15" />
        <span>管理后台</span>
      </button>
      <div class="flex items-center gap-2.5 pl-3 ml-1 border-l border-border">
        <div class="text-right hidden sm:block">
          <p class="text-sm text-ink leading-none">{{ userStore.user?.realName || userStore.user?.username }}</p>
          <p class="text-2xs text-ink-mute font-mono mt-0.5 leading-none">
            {{ roleLabel(userStore.user?.role) }} · {{ userStore.user?.studentId }}
          </p>
        </div>
        <button
          class="w-9 h-9 rounded-full bg-sage-100 text-sage-700 flex items-center justify-center font-semibold text-sm hover:bg-sage-200 transition-colors"
          @click="handleLogout"
          title="退出登录"
        >
          {{ (userStore.user?.realName || userStore.user?.username || '?').slice(0, 1) }}
        </button>
        <button
          class="p-2 rounded-md text-ink-mute hover:text-ink hover:bg-paper transition-colors"
          @click="handleLogout"
          title="退出登录"
        >
          <SwitchButton :size="16" />
        </button>
      </div>
    </div>
  </header>
</template>