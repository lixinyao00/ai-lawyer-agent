import request from '@/utils/request'

// 分页查询知识库法规文档列表
export function listDoc(query) {
  return request({
    url: '/lawyer/knowledge/docs',
    method: 'get',
    params: query
  })
}

// 获取单条知识库法规文档详情
export function getDoc(id) {
  return request({
    url: `/lawyer/knowledge/docs/${id}`,
    method: 'get'
  })
}

// 新增知识库法规文档（可选带正文自动切分）
export function addDoc(data) {
  return request({
    url: '/lawyer/knowledge/docs',
    method: 'post',
    data: data
  })
}

// 修改知识库法规文档元数据
export function updateDoc(id, data) {
  return request({
    url: `/lawyer/knowledge/docs/${id}`,
    method: 'put',
    data: data
  })
}

// 重新切分知识库法规文档正文
export function rechunkDoc(id, content) {
  return request({
    url: `/lawyer/knowledge/docs/${id}/rechunk`,
    method: 'post',
    data: { content }
  })
}

// 删除知识库法规文档（支持单个 id 或逗号分隔 ids，兼容数组）
export function delDoc(ids) {
  const targetIds = Array.isArray(ids) ? ids.join(',') : ids
  return request({
    url: `/lawyer/knowledge/docs/${targetIds}`,
    method: 'delete'
  })
}

// 导入法规文件（.txt/.md/.docx）并自动切分入库
export function importDoc(formData) {
  return request({
    url: '/lawyer/knowledge/docs/import',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

// 分页查询指定法规文档的切片列表
export function listChunk(docId, query) {
  return request({
    url: `/lawyer/knowledge/docs/${docId}/chunks`,
    method: 'get',
    params: query
  })
}

// 修改单条法规切片内容
export function updateChunk(docId, chunkId, data) {
  return request({
    url: `/lawyer/knowledge/docs/${docId}/chunks/${chunkId}`,
    method: 'put',
    data: data
  })
}

// 删除单条法规切片
export function delChunk(docId, chunkId) {
  return request({
    url: `/lawyer/knowledge/docs/${docId}/chunks/${chunkId}`,
    method: 'delete'
  })
}

// 法律知识库检索调试
export function searchKnowledge(q, topK = 4, mode) {
  const params = { q, topK }
  if (mode) {
    params.mode = mode
  }
  return request({
    url: '/lawyer/knowledge/docs/search',
    method: 'get',
    params
  })
}

// RAG 调试三路对比（BM25 / Vector / Hybrid RRF + Query Rewrite）
export function debugSearchKnowledge(data) {
  return request({
    url: '/lawyer/knowledge/docs/search-debug',
    method: 'post',
    data: data,
    headers: {
      repeatSubmit: false
    },
    timeout: 30000
  })
}

// 重建知识库切片向量索引（docId 可选）
export function rebuildKnowledgeEmbeddings(docId) {
  return request({
    url: '/lawyer/knowledge/docs/rebuild-embeddings',
    method: 'post',
    params: docId ? { docId } : {},
    timeout: 120000
  })
}
