import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/api/auth'
import * as authApi from '@/api/auth'
import { useChatStore } from '@/stores/chat'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const user = ref<User | null>(loadUser())

  const isLoggedIn = computed(() => !!token.value)
  const isStaff = computed(() => {
    const role = user.value?.role
    return role === 'counselor' || role === 'admin' || role === 'super_admin'
  })
  const isAdmin = computed(() => {
    const role = user.value?.role
    return role === 'admin' || role === 'super_admin'
  })

  function loadUser(): User | null {
    try {
      const raw = localStorage.getItem('user')
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  }

  async function login(studentId: string, password: string) {
    const result = await authApi.login({ studentId, password })
    persistLogin(result)
    return result
  }

  async function loginWithYibanTestUser() {
    const result = await authApi.loginWithYibanTestUser()
    persistLogin(result)
    return result
  }

  function persistLogin(result: authApi.LoginResult) {
    token.value = result.token
    user.value = result.user
    localStorage.setItem('token', result.token)
    localStorage.setItem('user', JSON.stringify(result.user))
  }

  async function fetchProfile() {
    if (!token.value) return null
    try {
      const profile = await authApi.fetchMe()
      user.value = profile
      localStorage.setItem('user', JSON.stringify(profile))
      return profile
    } catch {
      logout()
      return null
    }
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    useChatStore().reset()
  }

  return { token, user, isLoggedIn, isStaff, isAdmin, login, loginWithYibanTestUser, fetchProfile, logout }
})
