import request from './request'

export const pendingPage = (params) => request.get('/audit/pending', { params })
export const unpublishedPage = (params) => request.get('/audit/unpublished', { params })
export const publishedPage = (params) => request.get('/audit/published', { params })
export const pendingCount = () => request.get('/audit/pending-count')

// approve: { opinion, publish } —— publish=true 通过并发布；false 仅通过（待发布）
export const approve = (id, data) => request.post(`/audit/${id}/approve`, data)
export const returnBack = (id, data) => request.post(`/audit/${id}/return`, data)
export const reject = (id, data) => request.post(`/audit/${id}/reject`, data)
export const publish = (id, reason) => request.post(`/audit/${id}/publish`, { reason })
export const unpublish = (id, reason) => request.post(`/audit/${id}/unpublish`, { reason })
export const history = (id) => request.get(`/audit/${id}/history`)
