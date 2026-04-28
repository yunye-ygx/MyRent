# AI Smart Recommendation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (preferred in this repo) or superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dedicated `智能推荐` AI assistant page with a prominent center navigation entry, multi-turn requirement collection, Spring AI-powered `ASK / ADVISE / SEARCH` decisioning, Redis-backed session state, and real rental recommendations powered by the existing `smart-guide` backend.

**Architecture:** Keep the current `SmartGuideRecommendationService` as the only source of real house recommendations and build a new orchestration layer around it. The backend uses Spring AI only to interpret user intent and emit a structured decision, while Redis stores lightweight AI session state and the frontend renders a dedicated chat-style page plus a highlighted navigation entry with a dog icon.

**Tech Stack:** Spring Boot 3.5, Spring AI, MyBatis-Plus, Redis, JUnit 5, Mockito, Vue 3, Vue Router, Pinia, Vitest, Vue Test Utils, Axios

---

## File Map

### Backend

- Modify: `pom.xml`
  Responsibility: add Spring AI dependency management and chat-model starter.
- Modify: `src/main/resources/application.yml`
  Responsibility: add provider-neutral AI config placeholders and feature-level settings without committing secrets.
- Create: `src/main/java/cn/yy/myrent/controller/AiRecommendController.java`
  Responsibility: expose session bootstrap, chat turn, and reset endpoints.
- Create: `src/main/java/cn/yy/myrent/dto/AiRecommendChatReqDTO.java`
  Responsibility: validate user turn input.
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendService.java`
  Responsibility: orchestrate session load, model decisioning, action branching, `smart-guide` lookup, and response assembly.
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendDecisionClient.java`
  Responsibility: abstract the structured decision call so service tests do not depend on Spring AI transport details.
- Create: `src/main/java/cn/yy/myrent/service/ai/SpringAiRecommendDecisionClient.java`
  Responsibility: use `ChatClient` plus `BeanOutputConverter` to obtain `AiRecommendDecision`.
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendStateStore.java`
  Responsibility: abstract session state persistence.
- Create: `src/main/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStore.java`
  Responsibility: persist slot state and recent turns in Redis.
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendDecision.java`
  Responsibility: represent LLM output action, reply, slots, and missing slots.
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendSlots.java`
  Responsibility: represent normalized slot state.
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendSessionState.java`
  Responsibility: aggregate slots and recent turns loaded from Redis.
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendTurn.java`
  Responsibility: store one user or assistant message in state history.
- Create: `src/main/java/cn/yy/myrent/vo/AiRecommendChatVO.java`
  Responsibility: return action, assistant reply, updated slots, and optional recommendation payload to the frontend.
- Create: `src/test/java/cn/yy/myrent/controller/AiRecommendControllerWebMvcTest.java`
  Responsibility: verify endpoint contract, validation, reset behavior, and response wrapping.
- Create: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`
  Responsibility: verify `ASK`, `ADVISE`, `SEARCH`, downgrade, state persistence, and fallback behavior.
- Create: `src/test/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStoreTest.java`
  Responsibility: verify Redis JSON serialization and TTL behavior without pulling in the whole app.

### Frontend

- Create: `frontend/src/api/aiRecommend.js`
  Responsibility: expose AI session bootstrap, chat, and reset requests.
- Create: `frontend/src/views/AiRecommendView.vue`
  Responsibility: render the assistant hero, transcript, quick prompts, slot summary, and recommendation cards.
- Create: `frontend/src/components/ai/AiChatBubble.vue`
  Responsibility: render assistant and user chat bubbles consistently.
- Create: `frontend/src/components/ai/AiQuickPromptChips.vue`
  Responsibility: render and emit starter prompt chips.
- Create: `frontend/src/components/ai/AiRequirementSummary.vue`
  Responsibility: render current slot summary.
- Create: `frontend/src/components/ai/AiRecommendationPanel.vue`
  Responsibility: render `SmartGuideResultVO` cards inside the AI page.
- Create: `frontend/src/components/icons/DogAssistantIcon.vue`
  Responsibility: render the small dog icon used by desktop and mobile navigation.
- Modify: `frontend/src/router/index.js`
  Responsibility: register `/ai-recommend`.
- Modify: `frontend/src/design/site.js`
  Responsibility: add `智能推荐` nav metadata for desktop and mobile.
- Modify: `frontend/src/components/layout/AppTopNav.vue`
  Responsibility: render a featured center nav item with raised half-round styling and dog icon.
- Modify: `frontend/src/components/AppTabBar.vue`
  Responsibility: add the `智能推荐` mobile tab with dog icon.
- Modify: `frontend/src/components/__tests__/AppTopNav.spec.js`
  Responsibility: verify the featured nav item renders and activates correctly.
- Create: `frontend/src/components/__tests__/AppTabBar.spec.js`
  Responsibility: verify the mobile tab includes `智能推荐` and routes correctly.
- Create: `frontend/src/views/__tests__/AiRecommendView.spec.js`
  Responsibility: verify session bootstrap, sending a message, rendering summary, and rendering recommendation results.

## Task 1: Add Spring AI dependencies, app config, and backend endpoint contract

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/java/cn/yy/myrent/controller/AiRecommendController.java`
- Create: `src/main/java/cn/yy/myrent/dto/AiRecommendChatReqDTO.java`
- Create: `src/main/java/cn/yy/myrent/vo/AiRecommendChatVO.java`
- Create: `src/test/java/cn/yy/myrent/controller/AiRecommendControllerWebMvcTest.java`

- [ ] **Step 1: Write the failing controller tests**

```java
package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.dto.AiRecommendChatReqDTO;
import cn.yy.myrent.mapper.ChatMessageMapper;
import cn.yy.myrent.mapper.ChatSessionMapper;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
import cn.yy.myrent.mapper.HouseHistoryMapper;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.mapper.LocalTaskMapper;
import cn.yy.myrent.mapper.LocationDictMapper;
import cn.yy.myrent.mapper.MockPayTradeMapper;
import cn.yy.myrent.mapper.NotificationMapper;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.PaymentMapper;
import cn.yy.myrent.mapper.PaymentRefundMapper;
import cn.yy.myrent.mapper.PublisherFollowMapper;
import cn.yy.myrent.mapper.ReviewMapper;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IHouseHistoryService;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.IReviewService;
import cn.yy.myrent.service.ai.AiRecommendService;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.service.search.HouseKeywordSearchService;
import cn.yy.myrent.sync.house.service.HouseEsSyncService;
import cn.yy.myrent.vo.AiRecommendChatVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiRecommendController.class)
class AiRecommendControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiRecommendService aiRecommendService;

    @MockBean private JwtTokenUtil jwtTokenUtil;
    @MockBean private IHouseService houseService;
    @MockBean private HouseKeywordSearchService houseKeywordSearchService;
    @MockBean private IHouseCommandService houseCommandService;
    @MockBean private IHouseHistoryService houseHistoryService;
    @MockBean private IReviewService reviewService;
    @MockBean private HouseEsSyncService houseEsSyncService;
    @MockBean private HouseHotService houseHotService;
    @MockBean private ChatMessageMapper chatMessageMapper;
    @MockBean private ChatSessionMapper chatSessionMapper;
    @MockBean private HouseFavoriteMapper houseFavoriteMapper;
    @MockBean private HouseHistoryMapper houseHistoryMapper;
    @MockBean private HouseMapper houseMapper;
    @MockBean private LocalTaskMapper localTaskMapper;
    @MockBean private LocationDictMapper locationDictMapper;
    @MockBean private MockPayTradeMapper mockPayTradeMapper;
    @MockBean private NotificationMapper notificationMapper;
    @MockBean private OrderMapper orderMapper;
    @MockBean private PaymentMapper paymentMapper;
    @MockBean private PaymentRefundMapper paymentRefundMapper;
    @MockBean private PublisherFollowMapper publisherFollowMapper;
    @MockBean private ReviewMapper reviewMapper;
    @MockBean private UserMapper userMapper;

    @Test
    void sessionShouldReturnInitialAssistantState() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        AiRecommendChatVO vo = new AiRecommendChatVO();
        vo.setSessionId("ai-u1001");
        vo.setAction("ASK");
        vo.setAssistantReply("先告诉我预算或更在意通勤还是价格。");

        given(aiRecommendService.getOrCreateSession(1001L)).willReturn(vo);

        mockMvc.perform(get("/ai-recommend/session").header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value("ai-u1001"))
                .andExpect(jsonPath("$.data.action").value("ASK"));
    }

    @Test
    void chatShouldRejectBlankMessage() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        mockMvc.perform(post("/ai-recommend/chat")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(aiRecommendService);
    }

    @Test
    void chatShouldReturnRecommendationPayloadWhenSearchTriggered() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        SmartGuideResultVO recommendation = new SmartGuideResultVO();
        recommendation.setTipMessage("已找到符合条件的房源。");

        AiRecommendChatVO vo = new AiRecommendChatVO();
        vo.setSessionId("ai-u1001");
        vo.setAction("SEARCH");
        vo.setAssistantReply("我先按你的条件帮你筛一批房源。");
        vo.setRecommendation(recommendation);

        given(aiRecommendService.chat(any(Long.class), any(AiRecommendChatReqDTO.class))).willReturn(vo);

        mockMvc.perform(post("/ai-recommend/chat")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "预算3500，想在浦东整租"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.action").value("SEARCH"))
                .andExpect(jsonPath("$.data.recommendation.tipMessage").value("已找到符合条件的房源。"));
    }

    @Test
    void resetShouldInvokeServiceAndReturnFreshState() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        AiRecommendChatVO vo = new AiRecommendChatVO();
        vo.setSessionId("ai-u1001");
        vo.setAction("ASK");
        vo.setAssistantReply("我们重新开始，你先告诉我预算。");

        given(aiRecommendService.reset(1001L)).willReturn(vo);

        mockMvc.perform(post("/ai-recommend/reset").header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.assistantReply").value("我们重新开始，你先告诉我预算。"));

        verify(aiRecommendService).reset(1001L);
    }
}
```

- [ ] **Step 2: Run the controller test to verify it fails**

Run: `mvn "-Dtest=AiRecommendControllerWebMvcTest" test`

Expected: FAIL because the AI controller, request DTO, and response VO do not exist.

- [ ] **Step 3: Add the Spring AI dependency management and provider-neutral config placeholders**

```xml
<!-- pom.xml -->
<properties>
    <java.version>17</java.version>
    <spring-ai.version>1.1.4</spring-ai.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
</dependencies>
```

```yaml
# src/main/resources/application.yml
spring:
  ai:
    openai:
      base-url: ${AI_BASE_URL:}
      api-key: ${AI_API_KEY:}
      chat:
        options:
          model: ${AI_CHAT_MODEL:qwen-plus}

myrent:
  ai:
    recommend:
      state-ttl-hours: 48
      history-limit: 10
      default-budget-scope: RENT_ONLY
```

- [ ] **Step 4: Add the request DTO, response VO, and controller**

```java
// src/main/java/cn/yy/myrent/dto/AiRecommendChatReqDTO.java
package cn.yy.myrent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiRecommendChatReqDTO {

    @NotBlank(message = "message cannot be blank")
    private String message;
}
```

```java
// src/main/java/cn/yy/myrent/vo/AiRecommendChatVO.java
package cn.yy.myrent.vo;

import cn.yy.myrent.service.ai.AiRecommendSlots;
import lombok.Data;

import java.util.List;

@Data
public class AiRecommendChatVO {

    private String sessionId;

    private String action;

    private String assistantReply;

    private AiRecommendSlots slots;

    private List<String> missingSlots;

    private SmartGuideResultVO recommendation;
}
```

```java
// src/main/java/cn/yy/myrent/controller/AiRecommendController.java
package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.AiRecommendChatReqDTO;
import cn.yy.myrent.service.ai.AiRecommendService;
import cn.yy.myrent.vo.AiRecommendChatVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai-recommend")
@RequiredArgsConstructor
public class AiRecommendController {

    private final AiRecommendService aiRecommendService;

    @GetMapping("/session")
    public Result<AiRecommendChatVO> session() {
        return Result.success(aiRecommendService.getOrCreateSession(UserContext.getCurrentUserId()));
    }

    @PostMapping("/chat")
    public Result<AiRecommendChatVO> chat(@Valid @RequestBody AiRecommendChatReqDTO reqDTO) {
        return Result.success(aiRecommendService.chat(UserContext.getCurrentUserId(), reqDTO));
    }

    @PostMapping("/reset")
    public Result<AiRecommendChatVO> reset() {
        return Result.success(aiRecommendService.reset(UserContext.getCurrentUserId()));
    }
}
```

- [ ] **Step 5: Run the controller test to verify it passes**

Run: `mvn "-Dtest=AiRecommendControllerWebMvcTest" test`

Expected: PASS with session bootstrap, chat validation, and reset contract covered.

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/main/resources/application.yml src/main/java/cn/yy/myrent/controller/AiRecommendController.java src/main/java/cn/yy/myrent/dto/AiRecommendChatReqDTO.java src/main/java/cn/yy/myrent/vo/AiRecommendChatVO.java src/test/java/cn/yy/myrent/controller/AiRecommendControllerWebMvcTest.java
git commit -m "feat(ai): add recommend controller contract and spring ai dependency"
```

## Task 2: Implement AI decision model, Redis-backed state store, and service branching

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendDecision.java`
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendSlots.java`
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendTurn.java`
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendSessionState.java`
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendStateStore.java`
- Create: `src/main/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStore.java`
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendDecisionClient.java`
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendService.java`
- Create: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`
- Create: `src/test/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStoreTest.java`

- [ ] **Step 1: Write the failing service tests**

```java
package cn.yy.myrent.service.ai;

import cn.yy.myrent.dto.AiRecommendChatReqDTO;
import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.vo.AiRecommendChatVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRecommendServiceTest {

    @Mock
    private AiRecommendDecisionClient decisionClient;

    @Mock
    private AiRecommendStateStore stateStore;

    @Mock
    private IHouseService houseService;

    @InjectMocks
    private AiRecommendService aiRecommendService;

    @BeforeEach
    void setUp() {
        when(stateStore.loadOrCreate(1001L)).thenReturn(AiRecommendSessionState.empty("ai-u1001"));
    }

    @Test
    void chatShouldAskFollowUpWithoutCallingHouseService() {
        AiRecommendDecision decision = new AiRecommendDecision();
        decision.setAction("ASK");
        decision.setReply("预算大概多少？更偏向整租还是合租？");
        decision.setSlots(AiRecommendSlots.builder().city("上海").build());
        decision.setMissingSlots(List.of("budgetYuan", "rentMode", "locationName"));

        when(decisionClient.decide(any(), any(), any())).thenReturn(decision);

        AiRecommendChatReqDTO reqDTO = new AiRecommendChatReqDTO();
        reqDTO.setMessage("我想在上海租房");

        AiRecommendChatVO result = aiRecommendService.chat(1001L, reqDTO);

        assertEquals("ASK", result.getAction());
        assertEquals("上海", result.getSlots().getCity());
        assertNull(result.getRecommendation());
        verifyNoInteractions(houseService);
        verify(stateStore).save(any(Long.class), any(AiRecommendSessionState.class));
    }

    @Test
    void chatShouldCallSmartGuideWhenSearchDecisionIsUsable() {
        AiRecommendSlots slots = AiRecommendSlots.builder()
                .city("上海")
                .budgetYuan(3500)
                .budgetScope("RENT_ONLY")
                .rentMode("WHOLE")
                .locationName("浦东")
                .priority("COMMUTE")
                .build();

        AiRecommendDecision decision = new AiRecommendDecision();
        decision.setAction("SEARCH");
        decision.setReply("我先帮你筛房。");
        decision.setSlots(slots);
        decision.setMissingSlots(List.of());

        SmartGuideResultVO recommendation = new SmartGuideResultVO();
        recommendation.setTipMessage("已找到符合条件的房源。");

        when(decisionClient.decide(any(), any(), any())).thenReturn(decision);
        when(houseService.smartGuide(any(SmartGuideReqDTO.class))).thenReturn(recommendation);

        AiRecommendChatReqDTO reqDTO = new AiRecommendChatReqDTO();
        reqDTO.setMessage("预算3500，想在浦东整租");

        AiRecommendChatVO result = aiRecommendService.chat(1001L, reqDTO);

        assertEquals("SEARCH", result.getAction());
        assertEquals("已找到符合条件的房源。", result.getRecommendation().getTipMessage());

        ArgumentCaptor<SmartGuideReqDTO> captor = ArgumentCaptor.forClass(SmartGuideReqDTO.class);
        verify(houseService).smartGuide(captor.capture());
        assertEquals(3500, captor.getValue().getBudgetYuan());
        assertEquals("RENT_ONLY", captor.getValue().getBudgetScope());
        assertEquals("WHOLE", captor.getValue().getRentMode());
        assertEquals("浦东", captor.getValue().getLocationName());
    }

    @Test
    void chatShouldDowngradeInvalidSearchToAsk() {
        AiRecommendSlots slots = AiRecommendSlots.builder()
                .city("上海")
                .budgetYuan(3500)
                .rentMode("WHOLE")
                .build();

        AiRecommendDecision decision = new AiRecommendDecision();
        decision.setAction("SEARCH");
        decision.setReply("我先帮你找房。");
        decision.setSlots(slots);
        decision.setMissingSlots(List.of());

        when(decisionClient.decide(any(), any(), any())).thenReturn(decision);

        AiRecommendChatReqDTO reqDTO = new AiRecommendChatReqDTO();
        reqDTO.setMessage("预算3500，整租");

        AiRecommendChatVO result = aiRecommendService.chat(1001L, reqDTO);

        assertEquals("ASK", result.getAction());
        assertTrue(result.getAssistantReply().contains("区域"));
        verifyNoInteractions(houseService);
    }
}
```

```java
package cn.yy.myrent.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAiRecommendStateStoreTest {

    @Test
    void saveShouldWriteJsonAndExpireKeys() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        RedisAiRecommendStateStore store = new RedisAiRecommendStateStore(redisTemplate, new ObjectMapper(), 48, 10);

        AiRecommendSessionState state = AiRecommendSessionState.builder()
                .sessionId("ai-u1001")
                .slots(AiRecommendSlots.builder().city("上海").build())
                .history(List.of(new AiRecommendTurn("assistant", "你好")))
                .build();

        store.save(1001L, state);

        verify(redisTemplate.opsForValue()).set(eq("ai:recommend:state:1001"), org.mockito.ArgumentMatchers.anyString());
        verify(redisTemplate).expire("ai:recommend:state:1001", 48, TimeUnit.HOURS);
        verify(redisTemplate).expire("ai:recommend:history:1001", 48, TimeUnit.HOURS);
    }
}
```

- [ ] **Step 2: Run the service tests to verify they fail**

Run: `mvn "-Dtest=AiRecommendServiceTest,RedisAiRecommendStateStoreTest" test`

Expected: FAIL because the AI state store, decision model, and service classes do not exist.

- [ ] **Step 3: Add the state and decision model classes**

```java
// src/main/java/cn/yy/myrent/service/ai/AiRecommendSlots.java
package cn.yy.myrent.service.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendSlots {

    private String city;

    private Integer budgetYuan;

    @Builder.Default
    private String budgetScope = "RENT_ONLY";

    private String rentMode;

    private String locationName;

    private String priority;

    @Builder.Default
    private List<String> preferences = new ArrayList<>();
}
```

```java
// src/main/java/cn/yy/myrent/service/ai/AiRecommendDecision.java
package cn.yy.myrent.service.ai;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiRecommendDecision {

    private String action;

    private String reply;

    private AiRecommendSlots slots;

    private List<String> missingSlots = new ArrayList<>();
}
```

```java
// src/main/java/cn/yy/myrent/service/ai/AiRecommendTurn.java
package cn.yy.myrent.service.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendTurn {

    private String role;

    private String content;
}
```

```java
// src/main/java/cn/yy/myrent/service/ai/AiRecommendSessionState.java
package cn.yy.myrent.service.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendSessionState {

    private String sessionId;

    @Builder.Default
    private AiRecommendSlots slots = AiRecommendSlots.builder().build();

    @Builder.Default
    private List<AiRecommendTurn> history = new ArrayList<>();

    public static AiRecommendSessionState empty(String sessionId) {
        return AiRecommendSessionState.builder()
                .sessionId(sessionId)
                .slots(AiRecommendSlots.builder().build())
                .history(new ArrayList<>())
                .build();
    }
}
```

- [ ] **Step 4: Add the Redis state store and service branching**

```java
// src/main/java/cn/yy/myrent/service/ai/AiRecommendStateStore.java
package cn.yy.myrent.service.ai;

public interface AiRecommendStateStore {

    AiRecommendSessionState loadOrCreate(Long userId);

    void save(Long userId, AiRecommendSessionState state);

    void reset(Long userId);
}
```

```java
// src/main/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStore.java
package cn.yy.myrent.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisAiRecommendStateStore implements AiRecommendStateStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${myrent.ai.recommend.state-ttl-hours:48}")
    private int ttlHours;

    @Value("${myrent.ai.recommend.history-limit:10}")
    private int historyLimit;

    @Override
    public AiRecommendSessionState loadOrCreate(Long userId) {
        String stateJson = redisTemplate.opsForValue().get(stateKey(userId));
        String historyJson = redisTemplate.opsForValue().get(historyKey(userId));
        try {
            AiRecommendSessionState state = stateJson == null
                    ? AiRecommendSessionState.empty("ai-u" + userId)
                    : objectMapper.readValue(stateJson, AiRecommendSessionState.class);
            if (historyJson != null) {
                state.setHistory(objectMapper.readValue(historyJson, new TypeReference<List<AiRecommendTurn>>() {
                }));
            }
            return state;
        } catch (Exception e) {
            return AiRecommendSessionState.empty("ai-u" + userId);
        }
    }

    @Override
    public void save(Long userId, AiRecommendSessionState state) {
        try {
            List<AiRecommendTurn> limitedHistory = state.getHistory().stream()
                    .skip(Math.max(state.getHistory().size() - historyLimit, 0))
                    .toList();
            redisTemplate.opsForValue().set(stateKey(userId), objectMapper.writeValueAsString(state));
            redisTemplate.opsForValue().set(historyKey(userId), objectMapper.writeValueAsString(limitedHistory));
            redisTemplate.expire(stateKey(userId), ttlHours, TimeUnit.HOURS);
            redisTemplate.expire(historyKey(userId), ttlHours, TimeUnit.HOURS);
        } catch (Exception e) {
            throw new IllegalStateException("save ai recommend state failed", e);
        }
    }

    @Override
    public void reset(Long userId) {
        redisTemplate.delete(stateKey(userId));
        redisTemplate.delete(historyKey(userId));
    }

    private String stateKey(Long userId) {
        return "ai:recommend:state:" + userId;
    }

    private String historyKey(Long userId) {
        return "ai:recommend:history:" + userId;
    }
}
```

```java
// src/main/java/cn/yy/myrent/service/ai/AiRecommendDecisionClient.java
package cn.yy.myrent.service.ai;

import java.util.List;

public interface AiRecommendDecisionClient {

    AiRecommendDecision decide(String systemPrompt, AiRecommendSlots slots, List<AiRecommendTurn> history);
}
```

```java
// src/main/java/cn/yy/myrent/service/ai/AiRecommendService.java
package cn.yy.myrent.service.ai;

import cn.yy.myrent.dto.AiRecommendChatReqDTO;
import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.vo.AiRecommendChatVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AiRecommendService {

    private final AiRecommendDecisionClient decisionClient;
    private final AiRecommendStateStore stateStore;
    private final IHouseService houseService;

    public AiRecommendChatVO getOrCreateSession(Long userId) {
        AiRecommendSessionState state = stateStore.loadOrCreate(userId);
        if (state.getHistory().isEmpty()) {
            state.getHistory().add(new AiRecommendTurn("assistant", "我可以先帮你判断预算、区域和整租/合租方向。你先告诉我目前最在意什么。"));
            stateStore.save(userId, state);
        }
        return buildResponse("ASK", state.getHistory().get(state.getHistory().size() - 1).getContent(), state.getSlots(), List.of(), null, state.getSessionId());
    }

    public AiRecommendChatVO chat(Long userId, AiRecommendChatReqDTO reqDTO) {
        AiRecommendSessionState state = stateStore.loadOrCreate(userId);
        state.getHistory().add(new AiRecommendTurn("user", reqDTO.getMessage().trim()));

        AiRecommendDecision decision = decisionClient.decide(systemPrompt(), state.getSlots(), state.getHistory());
        AiRecommendSlots slots = normalizeSlots(decision.getSlots());
        String action = normalizeAction(decision.getAction());
        List<String> missingSlots = decision.getMissingSlots() == null ? new ArrayList<>() : decision.getMissingSlots();

        SmartGuideResultVO recommendation = null;
        String assistantReply = decision.getReply();

        if ("SEARCH".equals(action) && !isSearchReady(slots)) {
            action = "ASK";
            assistantReply = "我还需要一个更明确的区域或地铁站点，才能继续帮你查真实房源。";
            missingSlots = List.of("locationName");
        } else if ("SEARCH".equals(action)) {
            recommendation = houseService.smartGuide(toSmartGuideReq(slots));
        }

        state.setSlots(slots);
        state.getHistory().add(new AiRecommendTurn("assistant", assistantReply));
        stateStore.save(userId, state);
        return buildResponse(action, assistantReply, slots, missingSlots, recommendation, state.getSessionId());
    }

    public AiRecommendChatVO reset(Long userId) {
        stateStore.reset(userId);
        return getOrCreateSession(userId);
    }

    private String systemPrompt() {
        return """
                你是租房顾问助手。
                你的目标是帮助用户逐步明确租房需求，并在条件足够时进入真实房源查询。
                规则：
                1. 只允许输出 JSON。
                2. action 只能是 ASK、ADVISE、SEARCH。
                3. 当信息不足时优先追问，不要编造房源。
                4. 每次最多追问 1 到 2 个关键问题。
                5. 未获得可用 locationName 前，不要进入 SEARCH。
                """;
    }

    private AiRecommendSlots normalizeSlots(AiRecommendSlots slots) {
        if (slots == null) {
            return AiRecommendSlots.builder().build();
        }
        slots.setBudgetScope(StringUtils.hasText(slots.getBudgetScope()) ? slots.getBudgetScope().trim().toUpperCase(Locale.ROOT) : "RENT_ONLY");
        slots.setRentMode(StringUtils.hasText(slots.getRentMode()) ? slots.getRentMode().trim().toUpperCase(Locale.ROOT) : null);
        slots.setPriority(StringUtils.hasText(slots.getPriority()) ? slots.getPriority().trim().toUpperCase(Locale.ROOT) : null);
        return slots;
    }

    private String normalizeAction(String action) {
        return StringUtils.hasText(action) ? action.trim().toUpperCase(Locale.ROOT) : "ASK";
    }

    private boolean isSearchReady(AiRecommendSlots slots) {
        return slots != null
                && slots.getBudgetYuan() != null
                && StringUtils.hasText(slots.getRentMode())
                && StringUtils.hasText(slots.getLocationName());
    }

    private SmartGuideReqDTO toSmartGuideReq(AiRecommendSlots slots) {
        SmartGuideReqDTO reqDTO = new SmartGuideReqDTO();
        reqDTO.setBudgetYuan(slots.getBudgetYuan());
        reqDTO.setBudgetScope(slots.getBudgetScope());
        reqDTO.setRentMode(slots.getRentMode());
        reqDTO.setLocationName(slots.getLocationName());
        reqDTO.setPage(1);
        reqDTO.setSize(5);
        return reqDTO;
    }

    private AiRecommendChatVO buildResponse(String action,
                                            String assistantReply,
                                            AiRecommendSlots slots,
                                            List<String> missingSlots,
                                            SmartGuideResultVO recommendation,
                                            String sessionId) {
        AiRecommendChatVO vo = new AiRecommendChatVO();
        vo.setSessionId(sessionId);
        vo.setAction(action);
        vo.setAssistantReply(assistantReply);
        vo.setSlots(slots);
        vo.setMissingSlots(missingSlots);
        vo.setRecommendation(recommendation);
        return vo;
    }
}
```

- [ ] **Step 5: Run the service tests to verify they pass**

Run: `mvn "-Dtest=AiRecommendServiceTest,RedisAiRecommendStateStoreTest" test`

Expected: PASS with branching logic, downgrade behavior, and Redis persistence covered.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/ai/AiRecommendDecision.java src/main/java/cn/yy/myrent/service/ai/AiRecommendSlots.java src/main/java/cn/yy/myrent/service/ai/AiRecommendTurn.java src/main/java/cn/yy/myrent/service/ai/AiRecommendSessionState.java src/main/java/cn/yy/myrent/service/ai/AiRecommendStateStore.java src/main/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStore.java src/main/java/cn/yy/myrent/service/ai/AiRecommendDecisionClient.java src/main/java/cn/yy/myrent/service/ai/AiRecommendService.java src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java src/test/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStoreTest.java
git commit -m "feat(ai): add recommend service branching and redis state store"
```

## Task 3: Implement Spring AI structured decisioning

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/ai/SpringAiRecommendDecisionClient.java`
- Modify: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`

- [ ] **Step 1: Add a failing unit test that proves invalid model output is surfaced safely**

```java
@Test
void chatShouldFallbackWhenDecisionClientThrows() {
    when(decisionClient.decide(any(), any(), any())).thenThrow(new IllegalStateException("bad json"));

    AiRecommendChatReqDTO reqDTO = new AiRecommendChatReqDTO();
    reqDTO.setMessage("我想在上海租房");

    AiRecommendChatVO result = aiRecommendService.chat(1001L, reqDTO);

    assertEquals("ASK", result.getAction());
    assertTrue(result.getAssistantReply().contains("稍后重试"));
    verifyNoInteractions(houseService);
}
```

- [ ] **Step 2: Run the service test to verify it fails**

Run: `mvn "-Dtest=AiRecommendServiceTest" test`

Expected: FAIL because `AiRecommendService` currently lets the exception escape instead of returning a safe fallback.

- [ ] **Step 3: Implement the Spring AI client and safe fallback path**

```java
// src/main/java/cn/yy/myrent/service/ai/SpringAiRecommendDecisionClient.java
package cn.yy.myrent.service.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringAiRecommendDecisionClient implements AiRecommendDecisionClient {

    private final ChatClient chatClient;

    public SpringAiRecommendDecisionClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public AiRecommendDecision decide(String systemPrompt, AiRecommendSlots slots, List<AiRecommendTurn> history) {
        BeanOutputConverter<AiRecommendDecision> outputConverter = new BeanOutputConverter<>(AiRecommendDecision.class);
        String historyText = history.stream()
                .map(turn -> turn.getRole() + ": " + turn.getContent())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        String userPrompt = """
                当前槽位：
                %s

                对话历史：
                %s

                %s
                """.formatted(slots, historyText, outputConverter.getFormat());

        String content = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        return outputConverter.convert(content);
    }
}
```

```java
// src/main/java/cn/yy/myrent/service/ai/AiRecommendService.java
public AiRecommendChatVO chat(Long userId, AiRecommendChatReqDTO reqDTO) {
    AiRecommendSessionState state = stateStore.loadOrCreate(userId);
    state.getHistory().add(new AiRecommendTurn("user", reqDTO.getMessage().trim()));

    AiRecommendDecision decision;
    try {
        decision = decisionClient.decide(systemPrompt(), state.getSlots(), state.getHistory());
    } catch (Exception e) {
        String fallback = "智能推荐暂时不可用，请稍后重试，或者直接告诉我预算、整租/合租和目标区域。";
        state.getHistory().add(new AiRecommendTurn("assistant", fallback));
        stateStore.save(userId, state);
        return buildResponse("ASK", fallback, state.getSlots(), List.of("budgetYuan", "rentMode", "locationName"), null, state.getSessionId());
    }
    // keep the existing branch logic below
}
```

- [ ] **Step 4: Run the service test to verify it passes**

Run: `mvn "-Dtest=AiRecommendServiceTest" test`

Expected: PASS with safe fallback behavior added.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/ai/SpringAiRecommendDecisionClient.java src/main/java/cn/yy/myrent/service/ai/AiRecommendService.java src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java
git commit -m "feat(ai): wire spring ai structured decision client"
```

## Task 4: Build the dedicated AI recommendation page and API client

**Files:**
- Create: `frontend/src/api/aiRecommend.js`
- Create: `frontend/src/views/AiRecommendView.vue`
- Create: `frontend/src/components/ai/AiChatBubble.vue`
- Create: `frontend/src/components/ai/AiQuickPromptChips.vue`
- Create: `frontend/src/components/ai/AiRequirementSummary.vue`
- Create: `frontend/src/components/ai/AiRecommendationPanel.vue`
- Create: `frontend/src/views/__tests__/AiRecommendView.spec.js`

- [ ] **Step 1: Write the failing AI page test**

```javascript
import { flushPromises, mount } from '@vue/test-utils'
import AiRecommendView from '@/views/AiRecommendView.vue'

const fetchAiRecommendSession = vi.fn()
const sendAiRecommendMessage = vi.fn()
const resetAiRecommendSession = vi.fn()

vi.mock('@/api/aiRecommend', () => ({
  fetchAiRecommendSession: (...args) => fetchAiRecommendSession(...args),
  sendAiRecommendMessage: (...args) => sendAiRecommendMessage(...args),
  resetAiRecommendSession: (...args) => resetAiRecommendSession(...args)
}))

describe('AiRecommendView', () => {
  beforeEach(() => {
    fetchAiRecommendSession.mockReset()
    sendAiRecommendMessage.mockReset()
    resetAiRecommendSession.mockReset()

    fetchAiRecommendSession.mockResolvedValue({
      sessionId: 'ai-u1001',
      action: 'ASK',
      assistantReply: '先告诉我预算或更在意通勤还是价格。',
      slots: {
        city: '上海',
        budgetYuan: null,
        budgetScope: 'RENT_ONLY',
        rentMode: null,
        locationName: null,
        priority: null,
        preferences: []
      },
      missingSlots: ['budgetYuan', 'rentMode', 'locationName']
    })
  })

  it('loads the initial assistant message and slot summary', async () => {
    const wrapper = mount(AiRecommendView, {
      global: {
        stubs: {
          AiChatBubble: { props: ['message'], template: '<div>{{ message.content }}</div>' },
          AiQuickPromptChips: { template: '<div />' },
          AiRequirementSummary: { props: ['slots'], template: '<div>{{ slots.city }}</div>' },
          AiRecommendationPanel: { template: '<div />' }
        }
      }
    })

    await flushPromises()

    expect(fetchAiRecommendSession).toHaveBeenCalled()
    expect(wrapper.text()).toContain('先告诉我预算或更在意通勤还是价格。')
    expect(wrapper.text()).toContain('上海')
  })

  it('appends recommendation results after a chat turn', async () => {
    sendAiRecommendMessage.mockResolvedValue({
      sessionId: 'ai-u1001',
      action: 'SEARCH',
      assistantReply: '我先按你的条件帮你筛一批房源。',
      slots: {
        city: '上海',
        budgetYuan: 3500,
        budgetScope: 'RENT_ONLY',
        rentMode: 'WHOLE',
        locationName: '浦东',
        priority: 'COMMUTE',
        preferences: []
      },
      missingSlots: [],
      recommendation: {
        tipMessage: '已找到符合条件的房源。',
        recommendations: [
          { houseId: 7, title: '浦东一居', price: 3200, reasons: ['距地铁近'] }
        ]
      }
    })

    const wrapper = mount(AiRecommendView)
    await flushPromises()

    await wrapper.get('[data-test="ai-input"]').setValue('预算3500，浦东整租')
    await wrapper.get('[data-test="ai-send"]').trigger('click')
    await flushPromises()

    expect(sendAiRecommendMessage).toHaveBeenCalledWith({ message: '预算3500，浦东整租' })
    expect(wrapper.text()).toContain('我先按你的条件帮你筛一批房源。')
    expect(wrapper.text()).toContain('已找到符合条件的房源。')
    expect(wrapper.text()).toContain('浦东一居')
  })
})
```

- [ ] **Step 2: Run the AI page test to verify it fails**

Run: `npm --prefix frontend run test:run -- src/views/__tests__/AiRecommendView.spec.js`

Expected: FAIL because the AI page and AI API client do not exist.

- [ ] **Step 3: Add the API client and page-level components**

```javascript
// frontend/src/api/aiRecommend.js
import http from './http'

export function fetchAiRecommendSession() {
  return http.get('/ai-recommend/session')
}

export function sendAiRecommendMessage(payload) {
  return http.post('/ai-recommend/chat', payload)
}

export function resetAiRecommendSession() {
  return http.post('/ai-recommend/reset')
}
```

```vue
<!-- frontend/src/components/ai/AiChatBubble.vue -->
<template>
  <div class="ai-bubble" :class="message.role">
    <div class="bubble-role">{{ message.role === 'assistant' ? '智能推荐' : '我' }}</div>
    <div class="bubble-content">{{ message.content }}</div>
  </div>
</template>

<script setup>
defineProps({
  message: {
    type: Object,
    required: true
  }
})
</script>
```

```vue
<!-- frontend/src/components/ai/AiRequirementSummary.vue -->
<template>
  <section class="summary-card">
    <div class="summary-item">城市：{{ slots.city || '未确定' }}</div>
    <div class="summary-item">预算：{{ slots.budgetYuan ? `${slots.budgetYuan} 元` : '未确定' }}</div>
    <div class="summary-item">区域：{{ slots.locationName || '未确定' }}</div>
    <div class="summary-item">租住方式：{{ slots.rentMode || '未确定' }}</div>
    <div class="summary-item">优先级：{{ slots.priority || '未确定' }}</div>
  </section>
</template>

<script setup>
defineProps({
  slots: {
    type: Object,
    required: true
  }
})
</script>
```

```vue
<!-- frontend/src/components/ai/AiQuickPromptChips.vue -->
<template>
  <div class="quick-chips">
    <button
      v-for="chip in chips"
      :key="chip"
      type="button"
      class="quick-chip"
      @click="$emit('select', chip)"
    >
      {{ chip }}
    </button>
  </div>
</template>

<script setup>
defineEmits(['select'])

const chips = [
  '预算3000左右，想整租',
  '通勤方便最重要',
  '目前没想好住哪，先给我建议',
  '想住地铁附近',
  '预算有限，接受合租'
]
</script>
```

```vue
<!-- frontend/src/components/ai/AiRecommendationPanel.vue -->
<template>
  <section v-if="recommendation" class="recommend-panel">
    <p class="recommend-tip">{{ recommendation.tipMessage }}</p>
    <article
      v-for="item in recommendation.recommendations || []"
      :key="String(item.houseId)"
      class="recommend-card"
    >
      <h3>{{ item.title }}</h3>
      <p>{{ item.price }} / 月</p>
      <div class="reason-list">
        <span v-for="reason in item.reasons || []" :key="reason" class="reason-tag">{{ reason }}</span>
      </div>
    </article>
  </section>
</template>

<script setup>
defineProps({
  recommendation: {
    type: Object,
    default: null
  }
})
</script>
```

```vue
<!-- frontend/src/views/AiRecommendView.vue -->
<template>
  <div class="ai-page">
    <section class="hero-card">
      <h1>智能推荐</h1>
      <p>你可以先说一个模糊想法，我会逐步追问，再帮你找真实房源。</p>
      <button class="ghost-btn" type="button" @click="handleReset">重新开始</button>
    </section>

    <AiQuickPromptChips @select="applyQuickPrompt" />
    <AiRequirementSummary :slots="slots" />

    <section class="transcript-card">
      <AiChatBubble
        v-for="message in messages"
        :key="`${message.role}-${message.content}-${messageIndex(message)}`"
        :message="message"
      />
    </section>

    <AiRecommendationPanel :recommendation="recommendation" />

    <form class="composer" @submit.prevent="handleSend">
      <textarea
        v-model.trim="draft"
        data-test="ai-input"
        class="input"
        rows="3"
        placeholder="告诉我你的预算、整租/合租、区域偏好，或者先说你最在意什么。"
      />
      <button data-test="ai-send" class="primary-btn" type="submit" :disabled="sending || !draft">
        {{ sending ? '发送中...' : '发送' }}
      </button>
    </form>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchAiRecommendSession, resetAiRecommendSession, sendAiRecommendMessage } from '@/api/aiRecommend'
import AiChatBubble from '@/components/ai/AiChatBubble.vue'
import AiQuickPromptChips from '@/components/ai/AiQuickPromptChips.vue'
import AiRequirementSummary from '@/components/ai/AiRequirementSummary.vue'
import AiRecommendationPanel from '@/components/ai/AiRecommendationPanel.vue'

const draft = ref('')
const sending = ref(false)
const slots = ref({
  city: '',
  budgetYuan: null,
  budgetScope: 'RENT_ONLY',
  rentMode: '',
  locationName: '',
  priority: '',
  preferences: []
})
const messages = ref([])
const recommendation = ref(null)

function messageIndex(message) {
  return messages.value.indexOf(message)
}

function applyResponse(result) {
  slots.value = result?.slots || slots.value
  if (result?.assistantReply) {
    messages.value.push({ role: 'assistant', content: result.assistantReply })
  }
  recommendation.value = result?.recommendation || null
}

async function loadSession() {
  const result = await fetchAiRecommendSession()
  slots.value = result?.slots || slots.value
  messages.value = []
  recommendation.value = result?.recommendation || null
  if (result?.assistantReply) {
    messages.value.push({ role: 'assistant', content: result.assistantReply })
  }
}

async function handleSend() {
  if (!draft.value || sending.value) {
    return
  }
  const message = draft.value
  messages.value.push({ role: 'user', content: message })
  draft.value = ''
  sending.value = true
  try {
    const result = await sendAiRecommendMessage({ message })
    applyResponse(result)
  } finally {
    sending.value = false
  }
}

async function handleReset() {
  const result = await resetAiRecommendSession()
  messages.value = []
  recommendation.value = null
  slots.value = result?.slots || slots.value
  if (result?.assistantReply) {
    messages.value.push({ role: 'assistant', content: result.assistantReply })
  }
}

function applyQuickPrompt(text) {
  draft.value = text
}

onMounted(loadSession)
</script>
```

- [ ] **Step 4: Run the AI page test to verify it passes**

Run: `npm --prefix frontend run test:run -- src/views/__tests__/AiRecommendView.spec.js`

Expected: PASS with session bootstrap, message send, slot summary, and recommendation rendering covered.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/aiRecommend.js frontend/src/views/AiRecommendView.vue frontend/src/components/ai/AiChatBubble.vue frontend/src/components/ai/AiQuickPromptChips.vue frontend/src/components/ai/AiRequirementSummary.vue frontend/src/components/ai/AiRecommendationPanel.vue frontend/src/views/__tests__/AiRecommendView.spec.js
git commit -m "feat(ai): add recommend page and chat flow"
```

## Task 5: Add the dedicated route and highlighted navigation entry with dog icon

**Files:**
- Create: `frontend/src/components/icons/DogAssistantIcon.vue`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/design/site.js`
- Modify: `frontend/src/components/layout/AppTopNav.vue`
- Modify: `frontend/src/components/AppTabBar.vue`
- Modify: `frontend/src/components/__tests__/AppTopNav.spec.js`
- Create: `frontend/src/components/__tests__/AppTabBar.spec.js`

- [ ] **Step 1: Write the failing navigation tests**

```javascript
// frontend/src/components/__tests__/AppTopNav.spec.js
import { reactive } from 'vue'
import { RouterLinkStub, mount } from '@vue/test-utils'
import AppTopNav from '@/components/layout/AppTopNav.vue'

const switchCity = vi.fn()
const authState = reactive({
  currentCity: '南京',
  profile: {
    name: '元气小圆同学',
    city: '南京'
  }
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    ...authState,
    switchCity
  })
}))

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    totalUnread: 5
  })
}))

describe('AppTopNav featured ai item', () => {
  it('renders 智能推荐 as the featured center item', () => {
    const wrapper = mount(AppTopNav, {
      props: {
        items: [
          { label: '首页', to: '/home' },
          { label: '找房', to: '/houses' },
          { label: '智能推荐', to: '/ai-recommend', featured: true },
          { label: '消息', to: '/messages' },
          { label: '我的', to: '/mine' }
        ],
        currentPath: '/ai-recommend'
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
          DogAssistantIcon: { template: '<svg data-test="dog-icon" />' }
        }
      }
    })

    expect(wrapper.get('[data-nav="/ai-recommend"]').classes()).toContain('nav-link-featured')
    expect(wrapper.get('[data-nav="/ai-recommend"]').classes()).toContain('is-active')
    expect(wrapper.find('[data-test="dog-icon"]').exists()).toBe(true)
  })
})
```

```javascript
// frontend/src/components/__tests__/AppTabBar.spec.js
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import AppTabBar from '@/components/AppTabBar.vue'

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    totalUnread: 0
  })
}))

describe('AppTabBar', () => {
  it('includes the ai recommend tab', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/home', component: { template: '<div />' } },
        { path: '/houses', component: { template: '<div />' } },
        { path: '/ai-recommend', component: { template: '<div />' } },
        { path: '/messages', component: { template: '<div />' } },
        { path: '/mine', component: { template: '<div />' } }
      ]
    })
    router.push('/home')
    await router.isReady()

    const wrapper = mount(AppTabBar, {
      global: {
        plugins: [router],
        stubs: {
          DogAssistantIcon: { template: '<svg />' }
        }
      }
    })

    expect(wrapper.text()).toContain('智能推荐')
  })
})
```

- [ ] **Step 2: Run the navigation tests to verify they fail**

Run: `npm --prefix frontend run test:run -- src/components/__tests__/AppTopNav.spec.js src/components/__tests__/AppTabBar.spec.js`

Expected: FAIL because the route metadata, featured nav classes, and dog icon component do not exist.

- [ ] **Step 3: Add the dog icon, route, nav metadata, and featured styling**

```vue
<!-- frontend/src/components/icons/DogAssistantIcon.vue -->
<template>
  <svg viewBox="0 0 64 64" aria-hidden="true" class="dog-icon">
    <path d="M18 24c0-8 6-14 14-14s14 6 14 14v4c4 2 6 6 6 11 0 9-7 16-16 16h-8c-9 0-16-7-16-16 0-5 2-9 6-11v-4z" fill="currentColor" />
    <circle cx="26" cy="30" r="2.5" fill="#fff" />
    <circle cx="38" cy="30" r="2.5" fill="#fff" />
    <path d="M29 38c2 2 4 3 6 3s4-1 6-3" stroke="#fff" stroke-width="3" stroke-linecap="round" fill="none" />
  </svg>
</template>
```

```javascript
// frontend/src/design/site.js
export const topNavItems = [
  { label: '首页', to: '/home' },
  { label: '找房', to: '/houses' },
  { label: '智能推荐', to: '/ai-recommend', featured: true, icon: 'dog' },
  { label: '消息', to: '/messages' },
  { label: '我的', to: '/mine' }
]

export const mobileTabItems = [
  { path: '/home', label: '首页', icon: 'H' },
  { path: '/houses', label: '找房', icon: 'L' },
  { path: '/ai-recommend', label: '智能推荐', icon: 'dog' },
  { path: '/messages', label: '消息', icon: 'M' },
  { path: '/mine', label: '我的', icon: 'I' }
]
```

```javascript
// frontend/src/router/index.js
{
  path: 'ai-recommend',
  name: 'ai-recommend',
  component: () => import('@/views/AiRecommendView.vue')
}
```

```vue
<!-- frontend/src/components/layout/AppTopNav.vue -->
<script setup>
import DogAssistantIcon from '@/components/icons/DogAssistantIcon.vue'

function isFeaturedItem(item) {
  return item?.featured === true
}
</script>

<template>
  <header class="app-top-nav app-surface hidden lg:grid">
    <div class="brand">...</div>

    <nav class="nav-list">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        :data-nav="item.to"
        class="nav-link"
        :class="{
          'is-active': currentPath.startsWith(item.to),
          'nav-link-featured': isFeaturedItem(item)
        }"
      >
        <DogAssistantIcon v-if="item.icon === 'dog'" class="nav-dog-icon" />
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>
  </header>
</template>

<style scoped>
.nav-list {
  justify-content: center;
  gap: 10px;
  align-items: end;
}

.nav-link-featured {
  min-width: 120px;
  padding: 14px 18px 12px;
  border-radius: 999px 999px 24px 24px;
  background: linear-gradient(180deg, #f7c66a 0%, #f0a63e 100%);
  color: #2d2517;
  box-shadow: 0 14px 28px rgba(215, 148, 32, 0.28);
  transform: translateY(-10px);
  flex-direction: column;
  gap: 6px;
}

.nav-link-featured.is-active {
  background: linear-gradient(180deg, #ffd58a 0%, #f0a63e 100%);
  color: #2d2517;
}

.nav-dog-icon {
  width: 24px;
  height: 24px;
}
</style>
```

```vue
<!-- frontend/src/components/AppTabBar.vue -->
<script setup>
import DogAssistantIcon from '@/components/icons/DogAssistantIcon.vue'
</script>

<template>
  <nav class="tabbar grid">
    <button
      v-for="item in mobileTabItems"
      :key="item.path"
      class="tab-btn"
      :class="{ active: isActive(item.path) }"
      @click="go(item.path)"
    >
      <DogAssistantIcon v-if="item.icon === 'dog'" class="icon dog-tab-icon" />
      <span v-else class="icon">{{ item.icon }}</span>
      <span class="label-row">
        <span>{{ item.label }}</span>
      </span>
    </button>
  </nav>
</template>
```

- [ ] **Step 4: Run the navigation tests to verify they pass**

Run: `npm --prefix frontend run test:run -- src/components/__tests__/AppTopNav.spec.js src/components/__tests__/AppTabBar.spec.js`

Expected: PASS with the desktop featured nav item and mobile AI tab covered.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/icons/DogAssistantIcon.vue frontend/src/router/index.js frontend/src/design/site.js frontend/src/components/layout/AppTopNav.vue frontend/src/components/AppTabBar.vue frontend/src/components/__tests__/AppTopNav.spec.js frontend/src/components/__tests__/AppTabBar.spec.js
git commit -m "feat(ai): add highlighted intelligent recommendation navigation"
```

## Task 6: Run focused regression verification and manual smoke checks

**Files:**
- Modify: `src/test/java/cn/yy/myrent/controller/AiRecommendControllerWebMvcTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`
- Modify: `frontend/src/views/__tests__/AiRecommendView.spec.js`
- Modify: `frontend/src/components/__tests__/AppTopNav.spec.js`
- Modify: `frontend/src/components/__tests__/AppTabBar.spec.js`

- [ ] **Step 1: Add the final regression assertions if any gap remains**

```java
@Test
void sessionShouldReuseExistingHistoryWithoutDuplicatingOpeningMessage() {
    AiRecommendSessionState state = AiRecommendSessionState.builder()
            .sessionId("ai-u1001")
            .slots(AiRecommendSlots.builder().city("上海").build())
            .history(List.of(new AiRecommendTurn("assistant", "已有欢迎语")))
            .build();
    when(stateStore.loadOrCreate(1001L)).thenReturn(state);

    AiRecommendChatVO result = aiRecommendService.getOrCreateSession(1001L);

    assertEquals("已有欢迎语", result.getAssistantReply());
    verify(stateStore, org.mockito.Mockito.never()).save(any(), any());
}
```

```javascript
it('resets the transcript when the user clicks 重新开始', async () => {
  resetAiRecommendSession.mockResolvedValue({
    sessionId: 'ai-u1001',
    action: 'ASK',
    assistantReply: '我们重新开始，你先告诉我预算。',
    slots: {
      city: '上海',
      budgetYuan: null,
      budgetScope: 'RENT_ONLY',
      rentMode: null,
      locationName: null,
      priority: null,
      preferences: []
    },
    missingSlots: ['budgetYuan']
  })

  const wrapper = mount(AiRecommendView)
  await flushPromises()
  await wrapper.get('.ghost-btn').trigger('click')
  await flushPromises()

  expect(wrapper.text()).toContain('我们重新开始，你先告诉我预算。')
})
```

- [ ] **Step 2: Run the focused backend verification**

Run: `mvn "-Dtest=AiRecommendControllerWebMvcTest,AiRecommendServiceTest,RedisAiRecommendStateStoreTest" test`

Expected: PASS with controller contract, service branching, fallback handling, and Redis state behavior all green.

- [ ] **Step 3: Run the focused frontend verification**

Run: `npm --prefix frontend run test:run -- src/views/__tests__/AiRecommendView.spec.js src/components/__tests__/AppTopNav.spec.js src/components/__tests__/AppTabBar.spec.js`

Expected: PASS with AI page, desktop featured nav, and mobile AI tab all green.

- [ ] **Step 4: Run one manual end-to-end smoke check**

Run backend:

```bash
mvn spring-boot:run
```

Run frontend:

```bash
npm --prefix frontend run dev
```

Manual checklist:

```text
1. Open /ai-recommend after login.
2. Confirm the desktop nav center item is visually raised, labeled 智能推荐, and shows the dog icon.
3. Confirm the AI page shows an opening assistant message immediately.
4. Send: “我想在上海租房”.
5. Confirm the assistant asks a follow-up question instead of inventing houses.
6. Send: “预算3500，想在浦东整租”.
7. Confirm the assistant returns recommendation cards backed by the real smart-guide result.
8. Click 重新开始 and confirm the transcript resets to a fresh opening message.
9. On mobile width, confirm the tab bar includes 智能推荐 with the dog icon.
```

Expected:

```text
Desktop featured nav renders correctly.
Conversation can stay in ASK mode when information is insufficient.
Conversation can transition to SEARCH once budget, rent mode, and location are available.
Recommendation cards appear only after real backend search.
```

- [ ] **Step 5: Commit**

```bash
git add src/test/java/cn/yy/myrent/controller/AiRecommendControllerWebMvcTest.java src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java frontend/src/views/__tests__/AiRecommendView.spec.js frontend/src/components/__tests__/AppTopNav.spec.js frontend/src/components/__tests__/AppTabBar.spec.js
git commit -m "test(ai): verify intelligent recommendation flow"
```

## Self-Review

### Spec coverage

- dedicated `智能推荐` page: covered by Task 4 and Task 5
- desktop center navigation entry with dog icon and raised shape: covered by Task 5
- mobile AI tab entry: covered by Task 5
- multi-turn `ASK / ADVISE / SEARCH` flow: covered by Task 2 and Task 3
- Redis-backed temporary session state: covered by Task 2
- Spring AI structured decisioning rather than model training: covered by Task 1 and Task 3
- reuse existing `SmartGuideRecommendationService` via `IHouseService.smartGuide(...)`: covered by Task 2
- truthful behavior when city-only information is insufficient: covered by Task 2 and Task 6

### Placeholder scan

No `TODO`, `TBD`, “handle appropriately”, or “similar to above” placeholders remain. Every task lists exact files, commands, and concrete code snippets.

### Type consistency

- API action values stay consistent as `ASK`, `ADVISE`, `SEARCH`
- session bootstrap, chat turn, and reset all return `AiRecommendChatVO`
- slot names stay consistent across backend and frontend:
  - `city`
  - `budgetYuan`
  - `budgetScope`
  - `rentMode`
  - `locationName`
  - `priority`
  - `preferences`
- route name and path remain consistent as `ai-recommend` and `/ai-recommend`
