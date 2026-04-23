
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `chat_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_message` (
  `id` bigint NOT NULL COMMENT '消息主键 (强烈建议代码中使用雪花算法生成全局唯一ID，不要用自增)',
  `session_id` varchar(64) NOT NULL COMMENT '归属的会话ID (对应 chat_session 表)',
  `sender_id` bigint NOT NULL COMMENT '发送者用户ID',
  `receiver_id` bigint NOT NULL COMMENT '接收者用户ID',
  `msg_type` tinyint NOT NULL DEFAULT '1' COMMENT '消息类型: 1-纯文本, 2-图片URL, 3-房源卡片JSON, 4-系统通知',
  `content` text NOT NULL COMMENT '消息具体内容',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '消息状态: 0-未读, 1-已读, 2-已撤回',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息发送时间 (聊天气泡按此字段正序排列)',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_receiver_status` (`receiver_id`,`status`) COMMENT '用于快速统计某人的未读消息总数',
  KEY `idx_session_message` (`session_id`,`id`),
  KEY `idx_receiver_session_status` (`receiver_id`,`session_id`,`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天消息明细记录';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `chat_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` varchar(64) NOT NULL COMMENT '会话业务ID (建议生成规则：较小的userId_较大的userId_houseId)',
  `user_id_1` bigint NOT NULL COMMENT '参与者A的ID (强制规定：存较小的ID)',
  `user_id_2` bigint NOT NULL COMMENT '参与者B的ID (强制规定：存较大的ID)',
  `house_id` bigint DEFAULT NULL COMMENT '关联房源ID (租房业务特色：标记他们是在聊哪套房)',
  `last_msg_content` varchar(512) DEFAULT NULL COMMENT '最后一条消息摘要 (提升列表加载性能)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次会话时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃时间 (列表根据此字段倒序排列!)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`),
  KEY `idx_user1` (`user_id_1`),
  KEY `idx_user2` (`user_id_2`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天会话列表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `house`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `house` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `publisher_user_id` bigint NOT NULL COMMENT '发布者用户ID，关联user.id',
  `title` varchar(100) NOT NULL COMMENT '房源标题(供ES全文搜索)',
  `rent_type` tinyint NOT NULL DEFAULT '0' COMMENT '0-未知,1-整租,2-合租',
  `price` int NOT NULL COMMENT '月租金(分)',
  `deposit_amount` int NOT NULL COMMENT '锁定定金金额(分) - 【新增：锁房核心】',
  `total_cost` int GENERATED ALWAYS AS ((`price` + `deposit_amount`)) STORED COMMENT '首月总成本(分)',
  `longitude` decimal(10,6) NOT NULL COMMENT '经度 - 【核心：供ES做LBS附近搜索】',
  `latitude` decimal(10,6) NOT NULL COMMENT '纬度 - 【核心：供ES做LBS附近搜索】',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 1-可租(上架), 2-已锁定(有人交定金)',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号 - 【核心：防一房多租】',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_house_publisher_user_id` (`publisher_user_id`),
  KEY `idx_house_status_rent_lat_lon` (`status`,`rent_type`,`latitude`,`longitude`),
  KEY `idx_house_status_rent_price` (`status`,`rent_type`,`price`),
  KEY `idx_house_status_rent_total_cost` (`status`,`rent_type`,`total_cost`)
) ENGINE=InnoDB AUTO_INCREMENT=1032 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房源信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `house_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `house_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `house_id` bigint NOT NULL COMMENT '房源ID',
  `browse_date` date NOT NULL COMMENT '浏览日期，按天去重和分组',
  `last_browse_time` datetime NOT NULL COMMENT '当天最后一次浏览时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_house_browse_date` (`user_id`,`house_id`,`browse_date`),
  KEY `idx_user_browse_date_last_time` (`user_id`,`browse_date`,`last_browse_time`),
  KEY `idx_user_last_browse_time` (`user_id`,`last_browse_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房源浏览历史表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `local_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `local_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键，表内唯一标识',
  `message_id` varchar(64) NOT NULL COMMENT '消息唯一ID，用于幂等控制和全链路追踪',
  `biz_type` varchar(32) NOT NULL COMMENT '业务类型，如 ORDER、HOUSE',
  `biz_id` varchar(64) NOT NULL COMMENT '业务主键，如订单ID、房源ID',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型，如 ORDER_TIMEOUT_RELEASE、HOUSE_SEARCH_UPSERT',
  `payload` json DEFAULT NULL COMMENT '消息体，建议只放必要字段，不要塞过大对象',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0待执行 1执行中 2成功 3待重试 4失败 5取消/死信',
  `execute_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最早执行时间；立即消息填当前时间，延迟消息填未来\r\n  时间',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '当前已重试次数',
  `max_retry_count` int NOT NULL DEFAULT '5' COMMENT '最大重试次数，超过后进入失败或死信状态',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '版本号，用于顺序控制或乐观锁',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`),
  KEY `idx_status_execute_time` (`status`,`execute_time`),
  KEY `idx_status_next_retry_time` (`status`,`next_retry_time`),
  KEY `idx_biz_type_biz_id` (`biz_type`,`biz_id`),
  KEY `idx_target_event_type` (`event_type`)
) ENGINE=InnoDB AUTO_INCREMENT=2034099401470849027 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用本地消息表/延迟任务表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `location_dict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `location_dict` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `longitude` decimal(10,7) NOT NULL,
  `latitude` decimal(10,7) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_location_dict_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Smart guide local place dictionary';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order` (
  `id` bigint NOT NULL COMMENT 'distributed order id',
  `order_no` varchar(64) NOT NULL COMMENT 'business order number',
  `user_id` bigint NOT NULL COMMENT 'renter user id',
  `house_id` bigint NOT NULL COMMENT 'house id',
  `amount` int NOT NULL COMMENT 'deposit amount in cents',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0未支付，1已支付，2超时关闭，3用户取消，4已退款，5已完成，6已评价',
  `expire_time` datetime NOT NULL COMMENT 'payment expire time',
  `paid_time` datetime DEFAULT NULL COMMENT 'payment success time',
  `success_payment_no` varchar(64) DEFAULT NULL COMMENT 'final successful payment no',
  `close_time` datetime DEFAULT NULL COMMENT 'order close time',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_order_user_id` (`user_id`),
  KEY `idx_order_house_id` (`house_id`),
  KEY `idx_order_status` (`status`),
  KEY `idx_order_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='deposit order';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'payment record id',
  `payment_no` varchar(64) NOT NULL COMMENT 'payment number',
  `order_no` varchar(64) NOT NULL COMMENT 'business order number',
  `user_id` bigint NOT NULL COMMENT 'paying user id',
  `pay_amount` int NOT NULL COMMENT 'payment amount in cents',
  `channel` varchar(32) NOT NULL DEFAULT 'MOCK' COMMENT 'payment channel',
  `third_party_trade_no` varchar(64) DEFAULT NULL COMMENT 'third party trade number',
  `callback_no` varchar(64) DEFAULT NULL COMMENT 'callback request number',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0 pending, 1 paying, 2 paid, 3 user cancelled, 4 timeout closed, 5 duplicate paid',
  `expire_time` datetime NOT NULL COMMENT 'payment expire time',
  `paid_time` datetime DEFAULT NULL COMMENT 'payment success time',
  `callback_time` datetime DEFAULT NULL COMMENT 'callback time',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT 'failure or cancel reason',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  UNIQUE KEY `uk_third_party_trade_no` (`third_party_trade_no`),
  KEY `idx_payment_user_id` (`user_id`),
  KEY `idx_payment_status` (`status`),
  KEY `idx_payment_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='payment record';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `review` (
  `id` bigint NOT NULL COMMENT '评论ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号，每个订单只能评价一次',
  `house_id` bigint NOT NULL COMMENT '房源ID',
  `user_id` bigint NOT NULL COMMENT '评价用户ID',
  `score` tinyint NOT NULL COMMENT '评分，范围1-5',
  `content` text NOT NULL COMMENT '评价内容',
  `edit_count` tinyint NOT NULL DEFAULT '0' COMMENT '已修改次数，第一版最多允许修改1次',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_order_no` (`order_no`),
  KEY `idx_review_house_id_create_time` (`house_id`,`create_time`),
  KEY `idx_review_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房源评价表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mock_pay_trade`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mock_pay_trade` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'mock trade id',
  `payment_no` varchar(64) NOT NULL COMMENT 'internal payment number',
  `order_no` varchar(64) NOT NULL COMMENT 'business order number',
  `third_party_trade_no` varchar(64) DEFAULT NULL COMMENT 'mock third-party trade number',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0 created, 1 paying, 2 success, 3 user cancelled, 4 timeout closed',
  `amount` int NOT NULL COMMENT 'payment amount in cents',
  `paid_time` datetime DEFAULT NULL COMMENT 'mock payment success time',
  `callback_status` tinyint NOT NULL DEFAULT '0' COMMENT '0 not sent or not confirmed, 1 callback confirmed, 2 callback failed',
  `last_callback_time` datetime DEFAULT NULL COMMENT 'last callback attempt time',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mock_trade_payment_no` (`payment_no`),
  UNIQUE KEY `uk_mock_trade_third_party_trade_no` (`third_party_trade_no`),
  KEY `idx_mock_trade_order_no` (`order_no`),
  KEY `idx_mock_trade_status` (`status`),
  KEY `idx_mock_trade_callback_status` (`callback_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='mock third-party payment trade';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'notification primary key',
  `user_id` bigint NOT NULL COMMENT 'receiver user id',
  `type` varchar(64) NOT NULL COMMENT 'notification type',
  `title` varchar(128) NOT NULL COMMENT 'notification title',
  `content` varchar(255) NOT NULL COMMENT 'notification summary',
  `biz_key` varchar(255) NOT NULL COMMENT 'idempotency business key',
  `redirect_type` varchar(64) NOT NULL DEFAULT 'house_detail' COMMENT 'navigation target type',
  `redirect_target_id` bigint NOT NULL COMMENT 'navigation target id',
  `extra_json` varchar(1000) DEFAULT NULL COMMENT 'extra metadata json',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '0 unread, 1 read',
  `read_time` datetime DEFAULT NULL COMMENT 'read time',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_user_biz` (`user_id`,`biz_key`),
  KEY `idx_notification_user_read_time` (`user_id`,`is_read`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='in-app notification inbox';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `publisher_follow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `publisher_follow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'publisher follow primary key',
  `user_id` bigint NOT NULL COMMENT 'follower user id',
  `publisher_user_id` bigint NOT NULL COMMENT 'publisher user id',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1 active, 0 canceled',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'first follow time',
  `cancel_time` datetime DEFAULT NULL COMMENT 'cancel time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow_user_publisher` (`user_id`,`publisher_user_id`),
  KEY `idx_follow_publisher_status` (`publisher_user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='publisher follow relationship';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `phone` varchar(20) NOT NULL COMMENT '手机号(唯一)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `password` varchar(50) DEFAULT NULL,
  `name` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

