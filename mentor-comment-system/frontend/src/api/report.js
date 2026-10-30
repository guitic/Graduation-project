import request from './request'

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
