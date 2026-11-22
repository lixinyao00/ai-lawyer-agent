<template>
  <div class="doc-history">
    <div class="doc-history-head">
      <div class="doc-history-title-row">
        <h2>历史文书草稿</h2>
        <span class="doc-history-total">{{ total }} 份</span>
      </div>

      <el-input
        v-model="keyword"
        size="small"
        clearable
        placeholder="搜索文书标题..."
        :prefix-icon="Search"
        @input="handleKeywordInput"
        @clear="emitFilterChange"
      />

      <el-select
        v-model="templateCode"
        size="small"
        clearable
        placeholder="全部文书模板"
        style="width: 100%"
        @change="emitFilterChange"
      >
        <el-option
          v-for="tpl in templates"
          :key="tpl.code"
          :label="tpl.name"
          :value="tpl.code"
        />
      </el-select>
    </div>

    <div v-loading="loading && !drafts.length" class="doc-history-list">
      <div v-if="!loading && !drafts.length" class="doc-history-empty">
        <el-icon size="28"><Document /></el-icon>
        <p>暂无文书草稿记录</p>
        <el-button v-if="canCreate" type="primary" size="small" plain @click="emit('create-new')">
          立即起草文书
        </el-button>
      </div>

      <article
        v-for="item in drafts"
        :key="item.id"
        class="doc-history-item"
        :class="{ 'is-active': item.id === activeId }"
        tabindex="0"
        @click="emit('select', item.id)"
        @keydown.enter.prevent="emit('select', item.id)"
      >
        <div class="doc-history-item-top">
          <div class="doc-history-item-title" :title="item.title">
            {{ item.title || '未命名文书' }}
          </div>
          <button
            v-hasPermi="['lawyer:document:remove']"
            type="button"
            class="doc-history-delete"
            :disabled="deletingId === item.id"
            aria-label="删除文书草稿"
            title="删除文书草稿"
            @click.stop="emit('delete', item)"
          >
            <el-icon><Delete /></el-icon>
          </button>
        </div>

        <div class="doc-history-item-meta">
          <span class="doc-template-chip">{{ item.templateName || item.templateCode }}</span>
          <span
            class="doc-status-badge"
            :class="item.status === 'success' ? 'is-success' : 'is-failed'"
          >
            {{ item.status === 'success' ? '已生成' : '生成失败' }}
          </span>
        </div>

        <div class="doc-history-item-time">
          更新于 {{ formatTime(item.updateTime || item.createTime) }}
        </div>
      </article>

      <div v-if="drafts.length < total" class="doc-history-more">
        <el-button text size="small" :loading="loadingMore" @click="emit('load-more')">
          加载更多（{{ drafts.length }} / {{ total }}）
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { Delete, Document, Search } from '@element-plus/icons-vue'

defineProps({
  drafts: {
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
    type: Number,
    default: null
  },
  deletingId: {
    type: Number,
    default: null
  },
  templates: {
    type: Array,
    default: () => []
  },
  canCreate: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['select', 'delete', 'filter-change', 'load-more', 'create-new'])

const keyword = ref('')
const templateCode = ref('')
let searchTimer = null

function emitFilterChange() {
  emit('filter-change', {
    title: keyword.value.trim(),
    templateCode: templateCode.value || ''
  })
}

function handleKeywordInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    emitFilterChange()
  }, 300)
}

function formatTime(raw) {
  if (!raw) return '-'
  return String(raw).replace('T', ' ').slice(0, 16)
}

onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
})
</script>
