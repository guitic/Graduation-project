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

/**
 * 下载二进制文件（报表导出用）。
 *
 * 不能复用上面的 request 实例：它的响应拦截器假定每个响应都是
 * `{ code, msg, data }` 的 JSON，拿到 blob 会直接判为「服务响应异常」。
 *
 * 失败时后端仍走全局异常处理返回统一 JSON，因此这里必须按 content-type
 * 分流——否则会把 `{"code":400,"msg":"..."}` 原样存成 .xlsx，
 * 用户打开文件看到乱码，而界面上没有任何报错。
 */
export async function download(url, params, fallbackName = 'download') {
  let resp
  try {
    resp = await axios.get(url, {
      baseURL: '/api',
      params,
      responseType: 'blob',
      headers: { satoken: getToken() },
      timeout: 60000
    })
  } catch (e) {
    const msg = e?.response?.status === 401 ? '登录已失效，请重新登录' : '导出请求失败，请稍后重试'
    ElMessage.error(msg)
    throw new Error(msg)
  }

  const contentType = resp.headers['content-type'] || ''
  if (contentType.includes('application/json')) {
    let msg = '导出失败'
    try {
      const body = JSON.parse(await resp.data.text())
      msg = body.msg || msg
    } catch (e) {
      /* 响应体不是合法 JSON，沿用默认文案 */
    }
    ElMessage.error(msg)
    throw new Error(msg)
  }

  const filename = parseFilename(resp.headers['content-disposition']) || fallbackName
  const href = URL.createObjectURL(resp.data)
  const a = document.createElement('a')
  a.href = href
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  // 立即 revoke 会让部分浏览器来不及开始下载，延后释放
  setTimeout(() => URL.revokeObjectURL(href), 2000)
  return filename
}

/** 解析 Content-Disposition：优先 filename*=UTF-8''，兼容普通 filename */
function parseFilename(disposition) {
  if (!disposition) return ''
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (star) {
    try {
      return decodeURIComponent(star[1])
    } catch (e) {
      /* 编码异常时退回普通 filename */
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain ? plain[1] : ''
}
