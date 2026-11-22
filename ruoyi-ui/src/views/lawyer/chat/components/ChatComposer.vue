<template>
  <footer class="chat-footer">
    <div class="composer">
      <el-input
        ref="inputRef"
        :model-value="modelValue"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 6 }"
        resize="none"
        maxlength="4000"
        aria-label="输入法律问题"
        placeholder="输入你的法律问题…"
        @update:model-value="$emit('update:modelValue', $event)"
        @keydown="handleKeydown"
      />
      <div class="composer-actions">
        <span class="composer-hint">
          {{ streaming ? '正在生成回答…' : 'Enter 发送 · Shift + Enter 换行' }}
        </span>
        <button
          v-if="streaming"
          type="button"
          class="stop-button"
          aria-label="停止生成"
          title="停止生成"
          @click="$emit('abort')"
        >
          <el-icon><VideoPause /></el-icon>
          <span>停止生成</span>
        </button>
        <button
          v-else
          type="button"
          class="send-button"
          aria-label="发送消息"
          title="发送消息"
          :disabled="!canSend"
          @click="$emit('send')"
        >
          <el-icon><Top /></el-icon>
        </button>
      </div>
    </div>
    <p class="chat-note">AI 生成内容，法律依据请核验</p>
  </footer>
</template>

<script setup name="ChatComposer">
import { ref } from 'vue'
import { Top, VideoPause } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  canSend: {
    type: Boolean,
    default: false
  },
  streaming: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'send', 'abort'])

const inputRef = ref(null)

function handleKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || props.streaming) {
    return
  }
  event.preventDefault()
  if (props.canSend) {
    emit('send')
  }
}

function focus() {
  inputRef.value?.focus()
}

defineExpose({ focus })
</script>

<style scoped>
.chat-footer {
  flex: none;
  width: min(100%, 812px);
  margin: 0 auto;
  padding: 8px 24px max(13px, env(safe-area-inset-bottom));
}

.composer {
  padding: 12px 12px 10px;
  border: 1px solid var(--law-line);
  border-radius: 21px;
  background: var(--law-card);
  box-shadow: var(--law-shadow);
}

.composer:focus-within {
  border-color: color-mix(in srgb, var(--law-accent) 48%, var(--law-line));
}

.composer :deep(.el-textarea__inner) {
  padding: 2px 3px 4px;
  border: 0;
  box-shadow: none;
  background: transparent;
  color: var(--law-ink);
  font: inherit;
  font-size: 16px;
  line-height: 1.55;
}

.composer :deep(.el-textarea__inner::placeholder) {
  color: var(--law-muted);
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 7px;
  min-height: 34px;
  padding: 1px 0 0 3px;
}

.composer-hint,
.chat-note {
  color: var(--law-muted);
  font-size: 12px;
}

.send-button {
  display: grid;
  flex: 0 0 36px;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 0;
  border-radius: 12px;
  background: var(--law-accent);
  color: var(--law-on-accent);
  font-size: 18px;
  cursor: pointer;
}

.send-button:hover:not(:disabled) {
  background: var(--law-accent-strong);
}

.send-button:disabled {
  background: var(--law-hover);
  color: var(--law-muted);
  cursor: default;
}

.chat-note {
  margin: 10px 0 0;
  text-align: center;
}

@media (max-width: 760px) {
  .composer-hint {
    display: none;
  }

  .composer-actions {
    justify-content: flex-end;
  }

  .chat-footer {
    padding-inline: 14px;
  }
}
</style>
