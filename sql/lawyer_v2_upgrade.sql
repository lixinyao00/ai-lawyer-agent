-- ============================================================================
-- AI 律师助手 V2.0 增量升级脚本（M5 ~ M7 全量）
-- 幂等：可重复执行任意次；不含 DROP TABLE / TRUNCATE / 无条件 DELETE
-- 执行顺序：sql/lawyer.sql -> sql/lawyer_message.sql -> sql/lawyer_menu.sql
--           -> sql/lawyer_v1_upgrade.sql -> sql/lawyer_v2_upgrade.sql
--
-- 说明：MySQL 8 无 ADD COLUMN IF NOT EXISTS，统一使用
--       information_schema.COLUMNS / information_schema.STATISTICS
--       + PREPARE / EXECUTE / DEALLOCATE 模式保证 100% 幂等。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- ① law_knowledge_chunk：切片向量持久化（M5）
-- ----------------------------------------------------------------------------

-- embedding_json：切片归一化浮点向量 JSON 数组；未构建时为 NULL
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE law_knowledge_chunk ADD COLUMN embedding_json LONGTEXT NULL COMMENT ''切片归一化浮点向量JSON数组'' AFTER token_count',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'law_knowledge_chunk' AND COLUMN_NAME = 'embedding_json');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- embedding_model：生成该向量的模型标识（如 text-embedding-3-small / local-legal-hash-256）
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE law_knowledge_chunk ADD COLUMN embedding_model VARCHAR(64) NULL COMMENT ''生成向量的模型标识'' AFTER embedding_json',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'law_knowledge_chunk' AND COLUMN_NAME = 'embedding_model');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- ② law_chat_message：Query 改写 / 检索模式 / Agent 工具轨迹 / Token 可观测性
--    （M5 + M6 + M7）
-- ----------------------------------------------------------------------------

-- rewritten_query：多轮指代改写后的实际检索词；未改写或 user 消息为 NULL
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE law_chat_message ADD COLUMN rewritten_query VARCHAR(500) NULL COMMENT ''多轮指代改写后的实际检索词'' AFTER citations_json',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'law_chat_message' AND COLUMN_NAME = 'rewritten_query');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- retrieval_mode：实际生效检索模式 bm25 / vector / hybrid_rrf；未启用 RAG 为 NULL
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE law_chat_message ADD COLUMN retrieval_mode VARCHAR(32) NULL COMMENT ''实际生效检索模式'' AFTER rewritten_query',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'law_chat_message' AND COLUMN_NAME = 'retrieval_mode');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- tool_calls_json：Agent 工具调用轨迹 JSON 数组（仅 assistant 写入）
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE law_chat_message ADD COLUMN tool_calls_json LONGTEXT NULL COMMENT ''Agent工具调用轨迹JSON数组'' AFTER retrieval_mode',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'law_chat_message' AND COLUMN_NAME = 'tool_calls_json');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- prompt_tokens：本轮累计输入 Token 数
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE law_chat_message ADD COLUMN prompt_tokens INT NULL COMMENT ''本轮累计输入Token数'' AFTER tool_calls_json',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'law_chat_message' AND COLUMN_NAME = 'prompt_tokens');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- completion_tokens：本轮累计输出 Token 数
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE law_chat_message ADD COLUMN completion_tokens INT NULL COMMENT ''本轮累计输出Token数'' AFTER prompt_tokens',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'law_chat_message' AND COLUMN_NAME = 'completion_tokens');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- total_tokens：本轮总 Token 数
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE law_chat_message ADD COLUMN total_tokens INT NULL COMMENT ''本轮总Token数'' AFTER completion_tokens',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'law_chat_message' AND COLUMN_NAME = 'total_tokens');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ttft_ms：首字延迟（毫秒）
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE law_chat_message ADD COLUMN ttft_ms BIGINT NULL COMMENT ''首字延迟毫秒'' AFTER total_tokens',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'law_chat_message' AND COLUMN_NAME = 'ttft_ms');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- total_latency_ms：端到端总耗时（毫秒）
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE law_chat_message ADD COLUMN total_latency_ms BIGINT NULL COMMENT ''端到端总耗时毫秒'' AFTER ttft_ms',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'law_chat_message' AND COLUMN_NAME = 'total_latency_ms');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 联合索引：M7 可观测性按角色 + 时间窗口聚合
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE law_chat_message ADD INDEX idx_law_chat_message_role_time (role, create_time)',
  'SELECT 1') FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'law_chat_message'
    AND INDEX_NAME = 'idx_law_chat_message_role_time');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- ③ 验证查询（手动执行）
-- SHOW COLUMNS FROM law_knowledge_chunk LIKE 'embedding%';
-- 预期：embedding_json / embedding_model 两列
-- SHOW COLUMNS FROM law_chat_message
--   WHERE Field IN ('rewritten_query','retrieval_mode','tool_calls_json',
--                   'prompt_tokens','completion_tokens','total_tokens',
--                   'ttft_ms','total_latency_ms');
-- 预期：8 行
-- SHOW INDEX FROM law_chat_message WHERE Key_name = 'idx_law_chat_message_role_time';
-- ----------------------------------------------------------------------------
