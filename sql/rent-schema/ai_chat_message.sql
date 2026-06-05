DROP TABLE IF EXISTS `ai_chat_message`;
CREATE TABLE `ai_chat_message` (
  `id` bigint NOT NULL,
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `role` varchar(16) NOT NULL COMMENT 'user / assistant / tool',
  `content` text COMMENT '消息文本内容',
  `tool_name` varchar(64) DEFAULT NULL COMMENT '工具名称',
  `tool_call_id` varchar(128) DEFAULT NULL COMMENT '工具调用ID',
  `tool_params` text DEFAULT NULL COMMENT '工具调用参数JSON',
  `tool_result` text DEFAULT NULL COMMENT '工具返回结果JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天消息';
