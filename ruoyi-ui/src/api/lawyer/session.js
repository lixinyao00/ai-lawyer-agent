import request from '@/utils/request'

// 查询当前用户的会话列表
export function listSession(query) {
  return request({
    url: '/lawyer/sessions',
    method: 'get',
    params: query
  })
}

// 新建会话
export function addSession(data) {
  return request({
    url: '/lawyer/sessions',
    method: 'post',
    data: data
  })
}

// 删除会话
export function delSession(id) {
  return request({
    url: '/lawyer/sessions/' + id,
    method: 'delete'
  })
}
