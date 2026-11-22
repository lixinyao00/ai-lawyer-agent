<template>
  <div class="app-container lawyer-surface knowledge-page">
    <!-- 顶部筛选栏 -->
    <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true" label-width="76px">
      <el-form-item label="法规标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入法规名称"
          clearable
          style="width: 220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="分类" prop="category">
        <el-select
          v-model="queryParams.category"
          placeholder="全部分类"
          clearable
          style="width: 160px"
        >
          <el-option
            v-for="item in KNOWLEDGE_CATEGORIES"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select
          v-model="queryParams.status"
          placeholder="全部状态"
          clearable
          style="width: 130px"
        >
          <el-option
            v-for="item in KNOWLEDGE_STATUS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 顶部操作栏 -->
    <el-row :gutter="10" class="mb8 knowledge-toolbar">
      <el-col :span="1.5">
        <el-button
          v-hasPermi="['lawyer:knowledge:add']"
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
        >
          新增法规
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          v-hasPermi="['lawyer:knowledge:import']"
          type="success"
          plain
          icon="Upload"
          @click="openImportDialog"
        >
          文件导入法规
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          v-hasPermi="['lawyer:knowledge:query']"
          type="info"
          plain
          icon="Search"
          @click="openDebugDrawer"
        >
          检索调试
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          v-hasPermi="['lawyer:knowledge:edit']"
          type="warning"
          plain
          icon="Refresh"
          :loading="rebuildingAll"
          @click="handleRebuildEmbeddings()"
        >
          重建向量索引
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          v-hasPermi="['lawyer:knowledge:remove']"
          type="danger"
          plain
          icon="Delete"
          :disabled="selectedIds.length === 0"
          @click="handleBatchDelete"
        >
          批量删除
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 法规数据表格 -->
    <el-table v-loading="loading" :data="docList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="ID" align="center" prop="id" width="76" />
      <el-table-column label="法规名称" align="left" prop="title" min-width="220" :show-overflow-tooltip="true" />
      <el-table-column label="分类" align="center" prop="category" width="115">
        <template #default="{ row }">
          <el-tag effect="plain" size="small">
            {{ getKnowledgeCategoryLabel(row.category) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="文号" align="left" prop="docNumber" min-width="150" :show-overflow-tooltip="true">
        <template #default="{ row }">
          <span>{{ row.docNumber || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="施行日期" align="center" prop="effectiveDate" width="120">
        <template #default="{ row }">
          <span>{{ row.effectiveDate || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="切片数量" align="center" prop="chunkCount" width="95">
        <template #default="{ row }">
          <el-tag type="info" size="small">{{ row.chunkCount ?? 0 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="105">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            active-value="0"
            inactive-value="1"
            inline-prompt
            active-text="启用"
            inactive-text="停用"
            :loading="Boolean(statusLoadingMap[row.id])"
            @change="(val) => handleStatusChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" align="center" prop="updateTime" width="165" />
      <el-table-column label="操作" align="center" width="280" fixed="right">
        <template #default="{ row }">
          <el-button
            v-hasPermi="['lawyer:knowledge:query']"
            link
            type="primary"
            icon="Collection"
            @click="openChunkDrawer(row)"
          >
            切片管理
          </el-button>
          <el-button
            v-hasPermi="['lawyer:knowledge:edit']"
            link
            type="primary"
            icon="Edit"
            @click="handleEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-hasPermi="['lawyer:knowledge:edit']"
            link
            type="warning"
            icon="Scissor"
            @click="openRechunkDialog(row)"
          >
            重新切分
          </el-button>
          <el-button
            v-hasPermi="['lawyer:knowledge:remove']"
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

    <!-- 新增 / 编辑法规弹窗 -->
    <el-dialog
      v-model="docDialogVisible"
      :title="docDialogTitle"
      width="640px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="docFormRef" :model="docForm" :rules="docRules" label-width="92px">
        <el-form-item label="法规名称" prop="title">
          <el-input v-model="docForm.title" maxlength="200" show-word-limit placeholder="请输入法规完整标题" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="法规分类" prop="category">
              <el-select v-model="docForm.category" placeholder="请选择分类" style="width: 100%">
                <el-option
                  v-for="item in KNOWLEDGE_CATEGORIES"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用状态" prop="status">
              <el-radio-group v-model="docForm.status">
                <el-radio
                  v-for="item in KNOWLEDGE_STATUS"
                  :key="item.value"
                  :label="item.value"
                >
                  {{ item.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="发文字号" prop="docNumber">
              <el-input v-model="docForm.docNumber" maxlength="100" placeholder="如：主席令第七十三号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="施行日期" prop="effectiveDate">
              <el-date-picker
                v-model="docForm.effectiveDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="docForm.remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="可选，填写来源说明或修订备注"
          />
        </el-form-item>
        <el-form-item v-if="!docForm.id" label="法规正文" prop="content">
          <el-input
            v-model="docForm.content"
            type="textarea"
            :rows="8"
            maxlength="500000"
            placeholder="可选：贴入法规完整条文文本，提交后系统将自动按条文（第X条）切分入库"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="docDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="docSubmitting" @click="submitDocForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重新切分弹窗 -->
    <el-dialog
      v-model="rechunkDialogVisible"
      title="重新切分法规全文"
      width="680px"
      append-to-body
      destroy-on-close
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        class="mb16"
        :title="`将清空《${rechunkForm.title}》现有 ${rechunkForm.currentCount} 个切片，并按新文本重新切分生成切片`"
      />
      <el-form ref="rechunkFormRef" :model="rechunkForm" :rules="rechunkRules" label-width="84px">
        <el-form-item label="法规全文" prop="content">
          <el-input
            v-model="rechunkForm.content"
            type="textarea"
            :rows="12"
            maxlength="500000"
            placeholder="请贴入完整法规条文文本（支持按编/章/节与第X条自动识别切分）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechunkDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="rechunkSubmitting" @click="submitRechunk">开始切分</el-button>
      </template>
    </el-dialog>

    <!-- 文件导入法规弹窗 -->
    <el-dialog
      v-model="importDialogVisible"
      title="文件导入法规"
      width="580px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="importFormRef" :model="importForm" :rules="importRules" label-width="92px">
        <el-form-item label="法规文件" required>
          <el-upload
            ref="uploadRef"
            drag
            action="#"
            :auto-upload="false"
            :limit="1"
            accept=".txt,.md,.docx"
            :file-list="importFileList"
            :on-change="handleImportFileChange"
            :on-remove="handleImportFileRemove"
            :on-exceed="handleImportExceed"
            style="width: 100%"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">将文件拖到此处，或 <em>点击选择文件</em></div>
            <template #tip>
              <div class="el-upload__tip">
                仅支持 .txt / .md / .docx 文件，单文件不超过 {{ MAX_FILE_MB }}MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="法规名称" prop="title">
          <el-input
            v-model="importForm.title"
            maxlength="200"
            placeholder="选择文件后将自动回填文件名，可手动修改"
          />
        </el-form-item>
        <el-form-item label="法规分类" prop="category">
          <el-select v-model="importForm.category" placeholder="请选择分类" style="width: 100%">
            <el-option
              v-for="item in KNOWLEDGE_CATEGORIES"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="发文字号" prop="docNumber">
              <el-input v-model="importForm.docNumber" maxlength="100" placeholder="可选" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="施行日期" prop="effectiveDate">
              <el-date-picker
                v-model="importForm.effectiveDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="可选"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="importForm.remark" type="textarea" :rows="2" maxlength="500" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importSubmitting" @click="submitImport">导入并切分</el-button>
      </template>
    </el-dialog>

    <!-- 切片管理抽屉 -->
    <el-drawer
      v-model="chunkDrawerVisible"
      :title="chunkDrawerTitle"
      size="min(960px, 95vw)"
      class="lawyer-surface"
      destroy-on-close
    >
      <div class="knowledge-drawer-body">
        <el-form :inline="true" class="knowledge-chunk-search" @submit.prevent>
          <el-form-item label="关键词">
            <el-input
              v-model="chunkQuery.keyword"
              placeholder="搜索条号或条文内容"
              clearable
              style="width: 240px"
              @keyup.enter="handleChunkQuery"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleChunkQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetChunkQuery">重置</el-button>
            <el-button
              v-hasPermi="['lawyer:knowledge:edit']"
              type="warning"
              plain
              icon="Refresh"
              :loading="rebuildingDoc"
              @click="handleRebuildEmbeddings(currentDoc?.id)"
            >
              重建本法规向量
            </el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="chunkLoading" :data="chunkList" border>
          <el-table-column label="ID" prop="id" width="76" align="center" />
          <el-table-column label="条号" prop="articleNo" width="120" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.articleNo" type="success" effect="plain" size="small">
                {{ row.articleNo }}
              </el-tag>
              <span v-else class="knowledge-muted-text">序言/片段</span>
            </template>
          </el-table-column>
          <el-table-column label="条文正文" prop="content" min-width="320">
            <template #default="{ row }">
              <div class="knowledge-chunk-content">{{ row.content }}</div>
            </template>
          </el-table-column>
          <el-table-column label="章节 / 关键词" prop="keywords" width="180" :show-overflow-tooltip="true">
            <template #default="{ row }">
              <span>{{ row.keywords || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="Token 数" prop="tokenCount" width="90" align="center" />
          <el-table-column label="操作" width="130" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                v-hasPermi="['lawyer:knowledge:edit']"
                link
                type="primary"
                icon="Edit"
                @click="openChunkEditDialog(row)"
              >
                编辑
              </el-button>
              <el-button
                v-hasPermi="['lawyer:knowledge:remove']"
                link
                type="danger"
                icon="Delete"
                @click="handleChunkDelete(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-show="chunkTotal > 0"
          v-model:page="chunkQuery.pageNum"
          v-model:limit="chunkQuery.pageSize"
          :total="chunkTotal"
          @pagination="loadChunks"
        />
      </div>
    </el-drawer>

    <!-- 编辑切片弹窗 -->
    <el-dialog
      v-model="chunkEditVisible"
      title="编辑法条切片"
      width="620px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="chunkEditFormRef" :model="chunkEditForm" :rules="chunkEditRules" label-width="96px">
        <el-form-item label="条号" prop="articleNo">
          <el-input v-model="chunkEditForm.articleNo" maxlength="64" placeholder="如：第四十七条（序言可留空）" />
        </el-form-item>
        <el-form-item label="章节/关键词" prop="keywords">
          <el-input v-model="chunkEditForm.keywords" maxlength="255" placeholder="如：第四章 劳动合同的解除和终止" />
        </el-form-item>
        <el-form-item label="条文正文" prop="content">
          <el-input
            v-model="chunkEditForm.content"
            type="textarea"
            :rows="8"
            placeholder="请输入条文正文"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chunkEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="chunkEditSubmitting" @click="submitChunkEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- RAG 混合检索与多轮改写对比实验室抽屉 -->
    <el-drawer
      v-model="debugDrawerVisible"
      title="RAG 混合检索与多轮改写对比实验室"
      size="min(1160px, 95vw)"
      class="knowledge-debug-drawer lawyer-surface"
      destroy-on-close
    >
      <div class="knowledge-drawer-body">
        <el-form :model="debugForm" label-width="96px" class="knowledge-debug-form" @submit.prevent>
          <el-form-item label="快捷示例">
            <div class="knowledge-debug-presets">
              <el-button
                v-for="preset in DEBUG_PRESETS"
                :key="preset.label"
                size="small"
                plain
                @click="applyDebugPreset(preset)"
              >
                {{ preset.label }}
              </el-button>
              <el-button size="small" text type="info" @click="clearDebugForm">清空输入</el-button>
            </div>
          </el-form-item>

          <el-form-item label="前序上下文">
            <el-input
              v-model="debugForm.historyContext"
              type="textarea"
              :rows="2"
              maxlength="1000"
              show-word-limit
              placeholder="可选：填入上一轮咨询背景或追问上下文，用于测试多轮指代消解与 Query 改写"
            />
          </el-form-item>

          <el-form-item label="当前测试问题">
            <el-input
              v-model="debugForm.query"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              placeholder="输入咨询问题或条文关键词，例如：劳动合同法第四十七条、那赔偿金怎么算？"
            />
          </el-form-item>

          <el-form-item label="检索模式">
            <el-radio-group v-model="debugForm.mode" class="knowledge-mode-group">
              <el-radio-button
                v-for="item in RETRIEVAL_MODES"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="TopK">
            <div class="knowledge-debug-controls">
              <el-input-number v-model="debugForm.topK" :min="1" :max="10" />
              <el-button type="primary" icon="Search" :loading="debugLoading" @click="runSearchDebug">
                执行三路对比检索
              </el-button>
            </div>
          </el-form-item>
        </el-form>

        <!-- 顶部诊断信息栏 -->
        <section v-if="debugSearched && debugReport" class="knowledge-diagnostic-bar">
          <div class="knowledge-diagnostic-tags">
            <span class="knowledge-status-badge" :class="embeddingModeClass">
              向量引擎：{{ formatEmbeddingModeLabel(embeddingStatus.mode) }}
            </span>
            <span class="knowledge-status-badge is-neutral">
              模型：{{ embeddingStatus.model || '未配置' }}
            </span>
            <span v-if="embeddingStatus.dimensions" class="knowledge-status-badge is-neutral">
              维度：{{ embeddingStatus.dimensions }} 维
            </span>
            <span class="knowledge-status-badge is-accent">
              主模式：{{ getRetrievalModeLabel(debugReport.mode || debugForm.mode) }}
            </span>
            <span
              v-if="!embeddingStatus.remoteAvailable && embeddingStatus.lastErrorCode"
              class="knowledge-status-badge is-danger"
              :title="embeddingStatus.lastErrorCode"
            >
              降级原因：{{ embeddingStatus.lastErrorCode }}
            </span>
          </div>

          <!-- 多轮指代改写对比框 -->
          <div v-if="showRewritePanel" class="knowledge-rewrite-box">
            <div class="knowledge-rewrite-header">
              <span class="knowledge-rewrite-title">多轮指代改写（Query Rewrite）</span>
              <div class="knowledge-rewrite-meta">
                <el-tag :type="rewriteStrategyMeta.type" size="small" effect="plain">
                  {{ rewriteStrategyMeta.label }}
                </el-tag>
                <span class="knowledge-status-badge is-neutral">
                  耗时 {{ debugReport.rewriteLatencyMs ?? 0 }} ms
                </span>
              </div>
            </div>
            <div class="knowledge-rewrite-flow">
              <div class="knowledge-rewrite-node">
                <span class="knowledge-rewrite-label">原追问</span>
                <span class="knowledge-rewrite-text">{{ debugReport.originalQuery || debugForm.query }}</span>
              </div>
              <span class="knowledge-rewrite-arrow" aria-hidden="true">→</span>
              <div class="knowledge-rewrite-node is-target">
                <span class="knowledge-rewrite-label">改写后检索词</span>
                <span class="knowledge-rewrite-text">
                  {{ debugReport.rewrittenQuery || debugReport.originalQuery }}
                </span>
              </div>
            </div>
          </div>
        </section>

        <el-empty
          v-if="!debugSearched"
          description="输入问题（可选填入前序追问上下文）并点击「执行三路对比检索」，实时对比 BM25、语义向量与 RRF 混合重排"
        />

        <template v-else-if="debugReport">
          <el-tabs v-model="debugActiveTab" class="knowledge-debug-tabs">
            <el-tab-pane
              :label="`当前模式结果（${activeCitations.length}）`"
              name="active"
            />
            <el-tab-pane
              :label="`三路并列对比（BM25 ${bm25Results.length} / Vector ${vectorResults.length} / RRF ${hybridResults.length}）`"
              name="compare"
            />
          </el-tabs>

          <div
            class="knowledge-compare-grid"
            :class="{ 'is-single': debugActiveTab === 'active' }"
          >
            <section
              v-for="group in visibleResultGroups"
              :key="group.key"
              class="knowledge-compare-column"
            >
              <header v-if="debugActiveTab === 'compare'" class="knowledge-compare-col-header">
                <span class="knowledge-compare-col-title">{{ group.title }}</span>
                <el-tag size="small" :type="group.tagType" effect="plain">
                  {{ group.items.length }} 条
                </el-tag>
              </header>

              <el-empty
                v-if="!debugLoading && group.items.length === 0"
                :image-size="56"
                description="未检索到达到阈值的相关法条"
              />

              <div v-else class="knowledge-debug-list">
                <article
                  v-for="(item, idx) in group.items"
                  :key="`${group.key}-${item.chunkId ?? idx}`"
                  class="knowledge-debug-card"
                >
                  <header class="knowledge-debug-card-header">
                    <div class="knowledge-debug-title">
                      <span class="knowledge-debug-rank">#{{ idx + 1 }}</span>
                      <span>《{{ item.docTitle || '法律法规' }}》{{ item.articleNo || '条文片段' }}</span>
                    </div>
                    <span class="knowledge-muted-text">Chunk #{{ item.chunkId }}</span>
                  </header>

                  <div class="knowledge-debug-metrics">
                    <span
                      v-if="hasScoreValue(item.rrfScore)"
                      class="knowledge-metric-pill is-rrf"
                    >
                      RRF {{ formatDebugScore(item.rrfScore, 4) }}
                    </span>
                    <span
                      class="knowledge-metric-pill"
                      :class="hasRank(item.bm25Rank) || hasScoreValue(item.bm25Score) ? 'is-bm25' : 'is-miss'"
                    >
                      {{ formatChannelMetric('BM25', item.bm25Rank, item.bm25Score, 2) }}
                    </span>
                    <span
                      class="knowledge-metric-pill"
                      :class="hasRank(item.vectorRank) || hasScoreValue(item.vectorScore) ? 'is-vector' : 'is-miss'"
                    >
                      {{ formatChannelMetric('Vector', item.vectorRank, item.vectorScore, 4) }}
                    </span>
                    <span
                      v-if="!hasScoreValue(item.rrfScore) && !hasScoreValue(item.bm25Score) && !hasScoreValue(item.vectorScore) && hasScoreValue(item.score)"
                      class="knowledge-metric-pill is-rrf"
                    >
                      得分 {{ formatDebugScore(item.score, 2) }}
                    </span>
                  </div>

                  <p class="knowledge-debug-snippet">{{ item.snippet }}</p>
                </article>
              </div>
            </section>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup name="LawyerKnowledge">
import { computed, getCurrentInstance, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  addDoc,
  debugSearchKnowledge,
  delChunk,
  delDoc,
  getDoc,
  importDoc,
  listChunk,
  listDoc,
  rebuildKnowledgeEmbeddings,
  rechunkDoc,
  updateChunk,
  updateDoc
} from '@/api/lawyer/knowledge'
import {
  KNOWLEDGE_CATEGORIES,
  KNOWLEDGE_STATUS,
  MAX_FILE_MB,
  RETRIEVAL_MODES,
  getKnowledgeCategoryLabel,
  getRetrievalModeLabel
} from '@/constants/lawyer'

const { proxy } = getCurrentInstance()

const ALLOWED_IMPORT_EXT = ['txt', 'md', 'docx']

const loading = ref(false)
const showSearch = ref(true)
const docList = ref([])
const total = ref(0)
const selectedIds = ref([])
const statusLoadingMap = reactive({})

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  title: undefined,
  category: undefined,
  status: undefined
})

// 新增/编辑法规弹窗
const docDialogVisible = ref(false)
const docSubmitting = ref(false)
const docFormRef = ref(null)
const docForm = reactive({
  id: undefined,
  title: '',
  category: 'labor',
  docNumber: '',
  effectiveDate: '',
  status: '0',
  remark: '',
  content: ''
})

const docDialogTitle = computed(() => (docForm.id ? '编辑法规元数据' : '新增法规'))

const docRules = {
  title: [{ required: true, message: '请输入法规名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择法规分类', trigger: 'change' }]
}

// 重新切分弹窗
const rechunkDialogVisible = ref(false)
const rechunkSubmitting = ref(false)
const rechunkFormRef = ref(null)
const rechunkForm = reactive({
  id: undefined,
  title: '',
  currentCount: 0,
  content: ''
})

const rechunkRules = {
  content: [{ required: true, message: '请贴入完整法规文本', trigger: 'blur' }]
}

// 导入弹窗
const importDialogVisible = ref(false)
const importSubmitting = ref(false)
const importFormRef = ref(null)
const uploadRef = ref(null)
const importFile = ref(null)
const importFileList = ref([])
const importForm = reactive({
  title: '',
  category: 'labor',
  docNumber: '',
  effectiveDate: '',
  remark: ''
})

const importRules = {
  title: [{ required: true, message: '请输入法规名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择法规分类', trigger: 'change' }]
}

// 切片管理抽屉
const chunkDrawerVisible = ref(false)
const currentDoc = ref(null)
const chunkLoading = ref(false)
const chunkList = ref([])
const chunkTotal = ref(0)
const chunkQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: ''
})

const chunkDrawerTitle = computed(() => {
  if (!currentDoc.value) return '切片管理'
  return `切片管理 - 《${currentDoc.value.title}》（共 ${chunkTotal.value} 条）`
})

// 编辑切片弹窗
const chunkEditVisible = ref(false)
const chunkEditSubmitting = ref(false)
const chunkEditFormRef = ref(null)
const chunkEditForm = reactive({
  id: undefined,
  articleNo: '',
  keywords: '',
  content: ''
})

const chunkEditRules = {
  content: [{ required: true, message: '请输入条文正文', trigger: 'blur' }]
}

// 向量索引重建状态
const rebuildingAll = ref(false)
const rebuildingDoc = ref(false)

// RAG 混合检索与多轮改写对比实验室
const DEBUG_PRESETS = [
  {
    label: '示例1：辞退赔偿（多轮指代）',
    historyContext: '前序问题：公司没提前通知直接把我辞退了，我干了三年半。',
    query: '那赔偿金怎么算？'
  },
  {
    label: '示例2：退还押金（多轮指代）',
    historyContext: '前序问题：租房合同到期了房东验收完房子。',
    query: '如果他一直拖着不退押金怎么办？'
  }
]

const REWRITE_STRATEGY_META = {
  llm: { label: 'LLM 语义改写', type: 'success' },
  rule_fallback: { label: '规则实体兜底', type: 'warning' },
  passthrough: { label: '直通未改写', type: 'info' }
}

const debugDrawerVisible = ref(false)
const debugLoading = ref(false)
const debugSearched = ref(false)
const debugReport = ref(null)
const debugActiveTab = ref('compare')
const lastDebugHistoryContext = ref('')
const debugForm = reactive({
  query: '',
  topK: 4,
  mode: 'hybrid_rrf',
  historyContext: ''
})

const embeddingStatus = computed(() => debugReport.value?.embeddingStatus || {})

const embeddingModeClass = computed(() => {
  const mode = embeddingStatus.value.mode
  if (mode === 'local_fallback') return 'is-warning'
  if (embeddingStatus.value.remoteAvailable === false) return 'is-warning'
  return 'is-success'
})

const showRewritePanel = computed(() => {
  if (!debugReport.value) return false
  return Boolean(
    debugReport.value.rewritten ||
    lastDebugHistoryContext.value ||
    (debugReport.value.rewriteStrategy && debugReport.value.rewriteStrategy !== 'passthrough')
  )
})

const rewriteStrategyMeta = computed(() => {
  const strategy = debugReport.value?.rewriteStrategy || 'passthrough'
  return REWRITE_STRATEGY_META[strategy] || { label: strategy, type: 'info' }
})

const activeCitations = computed(() => (
  Array.isArray(debugReport.value?.citations) ? debugReport.value.citations : []
))
const bm25Results = computed(() => (
  Array.isArray(debugReport.value?.bm25Results) ? debugReport.value.bm25Results : []
))
const vectorResults = computed(() => (
  Array.isArray(debugReport.value?.vectorResults) ? debugReport.value.vectorResults : []
))
const hybridResults = computed(() => (
  Array.isArray(debugReport.value?.hybridResults) ? debugReport.value.hybridResults : []
))

const visibleResultGroups = computed(() => {
  if (debugActiveTab.value === 'active') {
    return [
      {
        key: 'active',
        title: getRetrievalModeLabel(debugReport.value?.mode || debugForm.mode),
        tagType: 'primary',
        items: activeCitations.value
      }
    ]
  }
  return [
    { key: 'bm25', title: '纯 BM25 关键词', tagType: 'info', items: bm25Results.value },
    { key: 'vector', title: '纯语义向量 (Vector)', tagType: 'warning', items: vectorResults.value },
    { key: 'hybrid', title: 'RRF 混合重排', tagType: 'success', items: hybridResults.value }
  ]
})

function buildQuery() {
  return {
    pageNum: queryParams.pageNum,
    pageSize: queryParams.pageSize,
    title: queryParams.title || undefined,
    category: queryParams.category || undefined,
    status: queryParams.status !== undefined && queryParams.status !== '' ? queryParams.status : undefined
  }
}

async function getList() {
  loading.value = true
  try {
    const res = await listDoc(buildQuery())
    docList.value = res.rows || []
    total.value = res.total || 0
  } catch {
    // 异常由 request 拦截器统一提示
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.title = undefined
  queryParams.category = undefined
  queryParams.status = undefined
  handleQuery()
}

function handleSelectionChange(selection) {
  selectedIds.value = selection.map(item => item.id)
}

function resetDocForm() {
  docForm.id = undefined
  docForm.title = ''
  docForm.category = 'labor'
  docForm.docNumber = ''
  docForm.effectiveDate = ''
  docForm.status = '0'
  docForm.remark = ''
  docForm.content = ''
}

function handleAdd() {
  resetDocForm()
  docDialogVisible.value = true
}

async function handleEdit(row) {
  resetDocForm()
  try {
    const res = await getDoc(row.id)
    const detail = res?.data || res || row
    docForm.id = detail.id
    docForm.title = detail.title || ''
    docForm.category = detail.category || 'other'
    docForm.docNumber = detail.docNumber || ''
    docForm.effectiveDate = detail.effectiveDate || ''
    docForm.status = detail.status ?? '0'
    docForm.remark = detail.remark || ''
    docDialogVisible.value = true
  } catch {
    // 异常由拦截器处理
  }
}

async function submitDocForm() {
  if (!docFormRef.value) return
  const valid = await docFormRef.value.validate().catch(() => false)
  if (!valid) return

  docSubmitting.value = true
  try {
    if (docForm.id) {
      await updateDoc(docForm.id, {
        title: docForm.title.trim(),
        category: docForm.category,
        docNumber: docForm.docNumber.trim() || undefined,
        effectiveDate: docForm.effectiveDate || undefined,
        status: docForm.status,
        remark: docForm.remark.trim() || undefined
      })
      ElMessage.success('法规元数据已更新')
    } else {
      const res = await addDoc({
        title: docForm.title.trim(),
        category: docForm.category,
        docNumber: docForm.docNumber.trim() || undefined,
        effectiveDate: docForm.effectiveDate || undefined,
        status: docForm.status,
        remark: docForm.remark.trim() || undefined,
        content: docForm.content.trim() || undefined
      })
      const count = res?.data?.chunkCount ?? res?.chunkCount ?? 0
      ElMessage.success(count > 0 ? `新增成功，已自动生成 ${count} 个切片` : '新增法规成功')
    }
    docDialogVisible.value = false
    getList()
  } catch {
    // 异常由拦截器处理
  } finally {
    docSubmitting.value = false
  }
}

async function handleStatusChange(row, newStatus) {
  const prevStatus = newStatus === '0' ? '1' : '0'
  statusLoadingMap[row.id] = true
  try {
    await updateDoc(row.id, {
      title: row.title,
      category: row.category,
      docNumber: row.docNumber,
      effectiveDate: row.effectiveDate,
      status: newStatus,
      remark: row.remark
    })
    ElMessage.success(newStatus === '0' ? '已启用该法规' : '已停用该法规')
  } catch {
    row.status = prevStatus
  } finally {
    statusLoadingMap[row.id] = false
  }
}

function openRechunkDialog(row) {
  rechunkForm.id = row.id
  rechunkForm.title = row.title
  rechunkForm.currentCount = row.chunkCount ?? 0
  rechunkForm.content = ''
  rechunkDialogVisible.value = true
}

async function submitRechunk() {
  if (!rechunkFormRef.value) return
  const valid = await rechunkFormRef.value.validate().catch(() => false)
  if (!valid) return

  rechunkSubmitting.value = true
  try {
    const res = await rechunkDoc(rechunkForm.id, rechunkForm.content)
    const count = res?.data?.chunkCount ?? res?.chunkCount ?? 0
    ElMessage.success(`重新切分完成，共生成 ${count} 个切片`)
    rechunkDialogVisible.value = false
    getList()
  } catch {
    // 异常由拦截器处理
  } finally {
    rechunkSubmitting.value = false
  }
}

async function confirmAndDelete(ids, label) {
  try {
    await ElMessageBox.confirm(
      `确认删除${label}及其全部法条切片吗？删除后不可恢复。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消'
      }
    )
    await delDoc(ids)
    ElMessage.success('删除成功')
    if (docList.value.length === 1 && queryParams.pageNum > 1) {
      queryParams.pageNum -= 1
    }
    getList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      // 接口异常由拦截器处理
    }
  }
}

function handleDelete(row) {
  confirmAndDelete(row.id, `法规《${row.title}》`)
}

function handleBatchDelete() {
  if (selectedIds.value.length === 0) return
  confirmAndDelete(selectedIds.value.join(','), `选中的 ${selectedIds.value.length} 部法规`)
}

function openImportDialog() {
  importFile.value = null
  importFileList.value = []
  importForm.title = ''
  importForm.category = 'labor'
  importForm.docNumber = ''
  importForm.effectiveDate = ''
  importForm.remark = ''
  importDialogVisible.value = true
}

function validateImportFile(rawFile) {
  if (!rawFile) return false
  const ext = (rawFile.name.split('.').pop() || '').toLowerCase()
  if (!ALLOWED_IMPORT_EXT.includes(ext)) {
    ElMessage.error('仅支持 .txt、.md、.docx 格式文件')
    return false
  }
  if (rawFile.size > MAX_FILE_MB * 1024 * 1024) {
    ElMessage.error(`文件大小不能超过 ${MAX_FILE_MB}MB`)
    return false
  }
  return true
}

function handleImportFileChange(file, fileList) {
  const raw = file?.raw
  if (!validateImportFile(raw)) {
    importFile.value = null
    importFileList.value = []
    return
  }
  importFile.value = raw
  importFileList.value = fileList.slice(-1)
  if (!importForm.title.trim()) {
    importForm.title = raw.name.replace(/\.[^.]+$/, '')
  }
}

function handleImportFileRemove() {
  importFile.value = null
  importFileList.value = []
}

function handleImportExceed(files) {
  const raw = files[0]
  if (!validateImportFile(raw)) return
  uploadRef.value?.clearFiles()
  uploadRef.value?.handleStart(raw)
}

async function submitImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择需要导入的法规文件')
    return
  }
  if (!importFormRef.value) return
  const valid = await importFormRef.value.validate().catch(() => false)
  if (!valid) return

  const formData = new FormData()
  formData.append('file', importFile.value)
  formData.append('title', importForm.title.trim())
  formData.append('category', importForm.category)
  if (importForm.docNumber.trim()) {
    formData.append('docNumber', importForm.docNumber.trim())
  }
  if (importForm.effectiveDate) {
    formData.append('effectiveDate', importForm.effectiveDate)
  }
  if (importForm.remark.trim()) {
    formData.append('remark', importForm.remark.trim())
  }

  importSubmitting.value = true
  try {
    const res = await importDoc(formData)
    const count = res?.data?.chunkCount ?? res?.chunkCount ?? 0
    ElMessage.success(`导入成功，已切分生成 ${count} 个切片`)
    importDialogVisible.value = false
    getList()
  } catch {
    // 异常由拦截器处理
  } finally {
    importSubmitting.value = false
  }
}

async function openChunkDrawer(row) {
  currentDoc.value = row
  chunkQuery.pageNum = 1
  chunkQuery.keyword = ''
  chunkDrawerVisible.value = true
  await loadChunks()
}

async function loadChunks() {
  if (!currentDoc.value) return
  chunkLoading.value = true
  try {
    const res = await listChunk(currentDoc.value.id, {
      pageNum: chunkQuery.pageNum,
      pageSize: chunkQuery.pageSize,
      keyword: chunkQuery.keyword.trim() || undefined
    })
    chunkList.value = res.rows || []
    chunkTotal.value = res.total || 0
  } catch {
    chunkList.value = []
  } finally {
    chunkLoading.value = false
  }
}

function handleChunkQuery() {
  chunkQuery.pageNum = 1
  loadChunks()
}

function resetChunkQuery() {
  chunkQuery.keyword = ''
  handleChunkQuery()
}

function openChunkEditDialog(row) {
  chunkEditForm.id = row.id
  chunkEditForm.articleNo = row.articleNo || ''
  chunkEditForm.keywords = row.keywords || ''
  chunkEditForm.content = row.content || ''
  chunkEditVisible.value = true
}

async function submitChunkEdit() {
  if (!chunkEditFormRef.value || !currentDoc.value) return
  const valid = await chunkEditFormRef.value.validate().catch(() => false)
  if (!valid) return

  chunkEditSubmitting.value = true
  try {
    await updateChunk(currentDoc.value.id, chunkEditForm.id, {
      articleNo: chunkEditForm.articleNo.trim() || null,
      keywords: chunkEditForm.keywords.trim() || null,
      content: chunkEditForm.content.trim()
    })
    ElMessage.success('切片已更新')
    chunkEditVisible.value = false
    loadChunks()
  } catch {
    // 异常由拦截器处理
  } finally {
    chunkEditSubmitting.value = false
  }
}

async function handleChunkDelete(row) {
  if (!currentDoc.value) return
  try {
    await ElMessageBox.confirm(
      `确认删除切片 ${row.articleNo || '#' + row.id} 吗？删除后不可恢复。`,
      '删除切片',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消'
      }
    )
    await delChunk(currentDoc.value.id, row.id)
    ElMessage.success('切片已删除')
    if (chunkList.value.length === 1 && chunkQuery.pageNum > 1) {
      chunkQuery.pageNum -= 1
    }
    await loadChunks()
    getList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      // 接口异常由拦截器处理
    }
  }
}

function formatEmbeddingModeLabel(mode) {
  if (mode === 'local_fallback') return '本地降级 (local_fallback)'
  if (mode === 'remote' || mode === 'http') return `远程向量 (${mode})`
  return mode || '未知模式'
}

function formatEmbeddingRebuildSummary(payload, docTitle) {
  const updated = payload?.updatedChunks ?? 0
  const totalCount = payload?.totalChunks ?? 0
  const skipped = payload?.skippedChunks ?? 0
  const model = payload?.model || '未知模型'
  const mode = formatEmbeddingModeLabel(payload?.embeddingMode)
  const duration = payload?.durationMs !== undefined && payload?.durationMs !== null
    ? `${payload.durationMs} ms`
    : '-'
  const scopeLabel = docTitle ? `《${docTitle}》` : '全部启用法规'

  return [
    `重建范围：${scopeLabel}`,
    `切片更新：${updated} / ${totalCount}（跳过 ${skipped}）`,
    `当前模型：${model}`,
    `向量模式：${mode}`,
    `执行耗时：${duration}`
  ].join('\n')
}

async function handleRebuildEmbeddings(docId) {
  const isSingleDoc = docId !== undefined && docId !== null
  const loadingRef = isSingleDoc ? rebuildingDoc : rebuildingAll
  loadingRef.value = true
  try {
    const res = await rebuildKnowledgeEmbeddings(isSingleDoc ? docId : undefined)
    const payload = res?.data && !Array.isArray(res.data) ? res.data : (res || {})
    const summaryText = formatEmbeddingRebuildSummary(
      payload,
      isSingleDoc ? currentDoc.value?.title : ''
    )
    await ElMessageBox.alert(summaryText, '向量索引重建完成', {
      type: payload?.embeddingMode === 'local_fallback' ? 'warning' : 'success',
      confirmButtonText: '确定',
      customClass: 'knowledge-rebuild-alert'
    })
  } catch {
    // 异常由拦截器处理
  } finally {
    loadingRef.value = false
  }
}

function openDebugDrawer() {
  debugDrawerVisible.value = true
}

function applyDebugPreset(preset) {
  debugForm.historyContext = preset.historyContext
  debugForm.query = preset.query
}

function clearDebugForm() {
  debugForm.historyContext = ''
  debugForm.query = ''
}

function hasScoreValue(val) {
  return typeof val === 'number' && !Number.isNaN(val)
}

function hasRank(rank) {
  return typeof rank === 'number' && !Number.isNaN(rank) && rank > 0
}

function formatDebugScore(score, digits = 2) {
  return hasScoreValue(score) ? Number(score).toFixed(digits) : Number(0).toFixed(digits)
}

function formatChannelMetric(channel, rank, score, digits) {
  if (!hasRank(rank) && !hasScoreValue(score)) {
    return `${channel} 未进候选`
  }
  const rankPart = hasRank(rank) ? `#${rank}` : '#-'
  const scorePart = hasScoreValue(score) ? formatDebugScore(score, digits) : '-'
  return `${channel} ${rankPart} / ${scorePart}`
}

async function runSearchDebug() {
  const query = debugForm.query.trim()
  if (!query) {
    ElMessage.warning('请输入测试问题或法条关键词')
    return
  }
  const historyContext = debugForm.historyContext.trim()
  lastDebugHistoryContext.value = historyContext
  debugLoading.value = true
  debugSearched.value = true
  try {
    const res = await debugSearchKnowledge({
      query,
      topK: debugForm.topK,
      mode: debugForm.mode,
      historyContext
    })
    const payload = res?.data && !Array.isArray(res.data) ? res.data : (res || null)
    debugReport.value = payload
  } catch {
    debugReport.value = null
  } finally {
    debugLoading.value = false
  }
}

onMounted(() => {
  getList()
})
</script>
