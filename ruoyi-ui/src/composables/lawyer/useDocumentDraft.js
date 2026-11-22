import { ref } from 'vue'
import {
  listTemplates,
  createDraft,
  updateDraft,
  exportDraftDocx
} from '@/api/lawyer/document'

const DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/
const MONEY_REGEX = /^\d+(\.\d{1,2})?$/
const NUMBER_REGEX = /^-?\d+(\.\d+)?$/
const PHONE_REGEX = /^1\d{10}$|^0\d{2,3}-?\d{7,8}$/
const IDCARD_REGEX = /^\d{17}[\dXx]$/

function isEmptyValue(value) {
  if (value === undefined || value === null) return true
  if (typeof value === 'string' && value.trim() === '') return true
  return false
}

function isValidCalendarDate(dateStr) {
  if (!DATE_REGEX.test(dateStr)) return false
  const [year, month, day] = dateStr.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  return (
    date.getFullYear() === year &&
    date.getMonth() === month - 1 &&
    date.getDate() === day
  )
}

function resolveMaxLength(field) {
  if (typeof field.maxLength === 'number' && field.maxLength > 0) {
    return field.maxLength
  }
  if (field.type === 'text') return 200
  if (field.type === 'textarea') return 2000
  return 0
}

/**
 * 法律文书起草 Composable（契约见 PLAN.md §6.5）
 */
export function useDocumentDraft() {
  const templates = ref([])
  const loadingTemplates = ref(false)
  const generating = ref(false)
  const saving = ref(false)

  /**
   * 加载文书模板列表并缓存到 templates
   * @returns {Promise<Array<object>>}
   */
  async function loadTemplates() {
    loadingTemplates.value = true
    try {
      const res = await listTemplates()
      const data = res && res.data !== undefined ? res.data : res
      const list = Array.isArray(data) ? data : []
      templates.value = list
      return list
    } finally {
      loadingTemplates.value = false
    }
  }

  /**
   * 按模板字段定义构建表单初始值（含 defaultValue）
   * @param {object} template DocumentTemplateVO
   * @returns {object}
   */
  function buildFormModel(template) {
    const model = {}
    const fields = Array.isArray(template?.fields) ? template.fields : []
    for (const field of fields) {
      if (!field || !field.key) continue
      if (field.defaultValue !== undefined && field.defaultValue !== null) {
        model[field.key] = field.defaultValue
      } else {
        model[field.key] = ''
      }
    }
    return model
  }

  /**
   * 按模板字段定义生成 Element Plus el-form 校验规则
   * 与后端 FormDataValidator（PLAN.md §M4.1）保持严格对齐
   * @param {object} template DocumentTemplateVO
   * @returns {object}
   */
  function buildFormRules(template) {
    const rules = {}
    const fields = Array.isArray(template?.fields) ? template.fields : []

    for (const field of fields) {
      if (!field || !field.key) continue
      const label = field.label || field.key
      const trigger = field.type === 'select' || field.type === 'date' ? 'change' : 'blur'
      const fieldRules = []

      if (field.required) {
        const prefix = field.type === 'select' || field.type === 'date' ? '请选择' : '请输入'
        fieldRules.push({
          required: true,
          trigger,
          validator: (_rule, value, callback) => {
            if (isEmptyValue(value)) {
              callback(new Error(`${prefix}${label}`))
            } else {
              callback()
            }
          }
        })
      }

      const maxLen = resolveMaxLength(field)
      if (maxLen > 0) {
        fieldRules.push({
          trigger: 'blur',
          validator: (_rule, value, callback) => {
            if (isEmptyValue(value)) {
              callback()
              return
            }
            if (String(value).length > maxLen) {
              callback(new Error(`${label}长度不能超过 ${maxLen} 个字符`))
            } else {
              callback()
            }
          }
        })
      }

      if (field.type === 'date') {
        fieldRules.push({
          trigger: ['change', 'blur'],
          validator: (_rule, value, callback) => {
            if (isEmptyValue(value)) {
              callback()
              return
            }
            const str = String(value).trim()
            if (!isValidCalendarDate(str)) {
              callback(new Error(`${label}日期格式须为 YYYY-MM-DD`))
            } else {
              callback()
            }
          }
        })
      } else if (field.type === 'money') {
        fieldRules.push({
          trigger: ['blur', 'change'],
          validator: (_rule, value, callback) => {
            if (isEmptyValue(value)) {
              callback()
              return
            }
            const str = String(value).trim()
            if (!MONEY_REGEX.test(str) || Number(str) < 0) {
              callback(new Error(`${label}须为非负金额且最多保留两位小数`))
            } else {
              callback()
            }
          }
        })
      } else if (field.type === 'number') {
        fieldRules.push({
          trigger: ['blur', 'change'],
          validator: (_rule, value, callback) => {
            if (isEmptyValue(value)) {
              callback()
              return
            }
            const str = String(value).trim()
            if (!NUMBER_REGEX.test(str) || !Number.isFinite(Number(str))) {
              callback(new Error(`${label}须为有效数字`))
            } else {
              callback()
            }
          }
        })
      } else if (field.type === 'phone') {
        fieldRules.push({
          trigger: 'blur',
          validator: (_rule, value, callback) => {
            if (isEmptyValue(value)) {
              callback()
              return
            }
            const str = String(value).trim()
            if (!PHONE_REGEX.test(str)) {
              callback(new Error(`${label}格式不正确，请输入有效的手机号或固定电话`))
            } else {
              callback()
            }
          }
        })
      } else if (field.type === 'idcard') {
        fieldRules.push({
          trigger: 'blur',
          validator: (_rule, value, callback) => {
            if (isEmptyValue(value)) {
              callback()
              return
            }
            const str = String(value).trim()
            if (!IDCARD_REGEX.test(str)) {
              callback(new Error(`${label}须为18位有效身份证号`))
            } else {
              callback()
            }
          }
        })
      } else if (field.type === 'select' && Array.isArray(field.options) && field.options.length > 0) {
        fieldRules.push({
          trigger: 'change',
          validator: (_rule, value, callback) => {
            if (isEmptyValue(value)) {
              callback()
              return
            }
            if (!field.options.includes(value) && !field.options.includes(String(value))) {
              callback(new Error(`${label}选项无效，请从列表中选择`))
            } else {
              callback()
            }
          }
        })
      }

      if (fieldRules.length > 0) {
        rules[field.key] = fieldRules
      }
    }

    return rules
  }

  /**
   * 调用模型生成法律文书草稿
   * @param {string} templateCode 模板编码
   * @param {object} formData 表单要素对象
   * @param {string} [title] 可选自定义标题
   * @returns {Promise<object>} DocumentDraftVO
   */
  async function generate(templateCode, formData, title) {
    const payload = {
      templateCode,
      formData: formData || {}
    }
    if (title !== undefined && title !== null && String(title).trim() !== '') {
      payload.title = String(title).trim()
    }

    generating.value = true
    try {
      const res = await createDraft(payload)
      return res && res.data !== undefined ? res.data : res
    } finally {
      generating.value = false
    }
  }

  /**
   * 保存编辑后的法律文书草稿
   * @param {number|string} id 草稿 ID
   * @param {string} title 文书标题
   * @param {string} content 文书正文
   * @returns {Promise<object>} DocumentDraftVO
   */
  async function save(id, title, content) {
    saving.value = true
    try {
      const res = await updateDraft(id, { title, content })
      return res && res.data !== undefined ? res.data : res
    } finally {
      saving.value = false
    }
  }

  /**
   * 导出法律文书草稿为 DOCX
   * @param {{ id: number|string, title?: string }} draft
   * @returns {Promise<void>}
   */
  async function exportDocx(draft) {
    if (!draft || draft.id === undefined || draft.id === null || draft.id === '') {
      throw new Error('缺少文书草稿 ID，无法导出')
    }
    const baseName = draft.title && String(draft.title).trim() ? String(draft.title).trim() : '法律文书'
    return exportDraftDocx(draft.id, `${baseName}.docx`)
  }

  return {
    templates,
    loadingTemplates,
    generating,
    saving,
    loadTemplates,
    buildFormModel,
    buildFormRules,
    generate,
    save,
    exportDocx
  }
}
