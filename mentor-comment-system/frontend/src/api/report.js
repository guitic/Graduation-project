import request, { download } from './request'

// scope: { term, teacherId, clazzId, studentId, startDate, endDate, months }
export const dashboard = (scope) => request.get('/report/dashboard', { params: scope })

// 导师端个人画像。不接受 teacherId —— 后端强制取登录态，前端传了也无效
export const myDashboard = (scope) => request.get('/report/my-dashboard', { params: scope })
export const sentiment = (scope) => request.get('/report/sentiment', { params: scope })
export const trend = (scope) => request.get('/report/trend', { params: scope })
export const auditStat = () => request.get('/report/audit-stat')
export const wordCloud = (scope) => request.get('/report/wordcloud', { params: scope })
export const wordDetail = (word, scope) =>
  request.get('/report/word-detail', { params: { word, ...scope } })
export const trendPoint = (month, scope) =>
  request.get('/report/trend-point', { params: { month, ...scope } })
export const commentSentiment = (id) => request.get(`/report/comment/${id}/sentiment`)
export const rebuild = () => request.post('/report/rebuild')

/**
 * KPI 卡下钻明细。
 *
 * kpi 取 total / published / pending / unpublished / returned / rejected / lowConfidence，
 * 口径由后端按「卡片同一套条件」计算，因此返回的 total 必然等于卡片上的数字。
 * label / caliber 也由后端下发，前端不硬编码口径文案。
 */
export const kpiDetail = (kpi, scope, page = 1, size = 10) =>
  request.get('/report/kpi-detail', { params: { kpi, page, size, ...scope } })

/**
 * 导出报表（xlsx，汇总 + 明细两个 sheet）。
 * 走 download 而不是 request：响应是二进制，不是 { code, msg, data }。
 * 范围由学期/导师/班级决定，不受趋势图「近 N 个月」影响。
 */
export const exportReport = (scope) =>
  download('/report/export', scope, '学业导师点评分析报表.xlsx')
