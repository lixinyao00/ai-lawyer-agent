-- ----------------------------
-- 咨询会话表：保存“谁发起了哪一次咨询”
-- ----------------------------
CREATE TABLE IF NOT EXISTS `law_chat_session` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户ID',
  `title`       VARCHAR(100) NOT NULL DEFAULT '新咨询' COMMENT '咨询标题',
  `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT NULL COMMENT '最近更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_law_chat_session_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='咨询会话表';
