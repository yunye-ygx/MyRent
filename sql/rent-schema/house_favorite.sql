CREATE TABLE `house_favorite` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                  `user_id` bigint NOT NULL COMMENT '用户ID',
                                  `house_id` bigint NOT NULL COMMENT '房源ID',
                                  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1-已收藏 0-已取消',
                                  `favorite_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次收藏时间',
                                  `cancel_time` datetime DEFAULT NULL COMMENT '最近一次取消收藏时间',
                                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_user_house` (`user_id`, `house_id`),
                                  KEY `idx_house_status_favorite_time` (`house_id`, `status`, `favorite_time`),
                                  KEY `idx_user_status_update_time` (`user_id`, `status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房源收藏关系表';
