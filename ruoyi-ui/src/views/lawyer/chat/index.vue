<template>
  <div class="chat-page lawyer-surface" @keydown.esc="closeMobileSidebar">
    <button v-if="isCompact && sidebarOpen" type="button" class="chat-backdrop" aria-label="关闭会话列表" @click="sidebarOpen = false" />
    <aside v-show="sidebarOpen" id="lawyer-history" class="chat-sidebar" aria-label="咨询会话">
      <div class="side-heading">
        <div class="workspace-brand">
          <LawyerMark />
          <span>AI 律师平台</span>
        </div>
        <button type="button" class="icon-button close-sidebar" aria-label="收起会话列表" title="收起会话列表" @click="sidebarOpen = false">
          <el-icon><Fold /></el-icon>
        </button>
      </div>

      <button v-if="canCreate" type="button" class="chat-new" :disabled="creating" @click="handleCreateSession">
        <el-icon><Plus /></el-icon><span>新建会话</span>
      </button>

      <section class="side-history">
        <h2>最近会话</h2>
        <div class="chat-session-list" :aria-busy="loading">
          <div v-for="session in sessions" :key="session.id" class="chat-session" :class="{ active: session.id === activeSessionId }">
            <button
              type="button"
              class="chat-session-main"
              :aria-current="session.id === activeSessionId ? 'true' : undefined"
              @click="handleSelectSession(session.id)"
            >
              <el-icon><ChatLineRound /></el-icon>
              <span :title="session.title">{{ session.title }}</span>
            </button>
            <button
              v-if="canDelete"
              type="button"
              class="chat-session-delete"
              :disabled="deletingId !== null"
              :aria-label="'删除会话 ' + session.title"
              title="删除会话"
              @click="handleDeleteSession(session)"
            >
              <el-icon><Delete /></el-icon>
            </button>
          </div>
          <p v-if="!sessions.length" class="chat-session-empty">{{ loading ? '正在加载会话…' : '暂无历史会话' }}</p>
        </div>
      </section>

      <div class="side-account">
        <el-dropdown trigger="click" placement="top-start" @command="handleAccount">
          <button type="button" class="account-button" aria-label="账户菜单">
            <span class="account-avatar" aria-hidden="true">{{ accountInitial }}</span>
            <span class="account-name">{{ accountName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="lock">锁定屏幕</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </aside>

    <main class="chat-main">
      <header class="chat-header">
        <div class="chat-header-start">
          <button
            v-if="!sidebarOpen || isCompact"
            type="button"
            class="icon-button open-sidebar"
            aria-label="打开会话列表"
            aria-controls="lawyer-history"
            :aria-expanded="sidebarOpen"
            title="打开会话列表"
            @click="sidebarOpen = true"
          >
            <el-icon><Expand /></el-icon>
          </button>
          <span class="header-section">智能咨询</span>
          <template v-if="activeSession && activeSession.title !== DEFAULT_TITLE">
            <span class="header-divider" aria-hidden="true"></span>
            <span class="header-session" :title="activeSession.title">{{ activeSession.title }}</span>
          </template>
        </div>
        <div class="chat-header-actions">
          <button
            type="button"
            class="icon-button"
            :aria-label="settingsStore.isDark ? '切换浅色模式' : '切换深色模式'"
            :title="settingsStore.isDark ? '切换浅色模式' : '切换深色模式'"
            @click="settingsStore.toggleTheme"
          >
            <el-icon><Sunny v-if="settingsStore.isDark" /><Moon v-else /></el-icon>
          </button>
          <router-link v-if="managementLink" class="management-link" :to="managementLink">
            <el-icon><Setting /></el-icon><span>管理后台</span>
          </router-link>
        </div>
      </header>

      <div ref="messageListRef" class="chat-messages" :class="{ 'chat-messages--empty': !messages.length }">
        <div v-if="!messages.length" class="chat-empty">
          <template v-if="loading || activeSession?.messagesLoading">
            <p class="chat-loading" role="status">正在加载咨询…</p>
          </template>
          <template v-else-if="canSendMessages">
            <div class="hero-mark"><LawyerMark /></div>
            <h1>今天想咨询什么？</h1>
            <p class="chat-intro">描述你的情况，我帮你梳理关键事实和下一步。</p>
            <div class="chat-suggestions" aria-label="示例问题">
              <button v-for="item in suggestions" :key="item.title" class="suggestion" type="button" @click="useSuggestion(item.prompt)">
                <span class="suggestion-icon"><el-icon><component :is="item.icon" /></el-icon></span>
                <span class="suggestion-text"><strong>{{ item.title }}</strong><span>{{ item.caption }}</span></span>
                <el-icon class="suggestion-arrow"><TopRight /></el-icon>
              </button>
            </div>
          </template>
          <template v-else>
            <div class="hero-mark"><LawyerMark /></div>
            <h1>咨询记录</h1>
            <p class="chat-intro">暂无可显示的消息。</p>
          </template>
        </div>

        <div v-else class="chat-transcript">
          <article v-for="message in messages" :key="message.id" class="chat-row" :class="message.role === 'user' ? 'is-user' : 'is-assistant'">
            <LawyerMark v-if="message.role !== 'user'" class="reply-mark" />
            <div class="chat-bubble" :class="message.role === 'user' ? 'bubble-user' : 'bubble-assistant'">
              <span v-if="message.role !== 'user'" class="reply-name">AI 律师助手</span>
              <span v-if="message.status === 'pending'" class="chat-pending" role="status">正在整理思路<span class="pending-dots" aria-hidden="true"><i></i><i></i><i></i></span></span>
              <span v-else-if="message.status === 'failed'" class="chat-text chat-error-text" role="status">{{ message.error || '回答生成失败' }}</span>
              <span v-else class="chat-text">{{ message.content }}</span>
            </div>
          </article>
        </div>
      </div>

      <footer v-if="canSendMessages" class="chat-footer">
        <div class="composer">
          <el-input
            ref="composerRef"
            v-model="draft"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 6 }"
            resize="none"
            maxlength="4000"
            aria-label="输入法律问题"
            placeholder="输入你的法律问题…"
            @keydown="handleKeydown"
          />
          <div class="composer-actions">
            <span class="composer-hint">Enter 发送 · Shift + Enter 换行</span>
            <button type="button" class="send-button" aria-label="发送消息" title="发送消息" :disabled="!canSend" @click="handleSend">
              <el-icon><Top /></el-icon>
            </button>
          </div>
        </div>
        <p class="chat-note">AI 生成内容，法律依据请核验</p>
      </footer>
    </main>
  </div>
</template>

<script setup name="LawChat">
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useMediaQuery } from '@vueuse/core'
import { ArrowDown, ChatLineRound, Delete, Document, Expand, Fold, House, Moon, Plus, Setting, Suitcase, Sunny, Top, TopRight } from '@element-plus/icons-vue'
import LawyerMark from '@/components/LawyerMark.vue'
import useSettingsStore from '@/store/modules/settings'
import useUserStore from '@/store/modules/user'
import useLockStore from '@/store/modules/lock'
import auth from '@/plugins/auth'
import { listSession, addSession, delSession } from '@/api/lawyer/session'
import { listMessage, sendMessage, buildRequestId } from '@/api/lawyer/chat'

const DEFAULT_TITLE = '新咨询'

const suggestions = [
  { icon: House, title: '房屋租赁', caption: '押金、退租与维修', prompt: '我在租房时遇到了纠纷，想了解需要先整理哪些信息。' },
  { icon: Suitcase, title: '劳动权益', caption: '用工、工资与离职', prompt: '我遇到了劳动争议，应该先整理哪些事实和材料？' },
  { icon: Document, title: '合同条款', caption: '义务、风险与违约', prompt: '我想了解签订合同前应该重点核对哪些条款。' },
]

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const settingsStore = useSettingsStore()
const isCompact = useMediaQuery('(max-width: 760px)')
const sidebarOpen = ref(!isCompact.value)
const accountName = computed(() => userStore.nickName || userStore.name || '我的账号')
const accountInitial = computed(() => Array.from(accountName.value)[0])
const managementLink = computed(() => [
  ['system:user:list', '/system/user'],
  ['system:role:list', '/system/role'],
  ['monitor:online:list', '/monitor/online'],
  ['monitor:logininfor:list', '/system/log/logininfor'],
  ['monitor:operlog:list', '/system/log/operlog'],
].find(([permission]) => auth.hasPermi(permission))?.[1])

watch(isCompact, compact => { sidebarOpen.value = !compact })

function closeMobileSidebar() {
  if (isCompact.value) sidebarOpen.value = false
}

async function handleAccount(command) {
  if (command === 'profile') {
    await router.push({ name: 'Profile' })
  } else if (command === 'lock') {
    useLockStore().lockScreen(route.fullPath)
    await router.push('/lock')
  } else if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录吗？', '退出登录', { confirmButtonText: '退出', cancelButtonText: '取消' })
      await userStore.logOut()
      location.href = '/login'
    } catch {
      // 取消确认时留在当前咨询。
    }
  }
}

const sessions = ref([])
const activeSessionId = ref(null)
const draft = ref('')
const sending = ref(false)
const loading = ref(false)
const creating = ref(false)
const deletingId = ref(null)
const messageListRef = ref(null)
const composerRef = ref(null)

function useSuggestion(prompt) {
  draft.value = prompt
  nextTick(() => composerRef.value?.focus())
}

const activeSession = computed(() => sessions.value.find((item) => item.id === activeSessionId.value) || null)
const messages = computed(() => activeSession.value?.messages || [])
const canCreate = computed(() => auth.hasPermi('lawyer:session:add'))
const canDelete = computed(() => auth.hasPermi('lawyer:session:remove'))
const canReadMessages = computed(() => auth.hasPermi('lawyer:chat:query'))
const canSendMessages = computed(() => auth.hasPermi('lawyer:chat:send') && (activeSession.value !== null || canCreate.value))
const canSend = computed(() => canSendMessages.value && draft.value.trim().length > 0 && !sending.value && !activeSession.value?.messagesLoading)

let messageSeq = 0
function nextMessageId() {
  messageSeq += 1
  return `local-${messageSeq}`
}

// 后端返回的消息统一补上本地渲染需要的状态
function normalizeMessage(row) {
  return { ...row, pendingLocal: false, error: row.status === 'failed' ? '回答生成失败' : '' }
}

async function loadMessages(sessionId) {
  const session = sessions.value.find((item) => item.id === sessionId)
  if (!canReadMessages.value || !session || session.messagesLoaded || session.messagesLoading) {
    scrollToBottom()
    return
  }
  session.messagesLoading = true
  try {
    const res = await listMessage(sessionId, { pageNum: 1, pageSize: 50 })
    session.messages = (res.rows || []).map(normalizeMessage)
    session.messagesLoaded = true
  } catch (error) {
    // 错误提示由 request 拦截器处理。
  } finally {
    session.messagesLoading = false
  }
  scrollToBottom()
}

// 切换会话时从后端读取该会话的历史消息
watch(activeSessionId, (id) => {
  if (id !== null) {
    loadMessages(id)
  }
})

function normalizeSession(data, existing, messagesLoaded = false) {
  return { ...data, messages: existing?.messages || [], messagesLoaded: existing?.messagesLoaded ?? messagesLoaded, messagesLoading: false }
}

async function loadSessions() {
  loading.value = true
  try {
    const res = await listSession({ pageNum: 1, pageSize: 20 })
    const previous = sessions.value
    sessions.value = (res.rows || []).map((row) => {
      const existing = previous.find((item) => item.id === row.id)
      return normalizeSession(row, existing)
    })
    if (!sessions.value.some((item) => item.id === activeSessionId.value)) {
      activeSessionId.value = sessions.value.length > 0 ? sessions.value[0].id : null
    }
  } catch (error) {
    // 列表加载失败时保持空列表，错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

onMounted(loadSessions)

async function handleCreateSession() {
  if (!canCreate.value || creating.value) {
    return
  }
  creating.value = true
  try {
    const res = await addSession({})
    sessions.value.unshift(normalizeSession(res.data, undefined, true))
    handleSelectSession(res.data.id)
    nextTick(() => composerRef.value?.focus())
  } catch (error) {
    // 新建失败时不改动列表，错误提示由 request 拦截器统一处理
  } finally {
    creating.value = false
  }
}

function handleSelectSession(id) {
  activeSessionId.value = id
  closeMobileSidebar()
  scrollToBottom()
}

async function handleDeleteSession(session) {
  if (!canDelete.value || deletingId.value !== null) {
    return
  }

  try {
    await ElMessageBox.confirm(`确认删除会话“${session.title}”吗？删除后不可恢复。`, '删除会话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (error) {
    // 用户取消，不做任何处理
    return
  }

  deletingId.value = session.id
  try {
    await delSession(session.id)
    const index = sessions.value.findIndex((item) => item.id === session.id)
    if (index > -1) {
      sessions.value.splice(index, 1)
    }
    // 删除的是当前会话时，自动切到列表第一个
    if (activeSessionId.value === session.id) {
      activeSessionId.value = sessions.value.length > 0 ? sessions.value[0].id : null
    }
  } catch (error) {
    // 删除失败时保留列表，错误提示由 request 拦截器统一处理
  } finally {
    deletingId.value = null
  }
}

function buildTitle(content) {
  const text = content.replace(/\s+/g, ' ').trim()
  return text.length > 20 ? `${text.slice(0, 20)}…` : text
}

function scrollToBottom() {
  nextTick(() => {
    const el = messageListRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

async function handleSend() {
  const content = draft.value.trim()
  if (!canSend.value) {
    return
  }
  sending.value = true

  try {
    // 还没有会话时直接提问，先建一个会话，标题用问题前 20 字
    let session = activeSession.value
    if (!session) {
      const res = await addSession({ title: buildTitle(content) })
      sessions.value.unshift(normalizeSession(res.data, undefined, true))
      activeSessionId.value = res.data.id
      session = sessions.value[0]
    }

    // 本地立即显示自己的消息和等待中的占位
    const localUserMessage = { id: nextMessageId(), role: 'user', content, status: 'success', pendingLocal: true }
    const localReply = { id: nextMessageId(), role: 'assistant', content: '', status: 'pending', pendingLocal: true }
    session.messages.push(localUserMessage, localReply)
    draft.value = ''
    scrollToBottom()

    // 发送请求，requestId 是幂等键，重试时重新生成
    const requestId = buildRequestId()
    let data
    try {
      const res = await sendMessage(session.id, { content, requestId })
      data = res.data
    } catch (error) {
      // 接口失败：本地占位转为失败状态，提示后端返回的信息（拦截器已弹过提示）
      const failedReply = session.messages.find(message => message.id === localReply.id)
      if (failedReply) {
        failedReply.status = 'failed'
        failedReply.error = '发送失败，请稍后重试'
      }
      sending.value = false
      return
    }

    // 用后端真实数据替换本地占位
    const userIndex = session.messages.indexOf(localUserMessage)
    if (userIndex > -1) {
      session.messages.splice(userIndex, 2, {
        id: data.userMessageId,
        role: 'user',
        content,
        status: 'success',
        pendingLocal: false,
        error: ''
      }, {
        id: data.assistantMessageId,
        role: 'assistant',
        content: data.content || '',
        status: data.status,
        pendingLocal: false,
        error: data.errorMessage || (data.status === 'failed' ? '回答生成失败' : '')
      })
    }
    // 后端可能更新了会话标题（首条消息自动命名）
    if (data.status === 'success' && session.title === DEFAULT_TITLE) {
      session.title = buildTitle(content)
    }
    sending.value = false
    scrollToBottom()
  } catch (error) {
    sending.value = false
  }
}

function handleKeydown(event) {
  // 中文输入法选字时按 Enter 不发送
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) {
    return
  }
  event.preventDefault()
  handleSend()
}
</script>

<style scoped>
.chat-page {
  display: flex;
  width: 100%;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: var(--law-bg);
}

.chat-page :where(button) {
  font: inherit;
}

.chat-page button:focus-visible,
.management-link:focus-visible {
  outline: 2px solid var(--law-accent);
  outline-offset: 2px;
}

.chat-sidebar {
  display: flex;
  flex: 0 0 258px;
  flex-direction: column;
  width: 258px;
  min-width: 0;
  min-height: 0;
  margin: 0;
  padding: 20px 14px 13px;
  border-right: 1px solid var(--law-line);
  border-radius: 0;
  background: var(--law-panel);
  color: var(--law-ink);
  font: inherit;
}

.side-heading,
.workspace-brand,
.chat-header-start,
.chat-header-actions {
  display: flex;
  align-items: center;
}

.side-heading {
  justify-content: space-between;
  gap: 4px;
  padding: 0 1px 0 4px;
}

.workspace-brand {
  gap: 10px;
  font-size: 15px;
  font-weight: 690;
  white-space: nowrap;
}

.icon-button {
  display: grid;
  flex: 0 0 38px;
  place-items: center;
  width: 38px;
  height: 38px;
  padding: 0;
  border: 0;
  border-radius: 11px;
  color: var(--law-muted);
  background: transparent;
  cursor: pointer;
  font-size: 18px;
}

.icon-button:hover,
.account-button:hover,
.chat-session-main:hover {
  background: var(--law-hover);
  color: var(--law-ink);
}

.chat-new {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  width: 100%;
  min-height: 43px;
  margin-top: 27px;
  border: 1px solid var(--law-line);
  border-radius: 12px;
  background: var(--law-card);
  color: var(--law-ink);
  font-weight: 620;
  box-shadow: 0 1px 2px #20272009;
  cursor: pointer;
}

.chat-new:hover:not(:disabled) {
  border-color: var(--law-accent);
}

.chat-new:disabled {
  cursor: wait;
  opacity: .65;
}

.side-history {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  margin-top: 32px;
}

.side-history h2 {
  margin: 0 10px 11px;
  color: var(--law-muted);
  font-size: 12px;
  font-weight: 630;
  letter-spacing: .03em;
}

.chat-session-list {
  overflow: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--law-line) transparent;
}

.chat-session {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-bottom: 4px;
  border-radius: 10px;
}

.chat-session:hover,
.chat-session:focus-within {
  background: var(--law-hover);
}

.chat-session.active {
  background: var(--law-wash);
}

.chat-session-main {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 9px;
  min-width: 0;
  min-height: 39px;
  padding: 0 8px 0 10px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--law-ink);
  text-align: left;
  cursor: pointer;
}

.chat-session-main:hover {
  background: transparent;
}

.chat-session-main span,
.account-name,
.header-session {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-session-main .el-icon,
.chat-session-delete {
  color: var(--law-muted);
  flex: none;
}

.chat-session-delete {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  padding: 0;
  margin-right: 3px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  opacity: 0;
  cursor: pointer;
}

.chat-session:hover .chat-session-delete,
.chat-session:focus-within .chat-session-delete {
  opacity: 1;
}

.chat-session-delete:hover {
  color: var(--el-color-danger);
  background: var(--law-card);
}

.chat-session-empty {
  margin: 0;
  padding: 9px 10px;
  color: var(--law-muted);
  font-size: 13px;
}

.side-account {
  margin-top: 14px;
  padding-top: 11px;
  border-top: 1px solid var(--law-line);
}

.side-account :deep(.el-dropdown) {
  display: flex;
  width: 100%;
}

.account-button {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-width: 0;
  padding: 7px 8px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--law-ink);
  cursor: pointer;
}

.account-avatar {
  display: grid;
  flex: 0 0 32px;
  place-items: center;
  height: 32px;
  border-radius: 50%;
  background: var(--law-wash);
  color: var(--law-accent);
  font-weight: 680;
}

.account-name {
  flex: 1;
  text-align: left;
}

.account-button .el-icon {
  color: var(--law-muted);
  font-size: 12px;
}

.chat-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  background: var(--law-bg);
}

.chat-header {
  display: flex;
  flex: 0 0 69px;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 0 28px;
  border-bottom: 1px solid var(--law-line);
}

.chat-header-start {
  min-width: 0;
  gap: 13px;
}

.header-section {
  flex: none;
  font-size: 15px;
  font-weight: 680;
}

.header-divider {
  width: 1px;
  height: 14px;
  flex: none;
  background: var(--law-line);
}

.header-session {
  color: var(--law-muted);
  font-size: 13px;
}

.chat-header-actions {
  flex: none;
  gap: 9px;
}

.management-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 35px;
  padding: 0 11px;
  border: 1px solid var(--law-line);
  border-radius: 10px;
  color: var(--law-ink);
  white-space: nowrap;
  font-size: 13px;
}

.management-link:hover {
  color: var(--law-accent);
  background: var(--law-hover);
}

.chat-messages {
  flex: 1;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: var(--law-line) transparent;
}

.chat-messages--empty {
  display: grid;
  place-items: center;
  background: radial-gradient(ellipse 335px 230px at 50% 43%, color-mix(in srgb, var(--law-wash) 38%, transparent), transparent);
}

.chat-empty {
  width: min(100%, 812px);
  padding: 44px 24px 33px;
  text-align: center;
}

.hero-mark {
  display: grid;
  width: 75px;
  height: 75px;
  place-items: center;
  margin: 0 auto 23px;
  border: 1px solid color-mix(in srgb, var(--law-accent) 13%, var(--law-line));
  border-radius: 25px;
  background: linear-gradient(145deg, var(--law-card), var(--law-wash));
  box-shadow: var(--law-shadow);
  --mark-size: 43px;
  --mark-bg: transparent;
  --mark-ink: var(--law-accent);
}

.chat-empty h1 {
  margin: 0;
  font-size: clamp(28px, 3.5vw, 47px);
  font-weight: 690;
  letter-spacing: -.045em;
  line-height: 1.16;
}

.chat-intro,
.chat-loading {
  margin: 15px 0 0;
  color: var(--law-muted);
  font-size: 15px;
  line-height: 1.65;
}

.chat-suggestions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 11px;
  margin: 36px auto 0;
}

.suggestion {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  min-height: 70px;
  padding: 10px 12px;
  border: 1px solid var(--law-line);
  border-radius: 15px;
  background: var(--law-card);
  color: var(--law-ink);
  text-align: left;
  box-shadow: 0 1px 2px #20272008;
  cursor: pointer;
  transition: background .18s, border-color .18s, transform .18s;
}

.suggestion:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--law-accent) 45%, var(--law-line));
  background: color-mix(in srgb, var(--law-card) 75%, var(--law-wash));
}

.suggestion-icon {
  display: grid;
  flex: 0 0 34px;
  place-items: center;
  height: 34px;
  border-radius: 10px;
  background: var(--law-wash);
  color: var(--law-accent);
  font-size: 17px;
}

.suggestion-text {
  display: grid;
  flex: 1;
  gap: 4px;
  min-width: 0;
}

.suggestion-text strong {
  font-weight: 685;
}

.suggestion-text span,
.suggestion-arrow {
  color: var(--law-muted);
  font-size: 12px;
}

.suggestion-arrow {
  flex: none;
  align-self: flex-start;
  margin-top: 4px;
}

.chat-transcript {
  width: min(100%, 812px);
  margin: 0 auto;
  padding: 35px 24px 17px;
}

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

.chat-backdrop {
  display: none;
}

@media (max-width: 980px) {
  .chat-suggestions {
    gap: 8px;
  }

  .suggestion {
    gap: 7px;
    padding-inline: 9px;
  }

  .suggestion-arrow {
    display: none;
  }
}

@media (max-width: 760px) {
  .chat-sidebar {
    position: fixed;
    z-index: 20;
    inset: 0 auto 0 0;
    width: min(292px, calc(100vw - 46px));
    padding-bottom: max(13px, env(safe-area-inset-bottom));
    box-shadow: 10px 0 34px #16211928;
  }

  .chat-backdrop {
    position: fixed;
    z-index: 19;
    inset: 0;
    display: block;
    width: 100%;
    padding: 0;
    border: 0;
    background: #15201742;
    backdrop-filter: blur(2px);
  }

  .chat-header {
    flex-basis: 56px;
    padding-inline: 14px;
  }

  .chat-header-start {
    gap: 7px;
  }

  .chat-session-delete {
    opacity: 1;
  }

  .chat-empty {
    padding: 27px 16px 20px;
  }

  .hero-mark {
    width: 62px;
    height: 62px;
    margin-bottom: 17px;
    border-radius: 21px;
  }

  .chat-intro {
    margin-top: 11px;
    font-size: 14px;
  }

  .chat-suggestions {
    max-width: 460px;
    margin-top: 23px;
  }

  .suggestion {
    flex-direction: column;
    align-items: flex-start;
    min-height: 72px;
    padding: 8px 9px;
    border-radius: 12px;
  }

  .suggestion-icon {
    flex-basis: auto;
    width: 29px;
    height: 29px;
    font-size: 16px;
  }

  .suggestion-text {
    font-size: 13px;
  }

  .suggestion-text span,
  .composer-hint {
    display: none;
  }

  .composer-actions {
    justify-content: flex-end;
  }

  .chat-transcript,
  .chat-footer {
    padding-inline: 14px;
  }

  .chat-transcript {
    padding-top: 24px;
  }

  .chat-row {
    margin-bottom: 18px;
  }
}

@media (hover: none) {
  .chat-session-delete { opacity: 1; }
}

@media (max-height: 620px) {
  .chat-empty { padding-top: 20px; padding-bottom: 12px; }
  .hero-mark { margin-bottom: 12px; }
  .chat-suggestions { margin-top: 18px; }
}

@media (prefers-reduced-motion: reduce) {
  .suggestion { transition: none; }
  .pending-dots i { animation: none; }
}
</style>
