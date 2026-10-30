import request from './request'

/**
 * AI 预审（L1 规则引擎）。
 *
 * 注意：这里的接口**都不会改变点评状态**，只产出建议。
 * 审核动作仍然走 audit.js 里的 approve / return / reject。
 */

// 单条预审：默认命中缓存则复用；force=true 强制重算
export const precheckOne = (id, force = false) =>
  request.post(`/ai/precheck/${id}`, null, { params: { force } })

// 只读缓存：未预审过返回 null（不触发计算）
export const precheckCached = (id) => request.get(`/ai/precheck/${id}`)

// 批量预审：列表页一次预审当前页，最多 50 条
export const precheckBatch = (ids) => request.post('/ai/precheck/batch', { ids })

// 规则目录（编码 / 名称 / 权重 / 级别）
export const precheckRules = () => request.get('/ai/precheck/rules')
