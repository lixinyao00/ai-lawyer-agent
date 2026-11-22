import request from '@/utils/request'
import { downloadBlob } from '@/utils/lawyer/download'

function ensureDocxExt(filename) {
  const name = (filename || '法律文书').trim() || '法律文书'
  return /\.docx$/i.test(name) ? name : `${name}.docx`
}

// 获取法律文书模板列表
export function listTemplates() {
  return request({
    url: '/lawyer/documents/templates',
    method: 'get'
  })
}

// 同步生成法律文书草稿（放宽超时至 150s）
export function createDraft(data) {
  return request({
    url: '/lawyer/documents/drafts',
    method: 'post',
    data: data,
    headers: {
      repeatSubmit: false
    },
    timeout: 150000
  })
}

// 编辑保存法律文书草稿
export function updateDraft(id, data) {
  return request({
    url: `/lawyer/documents/drafts/${id}`,
    method: 'put',
    data: data
  })
}

// 分页查询法律文书草稿列表
export function listDraft(query) {
  return request({
    url: '/lawyer/documents/drafts',
    method: 'get',
    params: query
  })
}

// 查询法律文书草稿详情
export function getDraft(id) {
  return request({
    url: `/lawyer/documents/drafts/${id}`,
    method: 'get'
  })
}

// 删除法律文书草稿
export function delDraft(id) {
  return request({
    url: `/lawyer/documents/drafts/${id}`,
    method: 'delete'
  })
}

// 导出法律文书草稿为 DOCX 文件
export function exportDraftDocx(id, filename) {
  return downloadBlob(
    '/lawyer/documents/drafts/' + id + '/export-docx',
    ensureDocxExt(filename || ('法律文书-' + id))
  )
}
