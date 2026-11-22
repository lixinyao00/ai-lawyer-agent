<template>
  <div class="app-container lawyer-surface session-admin-page">
    <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true" label-width="78px">
      <el-form-item label="用户 ID" prop="userId">
        <el-input
          v-model="queryParams.userId"
          placeholder="请输入用户 ID"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="用户名" prop="userName">
        <el-input
          v-model="queryParams.userName"
          placeholder="请输入用户名"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="会话标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入会话标题"
          clearable
          style="width: 220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="更新时间" style="width: 320px">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- AI 律师 Agent 可观测性指标看板 -->
    <section v-loading="obsLoading" class="obs-dashboard" aria-label="AI 律师 Agent 可观测性指标看板">
      <header class="obs-dashboard-header">
        <div class="obs-dashboard-title-wrap">
          <el-icon class="obs-dashboard-icon"><DataAnalysis /></el-icon>
          <h2 class="obs-dashboard-title">AI 律师 Agent 可观测性指标看板</h2>
        </div>
        <span class="obs-range-pill">{{ activeDateRangeLabel }}</span>
      </header>

      <div class="obs-kpi-grid">
        <article class="obs-kpi-card">
          <div class="obs-kpi-top">
            <span class="obs-kpi-label">总问答请求 / 成功率</span>
            <el-icon class="obs-kpi-icon is-accent"><ChatDotRound /></el-icon>
          </div>
          <div class="obs-kpi-value">
            {{ formatInt(observability.totalRequests) }}<span class="obs-kpi-unit"> 次</span>
          </div>
          <div class="obs-kpi-sub">
            成功 {{ formatInt(observability.successCount) }} / 失败 {{ formatInt(observability.failedCount) }} · 成功率
            <strong class="obs-kpi-highlight">{{ formatPercent(observability.successRate) }}%</strong>
          </div>
        </article>

        <article class="obs-kpi-card">
          <div class="obs-kpi-top">
            <span class="obs-kpi-label">Token 总消耗</span>
            <el-icon class="obs-kpi-icon is-token"><Coin /></el-icon>
          </div>
          <div class="obs-kpi-value">
            {{ formatInt(observability.totalTokens) }}
          </div>
          <div class="obs-kpi-sub">
            Prompt: {{ formatInt(observability.totalPromptTokens) }} / Completion: {{ formatInt(observability.totalCompletionTokens) }}
          </div>
        </article>

        <article class="obs-kpi-card">
          <div class="obs-kpi-top">
            <span class="obs-kpi-label">首字延迟 (TTFT)</span>
            <el-icon class="obs-kpi-icon is-latency"><Timer /></el-icon>
          </div>
          <div class="obs-kpi-value">
            平均 {{ formatMs(observability.avgTtftMs) }}<span class="obs-kpi-unit"> ms</span>
          </div>
          <div class="obs-kpi-sub">
            <strong>P95 {{ formatMs(observability.p95TtftMs, true) }} ms</strong>
          </div>
        </article>

        <article class="obs-kpi-card">
          <div class="obs-kpi-top">
            <span class="obs-kpi-label">端到端总耗时</span>
            <el-icon class="obs-kpi-icon is-latency"><Odometer /></el-icon>
          </div>
          <div class="obs-kpi-value">
            平均 {{ formatMs(observability.avgLatencyMs) }}<span class="obs-kpi-unit"> ms</span>
          </div>
          <div class="obs-kpi-sub">
            覆盖混合检索、工具调用与流式生成全链路
          </div>
        </article>

        <article class="obs-kpi-card">
          <div class="obs-kpi-top">
            <span class="obs-kpi-label">混合 RAG 召回 &amp; 指代改写</span>
            <el-icon class="obs-kpi-icon is-rag"><MagicStick /></el-icon>
          </div>
          <div class="obs-kpi-value">
            RAG 命中率 {{ formatPercent(observability.ragHitRate) }}<span class="obs-kpi-unit">%</span>
          </div>
          <div class="obs-kpi-sub">
            多轮指代改写 {{ formatInt(observability.queryRewriteCount) }} 次
          </div>
        </article>

        <article class="obs-kpi-card">
          <div class="obs-kpi-top">
            <span class="obs-kpi-label">ReAct 工具调用 &amp; 成功率</span>
            <el-icon class="obs-kpi-icon is-agent"><Cpu /></el-icon>
          </div>
          <div class="obs-kpi-value">
            共调用 {{ formatInt(observability.toolCallTotal) }}<span class="obs-kpi-unit"> 次</span>
          </div>
          <div class="obs-kpi-sub">
            成功率 <strong class="obs-kpi-highlight">{{ formatPercent(observability.toolCallSuccessRate) }}%</strong>
          </div>
        </article>
      </div>

      <!-- 分工具调用分布栏 -->
      <div v-if="toolBreakdownList.length > 0" class="obs-tool-breakdown">
        <div class="obs-tool-breakdown-header">
          <span class="obs-tool-breakdown-title">分工具调用分布</span>
          <span class="obs-tool-breakdown-caption">各 ReAct 工具调用次数、成功率与平均耗时</span>
        </div>
        <div class="obs-tool-list">
          <div
            v-for="item in toolBreakdownList"
            :key="item.toolName"
            class="obs-tool-row"
          >
            <div class="obs-tool-name-wrap">
              <span class="obs-tool-label">{{ getAgentToolLabel(item.toolName) || item.toolName }}</span>
              <code class="obs-tool-code">{{ item.toolName }}</code>
            </div>
            <div class="obs-tool-stats">
              <span class="obs-tool-stat-pill">调用 {{ formatInt(item.count) }} 次</span>
              <span class="obs-tool-stat-pill is-success">成功 {{ formatInt(item.successCount) }} 次</span>
              <span class="obs-tool-stat-pill is-duration">平均 {{ formatMs(item.avgDurationMs) }} ms</span>
            </div>
            <div class="obs-tool-progress-wrap">
              <div
                class="obs-tool-progress-track"
                role="progressbar"
                :aria-valuenow="item.successRate"
                aria-valuemin="0"
                aria-valuemax="100"
              >
                <div class="obs-tool-progress-fill" :style="{ width: `${item.successRate}%` }" />
              </div>
              <span class="obs-tool-progress-text">{{ formatPercent(item.successRate) }}%</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="refreshAll" />
    </el-row>

    <el-table v-loading="loading" :data="sessionList">
      <el-table-column label="会话 ID" align="center" prop="id" width="90" />
      <el-table-column label="用户 ID" align="center" prop="userId" width="90" />
      <el-table-column label="用户名" align="center" prop="userName" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="昵称" align="center" prop="nickName" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="会话标题" align="left" prop="title" min-width="200" :show-overflow-tooltip="true" />
      <el-table-column label="消息总数" align="center" prop="messageCount" width="95" />
      <el-table-column label="失败消息数" align="center" prop="failedCount" width="105">
        <template #default="{ row }">
          <el-tag :type="row.failedCount > 0 ? 'danger' : 'info'" size="small">
            {{ row.failedCount ?? 0 }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="累计 Token" align="center" prop="totalTokens" width="110">
        <template #default="{ row }">
          <span class="session-metric-num">{{ row.totalTokens ?? 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="平均 TTFT" align="center" prop="avgTtftMs" width="110">
        <template #default="{ row }">
          <span class="session-metric-num">{{ row.avgTtftMs ? `${row.avgTtftMs} ms` : '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="工具调用" align="center" prop="toolCallCount" width="95">
        <template #default="{ row }">
          <el-tag :type="row.toolCallCount > 0 ? 'primary' : 'info'" effect="plain" size="small">
            {{ row.toolCallCount ? `${row.toolCallCount} 次` : '0' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="165" />
      <el-table-column label="更新时间" align="center" prop="updateTime" width="165" />
      <el-table-column label="操作" align="center" width="170" fixed="right">
        <template #default="{ row }">
          <el-button
            v-hasPermi="['lawyer:manage:query']"
            link
            type="primary"
            icon="View"
            @click="handleAudit(row)"
          >
            消息审计
          </el-button>
          <el-button
            v-hasPermi="['lawyer:manage:remove']"
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />

    <!-- 只读消息审计抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      size="min(760px, 94vw)"
      destroy-on-close
    >
      <div v-loading="msgLoading" class="lawyer-surface audit-drawer-body">
        <el-empty v-if="!msgLoading && !messageList.length" description="该会话暂无消息记录" />

        <div v-else class="audit-timeline">
          <article
            v-for="msg in messageList"
            :key="msg.id"
            class="audit-item"
            :class="msg.role === 'user' ? 'is-user' : 'is-assistant'"
          >
            <header class="audit-item-header">
              <div class="audit-item-badges">
                <el-tag :type="msg.role === 'user' ? 'info' : 'success'" size="small">
                  {{ msg.role === 'user' ? '用户提问' : 'AI 律师助手' }}
                </el-tag>
                <el-tag :type="statusTagType(msg.status)" size="small">
                  {{ statusLabel(msg.status) }}
                </el-tag>
                <el-tag v-if="msg.errorCode" type="danger" effect="plain" size="small">
                  {{ msg.errorCode }}
                </el-tag>
                <el-tag v-if="msg.modelName" type="info" effect="plain" size="small">
                  {{ msg.modelName }}
                </el-tag>
              </div>
              <span class="audit-item-time">#{{ msg.id }} · {{ msg.createTime }}</span>
            </header>

            <!-- 助手消息可观测性指标徽章区 -->
            <div
              v-if="msg.role !== 'user' && hasAssistantMetrics(msg)"
              class="audit-item-metrics"
              aria-label="回答性能与检索指标"
            >
              <span v-if="hasMetricValue(msg.ttftMs)" class="audit-metric-badge">
                首字 {{ msg.ttftMs }}ms
              </span>
              <span v-if="hasMetricValue(msg.totalLatencyMs)" class="audit-metric-badge">
                总耗时 {{ msg.totalLatencyMs }}ms
              </span>
              <span v-if="hasMetricValue(msg.totalTokens)" class="audit-metric-badge is-token">
                Tokens: {{ msg.totalTokens }} (P:{{ msg.promptTokens || 0 }} / C:{{ msg.completionTokens || 0 }})
              </span>
              <span v-if="msg.retrievalMode" class="audit-metric-badge is-mode">
                {{ getRetrievalModeLabel(msg.retrievalMode) }}
              </span>
            </div>

            <!-- ReAct 工具调用轨迹与指代改写回放 -->
            <AgentToolTimeline
              v-if="msg.role !== 'user' && ((msg.toolCalls && msg.toolCalls.length) || msg.rewrittenQuery)"
              :tool-calls="msg.toolCalls || []"
              :rewritten-query="msg.rewrittenQuery || ''"
              :retrieval-mode="msg.retrievalMode || ''"
              :default-expanded="false"
            />

            <div v-if="msg.role === 'user'" class="audit-user-text">{{ msg.content }}</div>
            <div v-else-if="msg.content" class="lawyer-markdown audit-assistant-md" v-html="renderMarkdown(msg.content)" />
            <div v-else class="audit-empty-content">（无正文内容）</div>

            <!-- 检索法条引用卡片回放 -->
            <CitationCards
              v-if="msg.role !== 'user' && msg.citations && msg.citations.length"
              :citations="msg.citations"
            />
          </article>
        </div>

        <div v-if="msgTotal > msgQuery.pageSize" class="audit-pagination">
          <pagination
            v-model:page="msgQuery.pageNum"
            v-model:limit="msgQuery.pageSize"
            :total="msgTotal"
            @pagination="loadAuditMessages"
          />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup name="LawyerSession">
import { ref, reactive, computed, getCurrentInstance, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  Coin,
  Cpu,
  DataAnalysis,
  MagicStick,
  Odometer,
  Timer
} from '@element-plus/icons-vue'
import {
  listAdminSession,
  listAdminMessage,
  delAdminSession,
  getAdminObservability
} from '@/api/lawyer/admin'
import { getAgentToolLabel, getRetrievalModeLabel } from '@/constants/lawyer'
import { renderMarkdown } from '@/utils/lawyer/markdown'
import AgentToolTimeline from '@/views/lawyer/chat/components/AgentToolTimeline.vue'
import CitationCards from '@/views/lawyer/chat/components/CitationCards.vue'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const obsLoading = ref(false)
const showSearch = ref(true)
const sessionList = ref([])
const total = ref(0)
const dateRange = ref([])

const createEmptyObservability = () => ({
  totalRequests: 0,
  successCount: 0,
  failedCount: 0,
  successRate: 0,
  totalPromptTokens: 0,
  totalCompletionTokens: 0,
  totalTokens: 0,
  avgTtftMs: 0,
  p95TtftMs: 0,
  avgLatencyMs: 0,
  ragHitRate: 0,
  queryRewriteCount: 0,
  toolCallTotal: 0,
  toolCallSuccessRate: 0,
  toolBreakdown: []
})

const observability = ref(createEmptyObservability())

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  userId: undefined,
  userName: undefined,
  title: undefined
})

const drawerVisible = ref(false)
const currentSession = ref(null)
const msgLoading = ref(false)
const messageList = ref([])
const msgTotal = ref(0)
const msgQuery = reactive({
  pageNum: 1,
  pageSize: 50
})

const drawerTitle = computed(() => {
  if (!currentSession.value) return '消息审计'
  return `消息审计 - ${currentSession.value.title || '未命名会话'} (ID: ${currentSession.value.id})`
})

const activeDateRangeLabel = computed(() => {
  const begin = dateRange.value?.[0]
  const end = dateRange.value?.[1]
  if (begin && end) return `统计周期：${begin} ~ ${end}`
  if (begin) return `统计起始：${begin}`
  if (end) return `统计截止：${end}`
  return '统计周期：全部时间'
})

const toolBreakdownList = computed(() => {
  const raw = observability.value?.toolBreakdown
  if (!Array.isArray(raw)) return []
  return raw
    .filter((item) => item && item.toolName)
    .map((item) => {
      const count = Number(item.count || 0)
      const successCount = Number(item.successCount || 0)
      const successRate = count > 0 ? Math.min(100, Math.max(0, (successCount * 100) / count)) : 0
      return {
        toolName: item.toolName,
        count,
        successCount,
        avgDurationMs: item.avgDurationMs ?? 0,
        successRate
      }
    })
})

function buildQuery() {
  return {
    pageNum: queryParams.pageNum,
    pageSize: queryParams.pageSize,
    userId: queryParams.userId ? Number(queryParams.userId) : undefined,
    userName: queryParams.userName || undefined,
    title: queryParams.title || undefined,
    beginTime: dateRange.value?.[0] || undefined,
    endTime: dateRange.value?.[1] || undefined
  }
}

function buildObservabilityQuery() {
  return {
    beginTime: dateRange.value?.[0] || undefined,
    endTime: dateRange.value?.[1] || undefined
  }
}

async function getList() {
  loading.value = true
  try {
    const res = await listAdminSession(buildQuery())
    sessionList.value = res.rows || []
    total.value = res.total || 0
  } catch {
    // 异常由 request 拦截器处理
  } finally {
    loading.value = false
  }
}

async function loadObservability() {
  obsLoading.value = true
  try {
    const res = await getAdminObservability(buildObservabilityQuery())
    const payload = res?.data && !Array.isArray(res.data) ? res.data : (res || {})
    observability.value = {
      ...createEmptyObservability(),
      ...payload,
      toolBreakdown: Array.isArray(payload.toolBreakdown) ? payload.toolBreakdown : []
    }
  } catch {
    observability.value = createEmptyObservability()
  } finally {
    obsLoading.value = false
  }
}

function refreshAll() {
  getList()
  loadObservability()
}

function handleQuery() {
  queryParams.pageNum = 1
  refreshAll()
}

function resetQuery() {
  dateRange.value = []
  proxy.resetForm('queryRef')
  queryParams.userId = undefined
  queryParams.userName = undefined
  queryParams.title = undefined
  handleQuery()
}

function parseJsonArray(val) {
  if (Array.isArray(val)) return val
  if (typeof val === 'string' && val.trim()) {
    try {
      const parsed = JSON.parse(val)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return []
}

function normalizeAuditMessage(row) {
  return {
    ...row,
    toolCalls: parseJsonArray(row.toolCalls),
    citations: parseJsonArray(row.citations),
    rewrittenQuery: row.rewrittenQuery || '',
    retrievalMode: row.retrievalMode || ''
  }
}

async function handleAudit(row) {
  currentSession.value = row
  msgQuery.pageNum = 1
  drawerVisible.value = true
  await loadAuditMessages()
}

async function loadAuditMessages() {
  if (!currentSession.value) return
  msgLoading.value = true
  try {
    const res = await listAdminMessage(currentSession.value.id, {
      pageNum: msgQuery.pageNum,
      pageSize: msgQuery.pageSize
    })
    const rows = (res.rows || []).map(normalizeAuditMessage)
    messageList.value = [...rows].sort((a, b) => (a.id || 0) - (b.id || 0))
    msgTotal.value = res.total || 0
  } catch {
    messageList.value = []
  } finally {
    msgLoading.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除会话“${row.title || row.id}”（用户：${row.userName || row.userId}）及其全部消息吗？删除后不可恢复。`,
      '删除会话',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  try {
    await delAdminSession(row.id)
    ElMessage.success('删除成功')
    if (sessionList.value.length === 1 && queryParams.pageNum > 1) {
      queryParams.pageNum -= 1
    }
    refreshAll()
  } catch (error) {
    const code = error?.code ?? error?.response?.data?.code
    const msg = String(error?.msg || error?.message || '')
    if (Number(code) === 409 || msg.includes('409') || msg.includes('正在生成')) {
      ElMessage.warning('当前会话正在生成回答，请稍后再试')
    }
  }
}

function hasMetricValue(val) {
  return val !== null && val !== undefined && val !== ''
}

function hasAssistantMetrics(msg) {
  return (
    hasMetricValue(msg.ttftMs) ||
    hasMetricValue(msg.totalLatencyMs) ||
    hasMetricValue(msg.totalTokens) ||
    Boolean(msg.retrievalMode)
  )
}

function formatInt(val) {
  const num = Number(val ?? 0)
  if (!Number.isFinite(num)) return '0'
  return Math.round(num).toLocaleString('en-US')
}

function formatPercent(val) {
  const num = Number(val ?? 0)
  if (!Number.isFinite(num)) return '0.00'
  return num.toFixed(2)
}

function formatMs(val, asInteger = false) {
  const num = Number(val ?? 0)
  if (!Number.isFinite(num)) return '0'
  if (asInteger) return Math.round(num).toLocaleString('en-US')
  return Number.isInteger(num) ? num.toLocaleString('en-US') : num.toFixed(1)
}

function statusTagType(status) {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'pending') return 'warning'
  return 'info'
}

function statusLabel(status) {
  if (status === 'success') return '成功'
  if (status === 'failed') return '失败'
  if (status === 'pending') return '生成中'
  return status || '未知'
}

onMounted(refreshAll)
</script>

<style scoped>
.audit-drawer-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 16px;
}

.audit-timeline {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.audit-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  border: 1px solid var(--law-line);
  border-radius: 12px;
  background: var(--law-card);
}

.audit-item.is-user {
  background: var(--law-panel);
}

.audit-item-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.audit-item-badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.audit-item-time {
  color: var(--law-muted);
  font-size: 12px;
}

.audit-user-text {
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.audit-assistant-md {
  font-size: 14px;
}

.audit-empty-content {
  color: var(--law-muted);
  font-size: 13px;
}

.audit-pagination {
  display: flex;
  justify-content: flex-end;
}
</style>
