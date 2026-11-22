<template>
  <div class="doc-page lawyer-surface" @keydown.esc="closeMobileDrawer">
    <header class="workspace-header doc-header">
      <div class="workspace-header-start">
        <button
          v-if="isCompact"
          type="button"
          class="icon-button"
          aria-label="打开历史草稿"
          title="打开历史草稿"
          @click="historyDrawerOpen = true"
        >
          <el-icon><Expand /></el-icon>
        </button>
        <router-link to="/index" class="workspace-brand-link">
          <LawyerMark />
          <span>AI 律师平台</span>
        </router-link>
        <WorkspaceNav />
      </div>

      <div class="workspace-header-actions">
        <el-button
          v-hasPermi="['lawyer:document:add']"
          type="primary"
          size="small"
          class="doc-new-btn"
          @click="handleNewDraft"
        >
          <el-icon><Plus /></el-icon>
          <span>新建文书</span>
        </el-button>

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

        <el-dropdown trigger="click" placement="bottom-end" @command="handleAccount">
          <button type="button" class="doc-account-btn" aria-label="账户菜单">
            <span class="account-avatar" aria-hidden="true">{{ accountInitial }}</span>
            <span class="doc-account-name">{{ accountName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="lock">锁定屏幕</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="doc-workspace-body">
      <!-- 桌面端左侧历史草稿栏 -->
      <aside v-if="!isCompact" class="doc-sidebar" aria-label="历史文书草稿">
        <DraftHistory
          :drafts="historyList"
          :total="historyTotal"
          :loading="historyLoading"
          :loading-more="historyLoadingMore"
          :active-id="activeDraft?.id || null"
          :deleting-id="deletingId"
          :templates="templates"
          :can-create="canCreate"
          @select="handleSelectDraft"
          @delete="handleDeleteDraft"
          @filter-change="handleFilterChange"
          @load-more="handleLoadMore"
          @create-new="handleNewDraft"
        />
      </aside>

      <!-- 移动端历史草稿抽屉 -->
      <el-drawer
        v-if="isCompact"
        v-model="historyDrawerOpen"
        direction="ltr"
        size="300px"
        :with-header="false"
        class="doc-mobile-drawer lawyer-surface"
      >
        <DraftHistory
          :drafts="historyList"
          :total="historyTotal"
          :loading="historyLoading"
          :loading-more="historyLoadingMore"
          :active-id="activeDraft?.id || null"
          :deleting-id="deletingId"
          :templates="templates"
          :can-create="canCreate"
          @select="handleSelectDraft"
          @delete="handleDeleteDraft"
          @filter-change="handleFilterChange"
          @load-more="handleLoadMore"
          @create-new="handleNewDraft"
        />
      </el-drawer>

      <!-- 右侧主工作区 -->
      <main class="doc-main">
        <TemplatePicker
          v-model:collapsed="templateCollapsed"
          :templates="templates"
          :loading="loadingTemplates"
          :selected-code="selectedTemplateCode"
          @select="handleSelectTemplate"
        />

        <!-- 移动端双栏步骤切换 -->
        <div v-if="isCompact" class="doc-mobile-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            :aria-selected="mobilePane === 'form'"
            class="doc-mobile-tab"
            :class="{ 'is-active': mobilePane === 'form' }"
            @click="mobilePane = 'form'"
          >
            1. 要素采集
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="mobilePane === 'editor'"
            class="doc-mobile-tab"
            :class="{ 'is-active': mobilePane === 'editor' }"
            @click="mobilePane = 'editor'"
          >
            2. 编辑与预览
          </button>
        </div>

        <div class="doc-split-grid">
          <div
            class="doc-split-left"
            :class="{ 'is-mobile-hidden': isCompact && mobilePane !== 'form' }"
          >
            <DynamicForm
              ref="dynamicFormRef"
              v-model="formModel"
              v-model:custom-title="customTitle"
              :template="selectedTemplate"
              :rules="formRules"
              :generating="generating"
              @submit="handleGenerate"
              @reset="handleResetForm"
            />
          </div>

          <div
            v-loading="detailLoading"
            class="doc-split-right"
            :class="{ 'is-mobile-hidden': isCompact && mobilePane !== 'editor' }"
          >
            <DraftEditor
              :draft="activeDraft"
              :saving="saving"
              :exporting="exporting"
              :generating="generating"
              @save="handleSaveDraft"
              @export="handleExportDraft"
            />
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup name="WorkspaceDocument">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMediaQuery } from '@vueuse/core'
import {
  ArrowDown,
  Expand,
  Moon,
  Plus,
  Setting,
  Sunny
} from '@element-plus/icons-vue'
import LawyerMark from '@/components/LawyerMark.vue'
import WorkspaceNav from '@/views/lawyer/components/WorkspaceNav.vue'
import DraftHistory from './components/DraftHistory.vue'
import TemplatePicker from './components/TemplatePicker.vue'
import DynamicForm from './components/DynamicForm.vue'
import DraftEditor from './components/DraftEditor.vue'
import useSettingsStore from '@/store/modules/settings'
import useUserStore from '@/store/modules/user'
import useLockStore from '@/store/modules/lock'
import auth from '@/plugins/auth'
import { listDraft, getDraft, delDraft } from '@/api/lawyer/document'
import { useDocumentDraft } from '@/composables/lawyer/useDocumentDraft'

const PAGE_SIZE = 15

const router = useRouter()
const route = useRoute()
const settingsStore = useSettingsStore()
const userStore = useUserStore()

const {
  templates,
  loadingTemplates,
  generating,
  saving,
  loadTemplates,
  buildFormModel,
  buildFormRules,
  generate,
  save,
  exportDocx
} = useDocumentDraft()

const isCompact = useMediaQuery('(max-width: 768px)')
const historyDrawerOpen = ref(false)
const templateCollapsed = ref(false)
const mobilePane = ref('form')

const dynamicFormRef = ref(null)
const selectedTemplateCode = ref('')
const formModel = ref({})
const customTitle = ref('')

const historyList = ref([])
const historyTotal = ref(0)
const historyPage = ref(1)
const historyLoading = ref(false)
const historyLoadingMore = ref(false)
const filters = ref({ title: '', templateCode: '' })

const activeDraft = ref(null)
const detailLoading = ref(false)
const deletingId = ref(null)
const exporting = ref(false)

const canCreate = computed(() => auth.hasPermi('lawyer:document:add'))
const accountName = computed(() => userStore.nickName || userStore.name || '我的账号')
const accountInitial = computed(() => Array.from(accountName.value)[0] || '我')
const managementLink = computed(() => [
  ['lawyer:manage:list', '/lawyer/session'],
  ['lawyer:knowledge:list', '/lawyer/knowledge'],
  ['system:user:list', '/system/user'],
].find(([permission]) => auth.hasPermi(permission))?.[1])

const selectedTemplate = computed(() =>
  templates.value.find(item => item.code === selectedTemplateCode.value) || null
)

const formRules = computed(() => {
  if (!selectedTemplate.value) return {}
  return buildFormRules(selectedTemplate.value)
})

watch(isCompact, (compact) => {
  if (!compact) {
    historyDrawerOpen.value = false
  }
})

function closeMobileDrawer() {
  if (isCompact.value) {
    historyDrawerOpen.value = false
  }
}

function unwrapData(res) {
  return res && typeof res === 'object' && 'data' in res && res.data !== undefined ? res.data : res
}

function syncHistoryItem(draft) {
  if (!draft?.id) return
  const idx = historyList.value.findIndex(item => item.id === draft.id)
  if (idx !== -1) {
    historyList.value[idx] = {
      ...historyList.value[idx],
      title: draft.title || historyList.value[idx].title,
      status: draft.status || historyList.value[idx].status,
      updateTime: draft.updateTime || historyList.value[idx].updateTime
    }
  }
}

async function loadHistory(pageNum = 1, append = false) {
  if (append) {
    historyLoadingMore.value = true
  } else {
    historyLoading.value = true
  }
  try {
    const query = {
      pageNum,
      pageSize: PAGE_SIZE
    }
    if (filters.value.title) query.title = filters.value.title
    if (filters.value.templateCode) query.templateCode = filters.value.templateCode

    const res = await listDraft(query)
    const rows = res?.rows || []
    historyTotal.value = res?.total ?? rows.length
    historyPage.value = pageNum
    if (append) {
      const existingIds = new Set(historyList.value.map(item => item.id))
      const merged = [...historyList.value]
      for (const row of rows) {
        if (!existingIds.has(row.id)) {
          merged.push(row)
        }
      }
      historyList.value = merged
    } else {
      historyList.value = rows
    }
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    historyLoading.value = false
    historyLoadingMore.value = false
  }
}

function handleFilterChange(nextFilters) {
  filters.value = { ...nextFilters }
  loadHistory(1, false)
}

function handleLoadMore() {
  if (historyLoadingMore.value || historyList.value.length >= historyTotal.value) return
  loadHistory(historyPage.value + 1, true)
}

async function handleSelectTemplate(tpl) {
  if (!tpl) return
  selectedTemplateCode.value = tpl.code
  formModel.value = buildFormModel(tpl)
  customTitle.value = ''
  activeDraft.value = null
  mobilePane.value = 'form'
  await nextTick()
  dynamicFormRef.value?.clearValidate()
}

async function handleResetForm() {
  if (!selectedTemplate.value) return
  formModel.value = buildFormModel(selectedTemplate.value)
  customTitle.value = ''
  await nextTick()
  dynamicFormRef.value?.clearValidate()
}

async function handleNewDraft() {
  activeDraft.value = null
  customTitle.value = ''
  templateCollapsed.value = false
  mobilePane.value = 'form'
  closeMobileDrawer()
  if (selectedTemplate.value) {
    formModel.value = buildFormModel(selectedTemplate.value)
  } else if (templates.value.length) {
    selectedTemplateCode.value = templates.value[0].code
    formModel.value = buildFormModel(templates.value[0])
  }
  await nextTick()
  dynamicFormRef.value?.clearValidate()
}

function parseDraftId(raw) {
  const num = Number(raw)
  return Number.isInteger(num) && num > 0 ? num : null
}

async function handleSelectDraft(idOrDraft) {
  const id = typeof idOrDraft === 'object' && idOrDraft !== null
    ? parseDraftId(idOrDraft.id)
    : parseDraftId(idOrDraft)
  if (!id) return
  closeMobileDrawer()
  detailLoading.value = true
  try {
    const res = await getDraft(id)
    const detail = unwrapData(res)
    if (!detail) return

    activeDraft.value = detail
    if (detail.templateCode) {
      selectedTemplateCode.value = detail.templateCode
      const tpl = templates.value.find(item => item.code === detail.templateCode)
      const baseModel = tpl ? buildFormModel(tpl) : {}
      formModel.value = {
        ...baseModel,
        ...(detail.formData || {})
      }
    }
    customTitle.value = detail.title || ''
    templateCollapsed.value = true
    mobilePane.value = 'editor'
    await nextTick()
    dynamicFormRef.value?.clearValidate()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    detailLoading.value = false
  }
}

async function handleGenerate() {
  if (!selectedTemplate.value) return
  try {
    const titleArg = customTitle.value.trim() || undefined
    const res = await generate(selectedTemplate.value.code, formModel.value, titleArg)
    const created = unwrapData(res)
    if (!created) return

    activeDraft.value = created
    templateCollapsed.value = true
    mobilePane.value = 'editor'
    await loadHistory(1, false)
    if (created.status === 'success') {
      ElMessage.success('文书草稿生成成功')
    }
  } catch {
    // 错误提示由 composable / request 拦截器处理
  }
}

async function handleSaveDraft({ id, title, content }) {
  try {
    const res = await save(id, title, content)
    const updated = unwrapData(res)
    if (updated) {
      activeDraft.value = updated
      syncHistoryItem(updated)
    }
    ElMessage.success('文书修改已保存')
  } catch {
    // 错误提示由 composable / request 拦截器处理
  }
}

async function handleExportDraft(draft) {
  if (!draft?.id) return
  exporting.value = true
  try {
    await exportDocx(draft)
  } catch {
    // 错误提示由 composable / downloadBlob 处理
  } finally {
    exporting.value = false
  }
}

async function handleDeleteDraft(item) {
  if (!item?.id || deletingId.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除文书草稿「${item.title || '未命名文书'}」吗？删除后无法恢复。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }

  deletingId.value = item.id
  try {
    await delDraft(item.id)
    historyList.value = historyList.value.filter(entry => entry.id !== item.id)
    historyTotal.value = Math.max(0, historyTotal.value - 1)
    if (activeDraft.value?.id === item.id) {
      activeDraft.value = null
    }
    ElMessage.success('已删除文书草稿')
  } catch {
    // 错误提示由 request 拦截器处理
  } finally {
    deletingId.value = null
  }
}

async function handleAccount(command) {
  if (command === 'profile') {
    router.push('/user/profile')
  } else if (command === 'lock') {
    useLockStore().lockScreen(route.fullPath)
    router.push('/lock')
  } else if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定注销并退出系统吗？', '提示', { type: 'warning' })
      await userStore.logOut()
      location.href = '/index'
    } catch {
      // 用户取消退出
    }
  }
}

watch(
  () => route.query.draftId,
  async (nextDraftId) => {
    const id = parseDraftId(nextDraftId)
    if (id && id !== activeDraft.value?.id) {
      await handleSelectDraft({ id })
    }
  }
)

onMounted(async () => {
  await Promise.all([
    loadTemplates().catch(() => {}),
    loadHistory(1, false)
  ])
  if (!selectedTemplateCode.value && templates.value.length) {
    selectedTemplateCode.value = templates.value[0].code
    formModel.value = buildFormModel(templates.value[0])
  }
  const initialDraftId = parseDraftId(route.query.draftId)
  if (initialDraftId) {
    await handleSelectDraft({ id: initialDraftId })
  }
})
</script>
