<template>
  <nav v-if="visibleItems.length" class="workspace-nav" aria-label="工作台功能切换">
    <router-link
      v-for="item in visibleItems"
      :key="item.path"
      :to="item.path"
      class="workspace-nav-item"
      :class="{ 'is-active': isActive(item.path) }"
      :aria-current="isActive(item.path) ? 'page' : undefined"
    >
      <el-icon><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
    </router-link>
  </nav>
</template>

<script setup name="WorkspaceNav">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ChatDotRound, DocumentChecked, EditPen } from '@element-plus/icons-vue'
import auth from '@/plugins/auth'

const route = useRoute()

const navItems = [
  {
    path: '/index',
    label: '智能咨询',
    permission: 'lawyer:session:list',
    icon: ChatDotRound
  },
  {
    path: '/lawyer-workspace/contract',
    label: '合同风险审查',
    permission: 'lawyer:contract:list',
    icon: DocumentChecked
  },
  {
    path: '/lawyer-workspace/document',
    label: '法律文书起草',
    permission: 'lawyer:document:list',
    icon: EditPen
  }
]

const visibleItems = computed(() => navItems.filter((item) => auth.hasPermi(item.permission)))

function isActive(path) {
  return route.path === path
}
</script>
