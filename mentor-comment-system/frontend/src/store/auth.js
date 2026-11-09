import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '../api/auth'
import { getToken, setToken, clearToken } from '../api/request'
import * as auditApi from '../api/audit'

/**
 * 登录态。token 存 localStorage，用户档案放内存。
 * 刷新页面后若 token 还在但 user 为空，由 router 守卫调用 ensureLoaded() 重新拉 /auth/me。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken())
  const user = ref(null)
  const pendingCount = ref(0)

  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const isTeacher = computed(() => user.value?.role === 'TEACHER')
  const isStudent = computed(() => user.value?.role === 'STUDENT')
  const roleLabel = computed(() => user.value?.roleLabel || '')

  /** 各角色登录后的首页 */
  const homePath = computed(() => {
    if (isAdmin.value) return '/dashboard'
    if (isTeacher.value) return '/comments'
    if (isStudent.value) return '/my-comments'
    return '/login'
  })

  async function doLogin(form) {
    const data = await authApi.login(form)
    token.value = data.token
    setToken(data.token)
    user.value = data
    return data
  }

  async function ensureLoaded() {
    if (!token.value) return null
    if (user.value) return user.value
    const data = await authApi.me()
    user.value = data
    return data
  }

  async function refreshPending() {
    if (!isAdmin.value) return
    try {
      const r = await auditApi.pendingCount()
      pendingCount.value = r.pending || 0
    } catch (e) {
      /* 静默失败，不打断主流程 */
    }
  }

  /**
   * 只清本地登录态，不调登出接口。
   *
   * 用于「后端已经把会话踢掉了」的场景（修改密码成功即 logout）。
   * 此时再调 /auth/logout 会拿到 401，被响应拦截器转成「登录已失效」的警告，
   * 把「密码修改成功」的提示挤掉——用户看到的是报错，会以为改密失败。
   */
  function clearSession() {
    token.value = ''
    user.value = null
    pendingCount.value = 0
    clearToken()
  }

  async function doLogout() {
    try {
      await authApi.logout()
    } catch (e) {
      /* 忽略登出接口异常 */
    }
    clearSession()
  }

  return {
    token,
    user,
    pendingCount,
    isAdmin,
    isTeacher,
    isStudent,
    roleLabel,
    homePath,
    doLogin,
    ensureLoaded,
    refreshPending,
    clearSession,
    doLogout
  }
})
