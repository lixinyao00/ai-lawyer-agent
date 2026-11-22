<template>
  <div class="workspace-shell lawyer-surface">
    <header class="workspace-header">
      <div class="workspace-header-start">
        <router-link to="/index" class="workspace-brand-link">
          <LawyerMark />
          <span>AI 律师平台</span>
        </router-link>
        <WorkspaceNav />
      </div>
      <div class="workspace-header-actions">
        <button
          type="button"
          class="icon-button"
          :aria-label="settingsStore.isDark ? '切换浅色模式' : '切换深色模式'"
          :title="settingsStore.isDark ? '切换浅色模式' : '切换深色模式'"
          @click="settingsStore.toggleTheme"
        >
          <el-icon><Sunny v-if="settingsStore.isDark" /><Moon v-else /></el-icon>
        </button>
        <router-link v-if="managementLink" class="management-link" :to="managementLink">
          <el-icon><Setting /></el-icon><span>管理后台</span>
        </router-link>
      </div>
    </header>

    <main class="workspace-placeholder-body">
      <section class="workspace-placeholder-card">
        <div class="workspace-placeholder-icon">
          <el-icon><DocumentChecked /></el-icon>
        </div>
        <h1>合同风险审查</h1>
        <p>
          功能建设中（M3 即将上线）。上线后支持上传合同文件或粘贴文本、选择审查立场，智能识别高/中/低风险条款并生成修改建议与原件下载。
        </p>
        <el-button type="primary" @click="router.push('/index')">返回智能咨询</el-button>
      </section>
    </main>
  </div>
</template>

<script setup name="WorkspaceContract">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { DocumentChecked, Moon, Setting, Sunny } from '@element-plus/icons-vue'
import LawyerMark from '@/components/LawyerMark.vue'
import WorkspaceNav from '@/views/lawyer/components/WorkspaceNav.vue'
import useSettingsStore from '@/store/modules/settings'
import auth from '@/plugins/auth'

const router = useRouter()
const settingsStore = useSettingsStore()

const managementLink = computed(() => [
  ['lawyer:manage:list', '/lawyer/session'],
  ['lawyer:knowledge:list', '/lawyer/knowledge'],
  ['system:user:list', '/system/user'],
].find(([permission]) => auth.hasPermi(permission))?.[1])
</script>

<style scoped>
.icon-button {
  display: grid;
  flex: 0 0 38px;
  place-items: center;
  width: 38px;
  height: 38px;
  padding: 0;
  border: 0;
  border-radius: 11px;
  color: var(--law-muted);
  background: transparent;
  cursor: pointer;
  font-size: 18px;
}

.icon-button:hover {
  background: var(--law-hover);
  color: var(--law-ink);
}

.management-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 35px;
  padding: 0 11px;
  border: 1px solid var(--law-line);
  border-radius: 10px;
  color: var(--law-ink);
  white-space: nowrap;
  font-size: 13px;
  text-decoration: none;
}

.management-link:hover {
  color: var(--law-accent);
  background: var(--law-hover);
}
</style>
