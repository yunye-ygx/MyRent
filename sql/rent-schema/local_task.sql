
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
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

