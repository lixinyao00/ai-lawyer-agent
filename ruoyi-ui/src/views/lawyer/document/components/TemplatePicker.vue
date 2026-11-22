<template>
  <section class="doc-template-section" aria-label="文书模板选择">
    <div class="doc-template-section-head">
      <div class="doc-template-section-title">
        <span class="doc-section-eyebrow">文书模板</span>
        <strong v-if="activeTemplate" class="doc-active-template-name">{{ activeTemplate.name }}</strong>
        <span v-if="activeTemplate" class="doc-template-field-count">
          {{ activeTemplate.fields?.length || 0 }} 项要素
        </span>
      </div>
      <button
        type="button"
        class="doc-collapse-btn"
        :aria-expanded="!collapsed"
        @click="emit('update:collapsed', !collapsed)"
      >
        <span>{{ collapsed ? '展开切换模板' : '收起模板列表' }}</span>
        <el-icon><ArrowDown v-if="collapsed" /><ArrowUp v-else /></el-icon>
      </button>
    </div>

    <div v-show="!collapsed" class="doc-template-body">
      <div v-if="loading && !templates.length" class="doc-template-skeleton">
        <el-skeleton :rows="2" animated />
      </div>

      <div v-else-if="!templates.length" class="doc-template-empty">
        暂无可用文书模板，请稍后刷新重试
      </div>

      <div v-else class="doc-template-grid">
        <button
          v-for="tpl in templates"
          :key="tpl.code"
          type="button"
          class="doc-template-card"
          :class="{ 'is-selected': tpl.code === selectedCode }"
          @click="emit('select', tpl)"
        >
          <div class="doc-template-card-top">
            <span class="doc-template-icon">
              <el-icon><component :is="resolveIcon(tpl.code)" /></el-icon>
            </span>
            <span class="doc-template-count">{{ tpl.fields?.length || 0 }} 项要素</span>
          </div>
          <div class="doc-template-card-name">{{ tpl.name }}</div>
          <div class="doc-template-card-desc">{{ tpl.description }}</div>
          <span v-if="tpl.code === selectedCode" class="doc-template-check" aria-hidden="true">
            <el-icon><Check /></el-icon>
          </span>
        </button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import {
  ArrowDown,
  ArrowUp,
  Briefcase,
  Check,
  Document,
  House,
  Money,
  Tickets
} from '@element-plus/icons-vue'

const props = defineProps({
  templates: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  selectedCode: {
    type: String,
    default: ''
  },
  collapsed: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['select', 'update:collapsed'])

const ICON_MAP = {
  labor_arbitration: Briefcase,
  payment_demand: Money,
  loan_agreement: Tickets,
  lease_termination: House
}

const activeTemplate = computed(() =>
  props.templates.find(item => item.code === props.selectedCode) || null
)

function resolveIcon(code) {
  return ICON_MAP[code] || Document
}
</script>
