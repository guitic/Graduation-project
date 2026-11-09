import request from './request'

export const login = (data) => request.post('/auth/login', data)
export const logout = () => request.post('/auth/logout')
export const me = () => request.get('/auth/me')
export const demoAccounts = () => request.get('/demo/accounts')
export const health = () => request.get('/health')

/** 修改本人密码。成功后后端会踢掉该账号全部会话，前端须清 token 并跳登录页。 */
export const changePassword = (data) => request.post('/auth/password', data)
