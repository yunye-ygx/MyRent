
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
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

