import request from './request'

/**
 * 运行时规则（门槛）元数据。
 * 后端 `GET /api/meta/limits` 是审核意见字数与隐私样本门槛的唯一来源。
 */
export const limits = () => request.get('/meta/limits')
