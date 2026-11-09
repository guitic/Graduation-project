import request from './request'

/** 分页查询操作日志（仅教务） */
export const page = (params) => request.get('/oper-log/page', { params })

/** 出现过的动作名清单，供筛选下拉 */
export const actions = () => request.get('/oper-log/actions')
