<template>
  <section class="contract-report-panel">
    <!-- 详情加载中 -->
    <div v-if="loading && !review" class="contract-state-card">
      <el-icon class="contract-state-spinner is-loading"><Loading /></el-icon>
      <h2>正在加载审查报告…</h2>
    </div>

    <!-- 无数据兜底 -->
    <div v-else-if="!review" class="contract-state-card">
      <el-icon class="contract-state-icon"><DocumentChecked /></el-icon>
      <h2>请选择或发起合同审查</h2>
      <p>从左侧选择一条历史审查记录，或点击「新建审查」上传合同。</p>
      <el-button
        v-hasPermi="['lawyer:contract:add']"
        type="primary"
        @click="emit('create-new')"
      >
        新建合同审查
      </el-button>
    </div>

    <!-- pending 审查中状态 -->
    <div v-else-if="review.status === 'pending'" class="contract-state-card is-pending">
      <div class="contract-pending-orbit" aria-hidden="true">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>
      <h2>AI 正在逐条审阅合同条款、识别潜在法律风险...</h2>
      <p class="contract-pending-subtitle">
        正在以「{{ getStanceLabel(review.stance) }}」深度分析全文条款与违约风险，通常需要 15~60 秒。
      </p>
      <div class="contract-pending-meta">
        <span class="contract-pending-timer">已等待 {{ elapsedSeconds }} 秒</span>
        <span class="contract-stance-chip">{{ displayTitle }}</span>
      </div>
      <p class="contract-pending-hint">
        可随时切换查看其他历史记录，或点击「新建审查」，当前任务将在后台自动完成。
      </p>
    </div>

    <!-- failed 失败状态 -->
    <div v-else-if="review.status === 'failed'" class="contract-state-card is-failed">
      <div class="contract-failed-icon" aria-hidden="true">
        <el-icon><WarningFilled /></el-icon>
      </div>
      <h2>合同风险审查未能完成</h2>
      <div class="contract-failed-reason">
        {{ review.errorMessage || '审查过程中发生异常，请重新发起审查。' }}
      </div>
      <div class="contract-failed-actions">
        <el-button
          v-hasPermi="['lawyer:contract:add']"
          type="primary"
          @click="emit('retry', review)"
        >
          重新发起审查
        </el-button>
        <el-button
          v-if="review.fileId"
          plain
          @click="handleDownload"
        >
          <el-icon><Download /></el-icon>
          <span>下载原件</span>
        </el-button>
      </div>
    </div>

    <!-- success 完整报告与原文对照 -->
    <div v-else class="contract-report-ready">
      <!-- 报告头部概览卡 -->
      <header class="risk-overview-card">
        <div class="risk-overview-top">
          <div class="risk-overview-title-group">
            <div class="risk-overview-badges">
              <span
                class="contract-risk-badge is-large"
                :class="'is-' + (review.riskLevel || 'low')"
              >
                总体{{ getRiskLevelMeta(review.riskLevel).label }}
              </span>
              <span class="contract-stance-chip">{{ getStanceLabel(review.stance) }}</span>
              <span v-if="review.modelName" class="contract-model-chip">{{ review.modelName }}</span>
            </div>
            <h1>{{ displayTitle }}</h1>
            <div class="risk-overview-time">
              <span>{{ review.createTime || '' }}</span>
              <span v-if="review.fileName">· 原件：{{ review.fileName }}</span>
            </div>
          </div>

          <div class="risk-overview-actions">
            <el-button
              v-if="review.fileId"
              plain
              size="small"
              :loading="downloading"
              @click="handleDownload"
            >
              <el-icon><Download /></el-icon>
              <span>下载合同原件</span>
            </el-button>
          </div>
        </div>

        <div class="risk-count-strip">
          <button
            type="button"
            class="risk-count-chip is-high"
            :class="{ 'is-selected': activeLevelFilter === 'high' }"
            @click="toggleLevelFilter('high')"
          >
            <span class="risk-count-dot"></span>
            <span>高风险</span>
            <strong>{{ riskCounts.high }}</strong>
          </button>
          <button
            type="button"
            class="risk-count-chip is-medium"
            :class="{ 'is-selected': activeLevelFilter === 'medium' }"
            @click="toggleLevelFilter('medium')"
          >
            <span class="risk-count-dot"></span>
            <span>中风险</span>
            <strong>{{ riskCounts.medium }}</strong>
          </button>
          <button
            type="button"
            class="risk-count-chip is-low"
            :class="{ 'is-selected': activeLevelFilter === 'low' }"
            @click="toggleLevelFilter('low')"
          >
            <span class="risk-count-dot"></span>
            <span>低风险</span>
            <strong>{{ riskCounts.low }}</strong>
          </button>
        </div>

        <p v-if="review.summary" class="risk-overview-summary">
          {{ review.summary }}
        </p>

        <div class="risk-overview-disclaimer">
          <el-icon><InfoFilled /></el-icon>
          <span>AI 审查结果仅供参考，重要合同请由执业律师复核</span>
        </div>
      </header>

      <!-- 移动端双栏切换 Tab -->
      <div class="contract-mobile-tabs" role="tablist" aria-label="报告视图切换">
        <button
          type="button"
          role="tab"
          class="contract-mobile-tab"
          :class="{ 'is-active': mobilePane === 'risks' }"
          :aria-selected="mobilePane === 'risks'"
          @click="mobilePane = 'risks'"
        >
          风险清单 ({{ risks.length }})
        </button>
        <button
          type="button"
          role="tab"
          class="contract-mobile-tab"
          :class="{ 'is-active': mobilePane === 'source' }"
          :aria-selected="mobilePane === 'source'"
          @click="mobilePane = 'source'"
        >
          合同原文 ({{ contractLength }} 字)
        </button>
      </div>

      <!-- 双栏对照主体 -->
      <div class="contract-compare-grid">
        <!-- 左栏：合同抽取原文面板 -->
        <section
          class="contract-source-pane"
          :class="{ 'is-mobile-hidden': mobilePane !== 'source' }"
          aria-label="合同抽取原文"
        >
          <div class="contract-pane-header">
            <div class="contract-pane-title">
              <h3>合同抽取原文</h3>
              <span class="contract-length-chip">{{ contractLength }} 字</span>
            </div>
            <div class="contract-source-search">
              <el-input
                v-model="textSearch"
                size="small"
                clearable
                placeholder="在原文中搜索关键词..."
                :prefix-icon="Search"
                @input="handleSearchInput"
              />
            </div>
          </div>

          <div ref="sourceBodyRef" class="contract-source-body">
            <template v-if="contractText">
              <pre class="contract-source-pre"><template v-for="(seg, idx) in textSegments" :key="idx"><mark
                v-if="seg.highlight"
                :ref="seg.isPrimary ? setPrimaryMarkRef : undefined"
                class="contract-highlight"
                :class="{ 'is-snippet': seg.kind === 'snippet', 'is-search': seg.kind === 'search', 'is-primary': seg.isPrimary }"
              >{{ seg.text }}</mark><template v-else>{{ seg.text }}</template></template></pre>
            </template>
            <div v-else class="contract-pane-empty">
              暂无可展示的合同原文内容
            </div>
          </div>
        </section>

        <!-- 右栏：结构化风险清单 -->
        <section
          class="contract-risk-pane"
          :class="{ 'is-mobile-hidden': mobilePane !== 'risks' }"
          aria-label="结构化风险清单"
        >
          <div class="contract-pane-header">
            <div class="contract-pane-title">
              <h3>风险条款清单</h3>
              <span class="contract-length-chip">共 {{ risks.length }} 项</span>
            </div>
            <div class="risk-filter-pills">
              <button
                type="button"
                class="risk-filter-pill"
                :class="{ 'is-active': activeLevelFilter === 'all' }"
                @click="activeLevelFilter = 'all'"
              >
                全部 ({{ risks.length }})
              </button>
              <button
                v-for="lvl in RISK_LEVELS"
                :key="lvl.value"
                type="button"
                class="risk-filter-pill"
                :class="['is-' + lvl.value, { 'is-active': activeLevelFilter === lvl.value }]"
                @click="activeLevelFilter = lvl.value"
              >
                {{ lvl.label }} ({{ riskCounts[lvl.value] }})
              </button>
            </div>
          </div>

          <div class="contract-risk-list">
            <div v-if="!filteredRisks.length" class="contract-pane-empty">
              {{ risks.length ? '当前筛选等级下暂无风险项' : '未识别到明显法律风险条款' }}
            </div>

            <article
              v-for="(risk, idx) in filteredRisks"
              :key="risk._index"
              class="risk-item-card"
              :class="['is-' + (risk.riskLevel || 'medium'), { 'is-located': activeLocatedIndex === risk._index }]"
            >
              <header class="risk-item-header">
                <div class="risk-item-title-wrap">
                  <span class="risk-item-index">#{{ risk._index + 1 }}</span>
                  <h4>{{ risk.clauseTitle || `风险条款 ${risk._index + 1}` }}</h4>
                </div>
                <el-tag
                  :type="getRiskLevelMeta(risk.riskLevel).type"
                  size="small"
                  effect="light"
                >
                  {{ getRiskLevelMeta(risk.riskLevel).label }}
                </el-tag>
              </header>

              <!-- 原文摘录 -->
              <div v-if="risk.originalSnippet" class="risk-item-block">
                <div class="risk-item-block-label">
                  <span>原文摘录</span>
                  <button
                    type="button"
                    class="risk-locate-btn"
                    @click="locateSnippet(risk)"
                  >
                    <el-icon><Aim /></el-icon>
                    <span>定位原文</span>
                  </button>
                </div>
                <blockquote
                  class="risk-snippet-quote"
                  role="button"
                  tabindex="0"
                  title="点击在左侧原文中高亮定位"
                  @click="locateSnippet(risk)"
                  @keydown.enter.prevent="locateSnippet(risk)"
                >
                  {{ risk.originalSnippet }}
                </blockquote>
              </div>

              <!-- 风险隐患分析 -->
              <div v-if="risk.riskReason" class="risk-item-block">
                <div class="risk-item-block-label">
                  <span>风险隐患分析</span>
                </div>
                <p class="risk-item-text">{{ risk.riskReason }}</p>
              </div>

              <!-- 专业修改建议 -->
              <div v-if="risk.suggestion" class="risk-item-block">
                <div class="risk-item-block-label">
                  <span>专业修改建议</span>
                  <button
                    type="button"
                    class="risk-copy-btn"
                    @click="copySuggestion(risk.suggestion)"
                  >
                    <el-icon><CopyDocument /></el-icon>
                    <span>复制建议</span>
                  </button>
                </div>
                <p class="risk-item-suggestion">{{ risk.suggestion }}</p>
              </div>

              <!-- 参考法律依据 -->
              <div v-if="risk.legalBasis" class="risk-item-legal">
                <span class="risk-legal-label">参考法律依据：</span>
                <span class="risk-legal-value">{{ risk.legalBasis }}</span>
              </div>
            </article>
          </div>
        </section>
      </div>
    </div>
  </section>
</template>

<script setup name="RiskList">
import { ref, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Aim,
  CopyDocument,
  DocumentChecked,
  Download,
  InfoFilled,
  Loading,
  Search,
  WarningFilled
} from '@element-plus/icons-vue'
import { downloadFile } from '@/api/lawyer/file'
import {
  RISK_LEVELS,
  getStanceLabel,
  getRiskLevelMeta
} from '@/constants/lawyer'

const props = defineProps({
  review: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  },
  elapsedSeconds: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['retry', 'create-new'])

const activeLevelFilter = ref('all')
const mobilePane = ref('risks')
const textSearch = ref('')
const activeSnippet = ref('')
const activeLocatedIndex = ref(null)
const downloading = ref(false)
const sourceBodyRef = ref(null)
let primaryMarkEl = null

function setPrimaryMarkRef(el) {
  if (el) {
    primaryMarkEl = el
  }
}

watch(
  () => props.review?.id,
  () => {
    activeLevelFilter.value = 'all'
    mobilePane.value = 'risks'
    textSearch.value = ''
    activeSnippet.value = ''
    activeLocatedIndex.value = null
    primaryMarkEl = null
  }
)

const displayTitle = computed(() => {
  return props.review?.title || props.review?.fileName || '合同风险审查报告'
})

const contractText = computed(() => props.review?.contractText || '')

const contractLength = computed(() => {
  if (typeof props.review?.contractLength === 'number') {
    return props.review.contractLength
  }
  return contractText.value.length
})

const risks = computed(() => {
  const list = Array.isArray(props.review?.risks) ? props.review.risks : []
  return list.map((item, idx) => ({ ...item, _index: idx }))
})

const riskCounts = computed(() => {
  const counts = { high: 0, medium: 0, low: 0 }
  for (const item of risks.value) {
    if (item.riskLevel === 'high') counts.high += 1
    else if (item.riskLevel === 'medium') counts.medium += 1
    else counts.low += 1
  }
  return counts
})

const filteredRisks = computed(() => {
  if (activeLevelFilter.value === 'all') return risks.value
  return risks.value.filter(item => item.riskLevel === activeLevelFilter.value)
})

function toggleLevelFilter(level) {
  activeLevelFilter.value = activeLevelFilter.value === level ? 'all' : level
}

/**
 * 在合同原文中查找片段区间（优先精确匹配，回退空白符宽松匹配）
 */
function findSnippetRange(source, snippet) {
  if (!source || !snippet) return null
  const trimmed = snippet.trim()
  if (!trimmed) return null

  const exactIdx = source.indexOf(trimmed)
  if (exactIdx !== -1) {
    return { start: exactIdx, end: exactIdx + trimmed.length }
  }

  // 将连续空白替换为 \s+，兼容抽取文本换行差异
  const escaped = trimmed
    .replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    .replace(/\s+/g, '\\s+')
  try {
    const regex = new RegExp(escaped)
    const match = regex.exec(source)
    if (match) {
      return { start: match.index, end: match.index + match[0].length }
    }
  } catch {
    // 忽略极端正则构造异常
  }

  // 再尝试前 24 个字符前缀定位
  if (trimmed.length > 24) {
    const prefix = trimmed.slice(0, 24)
    const prefixIdx = source.indexOf(prefix)
    if (prefixIdx !== -1) {
      return { start: prefixIdx, end: Math.min(source.length, prefixIdx + trimmed.length) }
    }
  }

  return null
}

const textSegments = computed(() => {
  const text = contractText.value
  if (!text) return []

  // 优先高亮定位的原文摘录
  if (activeSnippet.value) {
    const range = findSnippetRange(text, activeSnippet.value)
    if (range) {
      const segs = []
      if (range.start > 0) {
        segs.push({ text: text.slice(0, range.start), highlight: false })
      }
      segs.push({
        text: text.slice(range.start, range.end),
        highlight: true,
        kind: 'snippet',
        isPrimary: true
      })
      if (range.end < text.length) {
        segs.push({ text: text.slice(range.end), highlight: false })
      }
      return segs
    }
  }

  // 其次高亮搜索关键词
  const kw = textSearch.value.trim()
  if (kw) {
    const segs = []
    const lowerText = text.toLowerCase()
    const lowerKw = kw.toLowerCase()
    let cursor = 0
    let firstHit = true
    while (cursor < text.length) {
      const hitIdx = lowerText.indexOf(lowerKw, cursor)
      if (hitIdx === -1) {
        segs.push({ text: text.slice(cursor), highlight: false })
        break
      }
      if (hitIdx > cursor) {
        segs.push({ text: text.slice(cursor, hitIdx), highlight: false })
      }
      segs.push({
        text: text.slice(hitIdx, hitIdx + kw.length),
        highlight: true,
        kind: 'search',
        isPrimary: firstHit
      })
      firstHit = false
      cursor = hitIdx + kw.length
    }
    return segs
  }

  return [{ text, highlight: false }]
})

function scrollToPrimaryMark() {
  nextTick(() => {
    if (primaryMarkEl && typeof primaryMarkEl.scrollIntoView === 'function') {
      primaryMarkEl.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  })
}

function handleSearchInput() {
  if (textSearch.value.trim()) {
    activeSnippet.value = ''
    activeLocatedIndex.value = null
    primaryMarkEl = null
    scrollToPrimaryMark()
  }
}

function locateSnippet(risk) {
  if (!risk?.originalSnippet) return
  if (!contractText.value) {
    ElMessage.info('当前审查暂无合同原文可定位')
    return
  }
  const range = findSnippetRange(contractText.value, risk.originalSnippet)
  if (!range) {
    ElMessage.info('未在原文中精确匹配到该摘录片段')
    return
  }
  primaryMarkEl = null
  activeSnippet.value = risk.originalSnippet
  activeLocatedIndex.value = risk._index
  mobilePane.value = 'source'
  scrollToPrimaryMark()
}

async function copySuggestion(text) {
  if (!text) return
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    ElMessage.success('已复制修改建议')
  } catch {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}

async function handleDownload() {
  if (!props.review?.fileId) return
  downloading.value = true
  try {
    await downloadFile(props.review.fileId, props.review.fileName || 'contract')
  } finally {
    downloading.value = false
  }
}
</script>
