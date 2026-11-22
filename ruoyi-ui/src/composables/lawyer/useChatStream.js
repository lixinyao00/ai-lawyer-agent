import { ref, getCurrentInstance, onUnmounted } from 'vue'
import { STREAM_URL } from '@/api/lawyer/chat'
import { streamPost } from '@/utils/lawyer/sse'

function isAbortError(err) {
  return Boolean(err && (err.name === 'AbortError' || err.code === 20))
}

/**
 * 法律智能咨询 SSE 流式发送 Composable（契约见 PLAN.md §6.5 & PLAN_V2.md §4.4 / §6.3 ④）
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
      onToolCall,
      onToolResult,
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
    let toolCalls = []
    let rewrittenQuery = null
    let retrievalMode = null
    let promptTokens = null
    let completionTokens = null
    let totalTokens = null
    let ttftMs = null
    let totalLatencyMs = null
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
                if (data?.rewrittenQuery !== undefined) {
                  rewrittenQuery = data.rewrittenQuery ?? null
                }
                if (data?.retrievalMode !== undefined) {
                  retrievalMode = data.retrievalMode ?? null
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
              case 'tool_call': {
                const callId = data?.callId ?? null
                const entry = {
                  callId,
                  step: data?.step ?? null,
                  toolName: data?.toolName ?? '',
                  displayTitle: data?.displayTitle ?? '',
                  arguments: data?.arguments ?? null,
                  status: 'running',
                  resultSummary: '',
                  detail: null,
                  durationMs: null,
                  draftId: null
                }
                const existingIdx = callId != null && callId !== ''
                  ? toolCalls.findIndex(item => item.callId === callId)
                  : -1
                if (existingIdx >= 0) {
                  toolCalls[existingIdx] = { ...toolCalls[existingIdx], ...entry }
                } else {
                  toolCalls.push(entry)
                }
                if (typeof onToolCall === 'function') {
                  onToolCall(data, [...toolCalls])
                }
                break
              }
              case 'tool_result': {
                const callId = data?.callId ?? null
                const patch = {
                  status: data?.status || 'success',
                  resultSummary: data?.summary || data?.resultSummary || '',
                  detail: data?.detail ?? null,
                  durationMs: data?.durationMs ?? null,
                  draftId: data?.draftId ?? null
                }
                const existingIdx = callId != null && callId !== ''
                  ? toolCalls.findIndex(item => item.callId === callId)
                  : -1
                if (existingIdx >= 0) {
                  toolCalls[existingIdx] = {
                    ...toolCalls[existingIdx],
                    ...(data?.step != null ? { step: data.step } : {}),
                    ...(data?.toolName ? { toolName: data.toolName } : {}),
                    ...patch
                  }
                } else {
                  toolCalls.push({
                    callId,
                    step: data?.step ?? null,
                    toolName: data?.toolName ?? '',
                    displayTitle: data?.displayTitle ?? '',
                    arguments: data?.arguments ?? null,
                    ...patch
                  })
                }
                if (typeof onToolResult === 'function') {
                  onToolResult(data, [...toolCalls])
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
                if (data?.rewrittenQuery !== undefined) {
                  rewrittenQuery = data.rewrittenQuery ?? null
                }
                if (data?.retrievalMode !== undefined) {
                  retrievalMode = data.retrievalMode ?? null
                }
                if (data?.promptTokens !== undefined) {
                  promptTokens = data.promptTokens ?? null
                }
                if (data?.completionTokens !== undefined) {
                  completionTokens = data.completionTokens ?? null
                }
                if (data?.totalTokens !== undefined) {
                  totalTokens = data.totalTokens ?? null
                }
                if (data?.ttftMs !== undefined) {
                  ttftMs = data.ttftMs ?? null
                }
                if (data?.totalLatencyMs !== undefined) {
                  totalLatencyMs = data.totalLatencyMs ?? null
                }
                let donePayload = data
                if (Array.isArray(data?.toolCalls) && data.toolCalls.length > 0) {
                  const detailByCallId = new Map()
                  for (const existing of toolCalls) {
                    if (existing?.callId != null && existing.callId !== '' && existing.detail != null) {
                      detailByCallId.set(existing.callId, existing.detail)
                    }
                  }
                  toolCalls = data.toolCalls.map((item, idx) => {
                    const fallbackDetail =
                      toolCalls[idx] && toolCalls[idx].toolName === item?.toolName
                        ? toolCalls[idx].detail
                        : null
                    const preservedDetail =
                      item?.detail ??
                      (item?.callId != null && item.callId !== '' ? detailByCallId.get(item.callId) : undefined) ??
                      fallbackDetail ??
                      null
                    return {
                      ...item,
                      detail: preservedDetail
                    }
                  })
                  donePayload = { ...data, toolCalls: [...toolCalls] }
                }
                if (typeof onDone === 'function') {
                  onDone(donePayload)
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
      toolCalls,
      rewrittenQuery,
      retrievalMode,
      promptTokens,
      completionTokens,
      totalTokens,
      ttftMs,
      totalLatencyMs,
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
