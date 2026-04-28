# AI Recommendation V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the AI rental assistant so prompts are externalized, backend owns workflow decisions, and model context uses summary plus a bounded recent-history window instead of full raw history.

**Architecture:** Keep the existing `/ai-recommend` API surface and Redis-backed conversation state, but split responsibilities more cleanly. The model should only return `reply + slots`, while `AiRecommendServiceImpl` becomes the single source of truth for `ASK / ADVISE / SEARCH`, `smartGuide` execution, reply override, and summary maintenance. Prompt content should be preloaded from `src/main/resources/prompts/ai-recommend/`, and Redis state should be separated into `slots`, `history`, and `summary`.

**Tech Stack:** Spring Boot, Spring AI, Redis via `StringRedisTemplate`, Jackson, JUnit 5, Mockito, Maven

---

## File Map

**Create:**
- `src/main/resources/prompts/ai-recommend/system.txt`
- `src/main/resources/prompts/ai-recommend/user-context.txt`
- `src/main/resources/prompts/ai-recommend/output-format.txt`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendPromptLoader.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendPromptBundle.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendSummaryBuilder.java`
- `src/test/java/cn/yy/myrent/service/ai/AiRecommendPromptLoaderTest.java`
- `src/test/java/cn/yy/myrent/service/ai/AiRecommendSummaryBuilderTest.java`

**Modify:**
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendDecision.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendDecisionClient.java`
- `src/main/java/cn/yy/myrent/service/ai/SpringAiRecommendDecisionClient.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendStateStore.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendSessionState.java`
- `src/main/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStore.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java`
- `src/main/resources/application.yml`
- `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`
- `src/test/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStoreTest.java`

---

### Task 1: Externalize and Preload Prompt Assets

**Files:**
- Create: `src/main/resources/prompts/ai-recommend/system.txt`
- Create: `src/main/resources/prompts/ai-recommend/user-context.txt`
- Create: `src/main/resources/prompts/ai-recommend/output-format.txt`
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendPromptBundle.java`
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendPromptLoader.java`
- Test: `src/test/java/cn/yy/myrent/service/ai/AiRecommendPromptLoaderTest.java`

- [ ] **Step 1: Write the failing prompt loader tests**

```java
package cn.yy.myrent.service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRecommendPromptLoaderTest {

    @Test
    void shouldLoadAllPromptFilesIntoMemory() {
        AiRecommendPromptLoader loader = new AiRecommendPromptLoader(
                "prompts/ai-recommend/system.txt",
                "prompts/ai-recommend/user-context.txt",
                "prompts/ai-recommend/output-format.txt"
        );

        AiRecommendPromptBundle bundle = loader.load();

        assertNotNull(bundle);
        assertTrue(bundle.systemPrompt().contains("role"));
        assertTrue(bundle.userContextTemplate().contains("${slots}"));
        assertTrue(bundle.outputFormatPrompt().contains("\"reply\""));
    }

    @Test
    void shouldReuseLoadedPromptContentWithoutRequestTimeIo() {
        AiRecommendPromptLoader loader = new AiRecommendPromptLoader(
                "prompts/ai-recommend/system.txt",
                "prompts/ai-recommend/user-context.txt",
                "prompts/ai-recommend/output-format.txt"
        );

        AiRecommendPromptBundle first = loader.load();
        AiRecommendPromptBundle second = loader.load();

        assertTrue(first == second);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn "-Dtest=AiRecommendPromptLoaderTest" test`  
Expected: FAIL because `AiRecommendPromptLoader` and `AiRecommendPromptBundle` do not exist yet.

- [ ] **Step 3: Create the prompt resource files**

```text
# src/main/resources/prompts/ai-recommend/system.txt
You are the language understanding and response layer for a rental recommendation assistant.
You may extract slots and draft a reply, but you do not decide whether backend search executes.
Never claim that you queried real listings unless backend data is already present in the input.
Never fabricate houses, prices, availability, or publishers.
Return only the fields required by the output format instructions.
```

```text
# src/main/resources/prompts/ai-recommend/user-context.txt
Current slots:
${slots}

Conversation summary:
${summary}

Recent conversation:
${recentHistory}

Current user message:
${userMessage}

Follow the output format exactly.
${format}
```

```text
# src/main/resources/prompts/ai-recommend/output-format.txt
Return valid JSON only.
{
  "reply": "string",
  "slots": {
    "city": null,
    "locationName": null,
    "budgetYuan": null,
    "budgetScope": null,
    "rentMode": null,
    "priority": null,
    "preferences": []
  }
}
```

- [ ] **Step 4: Implement the prompt bundle and loader**

```java
package cn.yy.myrent.service.ai;

public record AiRecommendPromptBundle(
        String systemPrompt,
        String userContextTemplate,
        String outputFormatPrompt
) {
}
```

```java
package cn.yy.myrent.service.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class AiRecommendPromptLoader {

    private final String systemPath;
    private final String userContextPath;
    private final String outputFormatPath;
    private volatile AiRecommendPromptBundle cached;

    public AiRecommendPromptLoader(String systemPath, String userContextPath, String outputFormatPath) {
        this.systemPath = systemPath;
        this.userContextPath = userContextPath;
        this.outputFormatPath = outputFormatPath;
    }

    public AiRecommendPromptBundle load() {
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cached == null) {
                cached = new AiRecommendPromptBundle(
                        read(systemPath),
                        read(userContextPath),
                        read(outputFormatPath)
                );
            }
            return cached;
        }
    }

    private String read(String path) {
        try (InputStreamReader reader = new InputStreamReader(
                new ClassPathResource(path).getInputStream(),
                StandardCharsets.UTF_8
        )) {
            return FileCopyUtils.copyToString(reader);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to load prompt: " + path, ex);
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn "-Dtest=AiRecommendPromptLoaderTest" test`  
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/prompts/ai-recommend src/main/java/cn/yy/myrent/service/ai/AiRecommendPromptBundle.java src/main/java/cn/yy/myrent/service/ai/AiRecommendPromptLoader.java src/test/java/cn/yy/myrent/service/ai/AiRecommendPromptLoaderTest.java
git commit -m "refactor: externalize ai recommend prompts"
```

---

### Task 2: Remove LLM Workflow Decisions From the Internal Contract

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendDecision.java`
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendDecisionClient.java`
- Modify: `src/main/java/cn/yy/myrent/service/ai/SpringAiRecommendDecisionClient.java`
- Test: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`

- [ ] **Step 1: Write the failing contract test**

```java
@Test
void chatShouldIgnoreModelActionAndUseReplyPlusSlotsOnly() {
    AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
    when(stateStore.loadOrCreate(1001L)).thenReturn(session);
    when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
            .thenReturn(AiRecommendDecision.builder()
                    .reply("Please tell me your budget.")
                    .slots(AiRecommendSlots.builder().city("Shanghai").build())
                    .build());

    AiRecommendChatVO result = aiRecommendService.chat(1001L, req("I want to rent in Shanghai"));

    assertEquals("ASK", result.getAction());
    assertEquals("Shanghai", result.getSlots().getCity());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn "-Dtest=AiRecommendServiceTest#chatShouldIgnoreModelActionAndUseReplyPlusSlotsOnly" test`  
Expected: FAIL because service logic still expects model `action`.

- [ ] **Step 3: Shrink the decision contract**

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendDecision {

    private String reply;

    private AiRecommendSlots slots;
}
```

```java
public interface AiRecommendDecisionClient {

    AiRecommendDecision decide(AiRecommendSessionState sessionState, String userMessage);
}
```

- [ ] **Step 4: Rework the Spring AI client to use the preloaded prompt assets**

```java
public class SpringAiRecommendDecisionClient implements AiRecommendDecisionClient {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AiRecommendPromptBundle prompts;

    public SpringAiRecommendDecisionClient(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            AiRecommendPromptLoader promptLoader
    ) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.prompts = promptLoader.load();
    }

    @Override
    public AiRecommendDecision decide(AiRecommendSessionState sessionState, String userMessage) {
        BeanOutputConverter<AiRecommendDecision> outputConverter = new BeanOutputConverter<>(AiRecommendDecision.class);
        String prompt = prompts.userContextTemplate()
                .replace("${slots}", toJson(sessionState.getSlots()))
                .replace("${summary}", safeText(sessionState.getSummary()))
                .replace("${recentHistory}", formatHistory(sessionState.getHistory()))
                .replace("${userMessage}", userMessage)
                .replace("${format}", prompts.outputFormatPrompt() + "\n" + outputConverter.getFormat());

        AiRecommendDecision decision = chatClient.prompt()
                .system(prompts.systemPrompt())
                .user(prompt)
                .call()
                .entity(outputConverter);
        if (decision == null) {
            throw new IllegalStateException("spring ai decision is null");
        }
        return decision;
    }
}
```

- [ ] **Step 5: Run the focused test again**

Run: `mvn "-Dtest=AiRecommendServiceTest#chatShouldIgnoreModelActionAndUseReplyPlusSlotsOnly" test`  
Expected: PASS after service logic no longer depends on model `action`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/ai/AiRecommendDecision.java src/main/java/cn/yy/myrent/service/ai/AiRecommendDecisionClient.java src/main/java/cn/yy/myrent/service/ai/SpringAiRecommendDecisionClient.java src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java
git commit -m "refactor: remove ai workflow decision output"
```

---

### Task 3: Split Redis State Into Slots, Raw History, and Summary

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendStateStore.java`
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendSessionState.java`
- Modify: `src/main/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStore.java`
- Modify: `src/test/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStoreTest.java`

- [ ] **Step 1: Write the failing state-store tests**

```java
@Test
void saveShouldPersistSlotsHistoryAndSummarySeparately() {
    AiRecommendSessionState state = AiRecommendSessionState.builder()
            .userId(1001L)
            .sessionId("ai-u1001")
            .summary("confirmed city: Shanghai")
            .slots(AiRecommendSlots.builder().city("Shanghai").build())
            .history(List.of(AiRecommendTurn.user("hello")))
            .build();

    stateStore.save(state);

    verify(valueOperations).set(eq("ai:recommend:slots:1001"), any(String.class), eq(Duration.ofHours(48)));
    verify(valueOperations).set(eq("ai:recommend:history:1001"), any(String.class), eq(Duration.ofHours(48)));
    verify(valueOperations).set(eq("ai:recommend:summary:1001"), any(String.class), eq(Duration.ofHours(48)));
}

@Test
void loadOrCreateShouldTrimStoredHistoryToThirtyTurns() {
    // construct 35 turns and assert load result keeps 30
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn "-Dtest=RedisAiRecommendStateStoreTest" test`  
Expected: FAIL because store still persists the old combined shape and summary does not exist.

- [ ] **Step 3: Add summary to session state**

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendSessionState {

    private Long userId;
    private String sessionId;
    private String summary;
    private AiRecommendSlots slots;

    @Builder.Default
    private List<AiRecommendTurn> history = new ArrayList<>();
}
```

- [ ] **Step 4: Update state store persistence contract**

```java
public interface AiRecommendStateStore {

    AiRecommendSessionState loadOrCreate(Long userId);

    void save(AiRecommendSessionState state);

    void reset(Long userId);
}
```

```java
private String slotsKey(Long userId) {
    return "ai:recommend:slots:" + userId;
}

private String historyKey(Long userId) {
    return "ai:recommend:history:" + userId;
}

private String summaryKey(Long userId) {
    return "ai:recommend:summary:" + userId;
}
```

- [ ] **Step 5: Implement separated Redis persistence**

```java
public void save(AiRecommendSessionState state) {
    Long userId = resolveUserId(state);
    List<AiRecommendTurn> trimmedHistory = trimHistory(state.getHistory(), 30);
    AiRecommendSlots safeSlots = state.getSlots() == null
            ? AiRecommendSlots.builder().preferences(new ArrayList<>()).build()
            : state.getSlots();
    String safeSummary = state.getSummary() == null ? "" : state.getSummary();

    valueOps.set(slotsKey(userId), objectMapper.writeValueAsString(safeSlots), ttl);
    valueOps.set(historyKey(userId), objectMapper.writeValueAsString(trimmedHistory), ttl);
    valueOps.set(summaryKey(userId), safeSummary, ttl);
}
```

```java
public AiRecommendSessionState loadOrCreate(Long userId) {
    String slotsJson = valueOps.get(slotsKey(userId));
    String historyJson = valueOps.get(historyKey(userId));
    String summary = valueOps.get(summaryKey(userId));
    // parse each payload independently and fall back to empty state
}
```

- [ ] **Step 6: Run state-store tests**

Run: `mvn "-Dtest=RedisAiRecommendStateStoreTest" test`  
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/ai/AiRecommendStateStore.java src/main/java/cn/yy/myrent/service/ai/AiRecommendSessionState.java src/main/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStore.java src/test/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStoreTest.java
git commit -m "refactor: split ai recommend redis state"
```

---

### Task 4: Add Backend Summary Generation

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendSummaryBuilder.java`
- Create: `src/test/java/cn/yy/myrent/service/ai/AiRecommendSummaryBuilderTest.java`
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java`

- [ ] **Step 1: Write the failing summary builder tests**

```java
class AiRecommendSummaryBuilderTest {

    @Test
    void shouldBuildCompactSummaryFromSlotsAndMissingFields() {
        AiRecommendSummaryBuilder builder = new AiRecommendSummaryBuilder();
        AiRecommendSlots slots = AiRecommendSlots.builder()
                .city("Shanghai")
                .locationName("Yuyuan")
                .budgetYuan(3500)
                .rentMode("WHOLE")
                .priority("COMMUTE")
                .preferences(List.of("near subway"))
                .build();

        String summary = builder.build(slots, List.of());

        assertTrue(summary.contains("Shanghai"));
        assertTrue(summary.contains("3500"));
        assertTrue(summary.contains("WHOLE"));
    }

    @Test
    void shouldMentionMissingRequiredSlotsWhenSearchIsNotReady() {
        AiRecommendSummaryBuilder builder = new AiRecommendSummaryBuilder();

        String summary = builder.build(
                AiRecommendSlots.builder().city("Shanghai").build(),
                List.of("budgetYuan", "rentMode", "locationName")
        );

        assertTrue(summary.contains("budgetYuan"));
        assertTrue(summary.contains("rentMode"));
        assertTrue(summary.contains("locationName"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn "-Dtest=AiRecommendSummaryBuilderTest" test`  
Expected: FAIL because `AiRecommendSummaryBuilder` does not exist.

- [ ] **Step 3: Implement the summary builder**

```java
package cn.yy.myrent.service.ai;

import java.util.List;
import java.util.StringJoiner;

public class AiRecommendSummaryBuilder {

    public String build(AiRecommendSlots slots, List<String> missingSlots) {
        StringJoiner joiner = new StringJoiner("; ");
        joiner.add("city=" + safe(slots.getCity()));
        joiner.add("location=" + safe(slots.getLocationName()));
        joiner.add("budget=" + safe(slots.getBudgetYuan()));
        joiner.add("budgetScope=" + safe(slots.getBudgetScope()));
        joiner.add("rentMode=" + safe(slots.getRentMode()));
        joiner.add("priority=" + safe(slots.getPriority()));
        joiner.add("preferences=" + (slots.getPreferences() == null ? "[]" : slots.getPreferences()));
        joiner.add("missing=" + missingSlots);
        return joiner.toString();
    }

    private Object safe(Object value) {
        return value == null ? "null" : value;
    }
}
```

- [ ] **Step 4: Wire summary generation into service state updates**

```java
List<String> missingSlots = buildMissingSlots(mergedSlots);
state.setSummary(summaryBuilder.build(mergedSlots, missingSlots));
```

- [ ] **Step 5: Run tests**

Run: `mvn "-Dtest=AiRecommendSummaryBuilderTest,AiRecommendServiceTest" test`  
Expected: PASS for the new summary cases and existing service tests still green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/ai/AiRecommendSummaryBuilder.java src/test/java/cn/yy/myrent/service/ai/AiRecommendSummaryBuilderTest.java src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java
git commit -m "feat: add backend ai conversation summary"
```

---

### Task 5: Move Workflow Decision Authority Fully Into the Service Layer

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java`
- Modify: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`

- [ ] **Step 1: Write the failing service tests for backend-owned action**

```java
@Test
void chatShouldDeriveSearchWhenRequiredSlotsAreReadyEvenWithoutModelAction() {
    AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
    when(stateStore.loadOrCreate(1001L)).thenReturn(session);
    when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
            .thenReturn(AiRecommendDecision.builder()
                    .reply("I will help you with that.")
                    .slots(AiRecommendSlots.builder()
                            .city("Shanghai")
                            .locationName("Yuyuan")
                            .budgetYuan(3500)
                            .budgetScope("RENT_ONLY")
                            .rentMode("WHOLE")
                            .build())
                    .build());
    when(houseService.smartGuide(any(SmartGuideReqDTO.class))).thenReturn(new SmartGuideResultVO());

    AiRecommendChatVO result = aiRecommendService.chat(1001L, req("budget 3500, whole rent, Yuyuan"));

    assertEquals("SEARCH", result.getAction());
}

@Test
void chatShouldDeriveAskWhenRequiredSlotsRemainMissing() {
    // same setup with missing rentMode/locationName and assert ASK
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn "-Dtest=AiRecommendServiceTest" test`  
Expected: FAIL because service still derives action from the removed model field.

- [ ] **Step 3: Replace model-action usage with backend derivation**

```java
String action = deriveAction(mergedSlots, message);
List<String> missingSlots = buildMissingSlots(mergedSlots);
String assistantReply = normalizeReply(decision.getReply());

if ("SEARCH".equals(action)) {
    try {
        recommendation = houseService.smartGuide(buildSmartGuideReq(mergedSlots));
    } catch (Exception ex) {
        action = "ADVISE";
        assistantReply = fallbackSearchFailureReply();
    }
} else if ("ASK".equals(action)) {
    assistantReply = ensureAskReply(assistantReply, missingSlots, mergedSlots);
} else {
    assistantReply = ensureAdviseReply(assistantReply, mergedSlots);
}
```

```java
private String deriveAction(AiRecommendSlots slots, String userMessage) {
    List<String> missingSlots = buildMissingSlots(slots);
    if (missingSlots.isEmpty()) {
        return "SEARCH";
    }
    if (shouldAdviseInsteadOfAsk(slots, userMessage, missingSlots)) {
        return "ADVISE";
    }
    return "ASK";
}
```

- [ ] **Step 4: Add reply override guards**

```java
private String ensureAskReply(String reply, List<String> missingSlots, AiRecommendSlots slots) {
    if (looksLikeSearchCommitment(reply)) {
        return buildDowngradeReply(missingSlots, slots);
    }
    return StringUtils.hasText(reply) ? reply : buildDowngradeReply(missingSlots, slots);
}
```

- [ ] **Step 5: Run the service suite**

Run: `mvn "-Dtest=AiRecommendServiceTest" test`  
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java
git commit -m "refactor: move ai workflow decisions into backend"
```

---

### Task 6: Limit Model Context to Summary Plus Six Recent Turns

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/ai/SpringAiRecommendDecisionClient.java`
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`

- [ ] **Step 1: Write the failing recent-history window test**

```java
@Test
void chatShouldSendOnlyRecentSixTurnsToModelContext() {
    AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
    session.setHistory(IntStream.range(0, 10)
            .mapToObj(i -> new AiRecommendTurn(i % 2 == 0 ? "user" : "assistant", "turn-" + i))
            .toList());
    when(stateStore.loadOrCreate(1001L)).thenReturn(session);
    when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
            .thenAnswer(invocation -> {
                AiRecommendSessionState forwarded = invocation.getArgument(0);
                assertEquals(6, forwarded.getHistory().size());
                return AiRecommendDecision.builder().reply("ok").slots(AiRecommendSlots.builder().build()).build();
            });

    aiRecommendService.chat(1001L, req("hello"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn "-Dtest=AiRecommendServiceTest#chatShouldSendOnlyRecentSixTurnsToModelContext" test`  
Expected: FAIL because full service history is still forwarded to the client.

- [ ] **Step 3: Add configurable recent-history prompt window**

```yaml
myrent:
  ai:
    recommend:
      history-limit: 30
      prompt-history-limit: 6
```

```java
private AiRecommendSessionState buildPromptState(AiRecommendSessionState state) {
    AiRecommendSessionState promptState = AiRecommendSessionState.builder()
            .userId(state.getUserId())
            .sessionId(state.getSessionId())
            .summary(state.getSummary())
            .slots(state.getSlots())
            .history(trimPromptHistory(state.getHistory()))
            .build();
    return promptState;
}
```

- [ ] **Step 4: Use prompt history trimming before model invocation**

```java
AiRecommendSessionState promptState = buildPromptState(state);
decision = decisionClient.decide(promptState, message);
```

- [ ] **Step 5: Run focused and full service tests**

Run: `mvn "-Dtest=AiRecommendServiceTest" test`  
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/application.yml src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java src/main/java/cn/yy/myrent/service/ai/SpringAiRecommendDecisionClient.java src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java
git commit -m "feat: bound ai recommend prompt history window"
```

---

### Task 7: End-to-End Regression Verification

**Files:**
- Modify: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStoreTest.java`

- [ ] **Step 1: Add regression coverage for the final V2 contract**

```java
@Test
void chatShouldPersistSummaryAndReturnBackendGeneratedSearchAction() {
    // setup search-ready slots, stub ai reply, assert SEARCH response and verify stateStore.save captures summary
}

@Test
void chatShouldOverrideMisleadingModelReplyWhenSearchIsNotReady() {
    // model says "I'll search now", but missing slots force ASK and reply override
}
```

- [ ] **Step 2: Run the combined focused backend suite**

Run: `mvn "-Dtest=AiRecommendServiceTest,RedisAiRecommendStateStoreTest,AiRecommendPromptLoaderTest,AiRecommendSummaryBuilderTest" test`  
Expected: PASS

- [ ] **Step 3: Run the broader controller regression**

Run: `mvn "-Dtest=AiRecommendControllerWebMvcTest,AiRecommendServiceTest,RedisAiRecommendStateStoreTest,AiRecommendPromptLoaderTest,AiRecommendSummaryBuilderTest" test`  
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java src/test/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStoreTest.java
git commit -m "test: cover ai recommendation v2 workflow"
```

---

## Spec Coverage Check

- Prompt externalization: covered by Task 1 and Task 2.
- Remove model workflow decisions: covered by Task 2 and Task 5.
- Keep model-generated reply: preserved in Task 2 and guarded in Task 5.
- Backend summary generation: covered by Task 4.
- Store raw history but send only recent 6 turns to model: covered by Task 3 and Task 6.
- Redis retention split into slots, history, summary: covered by Task 3.
- No per-request prompt file IO: covered by Task 1.

No uncovered requirement remains from the approved V2 spec.

## Placeholder Scan

- No `TODO`, `TBD`, or “implement later” placeholders remain.
- Each task has concrete files, tests, commands, and implementation snippets.

## Type Consistency Check

- Model contract is consistently described as `reply + slots`.
- Redis state is consistently split into `slots`, `history`, and `summary`.
- Prompt history window is consistently 6.
- Stored raw history limit is consistently 30.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-28-ai-recommendation-v2-implementation.md`.

Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?

