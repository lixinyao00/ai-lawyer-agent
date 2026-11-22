import { ref, getCurrentInstance, onUnmounted } from 'vue'
import { createReview, getReview } from '@/api/lawyer/contract'
import { ALLOWED_CONTRACT_EXT, MAX_FILE_MB } from '@/constants/lawyer'

const MAX_FILE_BYTES = MAX_FILE_MB * 1024 * 1024

function extractExt(filename) {
  if (!filename || typeof filename !== 'string') return ''
  const dotIdx = filename.lastIndexOf('.')
  if (dotIdx <= 0 || dotIdx === filename.length - 1) return ''
  return filename.slice(dotIdx + 1).toLowerCase()
}

/**
 * 合同风险审查 Composable（契约见 PLAN.md §6.5）
 */
export function useContractReview() {
  const submitting = ref(false)
  const polling = ref(false)

  let timerId = null
  let activeToken = 0
  let activeResolve = null
  let lastReview = null

  function clearTimer() {
    if (timerId !== null) {
      clearTimeout(timerId)
      timerId = null
    }
  }

  function stopPolling() {
    activeToken += 1
    clearTimer()
    polling.value = false
    if (activeResolve) {
      const resolve = activeResolve
      const snapshot = lastReview
      activeResolve = null
      lastReview = null
      resolve(snapshot)
    }
  }

  if (getCurrentInstance()) {
    onUnmounted(() => {
      stopPolling()
    })
  }

  /**
   * 合同文件前置校验，通过返回 null，失败返回错误文案
   * @param {File} file
   * @returns {string | null}
   */
  function validateFile(file) {
    if (!file) {
      return '请选择需要上传的合同文件'
    }
    const ext = extractExt(file.name)
    if (ext === 'doc') {
      return '暂不支持 .doc 格式，请先另存为 .docx'
    }
    if (!ALLOWED_CONTRACT_EXT.includes(ext)) {
      const allowedText = ALLOWED_CONTRACT_EXT.map(item => `.${item}`).join('、')
      return `仅支持 ${allowedText} 格式的文件`
    }
    if (typeof file.size === 'number') {
      if (file.size <= 0) {
        return '文件内容不能为空，请重新选择'
      }
      if (file.size > MAX_FILE_BYTES) {
        return `文件大小不能超过 ${MAX_FILE_MB}MB`
      }
    }
    return null
  }

  /**
   * 发起合同审查
   * @param {FormData | { file?: File, fileId?: number, contractText?: string, stance: string, title?: string }} params
   * @returns {Promise<object>} ContractReviewVO
   */
  async function submit(params) {
    let formData
    if (params instanceof FormData) {
      formData = params
    } else {
      const { file, fileId, contractText, stance, title } = params || {}
      formData = new FormData()
      if (file) {
        formData.append('file', file)
      }
      if (fileId !== undefined && fileId !== null && fileId !== '') {
        formData.append('fileId', String(fileId))
      }
      if (contractText !== undefined && contractText !== null && contractText !== '') {
        formData.append('contractText', contractText)
      }
      if (stance !== undefined && stance !== null && stance !== '') {
        formData.append('stance', stance)
      }
      if (title !== undefined && title !== null && title !== '') {
        formData.append('title', title)
      }
    }

    submitting.value = true
    try {
      const res = await createReview(formData)
      return res && res.data !== undefined ? res.data : res
    } finally {
      submitting.value = false
    }
  }

  /**
   * 轮询合同审查详情，直至 status 为 success / failed、超时或被 stopPolling() 停止
   * @param {number|string} id 审查记录 ID
   * @param {{ interval?: number, timeout?: number, onUpdate?: (review: object) => void }} opts
   * @returns {Promise<object | null>}
   */
  function poll(id, opts = {}) {
    stopPolling()

    const interval = opts.interval || 3000
    const timeout = opts.timeout || 180000
    const onUpdate = typeof opts.onUpdate === 'function' ? opts.onUpdate : null

    const token = ++activeToken
    const startTime = Date.now()
    polling.value = true
    lastReview = null

    return new Promise((resolve, reject) => {
      activeResolve = resolve

      const finishResolve = (result) => {
        if (token !== activeToken) return
        clearTimer()
        polling.value = false
        activeResolve = null
        lastReview = null
        resolve(result)
      }

      const finishReject = (err) => {
        if (token !== activeToken) return
        clearTimer()
        polling.value = false
        activeResolve = null
        lastReview = null
        reject(err)
      }

      const tick = async () => {
        if (token !== activeToken) return
        try {
          const res = await getReview(id)
          if (token !== activeToken) return

          const review = res && res.data !== undefined ? res.data : res
          lastReview = review
          if (onUpdate) {
            onUpdate(review)
          }

          if (review && (review.status === 'success' || review.status === 'failed')) {
            finishResolve(review)
            return
          }

          if (Date.now() - startTime >= timeout) {
            finishReject(new Error('合同审查轮询超时，请稍后刷新查看结果'))
            return
          }

          timerId = setTimeout(tick, interval)
        } catch (err) {
          finishReject(err)
        }
      }

      tick()
    })
  }

  return {
    submitting,
    polling,
    submit,
    poll,
    stopPolling,
    validateFile
  }
}
