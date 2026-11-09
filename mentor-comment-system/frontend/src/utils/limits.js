import { reactive } from 'vue'
import { limits as limitsApi } from '../api/meta'

/**
 * 门槛常量（前端唯一读取点）。
 *
 * 为什么不在页面里写死 5：
 * 这个数字原本前后端各写一份，后端拒绝、前端放行时用户看到的是「点了提交没反应」。
 * 现在统一由 `GET /api/meta/limits` 下发，后端改常量、前端文案自动跟随。
 *
 * **审核意见的字数门槛已取消**（只保留 500 字上限，由后端校验），
 * 因此这里不再有 opinion 一组数字——"至少 N 字"的文案在系统里已经不存在了。
 *
 * DEFAULTS 只在接口不可用时兜底，数值必须与后端 analysis.min-sample 默认值一致。
 */
const DEFAULTS = {
  minSample: 5
}

export const limits = reactive({
  minSample: DEFAULTS.minSample,
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
