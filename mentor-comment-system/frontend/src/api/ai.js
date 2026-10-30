import request from './request'

/**
 * 摘要建议。
 *
 * `summary({ content, topic })` 用于编辑中的草稿，不落库；
 * `summary({ commentId })` 用于已保存的点评。
 * 后端无论走模型还是规则，返回结构一致，前端不需要分支。
 */
export const summary = (data) => request.post('/ai/summary', data)

/** 当前 AI 能力状态：llmAvailable=false 时按钮文案改为"规则生成" */
export const status = () => request.get('/ai/status')

// ---------------- 配置（仅教务） ----------------

/** 读取配置。apiKey 只返回掩码，明文永不下发 */
export const getConfig = () => request.get('/ai/config')

/**
 * 保存配置。
 * 字段为 null 表示不修改；apiKey 传 '' 表示清除并回落环境变量。
 */
export const saveConfig = (data) => request.put('/ai/config', data)

/** 连通性自检：真发一次请求，区分"配置齐了"与"真能通" */
export const testConfig = () => request.post('/ai/config/test')

/**
 * 拉取该账号可用的模型列表（OpenAI 兼容 GET /v1/models）。
 * 需要先保存并启用 Key —— 后端会用当前生效的密钥去问服务端。
 */
export const listModels = () => request.get('/ai/models')

/**
 * AI 小助手对话（多轮）。
 * 场景由后端按登录角色决定，前端不传也不该传。
 * @param {{messages: Array<{role:'user'|'assistant', content:string}>}} data
 */
export const chat = (data) => request.post('/ai/chat', data)
