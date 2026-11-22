-- ----------------------------
-- 咨询消息表：保存一次问答中的用户提问和 AI 回答
-- 增量脚本，可重复执行，不会清空已有数据
-- ----------------------------
CREATE TABLE IF NOT EXISTS `law_chat_message` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id`  BIGINT       NOT NULL COMMENT '所属会话ID',
  `request_id`  VARCHAR(36)  NOT NULL COMMENT '本次发送的请求ID，同一问答共享',
  `role`        VARCHAR(16)  NOT NULL COMMENT '角色：user / assistant',
  `content`     LONGTEXT     NOT NULL COMMENT '消息正文，待生成时为空字符串',
  `status`      VARCHAR(16)  NOT NULL COMMENT '状态：pending / success / failed',
  `error_code`  VARCHAR(64)  DEFAULT NULL COMMENT '应用定义的错误代码',
  `model_name`  VARCHAR(100) DEFAULT NULL COMMENT '实际使用的模型名',
  `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_law_chat_message_session` (`session_id`, `id`),
  UNIQUE KEY `uk_law_chat_message_request` (`session_id`, `request_id`, `role`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='咨询消息表';
