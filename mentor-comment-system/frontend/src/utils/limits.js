import { reactive } from 'vue'
import { limits as limitsApi } from '../api/meta'

/**
 * 门槛常量（前端唯一读取点）。
 *
 * 为什么不在页面里写死 5/10/10/20：
 * 这些数字原本前后端各写一份，后端拒绝、前端放行时用户看到的是「点了提交没反应」，
 * 或者前端拦下后端本可接受的输入。现在统一由 `GET /api/meta/limits` 下发，
 * 后端改常量、前端文案自动跟随，不需要同步改两个仓库。
 *
 * DEFAULTS 只在接口不可用时兜底（例如后端未启动、旧版本无该接口），
 * 数值必须与后端 AuditService 常量 / analysis.min-sample 默认值保持一致。
 */
const DEFAULTS = {
  minSample: 5,
  opinion: {
    approvePublish: 5,
    approveOnly: 10,
    return: 10,
    reject: 20,
    unpublish: 10,
    republish: 5
  }
}

export const limits = reactive({
  minSample: DEFAULTS.minSample,
  opinion: { ...DEFAULTS.opinion },
  loaded: false
})

let pending = null

/** 登录后调用一次；并发调用共享同一个请求 */
export function ensureLimits() {
  if (limits.loaded) return Promise.resolve(limits)
  if (!pending) {
    pending = limitsApi()
      .then((r) => {
        if (r) {
          limits.minSample = r.minSample ?? DEFAULTS.minSample
          Object.assign(limits.opinion, r.opinion || {})
        }
        limits.loaded = true
        return limits
      })
      .catch(() => {
        // 失败时保留兜底值并允许下次重试，不阻塞页面渲染
        pending = null
        return limits
      })
  }
  return pending
}
