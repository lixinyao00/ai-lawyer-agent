import request from '@/utils/request'

// 发起合同风险审查（multipart/form-data，file / fileId / contractText 三选一）
export function createReview(formData) {
  return request({
    url: '/lawyer/contracts/reviews',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data',
      repeatSubmit: false
    },
    timeout: 120000
  })
}

// 分页查询合同审查记录列表
export function listReview(query) {
  return request({
    url: '/lawyer/contracts/reviews',
    method: 'get',
    params: query
  })
}

// 查询合同审查详情
export function getReview(id) {
  return request({
    url: `/lawyer/contracts/reviews/${id}`,
    method: 'get'
  })
}

// 删除合同审查记录
export function delReview(id) {
  return request({
    url: `/lawyer/contracts/reviews/${id}`,
    method: 'delete'
  })
}
