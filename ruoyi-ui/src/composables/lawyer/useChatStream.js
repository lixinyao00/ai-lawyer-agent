import { ref, getCurrentInstance, onUnmounted } from 'vue'
import { STREAM_URL } from '@/api/lawyer/chat'
import { streamPost } from '@/utils/lawyer/sse'

function isAbortError(err) {
  return Boolean(err && (err.name === 'AbortError' || err.code === 20))
}

/**
 * 法律智能咨询 SSE 流式发送 Composable（契约见 PLAN.md §6.5）
 */
export function useChatStream() {
  const streaming = ref(false)
  let controller = null

  function abort() {
    if (controller) {
      controller.abort()
      controller = null
    }
  }

  if (getCurrentInstance()) {
    onUnmounted(() => {
      abort()
    })
  }

  async function send(params) {
    const {
      sessionId,
      content,
      requestId,
      onMeta,
      onCitation,
      onDelta,
      onDone,
      onError
    } = params || {}

    if (controller) {
      controller.abort()
    }

    const currentController = new AbortController()
    controller = currentController
    streaming.value = true

    let status = 'failed'
    let fullText = ''
    let citations = []
    let userMessageId
    let assistantMessageId
    let errorMessage
    let settled = false

    try {
      await streamPost(
        STREAM_URL(sessionId),
        { content, requestId },
        {
          signal: currentController.signal,
          onEvent(event, data) {
            switch (event) {
              case 'meta': {
                if (data?.userMessageId != null) {
                  userMessageId = data.userMessageId
                }
                if (data?.assistantMessageId != null) {
                  assistantMessageId = data.assistantMessageId
                }
                if (typeof onMeta === 'function') {
                  onMeta(data)
                }
                break
              }
              case 'citation': {
                citations = Array.isArray(data?.citations) ? data.citations : []
                if (typeof onCitation === 'function') {
                  onCitation(citations)
                }
                break
              }
              case 'delta': {
                const chunk = typeof data?.content === 'string' ? data.content : ''
                if (chunk) {
                  fullText += chunk
                  if (typeof onDelta === 'function') {
                    onDelta(chunk, fullText)
                  }
                }
                break
              }
              case 'done': {
                settled = true
                status = data?.status === 'pending' ? 'pending' : 'success'
                if (typeof data?.content === 'string' && data.content.length > 0) {
                  fullText = data.content
                }
                if (Array.isArray(data?.citations)) {
                  citations = data.citations
                }
                if (data?.assistantMessageId != null) {
                  assistantMessageId = data.assistantMessageId
                }
                if (typeof onDone === 'function') {
                  onDone(data)
                }
                break
              }
              case 'error': {
                settled = true
                status = 'failed'
                const errObj = {
                  code: data?.code || 'INTERNAL',
                  message: data?.message || '回答生成失败，请稍后重试'
                }
                errorMessage = errObj.message
                if (data?.assistantMessageId != null) {
                  assistantMessageId = data.assistantMessageId
                }
                if (typeof onError === 'function') {
                  onError(errObj)
                }
                break
              }
              default:
                break
            }
          }
        }
      )

      if (!settled) {
        if (fullText.length > 0) {
          status = 'success'
        } else {
          status = 'failed'
          errorMessage = '连接意外中断，请重试'
          if (typeof onError === 'function') {
            onError({ code: 'NETWORK', message: errorMessage })
          }
        }
      }
    } catch (err) {
      if (isAbortError(err)) {
        status = 'aborted'
      } else {
        status = 'failed'
        const code = err?.code != null ? String(err.code) : 'NETWORK'
        const message = err?.msg || err?.message || '请求失败，请稍后重试'
        errorMessage = message
        if (typeof onError === 'function') {
          onError({ code, message })
        }
      }
    } finally {
      if (controller === currentController) {
        controller = null
        streaming.value = false
      }
    }

    return {
      status,
      content: fullText,
      citations,
      userMessageId,
      assistantMessageId,
      errorMessage
    }
  }

  return {
    streaming,
    send,
    abort
  }
}
