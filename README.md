# MyRent

00一个前后端分离的租房练手项目，后端基于 `Spring Boot 3`，前端基于 `Vue 3 + Vite`。

这个 README 按“新手第一次接手仓库”的顺序来写，目标只有两个：

1. 让你把项目完整跑起来
2. 让你知道启动后该怎么验证它确实能用

这次 README 不再内嵌 SQL 语句，数据库相关内容统一使用仓库里的 `sql` 目录。

---

## 1. 项目简介

MyRent 目前已经完成的核心链路包括：

- 用户注册、登录、JWT 鉴权
- 房源发布、房源详情、收藏
- 热门房源、附近房源搜索
- 智能找房推荐
- 定金下单、超时自动关单、房源自动释放
- 聊天会话、消息历史、WebSocket 实时消息推送

项目里也保留了一些“我的”页面入口，但其中一部分还是占位页，不是完整业务模块。这一点下面会单独说明。

---

## 2. 技术栈

### 后端

- Java 17
- Spring Boot 3.5.0
- MyBatis-Plus 3.5.7
- MySQL 8.x
- Redis
- RabbitMQ
- Elasticsearch 8.x
- WebSocket
- Knife4j / OpenAPI

### 前端

- Vue 3
- Vite
- Vue Router
- Pinia
- Axios

---

## 3. 目录说明

```text
MyRent
├─ src/main/java/cn/yy/myrent
│  ├─ controller      # 接口层
│  ├─ service         # 业务层
│  ├─ mapper          # MyBatis-Plus / Mapper
│  ├─ entity          # 实体类
│  ├─ websocket       # WebSocket 在线会话和推送
│  ├─ consumer        # RabbitMQ 消费者
│  ├─ sync            # 房源同步、补偿任务
│  └─ config          # 项目配置
├─ src/main/resources
│  ├─ application.yml # 后端配置
│  ├─ mapper          # XML Mapper
│  └─ Lua             # Redis Lua 脚本
├─ frontend           # Vue 前端
├─ sql                # 数据库脚本
├─ scripts            # SkyWalking 启动脚本
└─ tools              # 本地工具目录
```

---

## 4. 当前完成度

### 已完成的真实功能

- 登录 / 注册
- 首页热门房源
- 按地点名称搜索附近房源
- 智能找房页
- 房源详情页
- 收藏房源
- 我的收藏
- 我的订单
- 消息列表
- 聊天详情页
- WebSocket 实时收消息

### 仍然是占位页的模块

下面这些入口目前能打开页面，但还是 mock/占位状态：

- 个人资料
- 学生认证
- 我的预约
- 浏览记录
- 设置
- 客服与帮助
- 意见反馈

如果你是拿这个项目做毕业设计、课程设计或作品集，这样写 README 会更真实，也更方便答辩时解释当前完成范围。

---

## 5. 运行环境准备

启动这个项目前，建议先准备好下面这些环境：

- JDK 17
- Maven 3.8+
- Node.js 18+
- npm 9+
- MySQL 8.x
- Redis
- RabbitMQ
- Elasticsearch 8.x

### 默认端口

- 后端：`8081`
- 前端：`5173`
- MySQL：`3306`
- Redis：`6379`
- RabbitMQ：`5672`
- Elasticsearch：`9200`

---

## 6. 启动前必须先改的配置

配置文件位置：

`src/main/resources/application.yml`

当前仓库里的默认配置并不是纯本机环境，尤其是：

- RabbitMQ 主机默认写的是 `192.168.100.128`
- Redis 主机默认写的是 `192.168.100.128`
- Elasticsearch 地址默认写的是 `http://192.168.100.128:9200`

如果你是本机启动，请先把这些地址改成你自己的环境。

### 一个本机开发可参考的配置

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/rent?useSSL=false&serverTimezone=UTC
    username: root
    password: 你的MySQL密码

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

至少要重点确认这些字段：

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.rabbitmq.host`
- `spring.rabbitmq.port`
- `spring.rabbitmq.username`
- `spring.rabbitmq.password`
- `spring.data.redis.host`
- `spring.data.redis.port`
- `spring.data.redis.password`
- `spring.elasticsearch.uris`

---

## 7. 数据库准备

### 第一步：先创建数据库

先在 MySQL 里手动创建一个名为 `rent` 的数据库。

### 第二步：导入仓库里的 SQL 文件

SQL 文件目录：

`sql/rent-schema`

推荐导入顺序：

1. `rent-schema-all.sql`
2. `smart-guide-location-dict.sql`

说明：

- `rent-schema-all.sql` 是整套表结构
- `smart-guide-location-dict.sql` 是“智能找房 / 按地点搜索”需要的地点字典测试数据
- 目录里其余 `.sql` 文件是拆分版表结构，按需使用即可

### 一个很重要的提醒

当前仓库里的 SQL 目录主要是建表脚本，不是完整的演示数据包。

也就是说：

- 用户可以通过前端自己注册
- 但是房源测试数据默认不是现成灌好的

如果你想让首页、详情页、收藏、咨询、下单这些链路都能跑起来，后面还需要自己创建几条房源数据。最简单的方式不是写 SQL，而是启动后通过接口文档创建。

---

## 8. 前端启动前的额外配置

这一段非常重要。

当前后端代码里没有额外配置跨域，所以前端开发模式不要直接把 HTTP 请求发到 `http://localhost:8081`，否则浏览器很容易因为跨域请求失败。

推荐做法是让前端走 Vite 代理。

仓库里已经提供了：

`frontend/.env.development`

当前默认内容是：

```env
VITE_API_BASE_URL=/api
```

这意味着你直接执行 `npm run dev` 时，前端 HTTP 请求会走 `vite.config.js` 里的 `/api` 代理，WebSocket 也会走 `/ws` 代理。大多数本机开发场景下，不需要再额外创建环境文件。

只有在你想覆盖本机默认配置时，才需要自己新建：

`frontend/.env.local`

例如跨机器开发时可以这样写：

```env
VITE_API_BASE_URL=http://你的后端IP:8081
VITE_WS_BASE_URL=ws://你的后端IP:8081
```

补充说明：

- `frontend/.env.local` 属于本地私有配置，仓库默认不会提交它
- 如果你这样直连后端，就需要自己处理跨域问题
- 如果只是本机联调，保持仓库默认的 `VITE_API_BASE_URL=/api` 即可

---

## 9. 启动顺序

推荐按下面的顺序启动：

1. MySQL
2. Redis
3. RabbitMQ
4. Elasticsearch
5. 后端 Spring Boot
6. 前端 Vue

---

## 10. 启动后端

在项目根目录执行：

```bash
mvn spring-boot:run
```

如果你想先编译再启动，也可以：

```bash
mvn clean package -DskipTests
mvn spring-boot:run
```

启动成功后，默认地址：

- 后端服务：`http://localhost:8081`
- 接口文档：`http://localhost:8081/doc.html`

---

## 11. 启动前端

进入前端目录：

```bash
cd frontend
npm install
npm run dev
```

启动成功后访问：

`http://localhost:5173`

---

## 12. 第一次启动后怎么验证

如果你只是想确认“项目真的跑起来了”，建议按这个顺序做。

### 12.1 先确认页面能打开

检查下面这几个地址：

- `http://localhost:8081/doc.html`
- `http://localhost:5173/login`
- `http://localhost:5173/register`

只要这三个都能正常打开，说明前后端基本已经起来了。

### 12.2 先注册两个账号

在前端注册两个用户，建议这样分工：

- 用户 A：房东 / 发布者
- 用户 B：租客 / 咨询者 / 下单者

建议用两个浏览器窗口测试：

- 正常窗口登录用户 A
- 无痕窗口登录用户 B

这样后面测试聊天最方便。

### 12.3 用接口文档创建几条房源数据

当前前端没有“发布房源”页面，所以第一次联调最简单的方式是：

1. 用用户 A 登录
2. 打开 `http://localhost:8081/doc.html`
3. 先调用 `/user/login` 获取 token
4. 调用 `POST /house` 创建房源
5. 在请求头里手动带上：

```text
Authorization: Bearer 你的token
```

创建房源时，建议使用这种测试数据风格：

- `rentType`：`1` 表示整租，`2` 表示合租
- `price` 和 `depositAmount` 单位都是“分”
- `status` 用 `1`
- `version` 用 `0`
- 经纬度尽量放在上海这些测试地点附近，方便智能找房和附近搜索直接出结果

示例请求体：

```json
{
  "title": "人民广场地铁口一居室",
  "rentType": 1,
  "price": 450000,
  "depositAmount": 100000,
  "longitude": 121.4762,
  "latitude": 31.2320,
  "status": 1,
  "version": 0
}
```

再补充说明一次：

- `450000` 表示 `4500.00 元`
- `100000` 表示 `1000.00 元`

建议至少创建 2 到 3 条房源。

### 12.4 首次创建房源后，手动重建一次 ES 数据

如果你是首次准备测试数据，建议执行一次：

`POST /house/es/rebuild-all`

这样可以保证 Elasticsearch 里的房源索引和 MySQL 保持一致，后面测试：

- 热门房源
- 附近房源
- 智能找房

都会更稳定。

### 12.5 再回到前端验证核心链路

使用用户 B 登录前端后，按下面顺序验证：

1. 首页是否能看到房源
2. 点进房源详情页
3. 点“收藏”
4. 点“咨询”
5. 点“提交定金”
6. 去“我的收藏”看数据
7. 去“我的订单”看数据

如果这些能走通，说明项目主链路已经正常。

---

## 13. 推荐的完整联调流程

如果你想把整套项目从前到后都演示一遍，推荐这样操作：

### 场景一：热门房源 / 房源详情 / 收藏

1. 用户 A 创建几条房源
2. 用户 B 登录前端
3. 首页查看房源
4. 进入房源详情页
5. 点击收藏
6. 打开“我的收藏”确认数据已出现

### 场景二：聊天 + WebSocket 实时消息

1. 用户 A 保持登录，并打开消息页 `http://localhost:5173/messages`
2. 用户 B 打开某个房源详情页
3. 点击“咨询”
4. 进入聊天页后发送消息
5. 用户 A 应该可以在消息页和聊天页实时看到新消息

说明：

- 聊天消息会先落库
- 如果接收方在线，会通过 WebSocket 实时推送
- 如果中间断线，前端还会补拉历史消息和未读消息

### 场景三：定金下单 + 超时自动关单

1. 用户 B 在房源详情页点击“提交定金”
2. 去“我的订单”查看新订单
3. 当前代码里订单超时时间大约是 30 秒
4. 如果没有支付，等待一小会儿后，订单应自动变成关闭状态
5. 同时房源状态应重新释放

这个流程依赖：

- Redis Lua 锁房
- MySQL 订单落库
- 本地任务表 `local_task`
- RabbitMQ 延迟 / 死信链路
- 订单超时消费者

### 场景四：附近房源 / 智能找房

这两个功能都和 `location_dict` 里的测试地点有关。

当前仓库自带的地点字典是上海一组地点，例如：

- 人民广场
- 徐家汇
- 陆家嘴
- 静安寺
- 中山公园
- 世纪大道
- 张江高科
- 五角场
- 虹桥火车站
- 漕河泾开发区

注意：

- 前端首页文案目前写的是“广州”，这只是前端展示文案
- 实际测试时，请优先输入上面这些上海地点
- 你创建的测试房源坐标也最好放在这些地点附近

这样：

- 首页的“按地点搜索附近房源”
- 找房页的“智能找房”

都更容易一次成功。

---

## 14. 关键接口速查

### 用户

- `POST /user/register`
- `POST /user/login`

### 房源

- `GET /house/hot`
- `POST /house/nearby`
- `POST /house/smart-guide`
- `GET /house/{id}`
- `POST /house`
- `POST /house/es/rebuild-all`

### 收藏

- `POST /house-favorite/{houseId}`
- `DELETE /house-favorite/{houseId}`
- `GET /house-favorite/mine`

### 订单

- `POST /order/createOrder`
- `GET /order/mine`

### 聊天

- `GET /chat-session/mine`
- `POST /chat-session/send`
- `GET /chat-message/history`
- `GET /chat-message/pull`
- `POST /chat-message/read`
- WebSocket：`/ws/chat?token=你的token`

---

## 15. 常见问题

### 15.1 后端启动失败，提示连不上某个中间件

优先检查：

- MySQL 是否启动
- Redis 是否启动
- RabbitMQ 是否启动
- Elasticsearch 是否启动
- `application.yml` 里的地址、账号、密码是不是你自己机器上的

### 15.2 前端打开了，但接口全报错

大概率检查这两件事：

1. 你有没有在 `frontend/.env.local` 里配置 `VITE_API_BASE_URL=/api`
2. 你有没有重启过 `npm run dev`

如果没有这一步，浏览器很可能直接跨域请求 `http://localhost:8081`，然后失败。

### 15.3 登录成功了，但调接口还是 401

受保护接口需要带 JWT。

前端页面里会自动带 token，但在 Knife4j 里你需要自己加请求头：

```text
Authorization: Bearer 你的token
```

### 15.4 首页没有房源

通常是下面几种原因：

- 你还没有创建房源测试数据
- 房源在 MySQL 里有，但 ES 还没同步
- 这时建议先调用一次 `POST /house/es/rebuild-all`

### 15.5 搜“附近房源”或“智能找房”没结果

先检查：

- `smart-guide-location-dict.sql` 是否已经导入
- 你输入的地点是不是字典里存在的测试地点
- 房源坐标是不是在这些地点附近
- Elasticsearch 是否正常

第一次联调建议直接用这些关键词：

- `人民广场`
- `徐家汇`
- `陆家嘴`
- `静安寺`

### 15.6 聊天消息发出去了，但对方收不到实时消息

检查：

- 接收方是否已经登录
- 接收方页面是否打开了消息页或聊天页
- WebSocket 是否连上
- token 是否有效

前端聊天页会自动连接：

`/ws/chat?token=你的token`

### 15.7 定金订单没有自动关单

先检查这几个环节：

- RabbitMQ 是否正常
- `local_task` 表里有没有任务
- 后端日志里有没有 `OrderTimeoutTaskConsumer` 相关日志
- Redis、MQ、数据库是否都在正常工作

---

## 16. 你可以把这个项目理解成什么

如果你是学习用途，这个项目最值得看的不是“页面有多复杂”，而是几条比较完整的后端链路：

- JWT 登录鉴权
- 房源 MySQL -> MQ -> ES 同步
- Redis Lua 锁房 + MySQL 状态更新
- RabbitMQ 超时关单
- 聊天落库 + WebSocket 推送 + 补拉机制

它比较适合：

- Java 后端课程设计
- 毕业设计中的功能模块展示
- Spring Boot + 中间件整合练手
- 前后端分离项目答辩演示

---

## 17. 补充说明

- `scripts/` 和 `tools/` 主要是 SkyWalking 相关内容，正常启动项目不需要先动它们
- `frontend/node_modules/` 是前端依赖目录，`frontend/dist/`、`target/` 是构建产物，这些都属于本地生成内容，不会随仓库一起提交
- 所以你第一次拿到前端代码后，需要先在 `frontend` 目录执行一次 `npm install`
- 如果你后面要继续完善项目，最适合优先补的部分通常是：
  - 管理员后台
  - 房源发布页面
  - 真正的支付回调
  - 跨域和部署配置
  - 完整测试用例

---

如果你只是想先跑起来，照着这份文档做完这几步就够了：

1. 改 `application.yml`
2. 导入 `sql/rent-schema/rent-schema-all.sql`
3. 导入 `sql/rent-schema/smart-guide-location-dict.sql`
4. 确认 `frontend/.env.development` 里的 `VITE_API_BASE_URL=/api` 可用；只有需要覆盖本机配置时再创建 `frontend/.env.local`
5. 启动 MySQL / Redis / RabbitMQ / Elasticsearch
6. 启动后端
7. 启动前端
8. 注册两个账号
9. 用接口文档创建几条房源
10. 调一次 `POST /house/es/rebuild-all`

做到这里，这个项目就已经可以正常演示了。
