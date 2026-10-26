import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一请求封装。
 * 后端所有响应（含错误）都是 HTTP 200 + { code, msg, data }，
 * code=0 才是成功，因此错误判断必须看 code，而不是看 HTTP status。
 * 鉴权用 Sa-Token，token 放在 satoken 请求头。
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 20000
})

const TOKEN_KEY = 'mc_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}
export function setToken(t) {
  if (t) localStorage.setItem(TOKEN_KEY, t)
}
export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers['satoken'] = token
  }
  return config
})

let redirecting = false

request.interceptors.response.use(
  (resp) => {
    const body = resp.data
    // 非标准 JSON（例如后端未启动时代理返回 HTML）时给出可读提示
    if (body == null || typeof body.code === 'undefined') {
      ElMessage.error('服务响应异常，请确认后端已启动（8090）')
      return Promise.reject(new Error('bad response'))
    }
    if (body.code === 0) {
      return body.data
    }
    if (body.code === 401) {
      if (!redirecting) {
        redirecting = true
        clearToken()
        ElMessage.warning(body.msg || '登录已失效，请重新登录')
        setTimeout(() => {
          window.location.hash = '#/login'
          redirecting = false
        }, 400)
      }
      return Promise.reject(new Error(body.msg || '未登录'))
    }
    ElMessage.error(body.msg || '操作失败')
    return Promise.reject(new Error(body.msg || '操作失败'))
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      clearToken()
      window.location.hash = '#/login'
    } else {
      ElMessage.error(error?.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
