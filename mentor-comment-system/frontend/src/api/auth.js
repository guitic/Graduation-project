import request from './request'

export const login = (data) => request.post('/auth/login', data)
export const logout = () => request.post('/auth/logout')
export const me = () => request.get('/auth/me')
export const demoAccounts = () => request.get('/demo/accounts')
export const health = () => request.get('/health')
