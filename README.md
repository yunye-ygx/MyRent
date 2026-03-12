# MyRent

一个基于 `Spring Boot 3 + MyBatis-Plus + Redis + RabbitMQ + Elasticsearch + WebSocket` 的租房后端练手项目。

这个 README 按“新手可直接照做”的方式写，目标是：
- 能把项目启动起来；
- 能调通核心接口；
- 能看到 WebSocket 实时消息推送。

---

## 1. 项目功能（当前版本）

- 房源附近搜索（优先查 ES，异常时降级查 MySQL）
- 定金订单创建（Redis Lua + MySQL 乐观更新）
- 订单超时自动关单（本地消息表 + RabbitMQ 延迟/死信）
- 聊天发送消息（落库）
- WebSocket 实时推送给接收方

---

## 2. 技术栈

- Java 17
- Spring Boot 3.5.0
- MyBatis-Plus 3.5.7
- MySQL 8.x
- Redis 6+
- RabbitMQ 3.x
- Elasticsearch 8.x
- WebSocket（Spring WebSocket）
- Knife4j / OpenAPI

---

## 3. 运行前准备

请先安装：

- JDK 17
- Maven 3.8+
- MySQL 8
- Redis
- RabbitMQ（建议带管理后台镜像）
- Elasticsearch 8

> 说明：
> 项目默认端口是 `8081`，配置文件在 `src/main/resources/application.yml`。

---

## 4. 配置文件修改（非常重要）

打开 `src/main/resources/application.yml`，至少确认以下配置是你本机可访问的：

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.data.redis.host`
- `spring.data.redis.password`
- `spring.rabbitmq.host`
- `spring.rabbitmq.username`
- `spring.rabbitmq.password`
- `spring.elasticsearch.uris`

如果你全部在本机跑，通常可改成：

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/rent?useSSL=false&serverTimezone=UTC
    username: root
    password: 123456

  data:
    redis:
      host: localhost
      port: 6379
      password:
      database: 0

  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /

  elasticsearch:
    uris: http://localhost:9200
```

---

## 5. 初始化 MySQL 数据库

### 5.1 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS rent DEFAULT CHARACTER SET utf8mb4;
USE rent;
```

### 5.2 建表 SQL

```sql
-- 用户表
CREATE TABLE IF NOT EXISTS user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone VARCHAR(32) NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_phone (phone)
);

-- 房源表
CREATE TABLE IF NOT EXISTS house (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  price INT NOT NULL COMMENT '单位:分',
  deposit_amount INT NOT NULL COMMENT '单位:分',
  longitude DECIMAL(10,7),
  latitude DECIMAL(10,7),
  status INT NOT NULL DEFAULT 1 COMMENT '1-可租,2-已锁定',
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_house_status (status)
);

-- 订单表（注意表名是保留字，需要反引号）
CREATE TABLE IF NOT EXISTS `order` (
  id BIGINT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  house_id BIGINT NOT NULL,
  amount INT NOT NULL COMMENT '单位:分',
  status INT NOT NULL DEFAULT 0 COMMENT '0-待支付,1-已支付,2-超时取消',
  expire_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_order_house (house_id),
  KEY idx_order_user (user_id)
);

-- 支付流水表
CREATE TABLE IF NOT EXISTS payment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL,
  third_party_trade_no VARCHAR(128),
  pay_amount INT,
  status INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_payment_trade_no (third_party_trade_no)
);

-- 本地消息表（订单延时发送）
CREATE TABLE IF NOT EXISTS order_timeout (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  biz_id VARCHAR(64) NOT NULL,
  expire_time DATETIME NOT NULL,
  send_status INT NOT NULL DEFAULT 0 COMMENT '0-未发送,1-已发送,2-永久失败',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_time DATETIME NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ot_status_id (send_status, id),
  KEY idx_ot_biz (biz_id)
);

-- 聊天会话表
CREATE TABLE IF NOT EXISTS chat_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL COMMENT '规则:小userId_大userId',
  user_id1 BIGINT NOT NULL,
  user_id2 BIGINT NOT NULL,
  house_id BIGINT NULL,
  last_msg_content VARCHAR(1000),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_chat_session_id (session_id),
  KEY idx_chat_session_users (user_id1, user_id2)
);

-- 聊天消息表
CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  sender_id BIGINT NOT NULL,
  receiver_id BIGINT NOT NULL,
  msg_type INT NOT NULL DEFAULT 1,
  content TEXT,
  status INT NOT NULL DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_chat_msg_session_time (session_id, create_time),
  KEY idx_chat_msg_receiver (receiver_id)
);
```

### 5.3 插入一些测试数据

```sql
INSERT INTO user (phone) VALUES ('13800000001'), ('13800000002');

INSERT INTO house (title, price, deposit_amount, longitude, latitude, status, version)
VALUES
('天河区一室一厅', 320000, 100000, 113.324520, 23.099994, 1, 0),
('海珠区两室一厅', 450000, 120000, 113.280637, 23.125178, 1, 0);
```

---

## 6. 启动项目

在项目根目录执行：

```bash
mvn clean package -DskipTests
mvn spring-boot:run
```

看到类似日志表示启动成功：

- `Started MyRentApplication`

服务地址：

- `http://localhost:8081`
- Swagger/Knife4j：`http://localhost:8081/doc.html`

---

## 7. 快速联调（新手推荐顺序）

### 7.1 聊天实时推送（先测这个，最直观）

#### Step A：先让“接收方用户”建立 WebSocket 连接

前端连接地址：

```text
ws://localhost:8081/ws/chat?userId=2
```

浏览器控制台最小测试示例：

```javascript
const ws = new WebSocket("ws://localhost:8081/ws/chat?userId=2");
ws.onopen = () => console.log("ws connected");
ws.onmessage = (e) => console.log("receive:", e.data);
ws.onclose = () => console.log("ws closed");
```

#### Step B：调用发送接口

接口：`POST /chat-session/send`

```bash
curl -X POST "http://localhost:8081/chat-session/send" \
  -H "Content-Type: application/json" \
  -d '{
    "senderId": 1,
    "receiverId": 2,
    "content": "你好，我想咨询这套房子还在吗？"
  }'
```

预期：

- HTTP 返回成功；
- userId=2 的 WebSocket 连接即时收到一条 JSON 消息；
- `chat_session`、`chat_message` 表有对应数据。

---

### 7.2 创建订单

接口：`POST /order/createOrder`

```bash
curl -X POST "http://localhost:8081/order/createOrder" \
  -H "Content-Type: application/json" \
  -d '{
    "houseId": 1,
    "version": 0
  }'
```

说明：

- 会写入 `order` 和 `order_timeout`；
- 定时任务会扫描 `order_timeout` 并投递到 MQ；
- 超时后由监听器消费并自动关单。

---

### 7.3 附近房源查询

接口：`POST /house/nearby`

```bash
curl -X POST "http://localhost:8081/house/nearby" \
  -H "Content-Type: application/json" \
  -d '{
    "latitude": 23.099994,
    "longitude": 113.324520,
    "radius": "10km",
    "page": 1,
    "size": 10
  }'
```

说明：

- 该接口优先查询 Elasticsearch；
- 当 ES 超时/异常时，会降级到 MySQL。

---

## 8. 常见问题（新手必看）

### Q1：项目启动失败，提示数据库连接失败

检查：
- MySQL 是否启动；
- `application.yml` 的 `url/username/password` 是否正确；
- `rent` 库和表是否已创建。

### Q2：聊天接口成功，但接收方没收到实时消息

检查：
- 接收方是否先连接了 `ws://localhost:8081/ws/chat?userId=接收人ID`；
- `receiverId` 是否与你连接的 `userId` 一致；
- 浏览器控制台是否有 WebSocket 断开日志。

### Q3：订单超时逻辑没有触发

检查：
- RabbitMQ 是否可用；
- 定时任务是否在跑（`MessageSend`）；
- `order_timeout` 表里 `send_status` 是否变化。

### Q4：`/house/nearby` 返回空

可能原因：
- ES 中没有 `house_info` 数据；
- 或请求坐标/半径范围内没有数据。

---

## 9. 项目结构（简化）

```text
src/main/java/cn/yy/myrent
├─ controller     # 接口层
├─ service        # 业务层
├─ mapper         # MyBatis Mapper
├─ entity         # 数据实体
├─ websocket      # WebSocket 连接与推送管理
├─ consumer       # MQ 消费者
├─ common         # 工具类 / 定时任务
└─ config         # 配置类
```

---

## 10. 开发说明

- 当前是学习型项目，仍有可优化空间（鉴权、异常体系、统一日志、测试覆盖等）。
- 欢迎提交 Issue / PR，一起完善。

