<template>
  <section
    v-if="hasRewrittenQuery || sortedToolCalls.length > 0"
    class="agent-trace-wrap"
    aria-label="AI 法律 Agent 执行轨迹"
  >
    <!-- 顶部改写词与检索模式轻提示条 -->
    <div v-if="hasRewrittenQuery" class="agent-rewrite-banner" role="note">
      <div class="agent-rewrite-content">
        <el-icon class="agent-rewrite-icon"><MagicStick /></el-icon>
        <span class="agent-rewrite-text">
          已结合上下文将检索词优化为：「{{ rewrittenQuery.trim() }}」
        </span>
      </div>
      <span v-if="retrievalModeText" class="agent-retrieval-pill">
        {{ retrievalModeText }}
      </span>
    </div>

    <!-- ReAct 工具调用时间线卡片 -->
    <div v-if="sortedToolCalls.length > 0" class="agent-timeline-card">
      <button
        type="button"
        class="agent-timeline-header"
        :aria-expanded="expanded"
        @click="expanded = !expanded"
      >
        <span class="agent-timeline-header-left">
          <el-icon class="agent-timeline-arrow" :class="{ 'is-expanded': expanded }">
            <ArrowRight />
          </el-icon>
          <el-icon class="agent-timeline-cpu"><Cpu /></el-icon>
          <span class="agent-timeline-title">
            AI 法律 Agent 工具调用轨迹（{{ sortedToolCalls.length }} 步）
          </span>
        </span>
        <span class="agent-overall-badge" :class="overallStatusClass">
          <el-icon v-if="hasRunning" class="is-spinning"><Loading /></el-icon>
          <el-icon v-else><CircleCheckFilled /></el-icon>
          <span>{{ overallStatusLabel }}</span>
        </span>
      </button>

      <div v-show="expanded" class="agent-timeline-body">
        <ol class="agent-step-list">
          <li
            v-for="(item, idx) in sortedToolCalls"
            :key="stepKey(item, idx)"
            class="agent-step-item"
            :class="[`is-${item.status || 'running'}`, { 'is-last': idx === sortedToolCalls.length - 1 }]"
          >
            <!-- 左侧步骤轴线与状态指示器 -->
            <div class="agent-step-rail" aria-hidden="true">
              <span class="agent-step-dot" :class="`is-${item.status || 'running'}`">
                <el-icon v-if="item.status === 'running'" class="is-spinning"><Loading /></el-icon>
                <el-icon v-else-if="item.status === 'error'"><WarningFilled /></el-icon>
                <el-icon v-else><CircleCheckFilled /></el-icon>
              </span>
              <span v-if="idx < sortedToolCalls.length - 1" class="agent-step-line" />
            </div>

            <!-- 右侧步骤内容区 -->
            <div class="agent-step-content">
              <div class="agent-step-top">
                <span class="agent-step-index">Step {{ item.step ?? idx + 1 }}</span>
                <span class="agent-tool-tag" :class="getToolColorClass(item.toolName)">
                  {{ getAgentToolLabel(item.toolName) }}
                </span>
                <span v-if="item.displayTitle" class="agent-step-display-title">
                  {{ item.displayTitle }}
                </span>
                <span class="agent-step-meta-right">
                  <span
                    v-if="hasDuration(item.durationMs)"
                    class="agent-duration-badge"
                  >
                    {{ item.durationMs }}ms
                  </span>
                  <span
                    class="agent-step-status-pill"
                    :class="`is-${item.status || 'running'}`"
                  >
                    {{ getStepStatusLabel(item.status) }}
                  </span>
                </span>
              </div>

              <!-- 自愈重试醒目标注 -->
              <div v-if="item.status === 'error'" class="agent-self-heal-notice" role="status">
                <el-icon><WarningFilled /></el-icon>
                <span>参数校验未通过，已反馈模型自愈重试</span>
              </div>

              <!-- 结果摘要 -->
              <p v-if="resolveSummary(item)" class="agent-step-summary">
                {{ resolveSummary(item) }}
              </p>

              <!-- 底部动作栏：详情展开 + 文书草稿跳转 -->
              <div
                v-if="hasExpandableDetail(item) || hasDraftId(item)"
                class="agent-step-actions"
              >
                <button
                  v-if="hasExpandableDetail(item)"
                  type="button"
                  class="agent-detail-toggle"
                  :aria-expanded="isDetailExpanded(item, idx)"
                  @click="toggleDetail(item, idx)"
                >
                  <el-icon class="agent-detail-toggle-arrow" :class="{ 'is-expanded': isDetailExpanded(item, idx) }">
                    <ArrowRight />
                  </el-icon>
                  <span>{{ isDetailExpanded(item, idx) ? '收起计算过程/参数详情' : '查看计算过程/参数详情' }}</span>
                </button>

                <button
                  v-if="hasDraftId(item)"
                  type="button"
                  class="agent-draft-jump-btn"
                  @click="goToDocumentDraft(item.draftId)"
                >
                  <el-icon><DocumentChecked /></el-icon>
                  <span>前往编辑 / 导出《文书草稿》→</span>
                </button>
              </div>

              <!-- 可展开详情区 -->
              <div v-if="hasExpandableDetail(item) && isDetailExpanded(item, idx)" class="agent-detail-panel">
                <template v-if="isFormulaDetail(parseDetail(item))">
                  <div class="agent-formula-card">
                    <div v-if="parseDetail(item).formula" class="agent-formula-row">
                      <span class="agent-formula-label">计算公式</span>
                      <code class="agent-formula-code">{{ parseDetail(item).formula }}</code>
                    </div>

                    <div v-if="buildCalcBadges(parseDetail(item)).length > 0" class="agent-calc-badges">
                      <span
                        v-for="badge in buildCalcBadges(parseDetail(item))"
                        :key="badge.label"
                        class="agent-calc-badge"
                        :class="badge.tone"
                      >
                        {{ badge.label }}
                      </span>
                    </div>

                    <ol
                      v-if="Array.isArray(parseDetail(item).steps) && parseDetail(item).steps.length > 0"
                      class="agent-calc-steps"
                    >
                      <li v-for="(stepText, sIdx) in parseDetail(item).steps" :key="sIdx">
                        {{ stepText }}
                      </li>
                    </ol>

                    <div v-if="parseDetail(item).legalBasis" class="agent-legal-basis">
                      <span class="agent-legal-basis-label">法定依据：</span>
                      <span>{{ parseDetail(item).legalBasis }}</span>
                    </div>
                  </div>
                </template>

                <template v-else>
                  <div v-if="buildKvEntries(item).length > 0" class="agent-kv-grid">
                    <div
                      v-for="entry in buildKvEntries(item)"
                      :key="entry.key"
                      class="agent-kv-row"
                    >
                      <span class="agent-kv-key">{{ entry.key }}</span>
                      <span class="agent-kv-val">{{ entry.value }}</span>
                    </div>
                  </div>
                </template>
              </div>
            </div>
          </li>
        </ol>
      </div>
    </div>
  </section>
</template>

<script setup name="AgentToolTimeline">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  CircleCheckFilled,
  Cpu,
  DocumentChecked,
  Loading,
  MagicStick,
  WarningFilled
} from '@element-plus/icons-vue'
import {
  AGENT_TOOL_META,
  TOOL_STATUS_META,
  getAgentToolLabel,
  getRetrievalModeLabel
} from '@/constants/lawyer'

const props = defineProps({
  toolCalls: {
    type: Array,
    default: () => []
  },
  rewrittenQuery: {
    type: String,
    default: ''
  },
  retrievalMode: {
    type: String,
    default: ''
  },
  defaultExpanded: {
    type: Boolean,
    default: true
  }
})

const router = useRouter()
const expanded = ref(Boolean(props.defaultExpanded))
const detailExpandedMap = reactive({})

watch(
  () => props.defaultExpanded,
  (val) => {
    expanded.value = Boolean(val)
  }
)

const hasRewrittenQuery = computed(() => {
  return typeof props.rewrittenQuery === 'string' && props.rewrittenQuery.trim().length > 0
})

const retrievalModeText = computed(() => {
  return props.retrievalMode ? getRetrievalModeLabel(props.retrievalMode) : ''
})

const sortedToolCalls = computed(() => {
  if (!Array.isArray(props.toolCalls)) return []
  return props.toolCalls
    .map((item, idx) => ({ item, idx }))
    .filter(({ item }) => item && typeof item === 'object')
    .sort((a, b) => {
      const stepA = typeof a.item.step === 'number' ? a.item.step : a.idx + 1
      const stepB = typeof b.item.step === 'number' ? b.item.step : b.idx + 1
      if (stepA !== stepB) return stepA - stepB
      return a.idx - b.idx
    })
    .map(({ item }) => item)
})

const hasRunning = computed(() => {
  return sortedToolCalls.value.some((item) => item.status === 'running')
})

const overallStatusLabel = computed(() => {
  if (hasRunning.value) {
    const meta = TOOL_STATUS_META.find((m) => m.value === 'running')
    return meta ? `${meta.label}...` : '执行中...'
  }
  const meta = TOOL_STATUS_META.find((m) => m.value === 'success')
  return meta ? meta.label : '已完成'
})

const overallStatusClass = computed(() => {
  return hasRunning.value ? 'is-running' : 'is-success'
})

function stepKey(item, idx) {
  if (item.callId) return `${item.callId}-${idx}`
  return `step-${item.step ?? idx}-${item.toolName || 'tool'}`
}

function getToolColorClass(toolName) {
  const color = toolName && Object.prototype.hasOwnProperty.call(AGENT_TOOL_META, toolName)
    ? AGENT_TOOL_META[toolName].color
    : 'info'
  return `is-${color}`
}

function getStepStatusLabel(status) {
  const normalized = status || 'running'
  const matched = TOOL_STATUS_META.find((m) => m.value === normalized)
  return matched ? matched.label : normalized
}

function hasDuration(ms) {
  return typeof ms === 'number' && !Number.isNaN(ms) && ms >= 0
}

function resolveSummary(item) {
  return item?.resultSummary || item?.summary || ''
}

function safeParseObject(raw) {
  if (!raw) return null
  if (typeof raw === 'object' && !Array.isArray(raw)) return raw
  if (typeof raw === 'string') {
    const trimmed = raw.trim()
    if (!trimmed) return null
    try {
      const parsed = JSON.parse(trimmed)
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return parsed
      }
      return { raw: trimmed }
    } catch {
      return { raw: trimmed }
    }
  }
  return null
}

function parseDetail(item) {
  return safeParseObject(item?.detail)
}

function parseArguments(item) {
  return safeParseObject(item?.arguments)
}

function isFormulaDetail(detail) {
  if (!detail) return false
  return Boolean(detail.formula || (Array.isArray(detail.steps) && detail.steps.length > 0))
}

function hasExpandableDetail(item) {
  const detail = parseDetail(item)
  if (isFormulaDetail(detail)) return true
  if (detail && Object.keys(detail).length > 0) return true
  const args = parseArguments(item)
  return Boolean(args && Object.keys(args).length > 0)
}

function isDetailExpanded(item, idx) {
  return Boolean(detailExpandedMap[stepKey(item, idx)])
}

function toggleDetail(item, idx) {
  const key = stepKey(item, idx)
  detailExpandedMap[key] = !detailExpandedMap[key]
}

function formatScalar(val) {
  if (val === null || val === undefined) return '-'
  if (typeof val === 'boolean') return val ? '是' : '否'
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

function buildKvEntries(item) {
  const args = parseArguments(item)
  const detail = parseDetail(item)
  const source = args && Object.keys(args).length > 0 ? args : detail
  if (!source) return []

  const entries = []
  for (const [k, v] of Object.entries(source)) {
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      for (const [subK, subV] of Object.entries(v)) {
        entries.push({ key: `${k}.${subK}`, value: formatScalar(subV) })
      }
    } else {
      entries.push({ key: k, value: formatScalar(v) })
    }
  }
  return entries
}

function buildCalcBadges(detail) {
  if (!detail) return []
  const badges = []
  if (detail.countedMonths !== undefined && detail.countedMonths !== null) {
    badges.push({ label: `折算月数：${detail.countedMonths} 个月`, tone: 'is-accent' })
  }
  if (detail.capped !== undefined && detail.capped !== null) {
    badges.push({
      label: detail.capped ? '触发三倍社平 / 12年封顶' : '未触发封顶',
      tone: detail.capped ? 'is-warning' : 'is-neutral'
    })
  }
  if (detail.exceedsLpr4x !== undefined && detail.exceedsLpr4x !== null) {
    badges.push({
      label: detail.exceedsLpr4x ? '超出 LPR 四倍红线（已截断）' : '未超 LPR 四倍红线',
      tone: detail.exceedsLpr4x ? 'is-warning' : 'is-success'
    })
  }
  if (detail.expired !== undefined && detail.expired !== null) {
    badges.push({
      label: detail.expired ? '已过法定期间' : '在有效期间内',
      tone: detail.expired ? 'is-danger' : 'is-success'
    })
  }
  if (detail.amount !== undefined && detail.amount !== null) {
    const num = Number(detail.amount)
    const formatted = Number.isFinite(num)
      ? num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
      : detail.amount
    badges.push({ label: `精确金额：¥${formatted}`, tone: 'is-success' })
  }
  return badges
}

function hasDraftId(item) {
  return item?.draftId !== null && item?.draftId !== undefined && item?.draftId !== ''
}

function goToDocumentDraft(draftId) {
  router.push({ path: '/lawyer-workspace/document', query: { draftId } })
}
</script>
