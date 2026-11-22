// AI 律师助手前后端一致枚举常量（契约见 PLAN.md §2.3 & §6.4）

export const KNOWLEDGE_CATEGORIES = [
  { value: 'labor', label: '劳动人事' },
  { value: 'civil_contract', label: '合同通则' },
  { value: 'civil_lease', label: '租赁' },
  { value: 'civil_loan', label: '借贷' },
  { value: 'civil_tort', label: '侵权责任' },
  { value: 'other', label: '其他' }
]

export const KNOWLEDGE_STATUS = [
  { value: '0', label: '启用', type: 'success' },
  { value: '1', label: '停用', type: 'info' }
]

export const STANCE_OPTIONS = [
  { value: 'party_a', label: '甲方立场' },
  { value: 'party_b', label: '乙方立场' },
  { value: 'neutral', label: '中立审查' }
]

export const RISK_LEVELS = [
  { value: 'high', label: '高风险', type: 'danger' },
  { value: 'medium', label: '中风险', type: 'warning' },
  { value: 'low', label: '低风险', type: 'success' }
]

export const REVIEW_STATUS = [
  { value: 'pending', label: '审查中', type: 'info' },
  { value: 'success', label: '已完成', type: 'success' },
  { value: 'failed', label: '失败', type: 'danger' }
]

export const MESSAGE_STATUS = [
  { value: 'pending', label: '生成中', type: 'info' },
  { value: 'success', label: '已完成', type: 'success' },
  { value: 'failed', label: '失败', type: 'danger' }
]

export const DRAFT_STATUS = [
  { value: 'success', label: '已生成', type: 'success' },
  { value: 'failed', label: '失败', type: 'danger' }
]

export const RETRIEVAL_MODES = [
  { value: 'hybrid_rrf', label: '混合检索 (BM25 + 向量 RRF)' },
  { value: 'bm25', label: '关键词检索 (BM25)' },
  { value: 'vector', label: '语义向量检索 (Vector)' }
]

export const AGENT_TOOL_META = {
  search_law_articles: { label: '法条检索', color: 'primary' },
  calc_legal_amount: { label: '法律金额/时效计算', color: 'warning' },
  create_document_draft: { label: '法律文书起草', color: 'success' }
}

export const TOOL_STATUS_META = [
  { value: 'running', label: '执行中', type: 'info' },
  { value: 'success', label: '已完成', type: 'success' },
  { value: 'error', label: '需修正重试', type: 'danger' }
]

export const ALLOWED_CONTRACT_EXT = ['docx', 'pdf', 'txt', 'md']

export const MAX_FILE_MB = 10

export function findOption(options, value) {
  if (!Array.isArray(options)) return null
  return options.find(item => item.value === value) || null
}

export function getOptionLabel(options, value, fallback = '') {
  const matched = findOption(options, value)
  return matched ? matched.label : (fallback || value || '')
}

export function getOptionType(options, value, fallback = 'info') {
  const matched = findOption(options, value)
  return matched && matched.type ? matched.type : fallback
}

export function getKnowledgeCategoryLabel(value) {
  return getOptionLabel(KNOWLEDGE_CATEGORIES, value)
}

export function getStanceLabel(value) {
  return getOptionLabel(STANCE_OPTIONS, value)
}

export function getRiskLevelMeta(value) {
  return findOption(RISK_LEVELS, value) || { value, label: value || '未知', type: 'info' }
}

export function getReviewStatusMeta(value) {
  return findOption(REVIEW_STATUS, value) || { value, label: value || '未知', type: 'info' }
}

export function getRetrievalModeLabel(value) {
  return getOptionLabel(RETRIEVAL_MODES, value)
}

export function getAgentToolLabel(toolName) {
  if (toolName && Object.prototype.hasOwnProperty.call(AGENT_TOOL_META, toolName)) {
    return AGENT_TOOL_META[toolName].label
  }
  return toolName || ''
}
