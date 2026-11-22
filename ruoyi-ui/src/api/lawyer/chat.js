import request from '@/utils/request'

// 查询会话的历史消息
export function listMessage(sessionId, query) {
  return request({
    url: '/lawyer/sessions/' + sessionId + '/messages',
    method: 'get',
    params: query
  })
}

// 发送问题，等待完整回答，单独放宽超时
export function sendMessage(sessionId, data) {
  return request({
    url: '/lawyer/sessions/' + sessionId + '/messages',
    method: 'post',
    data: data,
    timeout: 90000
  })
}

// 生成每次发送用的请求ID
export function buildRequestId() {
  return crypto.randomUUID()
}

// 流式发送消息 SSE 接口路径
export const STREAM_URL = (sessionId) => '/lawyer/sessions/' + sessionId + '/messages/stream'
