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
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
