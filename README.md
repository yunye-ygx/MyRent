# MyRent

> 城市租住消息平台 · 前后端分离练习项目

MyRent 是一个面向年轻租客的租房平台练习项目。后端基于 Spring Boot 3，前端基于 Vue 3 + Vite，围绕租房业务核心链路做后端深度：锁房交易一致性、搜索推荐、房源 DB/ES 同步、聊天与通知。

## 页面预览

<table>
  <tr>
    <td><b>首页</b></td>
    <td><b>找房（列表 + 地图）</b></td>
  </tr>
  <tr>
    <td><img src="images/5.png" alt="首页" /></td>
    <td><img src="images/4.png" alt="找房页面" /></td>
  </tr>
  <tr>
    <td><b>AI 智能推荐</b></td>
    <td><b>消息 / 聊天</b></td>
  </tr>
  <tr>
    <td><img src="images/2.png" alt="AI智能推荐" /></td>
    <td><img src="images/1.png" alt="聊天页面" /></td>
  </tr>
  <tr>
    <td><b>个人中心</b></td>
    <td></td>
  </tr>
  <tr>
    <td><img src="images/3.png" alt="个人中心" /></td>
    <td></td>
  </tr>
</table>

## 技术栈

后端：

- Java 17
- Spring Boot 3.5.0
- MyBatis-Plus 3.5.7
- MySQL 8.x
- Redis
- RabbitMQ
- Elasticsearch 8.x
- WebSocket
- Spring AI
- Knife4j / OpenAPI
- SkyWalking Agent 本地接入脚本

前端：

- Vue 3
- Vite
- Vue Router
- Pinia
- Axios

## 当前后端完成状态

### 1. 交易一致性链路

已实现：

- 租客锁房下单，生成订单、支付单和模拟支付交易记录。
- 使用 Redis Lua 对房源进行短期预占，降低并发锁房冲突。
- 使用 MySQL 条件更新推进房源状态，避免房源状态被并发覆盖。
- 使用本地任务表落地订单超时释放任务，事务提交后再投递 RabbitMQ。
- 使用 RabbitMQ TTL + 死信队列处理订单超时关单。
- 超时关单消费失败后进入重试队列，超过最大次数后进入失败队列。
- 支付成功回调支持重复回调、超时后迟到成功、重复支付等分支处理。
- 定时任务扫描可疑支付单，尝试修复支付成功但订单状态未正确推进的异常。
- 支持用户申请退款、重复支付退款、超时后迟到成功不可恢复退款。
- 退款任务支持重试，失败后转入人工处理状态。

主要代码位置：

- `src/main/java/cn/yy/myrent/service/impl/OrderServiceImpl.java`
- `src/main/java/cn/yy/myrent/service/impl/PaymentServiceImpl.java`
- `src/main/java/cn/yy/myrent/service/impl/PaymentRefundServiceImpl.java`
- `src/main/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumer.java`
- `src/main/java/cn/yy/myrent/common/MessageSend.java`
- `src/main/resources/Lua/Stock.lua`

### 2. 搜索推荐链路

已实现：

- 附近房源搜索：基于 ES geo distance 查询，并按距离排序。
- 附近搜索 ES 超时或异常时，降级到 Redis 热门房源；Redis 为空或异常时再降级 DB。
- 热门房源：基于 Redis ZSet 保存热榜，定时从收藏、咨询、回复等行为指标重建。
- 关键词搜索：地点召回和文本召回并发执行，合并候选后再统一排序。
- 列表筛选：支持城市、区域、租住方式、价格区间、近地铁、独卫、阳台、民水民电、学生免押等条件。
- 多因子排序：综合召回命中、文本相关性、距离、预算接近度、租住方式、房源特征、发布时间等因素。
- 搜索原因输出：返回命中原因，前端可以展示“位置匹配”“关键词命中”“距离优势”等说明。
- 智能找房和 AI 推荐复用召回与排序链路，并支持预算放宽、偏好权重和推荐说明。

主要代码位置：

- `src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java`
- `src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRecallServiceImpl.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRankingServiceImpl.java`
- `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java`

### 3. 房源数据同步链路

已实现：

- 房源新增、更新、删除后触发 MySQL 到 Elasticsearch 的同步。
- 区分核心字段和普通字段变更：
  - 核心字段走本地任务表，保证事件先落库，再由定时扫描投递 MQ。
  - 普通字段直接投递 MQ，失败后写入 Redis 补偿队列。
- 房源同步消费者手动 ack，消费失败时 nack 并 requeue。
- 提供全量重建 ES 文档接口。
- 提供每日 DB/ES 一致性补偿任务，按时间窗口校验并修复 ES 文档。

主要代码位置：

- `src/main/java/cn/yy/myrent/service/impl/HouseCommandServiceImpl.java`
- `src/main/java/cn/yy/myrent/sync/house/HouseSyncDispatcher.java`
- `src/main/java/cn/yy/myrent/sync/house/strategy/CoreHouseSyncDispatchStrategy.java`
- `src/main/java/cn/yy/myrent/sync/house/strategy/NormalHouseSyncDispatchStrategy.java`
- `src/main/java/cn/yy/myrent/sync/house/HouseNormalSyncCompensateTask.java`
- `src/main/java/cn/yy/myrent/sync/house/HouseDailyEsConsistencyTask.java`
- `src/main/java/cn/yy/myrent/consumer/HouseSyncConsumer.java`
- `src/main/java/cn/yy/myrent/sync/house/service/impl/HouseEsSyncServiceImpl.java`

### 4. 消息与通知链路

已实现：

- 租客与房东基于房源建立聊天会话。
- WebSocket 连接使用 JWT token 识别用户。
- 支持多端在线连接管理，同一个用户可有多个 WebSocket session。
- 发送消息时先写入数据库，事务提交后再推送 WebSocket，避免事务回滚后错推消息。
- 用户离线或推送失败时不丢消息，前端可通过拉取接口补偿。
- 支持按游标拉取新消息、上滑加载历史消息、批量已读回执、未读总数统计。
- 会话和消息接口做了参与人权限校验，避免越权读取。
- 房源变更通知：
  - 关注房东后，房东发布新房源会生成站内通知。
  - 收藏房源后，房源价格变化、下架、出租、删除会通知收藏用户。
  - 通知表使用 `user_id + biz_key` 唯一键做幂等。

主要代码位置：

- `src/main/java/cn/yy/myrent/service/impl/ChatSessionServiceImpl.java`
- `src/main/java/cn/yy/myrent/service/impl/ChatMessageServiceImpl.java`
- `src/main/java/cn/yy/myrent/websocket/ChatWebSocketHandler.java`
- `src/main/java/cn/yy/myrent/websocket/ChatWebSocketSessionManager.java`
- `src/main/java/cn/yy/myrent/service/impl/NotificationServiceImpl.java`
- `src/main/java/cn/yy/myrent/service/impl/PublisherFollowServiceImpl.java`

## 扩展点当前状态

这些点已经纳入项目设计，但完成程度不同：

| 方向 | 当前状态 | 说明 |
| --- | --- | --- |
| 热门房源 Redis | 已实现 | Redis ZSet 保存热榜，定时重建；附近搜索异常时可降级到热榜。 |
| 收藏高并发基础 | 部分实现 | `house_favorite` 有 `user_id + house_id` 唯一索引，接口支持收藏/取消收藏幂等更新；暂未做 Redis 计数异步落库或高并发压测报告。 |
| 关注推拉结合 | 部分实现 | 已有关注关系和房东发布新房源站内通知；当前是通知收件箱模型，不是完整 feed 流。 |
| 聊天图片/语音 | 未实现 | 消息表预留 `msg_type`，但当前发送接口只支持文本 content，未实现文件上传、媒体消息校验和资源鉴权。 |
| 链路观测 | 部分实现 | 仓库有 SkyWalking Agent 下载/启动脚本和本地 agent 文件；README 不把它描述为完整生产监控体系。 |
| 压测量化 | 待补充 | 暂未看到 JMeter 压测报告或固定压测脚本。后续可以补锁房、搜索、消息拉取三个接口的简单压测结果。 |
| 项目上线 | 待补充 | 当前以本地开发运行为主，未提供完整 Docker Compose 或云服务器部署说明。 |

## 目录说明

```text
MyRent
├─ src/main/java/cn/yy/myrent
│  ├─ controller       # REST 接口
│  ├─ service          # 业务服务
│  ├─ mapper           # MyBatis-Plus Mapper
│  ├─ entity           # 实体类
│  ├─ websocket        # WebSocket 连接管理与消息推送
│  ├─ consumer         # RabbitMQ 消费者
│  ├─ sync             # 房源 DB/ES 同步与补偿任务
│  ├─ task             # 支付、退款等定时补偿任务
│  └─ config           # 项目配置
├─ src/main/resources
│  ├─ application.yml
│  ├─ mapper           # XML Mapper
│  ├─ Lua              # Redis Lua 脚本
│  └─ prompts          # AI 推荐提示词
├─ src/test/java       # 后端单元测试和 WebMvc 测试
├─ frontend            # Vue 前端
├─ sql                 # 数据库建表脚本
├─ scripts             # 热榜重建、SkyWalking 启动等脚本
└─ tools               # 本地工具目录
```

## 数据表概览

SQL 位于 `sql/rent-schema`。

核心表：

- `user`：用户信息
- `house`：房源信息
- `house_favorite`：房源收藏关系，包含 `uk_user_house`
- `publisher_follow`：关注房东关系，包含 `uk_follow_user_publisher`
- `notification`：站内通知，包含 `uk_notification_user_biz`
- `chat_session`：聊天会话
- `chat_message`：聊天消息，消息 ID 由代码侧雪花算法生成
- `order`：定金订单
- `payment`：支付单
- `mock_pay_trade`：模拟第三方支付交易
- `payment_refund`：退款单
- `local_task`：本地任务表 / 延迟任务表
- `review`：评价
- `house_history`：浏览历史

## 运行环境

建议准备：

- JDK 17
- Maven 3.8+
- Node.js 18+
- npm 9+
- MySQL 8.x
- Redis
- RabbitMQ
- Elasticsearch 8.x

默认后端端口来自 `src/main/resources/application.yml`，当前为 `8084`。

## 配置说明

配置文件：

```text
src/main/resources/application.yml
```

当前仓库中的配置包含个人本地环境地址，启动前需要按自己的环境修改：

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.rabbitmq.host`
- `spring.rabbitmq.username`
- `spring.rabbitmq.password`
- `spring.data.redis.host`
- `spring.data.redis.password`
- `spring.elasticsearch.uris`
- `spring.ai.openai.api-key`

AI 推荐默认读取 `BAILIAN_API_KEY`，建议使用环境变量，不要把真实 key 写死在配置文件里。

一个本地开发参考配置：

```yaml
server:
  port: 8084

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/rent?useSSL=false&serverTimezone=UTC
    username: root
    password: your-password

  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true

  data:
    redis:
      host: localhost
      port: 6379
      password:
      database: 0

  elasticsearch:
    uris: http://localhost:9200
    connection-timeout: 5s
    socket-timeout: 30s

myrent:
  jwt:
    secret: MyRentJwtSecretChangeMe
    expire-seconds: 86400
```

## 数据库初始化

1. 创建数据库：

```sql
CREATE DATABASE rent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

2. 导入表结构：

```text
sql/rent-schema/rent-schema-all.sql
```

3. 导入智能找房地点字典：

```text
sql/rent-schema/smart-guide-location-dict.sql
```

说明：SQL 目录主要是建表脚本，不是完整演示数据包。用户可通过前端注册，房源数据建议通过接口创建。

## 启动方式

后端：

```powershell
mvn spring-boot:run
```

前端：

```powershell
cd frontend
npm install
npm run dev
```

前端开发环境默认通过 Vite 代理访问后端，配置位于：

```text
frontend/.env.development
frontend/vite.config.js
```

## 常用验证入口

后端接口文档：

```text
http://localhost:8084/doc.html
```

常用验证顺序：

1. 注册 / 登录，拿到 JWT。
2. 创建房源。
3. 调用房源 ES 全量重建接口，或等待房源同步任务写入 ES。
4. 验证附近搜索、关键词搜索、列表筛选、热门房源。
5. 使用租客账号锁房下单，进入模拟支付页。
6. 触发模拟支付回调，验证订单和支付状态。
7. 验证订单超时后自动关单和房源释放。
8. 建立聊天会话，验证 WebSocket 在线推送、历史拉取和未读数。
9. 收藏房源、关注房东，验证房源变更后的通知收件箱。

## 测试

后端已有单元测试和 WebMvc 测试，覆盖搜索、推荐、订单、支付、退款、聊天、通知等模块。

运行：

```powershell
mvn test
```

前端测试：

```powershell
cd frontend
npm test
```

## 本地脚本

重建热门房源缓存：

```powershell
scripts/rebuild-hot-house-cache.ps1
```

使用 SkyWalking Agent 启动后端：

```powershell
scripts/run-with-skywalking.ps1
```

SkyWalking 当前定位是本地链路观测辅助工具，不代表项目已经具备完整生产级监控体系。

## 后续优先级

如果继续完善项目，建议只做服务于四条主线的增强：

1. 给锁房、搜索、消息拉取补 JMeter 压测脚本和结果记录。
2. 收藏数从实时 count 演进为 Redis 计数 + 定时校准，并补并发测试。
3. 聊天消息扩展为文本、图片、语音统一消息模型，并补资源鉴权。
4. 关注新房源通知从同步遍历优化为 MQ 异步 fanout。
5. 增加最小 Docker Compose，用于统一启动 MySQL、Redis、RabbitMQ、ES 和后端。
