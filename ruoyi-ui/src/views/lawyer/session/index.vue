<template>
  <div class="app-container lawyer-surface">
    <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true" label-width="78px">
      <el-form-item label="用户 ID" prop="userId">
        <el-input
          v-model="queryParams.userId"
          placeholder="请输入用户 ID"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="用户名" prop="userName">
        <el-input
          v-model="queryParams.userName"
          placeholder="请输入用户名"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="会话标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入会话标题"
          clearable
          style="width: 220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="更新时间" style="width: 320px">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="sessionList">
      <el-table-column label="会话 ID" align="center" prop="id" width="90" />
      <el-table-column label="用户 ID" align="center" prop="userId" width="90" />
      <el-table-column label="用户名" align="center" prop="userName" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="昵称" align="center" prop="nickName" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="会话标题" align="left" prop="title" min-width="200" :show-overflow-tooltip="true" />
      <el-table-column label="消息总数" align="center" prop="messageCount" width="95" />
      <el-table-column label="失败消息数" align="center" prop="failedCount" width="105">
        <template #default="{ row }">
          <el-tag :type="row.failedCount > 0 ? 'danger' : 'info'" size="small">
            {{ row.failedCount ?? 0 }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="165" />
      <el-table-column label="更新时间" align="center" prop="updateTime" width="165" />
      <el-table-column label="操作" align="center" width="170" fixed="right">
        <template #default="{ row }">
          <el-button
            v-hasPermi="['lawyer:manage:query']"
            link
            type="primary"
            icon="View"
            @click="handleAudit(row)"
          >
            消息审计
          </el-button>
          <el-button
            v-hasPermi="['lawyer:manage:remove']"
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />

    <!-- 只读消息审计抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      size="min(680px, 92vw)"
      destroy-on-close
    >
      <div v-loading="msgLoading" class="audit-drawer-body">
        <el-empty v-if="!msgLoading && !messageList.length" description="该会话暂无消息记录" />

        <div v-else class="audit-timeline">
          <article
            v-for="msg in messageList"
            :key="msg.id"
            class="audit-item"
            :class="msg.role === 'user' ? 'is-user' : 'is-assistant'"
          >
            <header class="audit-item-header">
              <div class="audit-item-badges">
                <el-tag :type="msg.role === 'user' ? 'info' : 'success'" size="small">
                  {{ msg.role === 'user' ? '用户提问' : 'AI 律师助手' }}
                </el-tag>
                <el-tag :type="statusTagType(msg.status)" size="small">
                  {{ statusLabel(msg.status) }}
                </el-tag>
                <el-tag v-if="msg.errorCode" type="danger" effect="plain" size="small">
                  {{ msg.errorCode }}
                </el-tag>
                <el-tag v-if="msg.modelName" type="info" effect="plain" size="small">
                  {{ msg.modelName }}
                </el-tag>
              </div>
              <span class="audit-item-time">#{{ msg.id }} · {{ msg.createTime }}</span>
            </header>

            <div v-if="msg.role === 'user'" class="audit-user-text">{{ msg.content }}</div>
            <div v-else-if="msg.content" class="lawyer-markdown audit-assistant-md" v-html="renderMarkdown(msg.content)" />
            <div v-else class="audit-empty-content">（无正文内容）</div>
          </article>
        </div>

        <div v-if="msgTotal > msgQuery.pageSize" class="audit-pagination">
          <pagination
            v-model:page="msgQuery.pageNum"
            v-model:limit="msgQuery.pageSize"
            :total="msgTotal"
            @pagination="loadAuditMessages"
          />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup name="LawyerSession">
import { ref, reactive, computed, getCurrentInstance, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAdminSession, listAdminMessage, delAdminSession } from '@/api/lawyer/admin'
import { renderMarkdown } from '@/utils/lawyer/markdown'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const showSearch = ref(true)
const sessionList = ref([])
const total = ref(0)
const dateRange = ref([])

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  userId: undefined,
  userName: undefined,
  title: undefined
})

const drawerVisible = ref(false)
const currentSession = ref(null)
const msgLoading = ref(false)
const messageList = ref([])
const msgTotal = ref(0)
const msgQuery = reactive({
  pageNum: 1,
  pageSize: 50
})

const drawerTitle = computed(() => {
  if (!currentSession.value) return '消息审计'
  return `消息审计 - ${currentSession.value.title || '未命名会话'} (ID: ${currentSession.value.id})`
})

function buildQuery() {
  const params = {
    pageNum: queryParams.pageNum,
    pageSize: queryParams.pageSize,
    userId: queryParams.userId ? Number(queryParams.userId) : undefined,
    userName: queryParams.userName || undefined,
    title: queryParams.title || undefined,
    beginTime: dateRange.value?.[0] || undefined,
    endTime: dateRange.value?.[1] || undefined
  }
  return params
}

async function getList() {
  loading.value = true
  try {
    const res = await listAdminSession(buildQuery())
    sessionList.value = res.rows || []
    total.value = res.total || 0
  } catch {
    // 异常由 request 拦截器处理
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  dateRange.value = []
  proxy.resetForm('queryRef')
  queryParams.userId = undefined
  queryParams.userName = undefined
  queryParams.title = undefined
  handleQuery()
}

async function handleAudit(row) {
  currentSession.value = row
  msgQuery.pageNum = 1
  drawerVisible.value = true
  await loadAuditMessages()
}

async function loadAuditMessages() {
  if (!currentSession.value) return
  msgLoading.value = true
  try {
    const res = await listAdminMessage(currentSession.value.id, {
      pageNum: msgQuery.pageNum,
      pageSize: msgQuery.pageSize
    })
    const rows = res.rows || []
    messageList.value = [...rows].sort((a, b) => (a.id || 0) - (b.id || 0))
    msgTotal.value = res.total || 0
  } catch {
    messageList.value = []
  } finally {
    msgLoading.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除会话“${row.title || row.id}”（用户：${row.userName || row.userId}）及其全部消息吗？删除后不可恢复。`,
      '删除会话',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  try {
    await delAdminSession(row.id)
    ElMessage.success('删除成功')
    if (sessionList.value.length === 1 && queryParams.pageNum > 1) {
      queryParams.pageNum -= 1
    }
    await getList()
  } catch (error) {
    const code = error?.code ?? error?.response?.data?.code
    const msg = String(error?.msg || error?.message || '')
    if (Number(code) === 409 || msg.includes('409') || msg.includes('正在生成')) {
      ElMessage.warning('当前会话正在生成回答，请稍后再试')
    }
  }
}

function statusTagType(status) {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'pending') return 'warning'
  return 'info'
}

function statusLabel(status) {
  if (status === 'success') return '成功'
  if (status === 'failed') return '失败'
  if (status === 'pending') return '生成中'
  return status || '未知'
}

onMounted(getList)
</script>

<style scoped>
.audit-drawer-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 16px;
}

.audit-timeline {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.audit-item {
  padding: 14px 16px;
  border: 1px solid var(--law-line);
  border-radius: 12px;
  background: var(--law-card);
}

.audit-item.is-user {
  background: var(--law-panel);
}

.audit-item-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.audit-item-badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.audit-item-time {
  color: var(--law-muted);
  font-size: 12px;
}

.audit-user-text {
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.audit-assistant-md {
  font-size: 14px;
}

.audit-empty-content {
  color: var(--law-muted);
  font-size: 13px;
}

.audit-pagination {
  display: flex;
  justify-content: flex-end;
}
</style>
