import request from './request'

export const teachers = () => request.get('/person/teachers')
export const myStudents = () => request.get('/person/my-students')
export const studentsOfTeacher = (teacherId) =>
  request.get('/person/students-of-teacher', { params: { teacherId } })
export const students = (clazzId) =>
  request.get('/person/students', { params: clazzId ? { clazzId } : {} })
export const clazzOptions = () => request.get('/person/clazz')
export const majors = () => request.get('/person/majors')
export const terms = () => request.get('/person/terms')
export const bind = (data) => request.post('/person/bind', data)
export const unbind = (id) => request.delete(`/person/bind/${id}`)
