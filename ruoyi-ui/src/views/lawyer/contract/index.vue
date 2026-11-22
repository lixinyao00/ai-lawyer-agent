<template>
  <div class="contract-page lawyer-surface" @keydown.esc="closeMobileDrawer">
    <header class="workspace-header contract-header">
      <div class="workspace-header-start">
        <button
          v-if="isCompact"
          type="button"
          class="icon-button"
          aria-label="打开审查历史"
          title="打开审查历史"
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
          v-hasPermi="['lawyer:contract:add']"
          type="primary"
          size="small"
          class="contract-new-btn"
          @click="handleNewReview"
        >
          <el-icon><Plus /></el-icon>
          <span>新建审查</span>
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
          <button type="button" class="contract-account-btn" aria-label="账户菜单">
            <span class="account-avatar" aria-hidden="true">{{ accountInitial }}</span>
            <span class="contract-account-name">{{ accountName }}</span>
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

    <div class="contract-workspace-body">
      <!-- 桌面端左侧历史栏 -->
      <aside v-if="!isCompact" class="contract-sidebar" aria-label="合同审查历史">
        <ReviewHistory
          :reviews="historyList"
          :total="historyTotal"
          :loading="historyLoading"
          :loading-more="historyLoadingMore"
          :active-id="viewMode === 'detail' ? activeReviewId : null"
          :deleting-id="deletingId"
          :can-create="canCreate"
          @select="handleSelectReview"
          @delete="handleDeleteReview"
          @filter-change="handleFilterChange"
          @load-more="handleLoadMore"
          @create-new="handleNewReview"
        />
      </aside>

      <!-- 移动端历史抽屉 -->
      <el-drawer
        v-if="isCompact"
        v-model="historyDrawerOpen"
        direction="ltr"
        size="300px"
        :with-header="false"
        class="contract-mobile-drawer lawyer-surface"
      >
        <ReviewHistory
          :reviews="historyList"
          :total="historyTotal"
          :loading="historyLoading"
          :loading-more="historyLoadingMore"
          :active-id="viewMode === 'detail' ? activeReviewId : null"
          :deleting-id="deletingId"
          :can-create="canCreate"
          @select="handleSelectReview"
          @delete="handleDeleteReview"
          @filter-change="handleFilterChange"
          @load-more="handleLoadMore"
          @create-new="handleNewReview"
        />
      </el-drawer>

      <!-- 右侧主工作区 -->
      <main class="contract-main">
        <ReviewUpload
          v-if="viewMode === 'upload'"
          :submitting="submitting"
          :validate-file="validateFile"
          :initial-draft="retryDraft"
          @submit="handleSubmitReview"
        />
        <RiskList
          v-else
          :review="activeReview"
          :loading="detailLoading"
          :elapsed-seconds="elapsedSeconds"
          @retry="handleRetryReview"
          @create-new="handleNewReview"
        />
      </main>
    </div>
  </div>
</template>

<script setup name="WorkspaceContract">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
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
import ReviewHistory from './components/ReviewHistory.vue'
import ReviewUpload from './components/ReviewUpload.vue'
import RiskList from './components/RiskList.vue'
import useSettingsStore from '@/store/modules/settings'
import useUserStore from '@/store/modules/user'
import useLockStore from '@/store/modules/lock'
import auth from '@/plugins/auth'
import { listReview, getReview, delReview } from '@/api/lawyer/contract'
import { useContractReview } from '@/composables/lawyer/useContractReview'

const PAGE_SIZE = 15

const router = useRouter()
const route = useRoute()
const settingsStore = useSettingsStore()
const userStore = useUserStore()

const { submitting, submit, poll, stopPolling, validateFile } = useContractReview()

const isCompact = useMediaQuery('(max-width: 768px)')
const historyDrawerOpen = ref(false)
const viewMode = ref('upload')
const retryDraft = ref(null)

const historyList = ref([])
const historyTotal = ref(0)
const historyPage = ref(1)
const historyLoading = ref(false)
const historyLoadingMore = ref(false)
const filters = ref({ title: '', riskLevel: '', status: '' })

const activeReviewId = ref(null)
const activeReview = ref(null)
const detailLoading = ref(false)
const deletingId = ref(null)

const elapsedSeconds = ref(0)
let elapsedTimer = null

const canCreate = computed(() => auth.hasPermi('lawyer:contract:add'))
const accountName = computed(() => userStore.nickName || userStore.name || '我的账号')
const accountInitial = computed(() => Array.from(accountName.value)[0] || '我')
const managementLink = computed(() => [
  ['lawyer:manage:list', '/lawyer/session'],
  ['lawyer:knowledge:list', '/lawyer/knowledge'],
  ['system:user:list', '/system/user'],
].find(([permission]) => auth.hasPermi(permission))?.[1])

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

function clearElapsedTimer() {
  if (elapsedTimer) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
}

function startElapsedTimer(createTime) {
  clearElapsedTimer()
  let initial = 0
  if (createTime) {
    const ts = new Date(String(createTime).replace(/-/g, '/')).getTime()
    if (!Number.isNaN(ts)) {
      initial = Math.max(0, Math.floor((Date.now() - ts) / 1000))
    }
  }
  elapsedSeconds.value = initial
  elapsedTimer = setInterval(() => {
    elapsedSeconds.value += 1
  }, 1000)
}

function unwrapData(res) {
  return res && typeof res === 'object' && 'data' in res ? res.data : res
}

function syncHistoryItem(review) {
  if (!review?.id) return
  const idx = historyList.value.findIndex(item => item.id === review.id)
  if (idx !== -1) {
    historyList.value[idx] = {
      ...historyList.value[idx],
      status: review.status,
      riskLevel: review.riskLevel,
      riskCount: Array.isArray(review.risks) ? review.risks.length : (historyList.value[idx].riskCount ?? 0),
      title: review.title || historyList.value[idx].title
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
    if (filters.value.riskLevel) query.riskLevel = filters.value.riskLevel
    if (filters.value.status) query.status = filters.value.status

    const res = await listReview(query)
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

function handleNewReview() {
  stopPolling()
  clearElapsedTimer()
  retryDraft.value = null
  viewMode.value = 'upload'
  closeMobileDrawer()
}

async function startReviewPolling(reviewId, createTime) {
  stopPolling()
  startElapsedTimer(createTime)
  try {
    const finalReview = await poll(reviewId, {
      interval: 3000,
      timeout: 180000,
      onUpdate: (updated) => {
        const normalized = unwrapData(updated)
        if (!normalized) return
        if (activeReviewId.value === reviewId) {
          activeReview.value = normalized
        }
        syncHistoryItem(normalized)
      }
    })
    const normalizedFinal = unwrapData(finalReview)
    if (normalizedFinal && activeReviewId.value === reviewId) {
      activeReview.value = normalizedFinal
    }
    syncHistoryItem(normalizedFinal)
  } catch {
    // 轮询超时或中断时不覆盖已有详情，仅停止计时
  } finally {
    clearElapsedTimer()
  }
}

async function handleSubmitReview(payload) {
  stopPolling()
  clearElapsedTimer()
  try {
    const res = await submit(payload)
    const created = unwrapData(res)
    if (!created?.id) return

    retryDraft.value = null
    activeReviewId.value = created.id
    activeReview.value = created
    viewMode.value = 'detail'
    await loadHistory(1, false)

    if (created.status === 'pending') {
      startReviewPolling(created.id, created.createTime)
    }
  } catch {
    // 提交失败提示由 composable / request 拦截器处理
  }
}

async function handleSelectReview(id) {
  if (!id) return
  closeMobileDrawer()
  stopPolling()
  clearElapsedTimer()

  activeReviewId.value = id
  viewMode.value = 'detail'
  detailLoading.value = true
  try {
    const res = await getReview(id)
    const detail = unwrapData(res)
    if (activeReviewId.value !== id) return
    activeReview.value = detail
    syncHistoryItem(detail)
    if (detail?.status === 'pending') {
      startReviewPolling(id, detail.createTime)
    }
  } catch {
    if (activeReviewId.value === id) {
      activeReview.value = null
    }
  } finally {
    detailLoading.value = false
  }
}

async function handleDeleteReview(item) {
  if (!item?.id) return
  const title = item.title || item.fileName || '该审查记录'
  try {
    await ElMessageBox.confirm(
      `确定删除「${title}」吗？删除后将无法恢复，关联的私有合同原件（若无其他引用）也将一并清理。`,
      '删除确认',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  deletingId.value = item.id
  try {
    await delReview(item.id)
    ElMessage.success('删除成功')
    if (activeReviewId.value === item.id) {
      stopPolling()
      clearElapsedTimer()
      activeReviewId.value = null
      activeReview.value = null
      viewMode.value = 'upload'
    }
    await loadHistory(1, false)
  } finally {
    deletingId.value = null
  }
}

function handleRetryReview(review) {
  stopPolling()
  clearElapsedTimer()
  retryDraft.value = {
    stance: review?.stance || 'party_a',
    title: review?.title || '',
    contractText: review?.contractText || ''
  }
  viewMode.value = 'upload'
}

async function handleAccount(command) {
  if (command === 'profile') {
    await router.push({ name: 'Profile' })
  } else if (command === 'lock') {
    useLockStore().lockScreen(route.fullPath)
    await router.push('/lock')
  } else if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录吗？', '退出登录', {
        confirmButtonText: '退出',
        cancelButtonText: '取消'
      })
      await userStore.logOut()
      location.href = '/login'
    } catch {
      // 取消退出
    }
  }
}

onMounted(() => {
  loadHistory(1, false)
})

onBeforeUnmount(() => {
  stopPolling()
  clearElapsedTimer()
})
</script>

<style scoped>
.icon-button {
  display: grid;
  flex: 0 0 36px;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 0;
  border-radius: 10px;
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
  min-height: 34px;
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
