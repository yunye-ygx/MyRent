# AI 推荐助手重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 AI 推荐助手从 prompt 模板 + JSON 格式约束改为 Function Calling 工具模式，新增 SSE 流式输出，对话历史存入 MySQL。

**Architecture:** 后端使用 Spring AI ChatClient 的 Function Calling 能力，手动处理工具调用循环（call → check tools → execute → loop），最后一次调用使用 stream() 获取文字流，通过 SseEmitter 推送给前端。前端使用 fetch + ReadableStream 解析 NDJSON 格式的 SSE 事件。

**Tech Stack:** Spring Boot 3 + Spring AI 1.1.4 (OpenAI-compatible, qwen-flash) + MyBatis Plus + Vue 3 + SSE

---

## File Structure

### 新增文件

| 文件 | 职责 |
|------|------|
| `sql/rent-schema/ai_chat_session.sql` | 会话表 DDL |
| `sql/rent-schema/ai_chat_message.sql` | 消息表 DDL |
| `entity/AiChatSession.java` | 会话实体 |
| `entity/AiChatMessage.java` | 消息实体 |
| `mapper/AiChatSessionMapper.java` | 会话 Mapper |
| `mapper/AiChatMessageMapper.java` | 消息 Mapper |
| `service/ai/chat/AiChatHistoryService.java` | 接口：会话和消息 CRUD |
| `service/ai/chat/AiChatHistoryServiceImpl.java` | 实现 |
| `service/ai/chat/tools/SearchHousesTool.java` | searchHouses 工具 |
| `service/ai/chat/tools/GetHouseDetailTool.java` | getHouseDetail 工具 |
| `service/ai/chat/AiChatToolCallbackProvider.java` | 工具注册 |
| `service/ai/chat/AiChatService.java` | 接口：核心聊天服务 |
| `service/ai/chat/AiChatServiceImpl.java` | 实现：SSE 流式 + 工具循环 |
| `controller/AiChatController.java` | SSE 端点 |
| `dto/AiChatReqDTO.java` | 请求 DTO |
| `frontend/src/api/aiChat.js` | SSE 客户端 |
| `frontend/src/views/AiChatView.vue` | 新页面 |
| `frontend/src/components/ai/AiChatMessage.vue` | 增强气泡组件 |

### 需修改的文件

| 文件 | 改动 |
|------|------|
| `frontend/src/router/index.js` | 添加 `/ai-chat` 路由 |

---

## Task 1: 数据库表

**Files:**
- Create: `sql/rent-schema/ai_chat_session.sql`
- Create: `sql/rent-schema/ai_chat_message.sql`

- [ ] **Step 1: 创建 ai_chat_session 表**

文件 `sql/rent-schema/ai_chat_session.sql`:

```sql
DROP TABLE IF EXISTS `ai_chat_session`;
CREATE TABLE `ai_chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(128) DEFAULT NULL COMMENT '会话标题，取自第一条用户消息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_update` (`user_id`, `update_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天会话';
```

- [ ] **Step 2: 创建 ai_chat_message 表**

文件 `sql/rent-schema/ai_chat_message.sql`:

```sql
DROP TABLE IF EXISTS `ai_chat_message`;
CREATE TABLE `ai_chat_message` (
  `id` bigint NOT NULL,
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `role` varchar(16) NOT NULL COMMENT 'user / assistant / tool',
  `content` text COMMENT '消息文本内容',
  `tool_name` varchar(64) DEFAULT NULL COMMENT '工具名称',
  `tool_call_id` varchar(128) DEFAULT NULL COMMENT '工具调用ID',
  `tool_params` text DEFAULT NULL COMMENT '工具调用参数JSON',
  `tool_result` text DEFAULT NULL COMMENT '工具返回结果JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天消息';
```

- [ ] **Step 3: 执行 DDL**

在 MySQL 中执行这两个 SQL 文件。

- [ ] **Step 4: Commit**

```bash
git add sql/rent-schema/ai_chat_session.sql sql/rent-schema/ai_chat_message.sql
git commit -m "feat(ai-chat): add database tables for AI chat sessions and messages"
```

---

## Task 2: 实体和 Mapper

**Files:**
- Create: `src/main/java/cn/yy/myrent/entity/AiChatSession.java`
- Create: `src/main/java/cn/yy/myrent/entity/AiChatMessage.java`
- Create: `src/main/java/cn/yy/myrent/mapper/AiChatSessionMapper.java`
- Create: `src/main/java/cn/yy/myrent/mapper/AiChatMessageMapper.java`

- [ ] **Step 1: 创建 AiChatSession 实体**

```java
package cn.yy.myrent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_chat_session")
public class AiChatSession {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
```

- [ ] **Step 2: 创建 AiChatMessage 实体**

```java
package cn.yy.myrent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    /** user / assistant / tool */
    private String role;

    private String content;

    private String toolName;

    private String toolCallId;

    /** 工具调用参数 JSON */
    private String toolParams;

    /** 工具返回结果 JSON */
    private String toolResult;

    private LocalDateTime createTime;
}
```

- [ ] **Step 3: 创建 Mapper 接口**

```java
package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.AiChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {
}
```

```java
package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.AiChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/cn/yy/myrent/entity/AiChatSession.java src/main/java/cn/yy/myrent/entity/AiChatMessage.java src/main/java/cn/yy/myrent/mapper/AiChatSessionMapper.java src/main/java/cn/yy/myrent/mapper/AiChatMessageMapper.java
git commit -m "feat(ai-chat): add entity and mapper classes"
```

---

## Task 3: AiChatHistoryService

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/ai/chat/AiChatHistoryService.java`
- Create: `src/main/java/cn/yy/myrent/service/ai/chat/AiChatHistoryServiceImpl.java`

- [ ] **Step 1: 创建接口**

```java
package cn.yy.myrent.service.ai.chat;

import cn.yy.myrent.entity.AiChatMessage;
import cn.yy.myrent.entity.AiChatSession;
import java.util.List;

public interface AiChatHistoryService {

    AiChatSession getOrCreateSession(Long userId);

    List<AiChatSession> listSessions(Long userId);

    List<AiChatMessage> loadMessages(Long sessionId, int limit);

    void saveMessage(AiChatMessage message);

    void saveMessages(List<AiChatMessage> messages);
}
```

- [ ] **Step 2: 创建实现**

```java
package cn.yy.myrent.service.ai.chat;

import cn.yy.myrent.entity.AiChatMessage;
import cn.yy.myrent.entity.AiChatSession;
import cn.yy.myrent.mapper.AiChatMessageMapper;
import cn.yy.myrent.mapper.AiChatSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiChatHistoryServiceImpl implements AiChatHistoryService {

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;

    @Override
    public AiChatSession getOrCreateSession(Long userId) {
        AiChatSession session = sessionMapper.selectOne(
                new LambdaQueryWrapper<AiChatSession>()
                        .eq(AiChatSession::getUserId, userId)
                        .orderByDesc(AiChatSession::getUpdateTime)
                        .last("LIMIT 1")
        );
        if (session != null) {
            return session;
        }
        session = new AiChatSession();
        session.setUserId(userId);
        session.setTitle("AI 找房助手");
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public List<AiChatSession> listSessions(Long userId) {
        return sessionMapper.selectList(
                new LambdaQueryWrapper<AiChatSession>()
                        .eq(AiChatSession::getUserId, userId)
                        .orderByDesc(AiChatSession::getUpdateTime)
        );
    }

    @Override
    public List<AiChatMessage> loadMessages(Long sessionId, int limit) {
        List<AiChatMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .orderByAsc(AiChatMessage::getId)
        );
        if (messages.size() <= limit) {
            return messages;
        }
        return new ArrayList<>(messages.subList(messages.size() - limit, messages.size()));
    }

    @Override
    public void saveMessage(AiChatMessage message) {
        messageMapper.insert(message);
    }

    @Override
    public void saveMessages(List<AiChatMessage> messages) {
        for (AiChatMessage message : messages) {
            messageMapper.insert(message);
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/ai/chat/AiChatHistoryService.java src/main/java/cn/yy/myrent/service/ai/chat/AiChatHistoryServiceImpl.java
git commit -m "feat(ai-chat): add chat history service"
```

---

## Task 4: SearchHousesTool

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/ai/chat/tools/SearchHousesTool.java`

这个工具封装了 `HouseRecallService` + `HouseRankingService`，给 LLM 调用。

- [ ] **Step 1: 创建 SearchHousesTool**

```java
package cn.yy.myrent.service.ai.chat.tools;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.discovery.*;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchHousesTool {

    private final HouseRecallService houseRecallService;
    private final HouseRankingService houseRankingService;

    @Tool(description = """
            根据用户需求搜索真实在租房源，返回匹配的房源列表。
            当用户表达了想看房、找房、推荐房源的意图时调用此工具。
            区域名称必填，其他参数可选——不确定的参数不要填，不要猜测用户没有说的信息。
            """)
    public String searchHouses(
            @ToolParam(description = "区域名称，如'陆家嘴'、'三林'、'世纪公园'。必填。") String locationName,
            @ToolParam(description = "月租预算上限（元），如 3500。用户没说就不填。") Integer budgetYuan,
            @ToolParam(description = "WHOLE=整租，SHARED=合租。用户没说就不填。") String rentMode,
            @ToolParam(description = "是否要求靠近地铁站。用户没说就不填。") Boolean nearSubway,
            @ToolParam(description = "是否要求独立卫浴。用户没说就不填。") Boolean privateBathroom,
            @ToolParam(description = "是否要求有阳台。用户没说就不填。") Boolean hasBalcony,
            @ToolParam(description = "是否要求民水民电。用户没说就不填。") Boolean civilWaterElectric,
            @ToolParam(description = "返回房源数量，默认5，最大10。") Integer limit
    ) {
        int pageSize = (limit != null && limit > 0 && limit <= 10) ? limit : 5;

        HouseRecallQuery recallQuery = HouseRecallQuery.builder()
                .locationName(locationName)
                .budgetYuan(budgetYuan)
                .rentMode(rentMode)
                .nearSubway(nearSubway)
                .privateBathroom(privateBathroom)
                .hasBalcony(hasBalcony)
                .civilWaterElectric(civilWaterElectric)
                .page(1)
                .size(pageSize)
                .recallProfile(HouseRecallProfile.AI_RECOMMEND)
                .build();

        HouseRankQuery rankQuery = HouseRankQuery.builder()
                .budgetYuan(budgetYuan)
                .budgetScope("RENT_ONLY")
                .rentMode(rentMode)
                .nearSubway(nearSubway)
                .privateBathroom(privateBathroom)
                .hasBalcony(hasBalcony)
                .civilWaterElectric(civilWaterElectric)
                .page(1)
                .size(pageSize)
                .rankingProfile(HouseRankingProfile.AI_RECOMMEND_DEFAULT)
                .build();

        try {
            HouseRecallResult recallResult = houseRecallService.recall(recallQuery);
            if (recallResult.candidates().isEmpty()) {
                return "{\"count\":0,\"message\":\"当前条件下没有找到匹配的房源，建议用户调整预算或扩大区域范围。\"}";
            }

            HouseRankResult rankResult = houseRankingService.rank(recallResult.candidates(), rankQuery);
            List<Map<String, Object>> houses = new ArrayList<>();

            for (HouseRankedItem item : rankResult.currentPageItems()) {
                House house = item.house();
                if (house == null) continue;

                Map<String, Object> h = new LinkedHashMap<>();
                h.put("houseId", house.getId());
                h.put("title", house.getTitle());
                h.put("priceYuan", toYuan(house.getPrice()));
                h.put("rentMode", Integer.valueOf(1).equals(house.getRentType()) ? "整租" : "合租");

                List<String> highlights = new ArrayList<>();
                if (Integer.valueOf(1).equals(house.getNearSubway())) highlights.add("近地铁");
                if (Integer.valueOf(1).equals(house.getPrivateBathroom())) highlights.add("独立卫浴");
                if (Integer.valueOf(1).equals(house.getHasBalcony())) highlights.add("带阳台");
                if (Integer.valueOf(1).equals(house.getCivilWaterElectric())) highlights.add("民水民电");
                h.put("highlights", highlights);

                houses.add(h);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", houses.size());
            result.put("location", locationName);
            result.put("houses", houses);
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);
        } catch (Exception e) {
            log.error("searchHouses failed for location={}", locationName, e);
            return "{\"count\":0,\"message\":\"搜索暂时不可用，请稍后再试。\"}";
        }
    }

    private BigDecimal toYuan(Integer cent) {
        if (cent == null) return null;
        return BigDecimal.valueOf(cent).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/ai/chat/tools/SearchHousesTool.java
git commit -m "feat(ai-chat): add SearchHousesTool"
```

---

## Task 5: GetHouseDetailTool

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/ai/chat/tools/GetHouseDetailTool.java`

- [ ] **Step 1: 创建 GetHouseDetailTool**

```java
package cn.yy.myrent.service.ai.chat.tools;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.IHouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetHouseDetailTool {

    private final IHouseService houseService;

    @Tool(description = """
            查询指定房源的详细信息，包括价格、设施、位置等。
            当用户问到某套具体房源的详情时调用。
            需要房源ID，通常来自 searchHouses 的返回结果。
            """)
    public String getHouseDetail(
            @ToolParam(description = "房源ID，必填。") Long houseId
    ) {
        try {
            House house = houseService.getById(houseId);
            if (house == null) {
                return "{\"error\":\"房源不存在或已下架\"}";
            }

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("houseId", house.getId());
            detail.put("title", house.getTitle());
            detail.put("city", house.getCity());
            detail.put("region", house.getRegion());
            detail.put("priceYuan", toYuan(house.getPrice()));
            detail.put("depositYuan", toYuan(house.getDepositAmount()));
            detail.put("rentMode", Integer.valueOf(1).equals(house.getRentType()) ? "整租" : "合租");

            List<String> facilities = new ArrayList<>();
            if (Integer.valueOf(1).equals(house.getNearSubway())) facilities.add("近地铁");
            if (Integer.valueOf(1).equals(house.getPrivateBathroom())) facilities.add("独立卫浴");
            if (Integer.valueOf(1).equals(house.getHasBalcony())) facilities.add("带阳台");
            if (Integer.valueOf(1).equals(house.getCivilWaterElectric())) facilities.add("民水民电");
            if (Integer.valueOf(1).equals(house.getSupportStudentDepositFree())) facilities.add("学生免押");
            detail.put("facilities", facilities);

            detail.put("status", Integer.valueOf(1).equals(house.getStatus()) ? "可租" : "已锁定");

            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(detail);
        } catch (Exception e) {
            log.error("getHouseDetail failed for houseId={}", houseId, e);
            return "{\"error\":\"查询房源详情失败\"}";
        }
    }

    private BigDecimal toYuan(Integer cent) {
        if (cent == null) return null;
        return BigDecimal.valueOf(cent).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/ai/chat/tools/GetHouseDetailTool.java
git commit -m "feat(ai-chat): add GetHouseDetailTool"
```

---

## Task 6: AiChatService 核心服务

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/ai/chat/AiChatService.java`
- Create: `src/main/java/cn/yy/myrent/service/ai/chat/AiChatServiceImpl.java`

这是最核心的部分：加载历史 → 构建消息 → LLM 工具循环 → 流式输出。

- [ ] **Step 1: 创建 AiChatService 接口**

```java
package cn.yy.myrent.service.ai.chat;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiChatService {

    void chat(Long userId, String message, Long sessionId, SseEmitter emitter);
}
```

- [ ] **Step 2: 创建 AiChatServiceImpl**

```java
package cn.yy.myrent.service.ai.chat;

import cn.yy.myrent.entity.AiChatMessage;
import cn.yy.myrent.entity.AiChatSession;
import cn.yy.myrent.service.ai.chat.tools.GetHouseDetailTool;
import cn.yy.myrent.service.ai.chat.tools.SearchHousesTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private static final int HISTORY_LIMIT = 20;
    private static final int MAX_TOOL_ROUNDS = 5;
    private static final long SSE_TIMEOUT = 120_000L;

    private static final String SYSTEM_PROMPT = """
            你是 Roam，一个专业的租房助手。你通过自然对话帮用户找到合适的房子。

            ## 行为准则

            1. 像一个懂行的朋友一样对话，不要像填表机器人
            2. 信息不足时，自然地追问并给建议。比如用户说"浦东"，你可以说"浦东很大，你是通勤优先还是环境优先？"
            3. 不要一口气问完所有信息，在对话中自然地逐步了解
            4. 当你判断用户想看具体房源时，调用 searchHouses 工具。搜索前最好先给用户预期管理
            5. 搜到结果后，用口语化的方式总结推荐理由，不要列清单
            6. 如果搜索结果为空，建议用户调整条件
            7. 用户问到某套房详情时，调用 getHouseDetail
            8. 你只能推荐系统中真实存在的房源，不能编造
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final AiChatHistoryService historyService;
    private final SearchHousesTool searchHousesTool;
    private final GetHouseDetailTool getHouseDetailTool;
    private final ObjectMapper objectMapper;

    @Override
    public void chat(Long userId, String userMessage, Long sessionId, SseEmitter emitter) {
        CompletableFuture.runAsync(() -> {
            List<AiChatMessage> newMessages = new ArrayList<>();
            try {
                AiChatSession session = resolveSession(userId, sessionId);
                Long actualSessionId = session.getId();

                // 1. 保存用户消息
                AiChatMessage userMsg = new AiChatMessage();
                userMsg.setSessionId(actualSessionId);
                userMsg.setRole("user");
                userMsg.setContent(userMessage);
                userMsg.setCreateTime(LocalDateTime.now());
                historyService.saveMessage(userMsg);
                newMessages.add(userMsg);

                // 更新会话标题
                if (session.getTitle() == null || "AI 找房助手".equals(session.getTitle())) {
                    String title = userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage;
                    session.setTitle(title);
                    // title update via mapper if needed
                }

                // 2. 加载历史消息
                List<Message> history = buildHistory(actualSessionId);

                // 3. 工具调用循环
                List<Message> messageAccumulator = new ArrayList<>(history);
                ChatClient chatClient = chatClientBuilder.build();
                int toolRounds = 0;

                while (toolRounds < MAX_TOOL_ROUNDS) {
                    Prompt prompt = new Prompt(messageAccumulator);
                    ChatResponse response = chatClient.prompt(prompt)
                            .system(SYSTEM_PROMPT)
                            .tools(searchHousesTool, getHouseDetailTool)
                            .call()
                            .chatResponse();

                    AssistantMessage assistantMsg = response.getResult().getOutput();
                    messageAccumulator.add(assistantMsg);

                    // 检查是否有工具调用
                    List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
                    if (toolCalls == null || toolCalls.isEmpty()) {
                        // 没有工具调用，这是最终回复
                        String finalText = assistantMsg.getText();
                        sendSseEvent(emitter, "text", "{\"content\":" + jsonEscape(finalText) + "}");

                        // 保存 assistant 消息
                        AiChatMessage assistantDbMsg = new AiChatMessage();
                        assistantDbMsg.setSessionId(actualSessionId);
                        assistantDbMsg.setRole("assistant");
                        assistantDbMsg.setContent(finalText);
                        assistantDbMsg.setCreateTime(LocalDateTime.now());
                        historyService.saveMessage(assistantDbMsg);
                        break;
                    }

                    // 有工具调用，执行工具
                    toolRounds++;
                    for (AssistantMessage.ToolCall toolCall : toolCalls) {
                        String toolName = toolCall.name();
                        String toolArgs = toolCall.arguments();

                        // 通知前端正在调用工具
                        sendSseEvent(emitter, "tool_call",
                                "{\"tool\":" + jsonEscape(toolName) + ",\"params\":" + toolArgs + "}");

                        // 执行工具
                        String toolResultJson = executeTool(toolName, toolArgs);

                        // 通知前端工具结果
                        sendSseEvent(emitter, "tool_result",
                                "{\"tool\":" + jsonEscape(toolName) + ",\"result\":" + toolResultJson + "}");

                        // 构建 ToolResponseMessage 并加入消息列表
                        org.springframework.ai.chat.messages.ToolResponseMessage toolResponseMsg =
                                new org.springframework.ai.chat.messages.ToolResponseMessage(
                                        List.of(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                                                toolCall.id(), toolName, toolResultJson
                                        ))
                                );
                        messageAccumulator.add(toolResponseMsg);

                        // 保存 tool 调用记录
                        AiChatMessage toolCallDbMsg = new AiChatMessage();
                        toolCallDbMsg.setSessionId(actualSessionId);
                        toolCallDbMsg.setRole("assistant");
                        toolCallDbMsg.setContent(null);
                        toolCallDbMsg.setToolName(toolName);
                        toolCallDbMsg.setToolCallId(toolCall.id());
                        toolCallDbMsg.setToolParams(toolArgs);
                        toolCallDbMsg.setCreateTime(LocalDateTime.now());
                        historyService.saveMessage(toolCallDbMsg);

                        AiChatMessage toolResultDbMsg = new AiChatMessage();
                        toolResultDbMsg.setSessionId(actualSessionId);
                        toolResultDbMsg.setRole("tool");
                        toolResultDbMsg.setContent(toolResultJson);
                        toolResultDbMsg.setToolName(toolName);
                        toolResultDbMsg.setToolCallId(toolCall.id());
                        toolResultDbMsg.setToolResult(toolResultJson);
                        toolResultDbMsg.setCreateTime(LocalDateTime.now());
                        historyService.saveMessage(toolResultDbMsg);
                    }
                }

                sendSseEvent(emitter, "done", "{}");
                emitter.complete();

            } catch (Exception e) {
                log.error("AI chat failed for userId={}", userId, e);
                try {
                    sendSseEvent(emitter, "error", "{\"message\":\"抱歉，出了点问题，请稍后再试。\"}");
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });
    }

    private AiChatSession resolveSession(Long userId, Long sessionId) {
        if (sessionId != null) {
            // TODO: validate session belongs to user
            AiChatSession session = new AiChatSession();
            session.setId(sessionId);
            session.setUserId(userId);
            return session;
        }
        return historyService.getOrCreateSession(userId);
    }

    private List<Message> buildHistory(Long sessionId) {
        List<AiChatMessage> dbMessages = historyService.loadMessages(sessionId, HISTORY_LIMIT);
        List<Message> messages = new ArrayList<>();
        for (AiChatMessage msg : dbMessages) {
            switch (msg.getRole()) {
                case "user" -> messages.add(new UserMessage(msg.getContent()));
                case "assistant" -> {
                    if (msg.getToolName() != null) {
                        // 这是一条工具调用记录，构建 AssistantMessage with ToolCall
                        // 注意：简化处理，完整实现需要保存完整的 toolCall 结构
                    } else if (msg.getContent() != null) {
                        messages.add(new AssistantMessage(msg.getContent()));
                    }
                }
                case "tool" -> {
                    // 工具结果消息在 history 重建时需要特殊处理
                    // 完整实现需要从 toolCallId 关联
                }
            }
        }
        return messages;
    }

    private String executeTool(String toolName, String toolArgsJson) {
        try {
            // 简单的工具路由
            // 完整实现应该使用 ToolCallingManager 或反序列化参数
            return switch (toolName) {
                case "searchHouses" -> {
                    var params = objectMapper.readTree(toolArgsJson);
                    String location = params.has("locationName") ? params.get("locationName").asText() : null;
                    Integer budget = params.has("budgetYuan") && !params.get("budgetYuan").isNull()
                            ? params.get("budgetYuan").asInt() : null;
                    String rentMode = params.has("rentMode") && !params.get("rentMode").isNull()
                            ? params.get("rentMode").asText() : null;
                    Boolean nearSubway = params.has("nearSubway") && !params.get("nearSubway").isNull()
                            ? params.get("nearSubway").asBoolean() : null;
                    Boolean privateBathroom = params.has("privateBathroom") && !params.get("privateBathroom").isNull()
                            ? params.get("privateBathroom").asBoolean() : null;
                    Boolean hasBalcony = params.has("hasBalcony") && !params.get("hasBalcony").isNull()
                            ? params.get("hasBalcony").asBoolean() : null;
                    Boolean civilWaterElectric = params.has("civilWaterElectric") && !params.get("civilWaterElectric").isNull()
                            ? params.get("civilWaterElectric").asBoolean() : null;
                    Integer limit = params.has("limit") && !params.get("limit").isNull()
                            ? params.get("limit").asInt() : null;
                    yield searchHousesTool.searchHouses(location, budget, rentMode, nearSubway,
                            privateBathroom, hasBalcony, civilWaterElectric, limit);
                }
                case "getHouseDetail" -> {
                    var params = objectMapper.readTree(toolArgsJson);
                    Long houseId = params.has("houseId") ? params.get("houseId").asLong() : null;
                    yield getHouseDetailTool.getHouseDetail(houseId);
                }
                default -> "{\"error\":\"unknown tool: " + toolName + "\"}";
            };
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            return "{\"error\":\"工具执行失败\"}";
        }
    }

    private void sendSseEvent(SseEmitter emitter, String eventName, String data) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(data));
    }

    private String jsonEscape(String text) {
        if (text == null) return "null";
        try {
            return objectMapper.writeValueAsString(text);
        } catch (Exception e) {
            return "\"" + text.replace("\"", "\\\"") + "\"";
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/ai/chat/AiChatService.java src/main/java/cn/yy/myrent/service/ai/chat/AiChatServiceImpl.java
git commit -m "feat(ai-chat): add AiChatService with SSE streaming and tool calling loop"
```

---

## Task 7: AiChatController

**Files:**
- Create: `src/main/java/cn/yy/myrent/controller/AiChatController.java`
- Create: `src/main/java/cn/yy/myrent/dto/AiChatReqDTO.java`

- [ ] **Step 1: 创建请求 DTO**

```java
package cn.yy.myrent.dto;

import lombok.Data;

@Data
public class AiChatReqDTO {

    private String message;

    private Long sessionId;
}
```

- [ ] **Step 2: 创建 Controller**

```java
package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.dto.AiChatReqDTO;
import cn.yy.myrent.service.ai.chat.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final JwtTokenUtil jwtTokenUtil;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @Valid @RequestBody AiChatReqDTO reqDTO,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String tokenParam) {

        Long userId = resolveUserId(authorization, tokenParam);
        SseEmitter emitter = new SseEmitter(120_000L);
        aiChatService.chat(userId, reqDTO.getMessage(), reqDTO.getSessionId(), emitter);
        return emitter;
    }

    private Long resolveUserId(String authorization, String tokenParam) {
        String token = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7);
        } else if (tokenParam != null && !tokenParam.isBlank()) {
            token = tokenParam;
        }
        if (token == null) {
            throw new IllegalStateException("未登录");
        }
        return jwtTokenUtil.parseUserId(token);
    }
}
```

注意：`jwtTokenUtil.parseUserId(token)` 方法名需要确认。如果实际方法名不同（比如 `getUserIdFromToken`），需要调整。请检查 `JwtTokenUtil.java` 中的实际方法名。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/cn/yy/myrent/controller/AiChatController.java src/main/java/cn/yy/myrent/dto/AiChatReqDTO.java
git commit -m "feat(ai-chat): add AiChatController with SSE endpoint"
```

---

## Task 8: 前端 SSE 客户端和页面

**Files:**
- Create: `frontend/src/api/aiChat.js`
- Create: `frontend/src/views/AiChatView.vue`
- Modify: `frontend/src/router/index.js` — 添加路由

- [ ] **Step 1: 创建 SSE API 客户端**

文件 `frontend/src/api/aiChat.js`:

```javascript
import { getToken } from '@/utils/storage'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL

/**
 * SSE 流式聊天请求
 * @param {Object} params - { message, sessionId }
 * @param {Object} callbacks - { onText, onToolCall, onToolResult, onDone, onError }
 * @returns {Function} abort function
 */
export function streamAiChat(params, callbacks) {
  const { onText, onToolCall, onToolResult, onDone, onError } = callbacks
  const token = getToken()

  const controller = new AbortController()

  fetch(`${apiBaseUrl}/ai/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(params),
    signal: controller.signal
  })
    .then(async (response) => {
      if (!response.ok) {
        const errorText = await response.text()
        onError?.(new Error(errorText || `请求失败 (${response.status})`))
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        let currentEvent = ''
        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.substring(6).trim()
          } else if (line.startsWith('data:')) {
            const dataStr = line.substring(5).trim()
            if (!dataStr) continue

            try {
              const data = JSON.parse(dataStr)
              switch (currentEvent) {
                case 'text':
                  onText?.(data.content)
                  break
                case 'tool_call':
                  onToolCall?.(data)
                  break
                case 'tool_result':
                  onToolResult?.(data)
                  break
                case 'done':
                  onDone?.()
                  break
                case 'error':
                  onError?.(new Error(data.message || '请求失败'))
                  break
              }
            } catch {
              // ignore parse errors for partial data
            }
          }
        }
      }
      onDone?.()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError?.(err)
      }
    })

  return () => controller.abort()
}
```

- [ ] **Step 2: 创建 AiChatView.vue**

文件 `frontend/src/views/AiChatView.vue`:

```vue
<template>
  <div class="page ai-chat-page">
    <section class="ai-hero">
      <div class="ai-hero__sky" aria-hidden="true"></div>
      <div class="ai-hero__content">
        <div class="ai-hero__mascot">
          <RoamMascotIcon size="big" />
        </div>
        <h1 class="ai-hero__title">Hi，我是 Roam，帮你找个家</h1>
        <p class="ai-hero__sub">
          告诉我你的需求，我从真实房源里帮你挑。
        </p>
      </div>
    </section>

    <section class="ai-chat-card">
      <div class="chat-thread" ref="threadRef">
        <AiChatMessage
          v-for="(msg, index) in messages"
          :key="index"
          :role="msg.role"
          :text="msg.text"
          :tool-call="msg.toolCall"
          :tool-result="msg.toolResult"
        />
        <div v-if="currentTool" class="tool-loading">
          <span class="tool-loading__spinner"></span>
          正在{{ currentTool === 'searchHouses' ? '搜索房源' : '查询详情' }}...
        </div>
      </div>

      <AiQuickPromptChips
        v-if="messages.length <= 1"
        :prompts="quickPrompts"
        @select="sendMessage"
      />

      <form class="chat-form" @submit.prevent="sendMessage(draft)">
        <textarea
          ref="inputRef"
          v-model="draft"
          class="chat-input"
          rows="3"
          placeholder="比如：预算 3500，想在浦东整租"
          :disabled="streaming"
        />
        <div class="chat-actions">
          <span v-if="streaming" class="chat-status">Roam 正在思考...</span>
          <button class="chat-send" type="submit" :disabled="streaming || !draft.trim()">发送</button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { streamAiChat } from '@/api/aiChat'
import AiChatMessage from '@/components/ai/AiChatMessage.vue'
import AiQuickPromptChips from '@/components/ai/AiQuickPromptChips.vue'
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'

const messages = ref([])
const draft = ref('')
const streaming = ref(false)
const currentTool = ref(null)
const threadRef = ref(null)
const inputRef = ref(null)
const sessionId = ref(null)
let abortFn = null

const quickPrompts = [
  '预算 3000 左右，想整租',
  '想住地铁附近，通勤方便最重要',
  '我现在只知道想在上海租房',
  '预算有限，可以接受合租'
]

onMounted(() => {
  messages.value.push({
    role: 'assistant',
    text: '你好！我是 Roam，你的租房助手。告诉我你想在哪个区域租房、预算大概多少，或者随便聊聊你的需求都行。'
  })
})

function sendMessage(text) {
  const content = String(text || draft.value || '').trim()
  if (!content || streaming.value) return

  messages.value.push({ role: 'user', text: content })
  draft.value = ''
  streaming.value = true
  currentTool.value = null

  // 添加一个空的 assistant 消息用于流式追加
  const assistantIndex = messages.value.length
  messages.value.push({ role: 'assistant', text: '' })

  scrollToBottom()

  abortFn = streamAiChat(
    { message: content, sessionId: sessionId.value },
    {
      onText(chunk) {
        messages.value[assistantIndex].text += chunk
        scrollToBottom()
      },
      onToolCall(data) {
        currentTool.value = data.tool
        messages.value.push({
          role: 'tool',
          toolCall: { tool: data.tool, params: data.params }
        })
        scrollToBottom()
      },
      onToolResult(data) {
        currentTool.value = null
        messages.value.push({
          role: 'tool',
          toolResult: { tool: data.tool, result: data.result }
        })
        scrollToBottom()
      },
      onDone() {
        streaming.value = false
        currentTool.value = null
        // 如果最后一条 assistant 消息是空的，删除它
        const last = messages.value[assistantIndex]
        if (last && last.role === 'assistant' && !last.text) {
          messages.value.splice(assistantIndex, 1)
        }
      },
      onError(err) {
        streaming.value = false
        currentTool.value = null
        const last = messages.value[assistantIndex]
        if (last && last.role === 'assistant' && !last.text) {
          messages.value[assistantIndex].text = '抱歉，出了点问题，请稍后再试。'
        }
      }
    }
  )
}

async function scrollToBottom() {
  await nextTick()
  if (threadRef.value) {
    threadRef.value.scrollTop = threadRef.value.scrollHeight
  }
}
</script>

<style scoped>
.ai-chat-page {
  display: grid;
  gap: 16px;
  width: 100%;
}

.ai-hero {
  position: relative;
  border-radius: 28px;
  overflow: hidden;
  padding: 36px 24px 28px;
  text-align: center;
}
.ai-hero__sky {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 15% 10%, rgba(255,209,102,0.18), transparent 40%),
    radial-gradient(circle at 85% 20%, rgba(255,184,200,0.22), transparent 45%),
    linear-gradient(180deg, #eaf4ff 0%, #f8f4ff 60%, #fff8e6 100%);
}
.ai-hero__content {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 10px;
  justify-items: center;
}
.ai-hero__mascot {
  width: 140px;
  height: 120px;
  animation: roam-float 3.5s ease-in-out infinite;
}
@keyframes roam-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}
.ai-hero__title {
  margin: 2px 0 0;
  font-size: clamp(20px, 3vw, 26px);
  color: #2d3748;
}
.ai-hero__sub {
  margin: 4px auto 10px;
  color: #5b6a8a;
  font-size: 14px;
  max-width: 500px;
}

.ai-chat-card {
  background: #ffffff;
  border-radius: 24px;
  padding: 18px;
  border: 1px solid rgba(184,200,224,0.3);
  box-shadow: 0 4px 14px rgba(100,130,200,0.06);
  display: grid;
  gap: 16px;
}

.chat-thread {
  min-height: 320px;
  max-height: 60vh;
  overflow-y: auto;
  display: grid;
  gap: 14px;
  align-content: start;
}

.tool-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #5b6a8a;
  font-size: 13px;
  padding: 8px 12px;
  background: #f0f5ff;
  border-radius: 12px;
}
.tool-loading__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid #d0daf0;
  border-top-color: #7aa3e0;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.chat-form { display: grid; gap: 12px; }
.chat-input {
  width: 100%;
  min-height: 100px;
  resize: vertical;
  border: 1px solid rgba(184,200,224,0.4);
  border-radius: 18px;
  padding: 12px 14px;
  font-size: 14px;
  background: #f8fbff;
  outline: none;
  font-family: inherit;
}
.chat-input:focus {
  border-color: #7aa3e0;
  background: #ffffff;
  box-shadow: 0 0 0 4px rgba(122,163,224,0.15);
}
.chat-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.chat-status { color: #5b6a8a; font-size: 13px; }
.chat-send {
  border: 0;
  background: linear-gradient(135deg, #7aa3e0, #9bb5e8);
  color: #ffffff;
  border-radius: 999px;
  padding: 10px 22px;
  font-weight: 800;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(122,163,224,0.35);
  margin-left: auto;
}
.chat-send:disabled { opacity: 0.6; cursor: not-allowed; }

@media (max-width: 640px) {
  .ai-hero { padding: 28px 16px 20px; }
  .ai-hero__mascot { width: 110px; height: 95px; }
  .ai-chat-card { padding: 14px; border-radius: 20px; }
}
</style>
```

- [ ] **Step 3: 创建 AiChatMessage.vue 组件**

文件 `frontend/src/components/ai/AiChatMessage.vue`:

```vue
<template>
  <div v-if="role === 'tool' && toolCall" class="tool-event">
    <span class="tool-event__icon">🔧</span>
    <span>调用了 {{ toolCall.tool === 'searchHouses' ? '房源搜索' : toolCall.tool }}</span>
  </div>
  <div v-else-if="role === 'tool' && toolResult" class="tool-event tool-event--result">
    <span class="tool-event__icon">✅</span>
    <span>{{ toolResult.tool === 'searchHouses' ? '搜索完成' : '查询完成' }}</span>
  </div>
  <div v-else-if="text" class="chat-row" :class="role === 'user' ? 'is-user' : 'is-assistant'">
    <div v-if="role === 'assistant'" class="chat-row__avatar">
      <RoamMascotIcon size="mini" />
    </div>
    <article class="bubble" :class="role === 'user' ? 'is-user' : 'is-assistant'">
      <div class="bubble-meta">{{ role === 'user' ? '你' : 'ROAM' }}</div>
      <div class="bubble-body">{{ text }}</div>
    </article>
  </div>
</template>

<script setup>
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'

defineProps({
  role: { type: String, default: 'assistant' },
  text: { type: String, default: '' },
  toolCall: { type: Object, default: null },
  toolResult: { type: Object, default: null }
})
</script>

<style scoped>
.chat-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.chat-row.is-user { justify-content: flex-end; }

.chat-row__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #ffffff;
  display: grid;
  place-items: center;
  box-shadow: 0 4px 10px rgba(100,130,200,0.14);
  border: 1px solid rgba(184,200,224,0.4);
  flex-shrink: 0;
}

.bubble {
  display: grid;
  gap: 4px;
  max-width: min(560px, 78%);
  padding: 12px 18px;
  line-height: 1.55;
  font-size: 14px;
}
.bubble.is-assistant {
  background: #ffffff;
  color: #2d3748;
  border-radius: 26px 26px 26px 8px;
  filter: drop-shadow(0 3px 10px rgba(100,130,200,0.1));
}
.bubble.is-user {
  background: linear-gradient(135deg, #a8d8ff, #7db5f0);
  color: #ffffff;
  border-radius: 22px 22px 6px 22px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(125,181,240,0.3);
}
.bubble-meta {
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 0.1em;
  opacity: 0.7;
}
.bubble-body { white-space: pre-wrap; line-height: 1.55; }

.tool-event {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: #f0f5ff;
  border-radius: 12px;
  font-size: 12px;
  color: #5b6a8a;
  width: fit-content;
}
.tool-event--result { background: #f0fff4; color: #2d6a4f; }
</style>
```

- [ ] **Step 4: 添加路由**

在 `frontend/src/router/index.js` 的 children 数组中，在 `ai-recommend` 路由之后添加：

```javascript
{
  path: 'ai-chat',
  name: 'ai-chat',
  component: () => import('@/views/AiChatView.vue')
},
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/aiChat.js frontend/src/views/AiChatView.vue frontend/src/components/ai/AiChatMessage.vue frontend/src/router/index.js
git commit -m "feat(ai-chat): add frontend SSE streaming chat page and components"
```

---

## Task 9: 集成测试和调试

- [ ] **Step 1: 启动后端，确认编译通过**

```bash
cd C:/javapractice/MyRent
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 启动前端，确认页面可访问**

```bash
cd C:/javapractice/MyRent/frontend
npm run dev
```

访问 `http://localhost:5173/ai-chat`，确认页面渲染正常。

- [ ] **Step 3: 测试 SSE 流式聊天**

在页面中发送 "我想在浦东租房"，确认：
1. 后端日志显示 LLM 被调用
2. SSE 事件流中有 `text` 事件
3. 前端页面文字逐字出现

- [ ] **Step 4: 测试工具调用**

发送 "帮我看看陆家嘴 3500 以内的房子"，确认：
1. SSE 事件流中有 `tool_call` 和 `tool_result` 事件
2. 前端显示"正在搜索房源..."
3. 搜索完成后 LLM 生成推荐文字

- [ ] **Step 5: 修复发现的问题**

根据测试结果修复 bug。

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "fix(ai-chat): fix integration issues found during testing"
```

---

## Task 10: 清理旧 AI 代码

**Files:**
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiRecommendDecisionClient.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/SpringAiRecommendDecisionClient.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiRecommendStateStore.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStore.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiRecommendSessionState.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiRecommendStage.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiRecommendSummaryBuilder.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiRecommendRankingPayloadBuilder.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiRecommendRankingPayload.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiRecommendPromptLoader.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiRecommendPromptBundle.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiPreviewService.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiPreviewServiceImpl.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiRecommendSlots.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiRecommendTurn.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiRecommendDecision.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiWeightedPreference.java`
- Delete: `src/main/java/cn/yy/myrent/service/ai/AiPreferenceWeightLevel.java`
- Delete: `src/main/resources/prompts/ai-recommend/` (整个目录)
- Delete: `src/main/java/cn/yy/myrent/controller/AiRecommendController.java`
- Delete: `src/main/java/cn/yy/myrent/dto/AiRecommendChatReqDTO.java`
- Delete: `src/main/java/cn/yy/myrent/dto/AiRecommendInteractionDTO.java`
- Delete: `src/main/java/cn/yy/myrent/dto/AiRecommendInteractionSlotPatchDTO.java`
- Delete: `src/main/java/cn/yy/myrent/vo/AiRecommendChatVO.java`
- Delete: `src/main/java/cn/yy/myrent/vo/AiRecommendSlotsVO.java`
- Delete: `src/main/java/cn/yy/myrent/vo/AiPreviewVO.java`
- Delete: `src/main/java/cn/yy/myrent/vo/AiPreviewGroupVO.java`
- Delete: `src/main/java/cn/yy/myrent/vo/AiPreviewSlotPatchVO.java`
- Delete: `frontend/src/api/aiRecommend.js`
- Delete: `frontend/src/views/AiRecommendView.vue`
- Delete: `frontend/src/components/ai/AiPreviewPanel.vue`
- Delete: `frontend/src/components/ai/AiRecommendationPanel.vue`
- Delete: `frontend/src/components/ai/AiRequirementSummary.vue`

- [ ] **Step 1: 删除旧后端文件**

```bash
cd C:/javapractice/MyRent
rm -rf src/main/java/cn/yy/myrent/service/ai/AiRecommend*.java
rm -rf src/main/java/cn/yy/myrent/service/ai/AiPreview*.java
rm -rf src/main/java/cn/yy/myrent/service/ai/AiWeighted*.java
rm -rf src/main/java/cn/yy/myrent/service/ai/AiPreferenceWeightLevel.java
rm -rf src/main/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStore.java
rm -rf src/main/java/cn/yy/myrent/service/ai/SpringAiRecommendDecisionClient.java
rm -rf src/main/resources/prompts/ai-recommend/
rm -f src/main/java/cn/yy/myrent/controller/AiRecommendController.java
rm -f src/main/java/cn/yy/myrent/dto/AiRecommendChatReqDTO.java
rm -f src/main/java/cn/yy/myrent/dto/AiRecommendInteractionDTO.java
rm -f src/main/java/cn/yy/myrent/dto/AiRecommendInteractionSlotPatchDTO.java
rm -f src/main/java/cn/yy/myrent/vo/AiRecommendChatVO.java
rm -f src/main/java/cn/yy/myrent/vo/AiRecommendSlotsVO.java
rm -f src/main/java/cn/yy/myrent/vo/AiPreviewVO.java
rm -f src/main/java/cn/yy/myrent/vo/AiPreviewGroupVO.java
rm -f src/main/java/cn/yy/myrent/vo/AiPreviewSlotPatchVO.java
```

- [ ] **Step 2: 删除旧前端文件**

```bash
rm -f frontend/src/api/aiRecommend.js
rm -f frontend/src/views/AiRecommendView.vue
rm -f frontend/src/components/ai/AiPreviewPanel.vue
rm -f frontend/src/components/ai/AiRecommendationPanel.vue
rm -f frontend/src/components/ai/AiRequirementSummary.vue
```

- [ ] **Step 3: 更新路由（删除旧路由）**

在 `frontend/src/router/index.js` 中删除 `ai-recommend` 路由。

- [ ] **Step 4: 确认编译通过**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS（可能需要先删除旧的测试文件）

- [ ] **Step 5: 删除旧测试文件**

```bash
rm -f src/test/java/cn/yy/myrent/service/ai/AiRecommend*.java
rm -f src/test/java/cn/yy/myrent/service/ai/AiWeighted*.java
rm -f src/test/java/cn/yy/myrent/service/ai/AiPreview*.java
rm -f src/test/java/cn/yy/myrent/service/ai/RedisAiRecommend*.java
rm -f src/test/java/cn/yy/myrent/controller/AiRecommendControllerWebMvcTest.java
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(ai-chat): remove old AI recommend code, replaced by function calling approach"
```
