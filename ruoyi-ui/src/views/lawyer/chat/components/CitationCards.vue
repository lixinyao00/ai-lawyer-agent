<template>
  <section v-if="validCitations.length > 0" class="citation-box" aria-label="参考法条依据">
    <button
      type="button"
      class="citation-toggle"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      <span class="citation-toggle-left">
        <el-icon class="citation-toggle-arrow" :class="{ 'is-expanded': expanded }">
          <ArrowRight />
        </el-icon>
        <el-icon class="citation-toggle-icon"><Reading /></el-icon>
        <span class="citation-toggle-title">参考法条依据（{{ validCitations.length }}）</span>
      </span>
      <span class="citation-verify-tag">依据由知识库检索，请以官方文本核验</span>
    </button>

    <div v-show="expanded" class="citation-list">
      <article
        v-for="(item, idx) in validCitations"
        :key="cardKey(item, idx)"
        class="citation-card"
      >
        <header class="citation-card-header">
          <div class="citation-card-title-wrap">
            <span class="citation-card-index">[{{ idx + 1 }}]</span>
            <h4 class="citation-card-title">
              《{{ item.docTitle || '法律法规' }}》{{ item.articleNo || '条文片段' }}
            </h4>
          </div>
          <div class="citation-card-actions">
            <span
              v-if="getModeMeta(item)"
              class="citation-mode-tag"
              :class="getModeMeta(item).className"
              :title="buildCitationMetaTooltip(item)"
            >
              {{ getModeMeta(item).label }}
            </span>
            <span
              v-if="hasRank(item.bm25Rank)"
              class="citation-rank-tag is-bm25"
              :title="hasScore(item.bm25Score) ? `BM25 得分 ${formatScore(item.bm25Score, 2)}` : undefined"
            >
              BM25 #{{ item.bm25Rank }}
            </span>
            <span
              v-if="hasRank(item.vectorRank)"
              class="citation-rank-tag is-vector"
              :title="hasScore(item.vectorScore) ? `向量余弦相似度 ${formatScore(item.vectorScore, 4)}` : undefined"
            >
              Vector #{{ item.vectorRank }}
            </span>
            <span
              v-if="resolveDisplayScore(item)"
              class="citation-score-tag"
              :title="buildCitationMetaTooltip(item)"
            >
              {{ resolveDisplayScore(item) }}
            </span>
            <button
              type="button"
              class="citation-copy-btn"
              title="复制法条内容"
              @click="copyCitation(item)"
            >
              <el-icon><CopyDocument /></el-icon>
              <span>复制</span>
            </button>
          </div>
        </header>

        <div v-if="item.snippet" class="citation-card-body">
          <p
            class="citation-snippet"
            :class="{ 'is-collapsed': isLongSnippet(item.snippet) && !isSnippetExpanded(item, idx) }"
          >
            {{ item.snippet }}
          </p>
          <button
            v-if="isLongSnippet(item.snippet)"
            type="button"
            class="citation-expand-btn"
            @click="toggleSnippet(item, idx)"
          >
            {{ isSnippetExpanded(item, idx) ? '收起条文' : '展开全文' }}
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup name="CitationCards">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRight, CopyDocument, Reading } from '@element-plus/icons-vue'

const props = defineProps({
  citations: {
    type: Array,
    default: () => []
  }
})

const expanded = ref(false)
const expandedSnippets = reactive({})

const validCitations = computed(() => {
  if (!Array.isArray(props.citations)) return []
  return props.citations.filter(item => item && (item.docTitle || item.articleNo || item.snippet))
})

function cardKey(item, idx) {
  if (item.chunkId !== undefined && item.chunkId !== null) {
    return `chunk-${item.chunkId}`
  }
  return `citation-${item.docId || 'doc'}-${idx}`
}

const CITATION_MODE_META = {
  hybrid_rrf: { label: 'RRF 混合', className: 'is-hybrid' },
  bm25: { label: 'BM25', className: 'is-bm25' },
  vector: { label: 'Vector 语义', className: 'is-vector' }
}

function hasScore(score) {
  return typeof score === 'number' && !Number.isNaN(score)
}

function hasRank(rank) {
  return typeof rank === 'number' && !Number.isNaN(rank) && rank > 0
}

function formatScore(score, digits = 2) {
  return Number(score).toFixed(digits)
}

function getEffectiveMode(item) {
  if (item?.retrievalMode) return item.retrievalMode
  if (hasScore(item?.rrfScore)) return 'hybrid_rrf'
  if (hasRank(item?.bm25Rank) && hasRank(item?.vectorRank)) return 'hybrid_rrf'
  if (hasRank(item?.vectorRank) || hasScore(item?.vectorScore)) return 'vector'
  if (hasRank(item?.bm25Rank) || hasScore(item?.bm25Score)) return 'bm25'
  return ''
}

function getModeMeta(item) {
  const mode = getEffectiveMode(item)
  if (!mode) return null
  return CITATION_MODE_META[mode] || { label: mode, className: 'is-hybrid' }
}

function resolveDisplayScore(item) {
  const mode = getEffectiveMode(item)
  if (mode === 'hybrid_rrf') {
    const val = hasScore(item.rrfScore) ? item.rrfScore : item.score
    return hasScore(val) ? `RRF ${formatScore(val, 4)}` : ''
  }
  if (mode === 'vector') {
    const val = hasScore(item.vectorScore) ? item.vectorScore : item.score
    return hasScore(val) ? `相似度 ${formatScore(val, 4)}` : ''
  }
  const val = hasScore(item.bm25Score) ? item.bm25Score : item.score
  return hasScore(val) ? `相关度 ${formatScore(val, 2)}` : ''
}

function buildCitationMetaTooltip(item) {
  const parts = []
  if (hasRank(item.bm25Rank) || hasScore(item.bm25Score)) {
    const rankText = hasRank(item.bm25Rank) ? `#${item.bm25Rank}` : '-'
    const scoreText = hasScore(item.bm25Score) ? formatScore(item.bm25Score, 2) : '-'
    parts.push(`BM25: ${rankText} (${scoreText})`)
  }
  if (hasRank(item.vectorRank) || hasScore(item.vectorScore)) {
    const rankText = hasRank(item.vectorRank) ? `#${item.vectorRank}` : '-'
    const scoreText = hasScore(item.vectorScore) ? formatScore(item.vectorScore, 4) : '-'
    parts.push(`Vector: ${rankText} (${scoreText})`)
  }
  if (hasScore(item.rrfScore)) {
    parts.push(`RRF: ${formatScore(item.rrfScore, 4)}`)
  }
  return parts.length > 0 ? parts.join(' | ') : undefined
}

function isLongSnippet(snippet) {
  return typeof snippet === 'string' && snippet.length > 130
}

function isSnippetExpanded(item, idx) {
  return Boolean(expandedSnippets[cardKey(item, idx)])
}

function toggleSnippet(item, idx) {
  const key = cardKey(item, idx)
  expandedSnippets[key] = !expandedSnippets[key]
}

async function copyCitation(item) {
  const header = `《${item.docTitle || '法律法规'}》${item.articleNo || '条文片段'}`
  const text = item.snippet ? `${header}\n${item.snippet}` : header
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
    ElMessage.success('已复制法条内容')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本复制')
  }
}
</script>
