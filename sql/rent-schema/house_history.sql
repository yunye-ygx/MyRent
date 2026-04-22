CREATE TABLE `house_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `house_id` bigint NOT NULL COMMENT '房源ID',
  `browse_date` date NOT NULL COMMENT '浏览日期，按天去重和分组',
  `last_browse_time` datetime NOT NULL COMMENT '当天最后一次浏览时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_house_browse_date` (`user_id`, `house_id`, `browse_date`),
  KEY `idx_user_browse_date_last_time` (`user_id`, `browse_date`, `last_browse_time`),
  KEY `idx_user_last_browse_time` (`user_id`, `last_browse_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房源浏览历史表';
