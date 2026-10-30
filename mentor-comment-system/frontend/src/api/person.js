import request from './request'

export const teachers = () => request.get('/person/teachers')

/** 管理页导师列表：含停用档案、联系方式、指导学生数 */
export const allTeachers = () => request.get('/person/teachers/all')

export const myStudents = () => request.get('/person/my-students')
export const studentsOfTeacher = (teacherId) =>
  request.get('/person/students-of-teacher', { params: { teacherId } })

/**
 * 学生列表。
 * includeDisabled=true 用于管理页（停用的档案也要能编辑和启用回来），
 * 绑定学生时用默认值 false —— 停用账号不应该被重新分配指导关系。
 */
export const students = (clazzId, includeDisabled = false) =>
  request.get('/person/students', {
    params: { ...(clazzId ? { clazzId } : {}), ...(includeDisabled ? { includeDisabled } : {}) }
  })

export const clazzOptions = () => request.get('/person/clazz')
export const majors = () => request.get('/person/majors')
export const terms = () => request.get('/person/terms')
export const bind = (data) => request.post('/person/bind', data)
export const unbind = (id) => request.delete(`/person/bind/${id}`)

// ---------------- 导师档案 ----------------
export const createTeacher = (data) => request.post('/person/teachers', data)
export const updateTeacher = (id, data) => request.put(`/person/teachers/${id}`, data)
/** force=false 时若有未完结点评，后端返回 409 与条数，由页面二次确认后带 force=true 重试 */
export const disableTeacher = (id, force = false) =>
  request.post(`/person/teachers/${id}/disable`, null, { params: { force } })
export const enableTeacher = (id) => request.post(`/person/teachers/${id}/enable`)

// ---------------- 学生档案 ----------------
export const createStudent = (data) => request.post('/person/students', data)
export const updateStudent = (id, data) => request.put(`/person/students/${id}`, data)
export const disableStudent = (id) => request.post(`/person/students/${id}/disable`)
export const enableStudent = (id) => request.post(`/person/students/${id}/enable`)

// ---------------- 账号 ----------------
/** 重置密码：返回新密码，只展示一次 */
export const resetPassword = (userId) => request.post(`/person/users/${userId}/reset-password`)
