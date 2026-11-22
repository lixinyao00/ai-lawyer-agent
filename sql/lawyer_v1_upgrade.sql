-- ============================================================================
-- AI 律师助手 V1.0 增量升级脚本（M1 ~ M4 全量）
-- 幂等：可重复执行任意次；不含 DROP TABLE / TRUNCATE / 无条件 DELETE 业务数据
-- 执行顺序建议：sql/lawyer.sql -> sql/lawyer_message.sql -> sql/lawyer_menu.sql
--               -> sql/lawyer_v1_upgrade.sql
-- ============================================================================

-- ----------------------------------------------------------------------------
-- ① law_chat_message 扩展：法条引用 JSON 列
--    MySQL 8 无 ADD COLUMN IF NOT EXISTS，用 information_schema + 预处理语句
-- ----------------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE law_chat_message ADD COLUMN citations_json LONGTEXT NULL COMMENT ''法条引用JSON数组'' AFTER model_name',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'law_chat_message' AND COLUMN_NAME = 'citations_json');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- ② M2 法律知识库：法规文档表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `law_knowledge_doc` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`          VARCHAR(200) NOT NULL COMMENT '法规名称，如“中华人民共和国劳动合同法”',
  `category`       VARCHAR(32)  NOT NULL COMMENT '分类：labor/civil_contract/civil_lease/civil_loan/civil_tort/other',
  `doc_number`     VARCHAR(100) NULL COMMENT '文号/版本，如“主席令第七十三号（2012修正）”',
  `effective_date` DATE         NULL COMMENT '施行日期',
  `status`         CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态：0启用 / 1停用（停用后不参与检索）',
  `chunk_count`    INT          NOT NULL DEFAULT 0 COMMENT '切片数（导入/重切后回写）',
  `create_by`      VARCHAR(64)  DEFAULT '' COMMENT '创建者用户名',
  `create_time`    DATETIME     NULL COMMENT '创建时间',
  `update_time`    DATETIME     NULL COMMENT '更新时间',
  `remark`         VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_law_knowledge_doc_status` (`status`),
  KEY `idx_law_knowledge_doc_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法律知识库法规文档表';

-- ② M2 法律知识库：法规切片表
CREATE TABLE IF NOT EXISTS `law_knowledge_chunk` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `doc_id`      BIGINT       NOT NULL COMMENT '所属法规ID',
  `article_no`  VARCHAR(32)  NULL COMMENT '规范化中文条号，如“第四十七条”；序言/无条号段为NULL',
  `content`     TEXT         NOT NULL COMMENT '条文原文（不含章节标题），≤4000字',
  `keywords`    VARCHAR(500) NULL COMMENT '章节标题 + 人工关键词，空格分隔',
  `token_count` INT          NOT NULL DEFAULT 0 COMMENT 'token 估算值（V1 用 content.length()）',
  `create_time` DATETIME     NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_law_knowledge_chunk_doc` (`doc_id`),
  KEY `idx_law_knowledge_chunk_article` (`article_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法律知识库切片表';

-- 全文索引：优先 ngram 解析器；云端未安装 ngram 插件时降级为普通索引
SET @has_ngram := (SELECT COUNT(*) FROM information_schema.PLUGINS
  WHERE PLUGIN_NAME = 'ngram' AND PLUGIN_STATUS = 'ACTIVE');
SET @ddl := (SELECT IF(COUNT(*) = 0,
  IF(@has_ngram > 0,
    'ALTER TABLE law_knowledge_chunk ADD FULLTEXT KEY ft_law_knowledge_chunk (content, keywords) WITH PARSER ngram',
    'ALTER TABLE law_knowledge_chunk ADD KEY ft_law_knowledge_chunk (content(191), keywords(191))'),
  'SELECT 1') FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'law_knowledge_chunk'
    AND INDEX_NAME = 'ft_law_knowledge_chunk');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- ③ M3 私有文件表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `law_private_file` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT       NOT NULL COMMENT '所有者用户ID',
  `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名（已去除路径成分）',
  `storage_path`  VARCHAR(500) NOT NULL COMMENT '相对存储根路径：{userId}/{yyyyMM}/{uuid}.{ext}',
  `file_ext`      VARCHAR(16)  NOT NULL COMMENT '小写不带点扩展名：docx/pdf/txt/md',
  `file_size`     BIGINT       NOT NULL COMMENT '文件字节数',
  `sha256`        CHAR(64)     NOT NULL COMMENT 'SHA-256 十六进制小写',
  `create_time`   DATETIME     NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_law_private_file_user` (`user_id`),
  KEY `idx_law_private_file_user_sha` (`user_id`, `sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户私有文件表';

-- ③ M3 合同风险审查表
CREATE TABLE IF NOT EXISTS `law_contract_review` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT       NOT NULL COMMENT '所有者用户ID',
  `file_id`       BIGINT       NULL COMMENT '关联私有文件ID；粘贴文本审查时为NULL',
  `title`         VARCHAR(200) NOT NULL COMMENT '标题：默认文件名去扩展名，或“合同审查-yyyyMMddHHmm”',
  `stance`        VARCHAR(16)  NOT NULL COMMENT '立场：party_a / party_b / neutral',
  `contract_text` LONGTEXT     NOT NULL COMMENT '抽取后全文（≤50000字，超出截断）',
  `status`        VARCHAR(16)  NOT NULL COMMENT '状态：pending / success / failed',
  `risk_level`    VARCHAR(16)  NULL COMMENT '风险等级：high / medium / low（success 时必填）',
  `summary`       TEXT         NULL COMMENT '总体评价',
  `risks_json`    LONGTEXT     NULL COMMENT '风险项 JSON 数组',
  `model_name`    VARCHAR(100) NULL COMMENT '实际使用的模型名',
  `error_msg`     VARCHAR(500) NULL COMMENT '面向用户的失败原因',
  `create_time`   DATETIME     NULL COMMENT '创建时间',
  `update_time`   DATETIME     NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_law_contract_review_user` (`user_id`, `id`),
  KEY `idx_law_contract_review_file` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='合同风险审查表';

-- ----------------------------------------------------------------------------
-- ④ M4 法律文书草稿表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `law_document_draft` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`        BIGINT       NOT NULL COMMENT '所有者用户ID',
  `template_code`  VARCHAR(32)  NOT NULL COMMENT '文书模板编码',
  `title`          VARCHAR(200) NOT NULL COMMENT '文书标题',
  `form_data_json` LONGTEXT     NOT NULL COMMENT '要素对象 JSON：{fieldKey: value}',
  `content`        LONGTEXT     NOT NULL COMMENT '文书正文纯文本；失败时为空串',
  `status`         VARCHAR(16)  NOT NULL COMMENT '状态：success / failed',
  `model_name`     VARCHAR(100) NULL COMMENT '实际使用的模型名',
  `create_time`    DATETIME     NULL COMMENT '创建时间',
  `update_time`    DATETIME     NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_law_document_draft_user` (`user_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法律文书草稿表';

-- ----------------------------------------------------------------------------
-- ⑤ 菜单 upsert（2000 ~ 2026）
--    前台 FRONT = 2000..2006, 2011..2020（17 个）
--    后台 ADMIN = 2007..2010, 2021..2026（10 个）
-- ----------------------------------------------------------------------------
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, update_by, update_time, remark)
VALUES
(2000, 'AI律师助手', 0, 5, 'lawyer', NULL, '', 'Lawyer', 1, 0, 'M', '0', '0', '', 'clipboard', 'admin', NOW(), '', NULL, 'AI律师助手目录'),
(2001, '智能咨询', 2000, 1, 'chat', 'lawyer/chat/index', '', 'LawyerChat', 1, 0, 'C', '0', '0', 'lawyer:session:list', 'message', 'admin', NOW(), '', NULL, '智能咨询菜单'),
(2002, '会话新增', 2001, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:session:add', '#', 'admin', NOW(), '', NULL, ''),
(2003, '会话查询', 2001, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:session:list', '#', 'admin', NOW(), '', NULL, ''),
(2004, '会话删除', 2001, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:session:remove', '#', 'admin', NOW(), '', NULL, ''),
(2005, '消息查询', 2001, 4, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:chat:query', '#', 'admin', NOW(), '', NULL, ''),
(2006, '消息发送', 2001, 5, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:chat:send', '#', 'admin', NOW(), '', NULL, '同步 + 流式共用'),
(2007, '会话管理', 2000, 2, 'session', 'lawyer/session/index', '', 'LawyerSession', 1, 0, 'C', '0', '0', 'lawyer:manage:list', 'peoples', 'admin', NOW(), '', NULL, '后台会话审计菜单'),
(2008, '会话列表', 2007, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:manage:list', '#', 'admin', NOW(), '', NULL, ''),
(2009, '消息审计', 2007, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:manage:query', '#', 'admin', NOW(), '', NULL, ''),
(2010, '会话删除', 2007, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:manage:remove', '#', 'admin', NOW(), '', NULL, ''),
(2011, '合同风险审查', 2000, 3, 'contract', 'lawyer/contract/index', '', 'LawyerContract', 1, 0, 'C', '1', '0', 'lawyer:contract:list', 'documentation', 'admin', NOW(), '', NULL, '隐藏菜单，入口在工作台'),
(2012, '发起审查', 2011, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:contract:add', '#', 'admin', NOW(), '', NULL, ''),
(2013, '审查详情', 2011, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:contract:query', '#', 'admin', NOW(), '', NULL, ''),
(2014, '审查删除', 2011, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:contract:remove', '#', 'admin', NOW(), '', NULL, ''),
(2015, '私有文件', 2011, 4, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:file:access', '#', 'admin', NOW(), '', NULL, '上传+下载'),
(2016, '法律文书起草', 2000, 4, 'document', 'lawyer/document/index', '', 'LawyerDocument', 1, 0, 'C', '1', '0', 'lawyer:document:list', 'edit', 'admin', NOW(), '', NULL, '隐藏菜单，入口在工作台'),
(2017, '生成文书', 2016, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:document:add', '#', 'admin', NOW(), '', NULL, ''),
(2018, '编辑文书', 2016, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:document:edit', '#', 'admin', NOW(), '', NULL, ''),
(2019, '删除文书', 2016, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:document:remove', '#', 'admin', NOW(), '', NULL, ''),
(2020, '导出文书', 2016, 4, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:document:export', '#', 'admin', NOW(), '', NULL, ''),
(2021, '法律知识库', 2000, 5, 'knowledge', 'lawyer/knowledge/index', '', 'LawyerKnowledge', 1, 0, 'C', '0', '0', 'lawyer:knowledge:list', 'education', 'admin', NOW(), '', NULL, '后台知识库菜单'),
(2022, '知识库查询', 2021, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:knowledge:query', '#', 'admin', NOW(), '', NULL, ''),
(2023, '知识库新增', 2021, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:knowledge:add', '#', 'admin', NOW(), '', NULL, ''),
(2024, '知识库修改', 2021, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:knowledge:edit', '#', 'admin', NOW(), '', NULL, ''),
(2025, '知识库删除', 2021, 4, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:knowledge:remove', '#', 'admin', NOW(), '', NULL, ''),
(2026, '法规导入', 2021, 5, '', NULL, '', '', 1, 0, 'F', '0', '0', 'lawyer:knowledge:import', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE
  menu_name  = VALUES(menu_name),
  parent_id  = VALUES(parent_id),
  order_num  = VALUES(order_num),
  path       = VALUES(path),
  component  = VALUES(component),
  `query`    = VALUES(`query`),
  route_name = VALUES(route_name),
  is_frame   = VALUES(is_frame),
  is_cache   = VALUES(is_cache),
  menu_type  = VALUES(menu_type),
  visible    = VALUES(visible),
  status     = VALUES(status),
  perms      = VALUES(perms),
  icon       = VALUES(icon),
  update_by  = VALUES(update_by),
  update_time = VALUES(update_time),
  remark     = VALUES(remark);

-- ----------------------------------------------------------------------------
-- ⑥ 角色授权
--    1) role 1 全部 2000~2026
--    2) role 2 / 100 仅前台集合 FRONT
--    3) 清理非 role 1 的后台 ADMIN 授权（防止历史误授）
-- ----------------------------------------------------------------------------

-- 1) 超级管理员：全部律师助手菜单
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.menu_id BETWEEN 2000 AND 2026
WHERE r.role_id = 1
  AND r.del_flag = '0';

-- 2) 普通咨询用户（role 2 / 100）：前台集合 FRONT
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.menu_id IN
  (2000, 2001, 2002, 2003, 2004, 2005, 2006,
   2011, 2012, 2013, 2014, 2015, 2016, 2017, 2018, 2019, 2020)
WHERE r.role_id IN (2, 100)
  AND r.del_flag = '0';

-- 3) 后台 ADMIN 集合只允许 role 1
DELETE FROM sys_role_menu
WHERE role_id <> 1
  AND menu_id IN (2007, 2008, 2009, 2010, 2021, 2022, 2023, 2024, 2025, 2026);

-- ----------------------------------------------------------------------------
-- ⑦ 验证查询（手动执行）
-- SELECT role_id, COUNT(*) FROM sys_role_menu
--   WHERE menu_id BETWEEN 2000 AND 2040 GROUP BY role_id;
-- 预期：role 1 = 27，role 2 = 17，role 100（若存在）= 17
--
-- SHOW COLUMNS FROM law_chat_message LIKE 'citations_json';
-- SHOW TABLES LIKE 'law_%';
-- ----------------------------------------------------------------------------
