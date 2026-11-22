import request from '@/utils/request'

// 后台分页查询全部用户会话
export function listAdminSession(query) {
  return request({
    url: '/lawyer/admin/sessions',
    method: 'get',
    params: query
  })
}

// 后台分页查询指定会话的消息审计记录
export function listAdminMessage(sessionId, query) {
  return request({
    url: '/lawyer/admin/sessions/' + sessionId + '/messages',
    method: 'get',
    params: query
  })
}

// 后台删除指定会话及其消息
export function delAdminSession(id) {
  return request({
    url: '/lawyer/admin/sessions/' + id,
    method: 'delete'
  })
}
