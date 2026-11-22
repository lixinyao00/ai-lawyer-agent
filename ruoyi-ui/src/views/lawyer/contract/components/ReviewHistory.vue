<template>
  <div class="contract-history">
    <div class="contract-history-head">
      <div class="contract-history-title-row">
        <h2>审查记录</h2>
        <span class="contract-history-total">{{ total }} 条</span>
      </div>
      <el-input
        v-model="keyword"
        size="small"
        clearable
        placeholder="搜索合同标题..."
        :prefix-icon="Search"
        @input="handleKeywordInput"
        @clear="emitFilters"
        @keyup.enter="emitFilters"
      />
      <div class="contract-history-filters">
        <el-select
          v-model="localRiskLevel"
          size="small"
          clearable
          placeholder="风险等级"
          @change="emitFilters"
        >
          <el-option
            v-for="item in RISK_LEVELS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select
          v-model="localStatus"
          size="small"
          clearable
          placeholder="状态"
          @change="emitFilters"
        >
          <el-option
            v-for="item in REVIEW_STATUS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
    </div>

    <div class="contract-history-list" :aria-busy="loading">
      <div v-if="loading && !reviews.length" class="contract-history-empty">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>正在加载审查记录…</span>
      </div>

      <div v-else-if="!reviews.length" class="contract-history-empty">
        <p>暂无合同审查记录</p>
        <el-button
          v-if="canCreate"
          type="primary"
          size="small"
          plain
          @click="emit('create-new')"
        >
          发起首次审查
        </el-button>
      </div>

      <template v-else>
        <div
          v-for="item in reviews"
          :key="item.id"
          class="contract-history-item"
          :class="{ 'is-active': item.id === activeId }"
          role="button"
          tabindex="0"
          :aria-current="item.id === activeId ? 'true' : undefined"
          @click="emit('select', item.id)"
          @keydown.enter.prevent="emit('select', item.id)"
          @keydown.space.prevent="emit('select', item.id)"
        >
          <div class="contract-history-item-top">
            <span class="contract-history-item-title" :title="displayTitle(item)">
              {{ displayTitle(item) }}
            </span>
            <button
              v-hasPermi="['lawyer:contract:remove']"
              type="button"
              class="contract-history-delete"
              :disabled="deletingId === item.id"
              :aria-label="'删除审查 ' + displayTitle(item)"
              title="删除记录"
              @click.stop="emit('delete', item)"
            >
              <el-icon><Delete /></el-icon>
            </button>
          </div>

          <div class="contract-history-item-meta">
            <span class="contract-stance-chip">{{ getStanceLabel(item.stance) }}</span>

            <span v-if="item.status === 'pending'" class="contract-status-badge is-pending">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>审查中</span>
            </span>
            <span v-else-if="item.status === 'failed'" class="contract-status-badge is-failed">
              失败
            </span>
            <span
              v-else
              class="contract-risk-badge"
              :class="'is-' + (item.riskLevel || 'low')"
            >
              {{ getRiskLevelMeta(item.riskLevel).label }}
            </span>

            <span v-if="item.status === 'success'" class="contract-history-risk-count">
              {{ item.riskCount ?? 0 }} 项风险
            </span>
          </div>

          <div class="contract-history-item-time">
            {{ item.createTime || '' }}
          </div>
        </div>

        <div v-if="reviews.length < total" class="contract-history-more">
          <el-button
            size="small"
            text
            :loading="loadingMore"
            @click="emit('load-more')"
          >
            {{ loadingMore ? '正在加载…' : '加载更多记录' }}
          </el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup name="ReviewHistory">
import { ref, onBeforeUnmount } from 'vue'
import { Delete, Loading, Search } from '@element-plus/icons-vue'
import {
  RISK_LEVELS,
  REVIEW_STATUS,
  getStanceLabel,
  getRiskLevelMeta
} from '@/constants/lawyer'

const props = defineProps({
  reviews: {
    type: Array,
    default: () => []
  },
  total: {
    type: Number,
    default: 0
  },
  loading: {
    type: Boolean,
    default: false
  },
  loadingMore: {
    type: Boolean,
    default: false
  },
  activeId: {
    type: [Number, String],
    default: null
  },
  deletingId: {
    type: [Number, String],
    default: null
  },
  canCreate: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['select', 'delete', 'filter-change', 'load-more', 'create-new'])

const keyword = ref('')
const localRiskLevel = ref('')
const localStatus = ref('')

let searchTimer = null

function displayTitle(item) {
  return item.title || item.fileName || '未命名合同审查'
}

function emitFilters() {
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
  emit('filter-change', {
    title: keyword.value.trim(),
    riskLevel: localRiskLevel.value || '',
    status: localStatus.value || ''
  })
}

function handleKeywordInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    emitFilters()
  }, 300)
}

onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
})
</script>
