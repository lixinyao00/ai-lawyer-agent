<template>
  <article class="chat-row" :class="message.role === 'user' ? 'is-user' : 'is-assistant'">
    <LawyerMark v-if="message.role !== 'user'" class="reply-mark" />
    <div class="chat-bubble" :class="message.role === 'user' ? 'bubble-user' : 'bubble-assistant'">
      <span v-if="message.role !== 'user'" class="reply-name">AI 律师助手</span>

      <!-- 用户消息：纯文本展示 -->
      <span v-if="message.role === 'user'" class="chat-text">{{ message.content }}</span>

      <!-- 助手消息：等待中（尚无内容） -->
      <span v-else-if="message.status === 'pending' && !message.content" class="chat-pending" role="status">
        {{ message.replayPending ? '回答仍在生成，请稍后刷新' : '正在整理思路' }}
        <span class="pending-dots" aria-hidden="true"><i></i><i></i><i></i></span>
      </span>

      <!-- 助手消息：失败状态 + 重试按钮 -->
      <div v-else-if="message.status === 'failed'" class="chat-failed-box" role="status">
        <span class="chat-text chat-error-text">{{ message.error || message.errorMessage || '回答生成失败' }}</span>
        <button
          v-if="canRetry"
          type="button"
          class="retry-button"
          :disabled="disableRetry"
          @click="$emit('retry', message)"
        >
          <el-icon><RefreshRight /></el-icon>
          <span>重试</span>
        </button>
      </div>

      <!-- 助手消息：Markdown 渲染正文（含流式增量与停止保留的局部内容） -->
      <template v-else>
        <div class="assistant-content-wrap">
          <div class="lawyer-markdown" v-html="renderedHtml" />
          <span v-if="message.streaming" class="stream-cursor" aria-hidden="true" />
        </div>

        <p v-if="message.replayPending" class="message-status-note" role="status">
          回答仍在生成，请稍后刷新
        </p>
        <p v-else-if="message.aborted || message.errorCode === 'aborted'" class="message-status-note">
          已停止生成（已保留当前内容）
        </p>

        <!-- M2 法条引用依据卡片 -->
        <CitationCards :citations="message.citations" />
      </template>
    </div>
  </article>
</template>

<script setup name="MessageItem">
import { ref, watch, onBeforeUnmount } from 'vue'
import { RefreshRight } from '@element-plus/icons-vue'
import LawyerMark from '@/components/LawyerMark.vue'
import CitationCards from './CitationCards.vue'
import { renderMarkdown } from '@/utils/lawyer/markdown'

const props = defineProps({
  message: {
    type: Object,
    required: true
  },
  canRetry: {
    type: Boolean,
    default: true
  },
  disableRetry: {
    type: Boolean,
    default: false
  }
})

defineEmits(['retry'])

const renderedHtml = ref('')
let throttleTimer = null
let lastRenderAt = 0
const THROTTLE_MS = 60

function flushMarkdown() {
  if (throttleTimer) {
    clearTimeout(throttleTimer)
    throttleTimer = null
  }
  lastRenderAt = Date.now()
  renderedHtml.value = props.message.role === 'user'
    ? ''
    : renderMarkdown(props.message.content || '')
}

watch(
  () => [props.message.content, props.message.streaming, props.message.role],
  ([, isStreaming, role]) => {
    if (role === 'user') {
      renderedHtml.value = ''
      return
    }
    if (!isStreaming) {
      flushMarkdown()
      return
    }
    const elapsed = Date.now() - lastRenderAt
    if (elapsed >= THROTTLE_MS) {
      flushMarkdown()
    } else if (!throttleTimer) {
      throttleTimer = setTimeout(() => {
        flushMarkdown()
      }, THROTTLE_MS - elapsed)
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  if (throttleTimer) {
    clearTimeout(throttleTimer)
    throttleTimer = null
  }
})
</script>

<style scoped>
.chat-row {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  margin-bottom: 24px;
}

.is-user {
  justify-content: flex-end;
}

.reply-mark {
  margin-top: 2px;
  --mark-size: 28px;
}

.chat-bubble {
  display: grid;
  gap: 6px;
  min-width: 0;
  line-height: 1.78;
}

.bubble-user {
  max-width: min(82%, 620px);
  padding: 10px 16px;
  border-radius: 19px 19px 6px 19px;
  background: var(--law-wash);
}

.bubble-assistant {
  flex: 1;
  padding-top: 1px;
}

.reply-name {
  color: var(--law-ink);
  font-size: 13px;
  font-weight: 700;
}

.chat-text {
  font-size: 15px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.chat-pending,
.chat-error-text {
  color: var(--law-muted);
}

.chat-error-text {
  color: var(--el-color-danger);
}

.chat-failed-box {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.retry-button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid var(--law-line);
  border-radius: 8px;
  background: var(--law-card);
  color: var(--law-ink);
  font-size: 12px;
  font-weight: 560;
  cursor: pointer;
}

.retry-button:hover:not(:disabled) {
  border-color: var(--law-accent);
  color: var(--law-accent);
  background: var(--law-wash);
}

.retry-button:disabled {
  opacity: .55;
  cursor: not-allowed;
}

.assistant-content-wrap {
  min-width: 0;
}

.message-status-note {
  margin: 4px 0 0;
  color: var(--law-muted);
  font-size: 12px;
}

.pending-dots {
  display: inline-flex;
  gap: 3px;
  margin-left: 7px;
  vertical-align: middle;
}

.pending-dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 1.2s infinite alternate;
}

.pending-dots i:nth-child(2) { animation-delay: .15s; }
.pending-dots i:nth-child(3) { animation-delay: .3s; }

@keyframes pulse {
  from { opacity: .2; }
  to { opacity: .85; }
}

@media (max-width: 760px) {
  .chat-row {
    margin-bottom: 18px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .pending-dots i { animation: none; }
}
</style>
