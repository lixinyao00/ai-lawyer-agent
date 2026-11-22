<template>
  <section class="doc-editor-pane" aria-label="文书在线编辑与公文预览">
    <!-- 顶部工具栏 -->
    <div class="doc-editor-toolbar">
      <div class="doc-editor-title-wrap">
        <el-input
          v-model="editTitle"
          :disabled="!draft || generating"
          maxlength="200"
          placeholder="文书标题（生成或加载草稿后可编辑）"
          class="doc-editor-title-input"
        />
        <span v-if="isDirty" class="doc-dirty-badge">未保存修改</span>
      </div>

      <div class="doc-editor-actions">
        <div class="doc-view-switch" role="tablist" aria-label="视图模式">
          <button
            type="button"
            role="tab"
            :aria-selected="viewMode === 'preview'"
            class="doc-view-tab"
            :class="{ 'is-active': viewMode === 'preview' }"
            @click="viewMode = 'preview'"
          >
            A4 公文预览
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="viewMode === 'edit'"
            class="doc-view-tab"
            :class="{ 'is-active': viewMode === 'edit' }"
            @click="viewMode = 'edit'"
          >
            在线编辑
          </button>
        </div>

        <el-button
          size="small"
          :disabled="!editContent.trim() || generating"
          @click="handleCopy"
        >
          <el-icon><CopyDocument /></el-icon>
          <span>复制全文</span>
        </el-button>

        <el-button
          v-hasPermi="['lawyer:document:edit']"
          size="small"
          :loading="saving"
          :disabled="!draft?.id || !isDirty || generating"
          @click="handleSave"
        >
          <el-icon v-if="!saving"><Check /></el-icon>
          <span>保存修改</span>
        </el-button>

        <el-button
          v-hasPermi="['lawyer:document:export']"
          type="primary"
          size="small"
          :loading="exporting"
          :disabled="!canExport || generating"
          @click="handleExport"
        >
          <el-icon v-if="!exporting"><Download /></el-icon>
          <span>导出 Word (.docx)</span>
        </el-button>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="doc-editor-body">
      <div v-if="generating" class="doc-editor-state">
        <div class="doc-state-orbit">
          <el-icon class="is-loading"><Loading /></el-icon>
        </div>
        <h3>AI 正在起草法律文书</h3>
        <p>正在根据您填写的案件要素组织规范法律文本，请稍候片刻...</p>
      </div>

      <div v-else-if="!draft" class="doc-editor-state">
        <div class="doc-state-orbit">
          <el-icon><Document /></el-icon>
        </div>
        <h3>等待生成文书草稿</h3>
        <p>
          请在左侧填写案件要素（或点击「填入示例数据」快速体验），然后点击「AI 生成文书草稿」；也可从左侧历史草稿列表加载已有文书。
        </p>
      </div>

      <div v-else-if="draft.status === 'failed'" class="doc-editor-state is-failed">
        <div class="doc-state-orbit is-failed">
          <el-icon><WarningFilled /></el-icon>
        </div>
        <h3>文书生成失败</h3>
        <p class="doc-error-detail">{{ draft.errorMessage || '服务暂时不可用，请稍后重试' }}</p>
        <p class="doc-error-guide">
          您可以核对并调整左侧表单要素后，再次点击「AI 生成文书草稿」重新生成。
        </p>
      </div>

      <template v-else>
        <!-- 在线编辑模式 -->
        <div v-if="viewMode === 'edit'" class="doc-edit-canvas">
          <div class="doc-edit-meta-bar">
            <span v-if="draft.templateName" class="doc-template-chip">{{ draft.templateName }}</span>
            <span v-if="draft.modelName" class="doc-model-chip">{{ draft.modelName }}</span>
            <span class="doc-word-count">{{ editContent.length }} / 50000 字</span>
          </div>
          <el-input
            v-model="editContent"
            type="textarea"
            :autosize="{ minRows: 20 }"
            maxlength="50000"
            placeholder="请输入或修改法律文书正文..."
            class="doc-raw-textarea"
          />
        </div>

        <!-- A4 公文排版预览模式 -->
        <div v-else class="doc-preview-canvas">
          <div class="doc-edit-meta-bar">
            <span v-if="draft.templateName" class="doc-template-chip">{{ draft.templateName }}</span>
            <span v-if="draft.modelName" class="doc-model-chip">{{ draft.modelName }}</span>
            <span class="doc-word-count">约 {{ editContent.length }} 字 · A4 仿真公文排版</span>
          </div>
          <article class="doc-a4-paper" aria-label="A4 公文预览">
            <h1 class="doc-a4-title">{{ previewTitle }}</h1>
            <div class="doc-a4-content">
              <p
                v-for="(line, idx) in parsedPreviewLines"
                :key="idx"
                class="doc-a4-line"
                :class="line.cls"
              >
                {{ line.text }}
              </p>
            </div>
          </article>
        </div>
      </template>
    </div>

    <!-- 底部固定免责提示 -->
    <footer class="doc-editor-footer">
      <el-icon><InfoFilled /></el-icon>
      <span>AI 起草文书仅供格式与思路参考，正式签署或提交前请仔细核对事实要素</span>
    </footer>
  </section>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Check,
  CopyDocument,
  Document,
  Download,
  InfoFilled,
  Loading,
  WarningFilled
} from '@element-plus/icons-vue'

const props = defineProps({
  draft: {
    type: Object,
    default: null
  },
  saving: {
    type: Boolean,
    default: false
  },
  exporting: {
    type: Boolean,
    default: false
  },
  generating: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['save', 'export'])

const viewMode = ref('preview')
const editTitle = ref('')
const editContent = ref('')

watch(
  () => props.draft,
  (next) => {
    editTitle.value = next?.title || ''
    editContent.value = next?.content || ''
    if (next?.status === 'success' && !next?.content) {
      viewMode.value = 'edit'
    }
  },
  { immediate: true, deep: true }
)

const isDirty = computed(() => {
  if (!props.draft) return false
  return (
    editTitle.value !== (props.draft.title || '') ||
    editContent.value !== (props.draft.content || '')
  )
})

const canExport = computed(() => {
  return Boolean(
    props.draft?.id &&
    props.draft.status === 'success' &&
    (editContent.value || '').trim().length > 0
  )
})

const previewTitle = computed(() => {
  const raw = (editTitle.value || props.draft?.templateName || '法律文书').trim()
  const dashIdx = raw.indexOf('-')
  return dashIdx > 0 ? raw.slice(0, dashIdx).trim() : raw
})

const SIGNATURE_RE = /^(申请人|具状人|出借人|借款人|出租人|承租人|委托人|律师|.{0,40}律师事务所)[：:].*/
const DATE_RE = /^\s*[0-9〇零一二三四五六七八九十]{4}年.{1,3}月.{1,3}日\s*$/

const parsedPreviewLines = computed(() => {
  const rawLines = (editContent.value || '').split(/\r?\n/)
  const nonEmptyIndices = []
  rawLines.forEach((line, index) => {
    if (line.trim().length > 0) {
      nonEmptyIndices.push(index)
    }
  })
  const tailStartIndex = nonEmptyIndices.length > 8
    ? nonEmptyIndices[nonEmptyIndices.length - 8]
    : 0

  let afterSalutation = false
  return rawLines.map((line, index) => {
    const trimmed = line.trim()
    if (!trimmed) {
      afterSalutation = false
      return { text: '', cls: 'is-blank' }
    }
    if (trimmed.startsWith('此致')) {
      afterSalutation = true
      return { text: trimmed, cls: 'is-no-indent' }
    }
    if (afterSalutation) {
      afterSalutation = false
      return { text: trimmed, cls: 'is-no-indent' }
    }
    if (index >= tailStartIndex && (SIGNATURE_RE.test(trimmed) || DATE_RE.test(trimmed))) {
      return { text: trimmed, cls: 'is-right' }
    }
    return { text: trimmed, cls: 'is-indent' }
  })
})

async function handleCopy() {
  const text = editContent.value || ''
  if (!text.trim()) return
  try {
    if (navigator.clipboard && window.isSecureContext) {
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
    ElMessage.success('已复制文书全文')
  } catch {
    ElMessage.error('复制失败，请手动选中文本复制')
  }
}

function handleSave() {
  if (!props.draft?.id) return
  const title = (editTitle.value || '').trim()
  if (!title) {
    ElMessage.warning('文书标题不能为空')
    return
  }
  emit('save', {
    id: props.draft.id,
    title,
    content: editContent.value
  })
}

function handleExport() {
  if (!canExport.value) return
  if (isDirty.value) {
    ElMessage.info('检测到未保存修改，将导出当前已保存的文书版本')
  }
  emit('export', props.draft)
}
</script>
