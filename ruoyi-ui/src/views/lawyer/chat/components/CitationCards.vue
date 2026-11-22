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
            <span v-if="hasScore(item.score)" class="citation-score-tag">
              相关度 {{ formatScore(item.score) }}
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

function hasScore(score) {
  return typeof score === 'number' && !Number.isNaN(score)
}

function formatScore(score) {
  return Number(score).toFixed(2)
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
