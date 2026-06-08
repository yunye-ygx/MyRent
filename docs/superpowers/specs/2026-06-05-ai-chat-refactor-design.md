# AI 推荐助手重构设计

## 背景

当前 AI 推荐助手存在以下问题：
- `AiRecommendServiceImpl` 是 1179 行的 God Class
- 无流式输出，用户体验差
- LLM 只做 slot 提取，回复被后端硬编码模板覆盖
- 用 prompt 约束 JSON 输出格式，而非使用 function calling
- Redis 存储 4 个 key，存在一致性风险

本次重构目标：**SSE 流式输出 + Function Calling 工具模式 + DB 持久化**

## 技术栈

- 后端：Spring Boot + Spring AI (OpenAI-compatible, Alibaba Bailian qwen-flash)
- 前端：Vue 3 + SSE (EventSource / fetch + ReadableStream)
- 数据库：MySQL (MyBatis Plus)
- 不再使用 Redis 存储 AI 会话状态

## 架构设计

### 请求流程

```
前端                          后端
  │                            │
  │── POST /ai/chat ────→     │ AiChatController
  │                            │   ↓
  │                            │ AiChatService
  │                            │   1. 加载历史 (DB, 最近N条)
  │                            │   2. 构建 messages: [system, ...history, user]
  │                            │   3. 调 LLM (SSE 流式, 带 tools)
  │                            │       ├─ LLM 输出文字 → SSE event 推前端
  │                            │       └─ LLM 调用工具 → 执行 → 结果喂回 LLM
  │                            │           ├─ searchHouses → RecallService + RankingService
  │                            │           └─ getHouseDetail → HouseService
  │                            │       └─ LLM 最终回复文字 → SSE event 推前端
  │                            │   4. 保存完整对话 (DB)
  │← SSE stream ──────────     │
```

### SSE 事件协议

```
event: text
data: {"content":"浦东确实是个不错的选择，"}

event: tool_call
data: {"tool":"searchHouses","params":{"locationName":"陆家嘴","budgetYuan":3500}}

event: tool_result
data: {"tool":"searchHouses","houses":[...]}

event: text
data: {"content":"我帮你找到了3套还不错的：\n1. XX公寓 3200/月"}

event: done
data: {}
```

前端根据 event type 决定渲染行为：
- `text`：追加到聊天气泡（打字机效果）
- `tool_call`：显示"正在搜索房源..."加载态
- `tool_result`：更新加载态，可选渲染房源卡片
- `done`：结束流，保存消息

## 工具定义

### searchHouses

搜索真实在租房源。LLM 在用户明确或暗示想看具体房源时调用。

```json
{
  "name": "searchHouses",
  "description": "根据用户需求搜索真实在租房源，返回匹配的房源列表。当用户表达了想看房、找房、推荐房源的意图时调用此工具。区域名称必填，其他参数可选——不确定的参数不要填，不要猜测。",
  "parameters": {
    "type": "object",
    "properties": {
      "locationName": {
        "type": "string",
        "description": "区域名称，如'陆家嘴'、'三林'、'世纪公园'。必填。"
      },
      "budgetYuan": {
        "type": "integer",
        "description": "月租预算上限（元），如 3500。可选。"
      },
      "rentMode": {
        "type": "string",
        "enum": ["WHOLE", "SHARED"],
        "description": "WHOLE=整租，SHARED=合租。可选。"
      },
      "nearSubway": {
        "type": "boolean",
        "description": "是否要求靠近地铁站。可选。"
      },
      "privateBathroom": {
        "type": "boolean",
        "description": "是否要求独立卫浴。可选。"
      },
      "hasBalcony": {
        "type": "boolean",
        "description": "是否要求有阳台。可选。"
      },
      "civilWaterElectric": {
        "type": "boolean",
        "description": "是否要求民水民电。可选。"
      },
      "limit": {
        "type": "integer",
        "description": "返回房源数量，默认5，最大10。可选。"
      }
    },
    "required": ["locationName"]
  }
}
```

返回值结构（喂给 LLM）：

```json
{
  "count": 3,
  "houses": [
    {
      "houseId": 123,
      "title": "陆家嘴精装一居室",
      "priceYuan": 3200,
      "rentMode": "WHOLE",
      "distanceToMetroKm": 0.3,
      "highlights": ["近地铁", "独立卫浴"]
    }
  ]
}
```

### getHouseDetail

查询某套房源的详细信息。

```json
{
  "name": "getHouseDetail",
  "description": "查询指定房源的详细信息，包括价格、设施、位置、图片等。当用户问到某套具体房源的详情时调用。",
  "parameters": {
    "type": "object",
    "properties": {
      "houseId": {
        "type": "integer",
        "description": "房源ID。必填。"
      }
    },
    "required": ["houseId"]
  }
}
```

返回值结构（喂给 LLM）：

```json
{
  "houseId": 123,
  "title": "陆家嘴精装一居室",
  "priceYuan": 3200,
  "depositYuan": 3200,
  "rentMode": "WHOLE",
  "area": 35,
  "address": "浦东新区陆家嘴XX路XX号",
  "facilities": ["独立卫浴", "近地铁", "民水民电"],
  "description": "..."
}
```

## System Prompt

```
你是 Roam，一个专业的租房助手。你通过自然对话帮用户找到合适的房子。

## 行为准则

1. 像一个懂行的朋友一样对话，不要像填表机器人
2. 信息不足时，自然地追问并给建议。比如用户说"浦东"，你可以说"浦东很大，你是通勤优先还是环境优先？"
3. 不要一口气问完所有信息，在对话中自然地逐步了解
4. 当你判断用户想看具体房源时，调用 searchHouses 工具。搜索前最好先给用户预期管理，比如"这个预算在陆家嘴整租会比较紧张"
5. 搜到结果后，用口语化的方式总结推荐理由，不要列清单
6. 如果搜索结果为空，建议用户调整条件
7. 用户问到某套房详情时，调用 getHouseDetail
8. 你只能推荐系统中真实存在的房源，不能编造
```

## 数据库设计

### ai_chat_session 表

```sql
CREATE TABLE `ai_chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(128) DEFAULT NULL COMMENT '会话标题，取自第一条用户消息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_update` (`user_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天会话';
```

### ai_chat_message 表

```sql
CREATE TABLE `ai_chat_message` (
  `id` bigint NOT NULL,
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `role` varchar(16) NOT NULL COMMENT 'user / assistant / tool',
  `content` text COMMENT '消息内容',
  `tool_name` varchar(64) DEFAULT NULL COMMENT '工具名称 (role=tool时)',
  `tool_call_id` varchar(128) DEFAULT NULL COMMENT '工具调用ID (Spring AI function calling)',
  `tool_params` text DEFAULT NULL COMMENT '工具调用参数JSON (role=assistant且调用工具时)',
  `tool_result` text DEFAULT NULL COMMENT '工具返回结果JSON (role=tool时)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天消息';
```

`role` 字段遵循 Spring AI 的 message 角色：
- `user`：用户消息
- `assistant`：AI 回复（可能包含 tool_calls）
- `tool`：工具执行结果

## 后端文件结构

### 新增文件

```
service/ai/chat/
├── AiChatService.java              # 接口
├── AiChatServiceImpl.java          # 核心实现（调LLM、执行工具、SSE推送）
├── AiChatSessionService.java       # 会话CRUD
├── AiChatMessageService.java       # 消息CRUD
├── AiChatToolRegistry.java         # 工具注册中心（定义所有可用工具）
├── tools/
│   ├── SearchHousesTool.java       # searchHouses 工具实现
│   └── GetHouseDetailTool.java     # getHouseDetail 工具实现
controller/
├── AiChatController.java           # 新控制器，替代 AiRecommendController
dto/
├── AiChatReqDTO.java               # 请求DTO
vo/
├── AiChatSessionVO.java            # 会话VO
├── AiChatMessageVO.java            # 消息VO
entity/
├── AiChatSession.java              # 会话实体
├── AiChatMessage.java              # 消息实体
mapper/
├── AiChatSessionMapper.java
├── AiChatMessageMapper.java
```

### 需要修改的文件

```
config/
└── SpringAiConfig.java             # 新增：ChatClient Bean 配置，注册 tools
```

### 可删除/废弃的文件（旧 AI 功能）

```
service/ai/
├── AiRecommendServiceImpl.java         # 整个旧实现
├── AiRecommendDecisionClient.java      # 旧 LLM 调用
├── SpringAiRecommendDecisionClient.java
├── AiRecommendStateStore.java          # 旧 Redis 状态
├── RedisAiRecommendStateStore.java
├── AiRecommendSessionState.java        # 旧会话状态
├── AiRecommendStage.java               # 旧状态机
├── AiRecommendSummaryBuilder.java      # 旧摘要
├── AiRecommendRankingPayloadBuilder.java
├── AiRecommendRankingPayload.java
├── AiRecommendPromptLoader.java        # 旧 prompt 加载
├── AiRecommendPromptBundle.java
├── AiPreviewService.java               # 旧预览（功能合并到工具）
├── AiPreviewServiceImpl.java
├── AiRecommendSlots.java               # 旧 slots（LLM 不再需要输出这个）
├── AiRecommendTurn.java
├── AiRecommendDecision.java
├── AiWeightedPreference.java
├── AiPreferenceWeightLevel.java
resources/prompts/ai-recommend/         # 旧 prompt 文件
```

## 前端改动

### SSE 流式请求

替换现有的 `chatAiRecommend()` 调用方式：

```javascript
// 现在：等待完整响应
const result = await chatAiRecommend({ message })

// 改为：SSE 流式
async function streamChat(message, sessionId, onText, onToolCall, onToolResult, onDone) {
  const response = await fetch('/ai/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, sessionId })
  })

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  // ... 解析 SSE event 流，调用对应回调
}
```

### 前端状态简化

不再需要 `stage`、`slots`、`missingSlots`、`preview` 等状态。只需要：

```javascript
const messages = ref([])      // 对话列表
const streaming = ref(false)  // 是否正在流式输出
const currentTool = ref(null) // 当前正在执行的工具
```

### 组件变化

| 现有组件 | 变化 |
|---------|------|
| AiRequirementSummary.vue | 删除（不再有 slots 概念） |
| AiPreviewPanel.vue | 删除（preview 功能合并到 LLM 对话中） |
| AiRecommendationPanel.vue | 简化为房源卡片组件，嵌入聊天气泡中 |
| AiQuickPromptChips.vue | 保留，但快捷提示根据对话上下文变化 |
| AiChatBubble.vue | 增强：支持流式文字动画、工具调用状态显示 |

## 实施顺序

1. **数据库表**：创建 ai_chat_session + ai_chat_message
2. **实体/Mapper/Service**：基础 CRUD
3. **工具实现**：SearchHousesTool、GetHouseDetailTool
4. **AiChatService**：核心服务，集成 Spring AI ChatClient + tools + SSE
5. **Controller**：SSE 端点
6. **前端**：SSE 流式请求 + 打字机效果 + 工具状态显示
7. **清理**：删除旧 AI 代码

## 待定事项

- Spring AI 对 qwen-flash function calling 的兼容性需要实际测试
- 工具执行超时处理（搜索 ES 很慢时的降级策略）
- 对话历史加载多少条发给 LLM（建议最近 20 条）
