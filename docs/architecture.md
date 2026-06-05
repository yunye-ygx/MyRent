# 架构设计与实现细节

本文档记录 MyRent 四条核心链路的详细设计、代码位置、扩展点状态和后续优先级。

## 1. 交易一致性链路

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

## 2. 搜索推荐链路

已实现：

- 附近房源搜索：基于 ES geo distance 查询，并按距离排序。
- 附近搜索 ES 超时或异常时，降级到 Redis 热门房源；Redis 为空或异常时再降级 DB。
- 热门房源：基于 Redis ZSet 保存热榜，定时从收藏、咨询、回复等行为指标重建。
- 关键词搜索：地点召回和文本召回并发执行，合并候选后再统一排序。
- 列表筛选：支持城市、区域、租住方式、价格区间、近地铁、独卫、阳台、民水民电、学生免押等条件。
- 多因子排序：综合召回命中、文本相关性、距离、预算接近度、租住方式、房源特征、发布时间等因素。
- 搜索原因输出：返回命中原因，前端可以展示"位置匹配""关键词命中""距离优势"等说明。
- 智能找房和 AI 推荐复用召回与排序链路，并支持预算放宽、偏好权重和推荐说明。

主要代码位置：

- `src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java`
- `src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRecallServiceImpl.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRankingServiceImpl.java`
- `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java`

## 3. 房源数据同步链路

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

## 4. 消息与通知链路

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
| 链路观测 | 部分实现 | 仓库有 SkyWalking Agent 下载/启动脚本和本地 agent 文件；不代表完整生产监控体系。 |
| 压测量化 | 待补充 | 暂未看到 JMeter 压测报告或固定压测脚本。后续可以补锁房、搜索、消息拉取三个接口的简单压测结果。 |
| 项目上线 | 待补充 | 当前以本地开发运行为主，未提供完整 Docker Compose 或云服务器部署说明。 |

## 后续优先级

如果继续完善项目，建议只做服务于四条主线的增强：

1. 给锁房、搜索、消息拉取补 JMeter 压测脚本和结果记录。
2. 收藏数从实时 count 演进为 Redis 计数 + 定时校准，并补并发测试。
3. 聊天消息扩展为文本、图片、语音统一消息模型，并补资源鉴权。
4. 关注新房源通知从同步遍历优化为 MQ 异步 fanout。
5. 增加最小 Docker Compose，用于统一启动 MySQL、Redis、RabbitMQ、ES 和后端。
