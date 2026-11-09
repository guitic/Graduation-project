import request from './request'

// 导师端
export const pageTeacher = (params) => request.get('/comment/page', { params })
// 教务端（全量）
export const pageAdmin = (params) => request.get('/comment/admin-page', { params })
// 学生端（仅本人已发布）
export const pageStudent = (params) => request.get('/comment/student-page', { params })

export const detail = (id) => request.get(`/comment/${id}`)
export const save = (data) => request.post('/comment', data)
export const update = (id, data) => request.put(`/comment/${id}`, data)
export const submit = (id) => request.post(`/comment/${id}/submit`)
export const remove = (id) => request.delete(`/comment/${id}`)

// 学生端详情（仅正文/导师/发布时间/学期/主题 + 本人反馈）
export const studentDetail = (id) => request.get(`/student-comment/${id}`)

/**
 * 提交 / 更新本人对某条已发布点评的反馈。
 * 反馈对象是「这条点评对我的帮助」，不进情感统计、不改点评状态。
 */
export const submitFeedback = (id, data) => request.post(`/student-comment/${id}/feedback`, data)
