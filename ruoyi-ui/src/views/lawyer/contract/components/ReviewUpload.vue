<template>
  <section class="contract-upload-panel">
    <div class="contract-upload-card">
      <header class="contract-upload-header">
        <div class="contract-upload-icon">
          <el-icon><DocumentChecked /></el-icon>
        </div>
        <div>
          <h1>智能合同风险审查</h1>
          <p>上传合同文件或粘贴合同全文，AI 将结合您选择的审查立场逐条排查潜在法律风险并给出修改建议。</p>
        </div>
      </header>

      <div class="contract-mode-tabs" role="tablist" aria-label="输入方式">
        <button
          type="button"
          role="tab"
          class="contract-mode-tab"
          :class="{ 'is-active': inputMode === 'file' }"
          :aria-selected="inputMode === 'file'"
          @click="inputMode = 'file'"
        >
          <el-icon><UploadFilled /></el-icon>
          <span>文件上传审查</span>
        </button>
        <button
          type="button"
          role="tab"
          class="contract-mode-tab"
          :class="{ 'is-active': inputMode === 'text' }"
          :aria-selected="inputMode === 'text'"
          @click="inputMode = 'text'"
        >
          <el-icon><Document /></el-icon>
          <span>粘贴文本审查</span>
        </button>
      </div>

      <!-- 文件上传模式 -->
      <div v-show="inputMode === 'file'" class="contract-upload-section">
        <input
          ref="fileInputRef"
          type="file"
          class="contract-file-hidden-input"
          :accept="acceptAttr"
          @change="handleFileInputChange"
        />

        <div
          v-if="!selectedFile"
          class="contract-dropzone"
          :class="{ 'is-dragging': isDragging }"
          role="button"
          tabindex="0"
          aria-label="点击或拖拽上传合同文件"
          @click="triggerFileSelect"
          @keydown.enter.prevent="triggerFileSelect"
          @keydown.space.prevent="triggerFileSelect"
          @dragover.prevent="isDragging = true"
          @dragleave.prevent="isDragging = false"
          @drop.prevent="handleDrop"
        >
          <div class="contract-dropzone-icon">
            <el-icon><UploadFilled /></el-icon>
          </div>
          <p class="contract-dropzone-title">将合同文件拖到此处，或 <span>点击选择文件</span></p>
          <p class="contract-dropzone-hint">
            支持 {{ formattedAllowedExts }} 格式，单文件最大 {{ MAX_FILE_MB }}MB
          </p>
        </div>

        <div v-else class="contract-file-preview">
          <div class="contract-file-badge">{{ fileExtBadge }}</div>
          <div class="contract-file-info">
            <div class="contract-file-name" :title="selectedFile.name">{{ selectedFile.name }}</div>
            <div class="contract-file-size">{{ formatFileSize(selectedFile.size) }}</div>
          </div>
          <div class="contract-file-actions">
            <el-button size="small" plain @click="triggerFileSelect">更换文件</el-button>
            <el-button size="small" type="danger" plain @click="clearSelectedFile">移除</el-button>
          </div>
        </div>
      </div>

      <!-- 粘贴文本模式 -->
      <div v-show="inputMode === 'text'" class="contract-upload-section">
        <div class="contract-text-toolbar">
          <span class="contract-field-label">合同全文内容</span>
          <div class="contract-text-toolbar-actions">
            <el-button size="small" plain @click="fillSampleContract">
              填入示例合同
            </el-button>
            <el-button
              v-if="contractText"
              size="small"
              text
              @click="contractText = ''"
            >
              清空
            </el-button>
          </div>
        </div>
        <el-input
          v-model="contractText"
          type="textarea"
          :rows="10"
          maxlength="50000"
          show-word-limit
          resize="vertical"
          placeholder="请粘贴需要审查的合同全文内容（最多 50,000 字）..."
        />
      </div>

      <!-- 审查立场选择 -->
      <div class="contract-upload-section">
        <span class="contract-field-label">审查立场</span>
        <div class="contract-stance-grid" role="radiogroup" aria-label="审查立场">
          <label
            v-for="item in STANCE_OPTIONS"
            :key="item.value"
            class="contract-stance-card"
            :class="{ 'is-selected': stance === item.value }"
          >
            <input
              v-model="stance"
              type="radio"
              name="contract-stance"
              :value="item.value"
              class="contract-stance-radio"
            />
            <div class="contract-stance-body">
              <strong>{{ item.label }}</strong>
              <span>{{ stanceDescription(item.value) }}</span>
            </div>
          </label>
        </div>
      </div>

      <!-- 审查标题 -->
      <div class="contract-upload-section">
        <label class="contract-field-label" for="contract-review-title">
          审查标题 <span class="contract-field-optional">（可选）</span>
        </label>
        <el-input
          id="contract-review-title"
          v-model="title"
          maxlength="100"
          show-word-limit
          clearable
          placeholder="留空将自动使用文件名或默认标题"
        />
      </div>

      <footer class="contract-upload-footer">
        <span class="contract-upload-tip">
          AI 审查结果仅供参考，重要合同建议由执业律师复核。
        </span>
        <el-button
          v-hasPermi="['lawyer:contract:add']"
          type="primary"
          size="large"
          :loading="submitting"
          :disabled="!canSubmit"
          @click="handleSubmit"
        >
          {{ submitting ? '正在提交审查…' : '开始智能审查' }}
        </el-button>
      </footer>
    </div>
  </section>
</template>

<script setup name="ReviewUpload">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, DocumentChecked, UploadFilled } from '@element-plus/icons-vue'
import {
  STANCE_OPTIONS,
  ALLOWED_CONTRACT_EXT,
  MAX_FILE_MB
} from '@/constants/lawyer'

const SAMPLE_CONTRACT = `劳动合同（示例）

甲方（用人单位）：某某科技有限公司
乙方（劳动者）：张某

第一条 合同期限与试用期
本合同期限为三年，自2025年3月1日起至2028年2月28日止。其中试用期为六个月；若甲方认为乙方表现未达预期，甲方有权单方决定延长试用期至十二个月，无需另行协商。

第二条 工作岗位与地点
乙方担任高级研发工程师，工作地点为北京。甲方可根据经营需要随时将乙方调岗至全国任意城市或调整工作岗位及薪资标准，乙方应当无条件服从，拒绝服从视为严重违纪，甲方可立即解除劳动合同且无需支付任何经济补偿。

第三条 劳动报酬
乙方转正后月薪为人民币18,000元，试用期工资为转正工资的60%。甲方有权根据公司效益单方扣发或延迟发放工资及绩效奖金，延迟不超过九十日的，不视为拖欠工资。

第四条 保密与竞业限制
乙方离职后三年内不得在同行业任何企业任职或自营相关业务。甲方无需在离职后向乙方支付任何竞业限制经济补偿；若乙方违反本条约定，应向甲方支付违约金人民币五十万元。

第五条 违约与解除
乙方在合同期内提前离职的，除赔偿甲方损失外，还需额外支付相当于三个月工资的违约金。本合同未尽事宜，均由甲方单方解释。`

const props = defineProps({
  submitting: {
    type: Boolean,
    default: false
  },
  validateFile: {
    type: Function,
    required: true
  },
  initialDraft: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['submit'])

const inputMode = ref('file')
const selectedFile = ref(null)
const contractText = ref('')
const stance = ref(STANCE_OPTIONS[0]?.value || 'party_a')
const title = ref('')
const isDragging = ref(false)
const fileInputRef = ref(null)

const acceptAttr = computed(() => ALLOWED_CONTRACT_EXT.map(ext => `.${ext}`).join(','))
const formattedAllowedExts = computed(() => ALLOWED_CONTRACT_EXT.map(ext => `.${ext}`).join('、'))

const fileExtBadge = computed(() => {
  if (!selectedFile.value?.name) return 'FILE'
  const parts = selectedFile.value.name.split('.')
  return parts.length > 1 ? parts.pop().toUpperCase() : 'FILE'
})

const canSubmit = computed(() => {
  if (props.submitting) return false
  if (inputMode.value === 'file') {
    return !!selectedFile.value
  }
  return contractText.value.trim().length > 0
})

watch(
  () => props.initialDraft,
  (draft) => {
    if (!draft) return
    if (draft.stance) stance.value = draft.stance
    if (draft.title !== undefined) title.value = draft.title || ''
    if (draft.contractText) {
      inputMode.value = 'text'
      contractText.value = draft.contractText
      selectedFile.value = null
    }
  },
  { immediate: true }
)

function stanceDescription(value) {
  if (value === 'party_a') return '侧重维护甲方 / 发起方权益，严查对我方不利条款'
  if (value === 'party_b') return '侧重维护乙方 / 签署方权益，识别不对等风险与义务陷阱'
  return '客观中立评估双方权利义务平衡与合规性'
}

function triggerFileSelect() {
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
    fileInputRef.value.click()
  }
}

function acceptFile(file) {
  if (!file) return
  const errMsg = props.validateFile(file)
  if (errMsg) {
    ElMessage.warning(errMsg)
    return
  }
  selectedFile.value = file
  if (!title.value.trim()) {
    const baseName = file.name.replace(/\.[^.]+$/, '')
    title.value = baseName.slice(0, 100)
  }
}

function handleFileInputChange(event) {
  const file = event.target?.files?.[0]
  acceptFile(file)
}

function handleDrop(event) {
  isDragging.value = false
  const file = event.dataTransfer?.files?.[0]
  acceptFile(file)
}

function clearSelectedFile() {
  selectedFile.value = null
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

function formatFileSize(bytes) {
  if (!bytes && bytes !== 0) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

function fillSampleContract() {
  contractText.value = SAMPLE_CONTRACT
  if (!title.value.trim()) {
    title.value = '劳动合同风险审查示例'
  }
}

function handleSubmit() {
  if (inputMode.value === 'file') {
    if (!selectedFile.value) {
      ElMessage.warning('请先选择需要审查的合同文件')
      return
    }
    const errMsg = props.validateFile(selectedFile.value)
    if (errMsg) {
      ElMessage.warning(errMsg)
      return
    }
    emit('submit', {
      file: selectedFile.value,
      stance: stance.value,
      title: title.value.trim() || undefined
    })
    return
  }

  const trimmedText = contractText.value.trim()
  if (!trimmedText) {
    ElMessage.warning('请先粘贴需要审查的合同文本')
    return
  }
  emit('submit', {
    contractText: trimmedText,
    stance: stance.value,
    title: title.value.trim() || undefined
  })
}
</script>
