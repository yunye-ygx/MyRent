CREATE TABLE `house_hot_daily_stats` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `house_id` bigint NOT NULL COMMENT 'house id',
  `city` varchar(32) NOT NULL COMMENT 'house city',
  `stat_date` date NOT NULL COMMENT 'stat date',
  `browse_count` bigint NOT NULL DEFAULT 0 COMMENT 'daily dedup browse count',
  `favorite_count` bigint NOT NULL DEFAULT 0 COMMENT 'daily active favorite count',
  `consult_count` bigint NOT NULL DEFAULT 0 COMMENT 'daily consult count',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_house_stat_date` (`house_id`, `stat_date`),
  KEY `idx_city_stat_date` (`city`, `stat_date`),
  KEY `idx_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='house hot daily behavior stats';
