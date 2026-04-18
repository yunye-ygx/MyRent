
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
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0 waiting, 1 success, 3 cancelled, 4 timeout closed',
  `expire_time` datetime NOT NULL COMMENT 'payment expire time',
  `paid_time` datetime DEFAULT NULL COMMENT 'payment success time',
  `callback_time` datetime DEFAULT NULL COMMENT 'callback time',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT 'failure or cancel reason',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  UNIQUE KEY `uk_payment_order_no` (`order_no`),
  UNIQUE KEY `uk_third_party_trade_no` (`third_party_trade_no`),
  KEY `idx_payment_user_id` (`user_id`),
  KEY `idx_payment_status` (`status`),
  KEY `idx_payment_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='payment record';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
