# AI 律师助手 V2.0

面向中国大陆个人用户的轻量法律咨询平台。后端基于 RuoYi-Vue 3.9.2（Spring Boot 4.1.2 / Java 17+），前端基于 Vue 3 + Element Plus，提供 **智能法律咨询、法律知识库 RAG、合同风险审查、法律文书起草** 四项核心能力。

V2.0 在 V1.0 基础上完成 **M5 ~ M7** 三大升级：BM25 + 语义向量 RRF 混合检索与多轮指代 Query Rewrite、ReAct Tool Calling 法律 Agent 与工具异常自愈、Token / TTFT / RAG 命中率 / 工具成功率可观测性与 30 + 15 题离线量化基准评测。

> 本项目仅提供法律信息与文书辅助，不构成执业律师出具的正式法律意见。

## 核心能力（M1 ~ M7）

| 模块 | 用户入口 | 关键能力 |
| :--- | :--- | :--- |
| **M1 智能法律咨询** | 前台「智能咨询」`/lawyer/chat` | 多会话管理、历史消息、SSE 流式回答（`meta → citation → delta → done`）、`requestId` 幂等与断线回放（`replay:true`）、Redis 会话锁并发保护（`409`）、失败消息收敛、后台会话与消息审计 |
| **M2 法律知识库 RAG（V1 基线）** | 对话内自动检索；后台「法律知识库」`/lawyer/knowledge` | 内置 5 份法规语料首次启动自动导入（劳动合同法 + 民法典合同/租赁/借贷/侵权，共 144 个条文切片）；BM25 + bigram + 条号精确匹配的轻量检索；回答携带法条引用与溯源片段；后台支持法规导入、切片维护、重切与检索调试 |
| **M3 合同风险审查 + 私有文件** | 前台「合同风险审查」`/lawyer/contract` | `.docx/.pdf/.txt/.md` 上传（大小与类型白名单校验）、文本抽取、按立场（甲方/乙方/中立）异步审查、风险等级与逐条风险/依据/修改建议、审查历史；私有文件独立落盘 `./data/lawyer-files/{userId}/`，不暴露在 `/profile/**`，下载强制归属鉴权 |
| **M4 法律文书起草 + DOCX 导出** | 前台「法律文书起草」`/lawyer/document` | 4 套内置模板（劳动仲裁申请书、律师催款函、规范借条、房屋租赁解约通知书）、动态表单必填/格式校验（`422` 并返回字段名）、AI 生成草稿、草稿编辑/列表/详情、标准公文 DOCX 导出（Apache POI，OOXML） |
| **M5 混合检索 + 多轮指代改写** | 对话内自动检索；后台「法律知识库」检索调试抽屉 | BM25 + 语义向量双路召回，RRF（k=60）倒数排名融合；精确条号直通加权；多轮追问的 Query Rewrite（规则实体补全优先、LLM 次之、失败回退原文）；`embedding_json` / `embedding_model` 持久化与一键重建；后台三路（BM25 / Vector / Hybrid）结果对比 |
| **M6 ReAct Tool Calling 法律 Agent** | 前台「智能咨询」`/lawyer/chat` | 模型自主决定工具调用，内置 `search_law_articles`、`calc_legal_amount`、`create_document_draft` 三大工具；确定性金额/利息/时效计算（`BigDecimal + LocalDate`）；工具参数畸形、字段缺失、未知工具均结构化回传并由模型自愈；`max-iterations=4` 熔断后移除 tools 完成兜底回答；SSE 新增 `tool_call` / `tool_result` 与工具时间线回放 |
| **M7 可观测性 + 离线量化评测** | 后台「会话管理」`/lawyer/session` 指标看板 | 聚合 `totalRequests` / `totalTokens` / `avgTtftMs` / `p95TtftMs` / `ragHitRate` / `toolCallTotal` / `toolCallSuccessRate` / `toolBreakdown`；会话行展示 Token / TTFT / 工具调用数；审计抽屉回放工具轨迹；内置 30 题 RAG 与 15 题 Agent 离线评测集，自动生成量化报告 |

后台管理：

- **会话管理**：分页查看全量咨询会话、消息数/失败数/Token/TTFT/工具调用统计，逐条消息审计、工具轨迹回放与删除。
- **知识库管理**：法规分页、全文检索、导入、切片查看/编辑/删除、重新切片、向量重建与三路检索调试。

## 技术栈

- 后端：Spring Boot 4.1.2、Spring Security + JWT、MyBatis、Druid、Redis、Apache POI、FastJSON2、Jackson
- 前端：Vue 3.5、Vite 6、Element Plus 2.13、Pinia、Vue Router 4
- 数据库：MySQL 8（`utf8mb4`）；缓存/会话锁：Redis
- 模型接入：任意 OpenAI 兼容 Chat Completions 服务，支持 SSE 流式输出与 Tool Calling
- 向量检索：内置 `InMemoryVectorSearchEngine`（余弦相似度）与 `LocalSemanticEmbeddingEncoder` 确定性降级；可切换外部 Embedding 网关

```
ai-lawyer-agent/
├── ruoyi-admin/          # 启动模块、REST 控制器（含 lawyer/*）
├── ruoyi-lawyer/         # 律师业务：聊天、Agent、知识库/混合检索、合同审查、文书、DOCX、私有文件
├── ruoyi-common/         # 通用工具与基础能力
├── ruoyi-framework/      # 安全、JWT、数据源等框架配置
├── ruoyi-system/         # 系统管理
├── ruoyi-ui/             # Vue 3 前端
├── sql/                  # 建表、菜单与增量升级脚本
└── doc/                  # 设计与验收文档
```

## 快速开始

### 1. 环境要求

- JDK 17 及以上（已在 JDK 21 验证）
- Maven 3.9+
- MySQL 8.0+
- Redis 6.0+
- Node.js 18+（前端）

### 2. 数据库初始化

**全新数据库**按顺序执行：

```bash
mysql -uroot -p ry-vue < sql/ry_20260417.sql
mysql -uroot -p ry-vue < sql/lawyer.sql
mysql -uroot -p ry-vue < sql/lawyer_message.sql
mysql -uroot -p ry-vue < sql/lawyer_menu.sql
mysql -uroot -p ry-vue < sql/lawyer_v1_upgrade.sql
mysql -uroot -p ry-vue < sql/lawyer_v2_upgrade.sql
```

**已部署 V1.0 的旧库**只需执行 V2.0 增量脚本：

```bash
mysql -uroot -p ry-vue < sql/lawyer_v2_upgrade.sql
```

`sql/lawyer_v2_upgrade.sql` 幂等，可重复执行，不包含 `DROP TABLE`、`TRUNCATE` 或业务数据清理，主要包含：

1. `law_knowledge_chunk` 增加 `embedding_json`、`embedding_model`（M5 向量持久化）；
2. `law_chat_message` 增加 `rewritten_query`、`retrieval_mode`、`tool_calls_json`、`prompt_tokens`、`completion_tokens`、`total_tokens`、`ttft_ms`、`total_latency_ms`（M5 / M6 / M7）；
3. 新增联合索引 `idx_law_chat_message_role_time (role, create_time)`，供可观测性时间窗口聚合。

首次启动且 `law_knowledge_doc` 为空时，`KnowledgeSeedInitializer` 会自动导入 `ruoyi-lawyer/src/main/resources/knowledge/seed/` 下的 5 份内置法规（共 144 个切片）。如需关闭自动导入，设置 `lawyer.knowledge.seed-enabled=false`。

升级后建议调用一次向量重建接口，为存量 144 条切片回写向量：

```bash
curl -X POST http://localhost:8989/lawyer/knowledge/docs/rebuild-embeddings \
  -H "Authorization: Bearer <token>"
```

> 历史库如需清理若依演示菜单，可另行执行 `sql/upgrade_trim_ruoyi.sql`、`sql/upgrade_trim_admin_ui.sql`、`sql/upgrade_user_home_permissions.sql`；新库无需执行。

### 3. 环境变量

后端配置位于 `ruoyi-admin/src/main/resources/application.yml` 与 `application-druid.yml`。生产环境请通过环境变量覆盖，不要把密钥写入仓库：

| 变量 | 说明 | 默认/示例 |
| :--- | :--- | :--- |
| `LAWYER_AI_BASE_URL` | OpenAI 兼容服务地址（需带版本段） | `https://your-host/v1` |
| `LAWYER_AI_API_KEY` | 模型服务密钥 | 必填 |
| `LAWYER_AI_MODEL` | 模型名 | `global:deepseek-v4.1-flash-sg` |
| `LAWYER_AI_CONNECT_TIMEOUT` | 建连超时（毫秒） | `5000` |
| `LAWYER_AI_REQUEST_TIMEOUT` | 同步请求超时（毫秒） | `60000` |
| `LAWYER_AI_STREAM_TIMEOUT` | 流式总超时（毫秒） | `100000` |
| `LAWYER_AI_LONG_REQUEST_TIMEOUT` | 合同审查/文书生成超时（毫秒） | `120000` |
| `LAWYER_AI_EMBEDDING_ENABLED` | 是否启用远端 Embedding（失败自动降级） | `true` |
| `LAWYER_AI_EMBEDDING_BASE_URL` | 独立 Embedding 网关（留空复用主网关） | 空 |
| `LAWYER_AI_EMBEDDING_API_KEY` | 独立 Embedding 密钥（留空复用主密钥） | 空 |
| `LAWYER_AI_EMBEDDING_MODEL` | Embedding 模型名 | `text-embedding-3-small` |
| `LAWYER_AI_EMBEDDING_DIMENSIONS` | 向量维度 / 本地降级维度 | `256` |
| `LAWYER_KNOWLEDGE_MODE` | 默认检索模式 `bm25` / `vector` / `hybrid_rrf` | `hybrid_rrf` |
| `LAWYER_KNOWLEDGE_RRF_K` | RRF 融合平滑常数 | `60` |
| `LAWYER_KNOWLEDGE_REWRITE_ENABLED` | 是否启用多轮指代 Query Rewrite | `true` |
| `LAWYER_KNOWLEDGE_REWRITE_TIMEOUT` | LLM 改写超时（毫秒，超时降级规则补全） | `8000` |
| `LAWYER_AGENT_ENABLED` | 是否启用 ReAct 法律 Agent | `true` |
| `LAWYER_AGENT_MAX_ITERATIONS` | Tool Calling 最大迭代轮数 | `4` |
| `LAWYER_AGENT_TOOL_TIMEOUT_SECONDS` | 单工具执行超时（秒） | `30` |
| `LAWYER_STORAGE_ROOT` | 私有文件根目录 | `./data/lawyer-files` |
| `RUOYI_PROFILE` | 若依通用上传目录 | `./data/uploadPath` |

数据库与 Redis 连接在 `application-druid.yml` / `application.yml` 中配置，部署时请替换为实际地址和强密码。Redis 同时用于登录令牌、配置缓存、验证码与会话生成锁。

### 4. 启动后端

```bash
# 离线构建（跳过测试）
mvn -o -pl ruoyi-admin -am -DskipTests package

# 启动，默认端口 8989
java -jar ruoyi-admin/target/ruoyi-admin.jar

# 开发环境可启用 Swagger
java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=druid,dev
```

健康检查：`GET http://localhost:8989/captchaImage`。

### 5. 启动前端

```bash
cd ruoyi-ui
npm install
npm run dev          # 默认 http://localhost:80，/dev-api 代理到 http://localhost:8989
npm run build:prod   # 生产构建，输出 dist/
```

前端开发代理配置见 `ruoyi-ui/vite.config.js`，如后端地址不同请同步修改 `baseUrl`。

## 接口一览

### V1.0 基础接口

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| `POST` | `/lawyer/sessions` | 新建咨询会话 | `lawyer:session:add` |
| `GET` | `/lawyer/sessions` | 本人会话分页 | `lawyer:session:list` |
| `DELETE` | `/lawyer/sessions/{id}` | 删除本人会话 | `lawyer:session:remove` |
| `GET` | `/lawyer/sessions/{id}/messages` | 历史消息 | `lawyer:chat:query` |
| `POST` | `/lawyer/sessions/{id}/messages` | 同步问答（降级） | `lawyer:chat:send` |
| `POST` | `/lawyer/sessions/{id}/messages/stream` | SSE 流式问答（V2 扩展工具事件） | `lawyer:chat:send` |
| `GET` | `/lawyer/knowledge/docs` | 知识库法规分页 | `lawyer:knowledge:list` |
| `GET` | `/lawyer/knowledge/docs/search?q=` | 法条检索（支持 `mode` / `topK`） | `lawyer:knowledge:query` |
| `POST` | `/lawyer/files/upload` | 上传私有文件 | `lawyer:file:access` |
| `GET` | `/lawyer/files/{id}/download` | 鉴权下载私有文件 | `lawyer:file:access` |
| `POST` | `/lawyer/contracts/reviews` | 发起合同审查 | `lawyer:contract:add` |
| `GET` | `/lawyer/contracts/reviews` | 本人审查记录 | `lawyer:contract:list` |
| `GET` | `/lawyer/contracts/reviews/{id}` | 审查详情 | `lawyer:contract:query` |
| `GET` | `/lawyer/documents/templates` | 文书模板列表 | `lawyer:document:list` |
| `POST` | `/lawyer/documents/drafts` | 创建文书草稿 | `lawyer:document:add` |
| `PUT` | `/lawyer/documents/drafts/{id}` | 编辑草稿 | `lawyer:document:edit` |
| `GET` | `/lawyer/documents/drafts/{id}/export-docx` | 导出标准 DOCX | `lawyer:document:export` |
| `GET` | `/lawyer/admin/sessions` | 后台会话审计（V2 扩展指标列） | `lawyer:manage:list` |
| `GET` | `/lawyer/admin/sessions/{id}/messages` | 后台消息审计 | `lawyer:manage:query` |

### V2.0（M5 ~ M7）新增 / 扩展接口

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| `POST` | `/lawyer/knowledge/docs/search-debug` | 三路检索对比 + Query Rewrite 透出 | `lawyer:knowledge:query` |
| `POST` | `/lawyer/knowledge/docs/rebuild-embeddings` | 重建切片向量并回写 `embedding_json` / `embedding_model`（可选 `docId`） | `lawyer:knowledge:edit` |
| `GET` | `/lawyer/knowledge/docs/search` | 兼容 V1，新增 `mode=bm25\|vector\|hybrid_rrf` | `lawyer:knowledge:query` |
| `GET` | `/lawyer/admin/sessions/observability` | Token / TTFT / RAG / Tool 聚合统计（可选 `beginTime` / `endTime`） | `lawyer:manage:list` |
| `GET` | `/lawyer/admin/sessions` | 会话行新增 `totalTokens` / `avgTtftMs` / `toolCallCount` | `lawyer:manage:list` |

SSE 事件约定（V2 向后兼容 V1）：

- `meta`：`requestId`、`sessionId`、用户/助手消息 ID、`replay`、`rewrittenQuery`、`retrievalMode`；
- `citation`：本次检索命中的法条引用列表（含法规名、条号、片段、BM25/向量/RRF 得分）；
- `tool_call`（新增）：`callId`、`step`、`toolName`、`displayTitle`、`arguments`；
- `tool_result`（新增）：与 `tool_call` 一一配对，含 `status`（`success`/`error`）、`summary`、`detail`、`durationMs`、`draftId`；
- `delta`：增量正文；
- `done`：最终状态、消息 ID、模型名、完整正文、引用、`toolCalls`、`promptTokens`/`completionTokens`/`totalTokens`、`ttftMs`、`totalLatencyMs`；
- `error`：可读错误码与提示。

## 构建与测试

```bash
# 全量离线构建（跳过测试）
mvn -o -pl ruoyi-admin -am -DskipTests package

# 律师模块全量单元测试（161 项，含 30 题 RAG 与 15 题 Agent 离线评测）
mvn -o -pl ruoyi-lawyer -am test -DskipTests=false
```

V2.0 验收结果：离线构建成功，**161 项单元测试全部通过（0 失败、0 错误、0 跳过）**；离线评测中 **RAG 30/30、Agent 15/15（12/12 项确定性数值断言零误差）全部通过**。关键量化结论：

- `Hybrid RRF + Query Rewrite` 在 30 题上 Recall@5 **90.00%**、MRR@5 **0.7233**，较纯 `BM25 (无 Rewrite)` 的 60.00% / 0.5483 分别提升 **30.00 个百分点 / 0.1750**；
- 精确条号场景五组实验 Hit@1 均 **100.00%**；
- 2N 违法解除赔偿金工具计算实测 **¥80,000.00**，与确定性基准题一致。

完整评测数据见 [`doc/V2.0_BENCHMARK_REPORT.md`](doc/V2.0_BENCHMARK_REPORT.md)，构建、单测、数据库扩列与 M5~M7 端到端冒烟实测证据见 [`doc/V2.0_ACCEPTANCE_REPORT.md`](doc/V2.0_ACCEPTANCE_REPORT.md)。

## 文档

- [V2.0 验收报告](doc/V2.0_ACCEPTANCE_REPORT.md)
- [V2.0 量化基准评测报告](doc/V2.0_BENCHMARK_REPORT.md)
- [V2.0 架构与契约总规划](PLAN_V2.md)
- [V1.0 验收报告](doc/V1.0_ACCEPTANCE_REPORT.md)
- [AI 律师助手端到端跑通计划](doc/AI律师助手端到端跑通计划.md)
- [AI 律师助手雏形设计方案](doc/AI律师助手雏形设计方案.md)
- [阶段 0 环境诊断](doc/PHASE0_DIAGNOSIS.md)

## 致谢与许可

本项目基于 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue) 二次开发，遵循 MIT License。感谢若依开源社区。
