import { ElMessage, ElMessageBox } from 'element-plus'
import { getToken } from '@/utils/auth'
import errorCode from '@/utils/errorCode'
import { isRelogin } from '@/utils/request'
import useUserStore from '@/store/modules/user'

const IDLE_TIMEOUT_MS = 45000

function buildFullUrl(url) {
  if (/^https?:\/\//i.test(url)) {
    return url
  }
  const baseApi = import.meta.env.VITE_APP_BASE_API || ''
  if (!baseApi) {
    return url
  }
  const normalizedBase = baseApi.endsWith('/') ? baseApi.slice(0, -1) : baseApi
  const normalizedUrl = url.startsWith('/') ? url : '/' + url
  return normalizedBase + normalizedUrl
}

function createBizError(code, msg) {
  const err = new Error(msg)
  err.code = code
  err.msg = msg
  return err
}

function createAbortError() {
  try {
    return new DOMException('The operation was aborted.', 'AbortError')
  } catch {
    const err = new Error('The operation was aborted.')
    err.name = 'AbortError'
    return err
  }
}

function isAbortError(err) {
  return Boolean(err && (err.name === 'AbortError' || err.code === 20))
}

function dispatchEventBlock(block, onEvent) {
  if (!block || !block.trim()) {
    return
  }
  const lines = block.split('\n')
  let eventName = 'message'
  const dataLines = []

  for (const line of lines) {
    if (!line || line.startsWith(':')) {
      continue
    }
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim() || 'message'
    } else if (line.startsWith('data:')) {
      const value = line.slice(5)
      dataLines.push(value.startsWith(' ') ? value.slice(1) : value)
    }
  }

  if (dataLines.length === 0) {
    return
  }

  const rawData = dataLines.join('\n')
  try {
    const parsed = JSON.parse(rawData)
    if (typeof onEvent === 'function') {
      onEvent(eventName, parsed)
    }
  } catch (err) {
    console.warn('[SSE] Failed to parse event data:', err)
  }
}

/**
 * 发起 POST SSE 流式请求
 * @param {string} url 相对路径（自动拼 import.meta.env.VITE_APP_BASE_API）
 * @param {object} body JSON 请求体
 * @param {{signal?: AbortSignal, onEvent: (event: string, data: object) => void}} opts
 * @returns {Promise<void>} 流正常结束 resolve；JSON 业务错误 reject(Error{code,msg})；AbortError 原样 reject
 */
export async function streamPost(url, body, opts = {}) {
  const { signal: externalSignal, onEvent } = opts || {}

  if (externalSignal?.aborted) {
    throw createAbortError()
  }

  const innerController = new AbortController()
  let timedOut = false
  let idleTimer = null
  let reader = null

  const clearIdleTimer = () => {
    if (idleTimer !== null) {
      clearTimeout(idleTimer)
      idleTimer = null
    }
  }

  const resetIdleTimer = () => {
    clearIdleTimer()
    idleTimer = setTimeout(() => {
      timedOut = true
      innerController.abort()
    }, IDLE_TIMEOUT_MS)
  }

  const handleExternalAbort = () => {
    clearIdleTimer()
    innerController.abort()
  }

  if (externalSignal) {
    externalSignal.addEventListener('abort', handleExternalAbort, { once: true })
  }

  try {
    const headers = {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream, application/json'
    }
    const token = getToken()
    if (token) {
      headers['Authorization'] = 'Bearer ' + token
    }

    resetIdleTimer()

    const response = await fetch(buildFullUrl(url), {
      method: 'POST',
      headers,
      body: JSON.stringify(body ?? {}),
      signal: innerController.signal
    })

    resetIdleTimer()

    const contentType = (response.headers.get('content-type') || '').toLowerCase()

    // 两段式错误：流开始前后端返回 application/json (AjaxResult)
    if (contentType.includes('application/json')) {
      clearIdleTimer()
      let res = null
      try {
        res = await response.json()
      } catch {
        res = null
      }
      const code = res?.code ?? response.status ?? 500
      const msg = errorCode[code] || res?.msg || errorCode['default'] || '系统未知错误'

      if (code === 401) {
        if (!isRelogin.show) {
          isRelogin.show = true
          ElMessageBox.confirm(
            '登录状态已过期，您可以继续留在该页面，或者重新登录',
            '系统提示',
            { confirmButtonText: '重新登录', cancelButtonText: '取消', type: 'warning' }
          ).then(() => {
            isRelogin.show = false
            useUserStore().logOut().then(() => {
              location.href = '/index'
            })
          }).catch(() => {
            isRelogin.show = false
          })
        }
        throw createBizError(401, '无效的会话，或者会话已过期，请重新登录。')
      }

      ElMessage.error(msg)
      throw createBizError(code, msg)
    }

    if (!response.ok) {
      clearIdleTimer()
      const msg = `系统接口${response.status}异常`
      ElMessage.error(msg)
      throw createBizError(response.status, msg)
    }

    if (!response.body) {
      clearIdleTimer()
      const msg = '当前浏览器不支持流式响应'
      ElMessage.error(msg)
      throw createBizError(500, msg)
    }

    reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { value, done } = await reader.read()

      if (done) {
        buffer += decoder.decode()
        buffer = buffer.replace(/\r\n/g, '\n')
        if (buffer.trim()) {
          const remainingBlocks = buffer.split('\n\n')
          for (const block of remainingBlocks) {
            dispatchEventBlock(block, onEvent)
          }
        }
        break
      }

      if (value && value.byteLength > 0) {
        resetIdleTimer()
        buffer += decoder.decode(value, { stream: true })
        buffer = buffer.replace(/\r\n/g, '\n')

        let delimiterIndex = buffer.indexOf('\n\n')
        while (delimiterIndex !== -1) {
          const block = buffer.slice(0, delimiterIndex)
          buffer = buffer.slice(delimiterIndex + 2)
          dispatchEventBlock(block, onEvent)
          delimiterIndex = buffer.indexOf('\n\n')
        }
      }
    }
  } catch (err) {
    clearIdleTimer()
    if (timedOut) {
      if (typeof onEvent === 'function') {
        onEvent('error', { code: 'NETWORK', message: '连接超时，请重试' })
      }
      return
    }
    if (isAbortError(err)) {
      throw err
    }
    throw err
  } finally {
    clearIdleTimer()
    if (externalSignal) {
      externalSignal.removeEventListener('abort', handleExternalAbort)
    }
    if (reader) {
      try {
        reader.releaseLock()
      } catch {
        // ignore releaseLock errors
      }
    }
  }
}
