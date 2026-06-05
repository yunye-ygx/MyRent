# 数据库表概览

SQL 位于 `sql/rent-schema`。

## 核心表

| 表名 | 说明 |
| --- | --- |
| `user` | 用户信息 |
| `house` | 房源信息 |
| `house_favorite` | 房源收藏关系，包含 `uk_user_house` |
| `publisher_follow` | 关注房东关系，包含 `uk_follow_user_publisher` |
| `notification` | 站内通知，包含 `uk_notification_user_biz` |
| `chat_session` | 聊天会话 |
| `chat_message` | 聊天消息，消息 ID 由代码侧雪花算法生成 |
| `order` | 定金订单 |
| `payment` | 支付单 |
| `mock_pay_trade` | 模拟第三方支付交易 |
| `payment_refund` | 退款单 |
| `local_task` | 本地任务表 / 延迟任务表 |
| `review` | 评价 |
| `house_history` | 浏览历史 |
