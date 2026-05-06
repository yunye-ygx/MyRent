# AI Guided Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (preferred in this repo) or superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real-listing-grounded guided preview stage to the AI rental assistant so users can see 2 to 3 preview directions after giving a resolvable area, then refine into formal search without being forced through rigid slot questioning.

**Architecture:** Keep `SmartGuideRecommendationService` as the only source of final recommendation truth, but extract a shared candidate collection foundation that preview can reuse with optional `budgetYuan` and `rentMode`. Extend the AI orchestration layer with a deterministic `ASK / PREVIEW / REFINE / SEARCH` state machine, preview response objects, structured preview-selection input, and a frontend preview card that posts preference patches back to the same `/ai-recommend/chat` endpoint.

**Tech Stack:** Spring Boot 3.5, Spring AI, MyBatis-Plus, Redis, Elasticsearch via Spring Data Elasticsearch, JUnit 5, Mockito, Vue 3, Vue Router, Axios, Vitest, Vue Test Utils

---

## File Map

### Backend

- Modify: `src/main/java/cn/yy/myrent/dto/AiRecommendChatReqDTO.java`
  Responsibility: accept either free-text chat input or a structured preview interaction.
- Create: `src/main/java/cn/yy/myrent/dto/AiRecommendInteractionDTO.java`
  Responsibility: represent a frontend preview selection event.
- Create: `src/main/java/cn/yy/myrent/dto/AiRecommendInteractionSlotPatchDTO.java`
  Responsibility: carry the structured preference patch chosen from one preview group.
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendStage.java`
  Responsibility: define the backend-owned stage enum.
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendSessionState.java`
  Responsibility: persist the last backend stage with the existing slots, history, and summary.
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendStateStore.java`
  Responsibility: continue abstracting session persistence while including stage.
- Modify: `src/main/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStore.java`
  Responsibility: serialize and deserialize stage alongside slots, history, and summary.
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java`
  Responsibility: orchestrate text input, preview selection input, stage derivation, preview generation, and final search.
- Modify: `src/main/java/cn/yy/myrent/vo/AiRecommendChatVO.java`
  Responsibility: return `stage`, optional preview payload, and final recommendation payload.
- Create: `src/main/java/cn/yy/myrent/vo/AiPreviewVO.java`
  Responsibility: return grouped preview directions for one location.
- Create: `src/main/java/cn/yy/myrent/vo/AiPreviewGroupVO.java`
  Responsibility: represent one selectable preview direction card.
- Create: `src/main/java/cn/yy/myrent/vo/AiPreviewSlotPatchVO.java`
  Responsibility: describe the slot patch tied to one preview group.
- Modify: `src/main/resources/prompts/ai-recommend/system.txt`
  Responsibility: tell the model how to behave when backend is in preview or refinement stages.
- Modify: `src/main/resources/prompts/ai-recommend/user-context.txt`
  Responsibility: include preview digest when backend has already formed preview groups.
- Create: `src/main/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateQuery.java`
  Responsibility: carry preview or search candidate query parameters with optional budget and rent mode.
- Create: `src/main/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateBundle.java`
  Responsibility: return resolved location plus collected candidate houses.
- Create: `src/main/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateCollector.java`
  Responsibility: extract and reuse candidate retrieval logic from the current smart guide service.
- Modify: `src/main/java/cn/yy/myrent/service/smartguide/SmartGuideRecommendationService.java`
  Responsibility: delegate candidate collection to the new collector while preserving final search behavior.
- Create: `src/main/java/cn/yy/myrent/service/ai/AiPreviewService.java`
  Responsibility: define preview generation from shared candidate data.
- Create: `src/main/java/cn/yy/myrent/service/ai/AiPreviewServiceImpl.java`
  Responsibility: build 2 to 3 factual preview groups using supported house fields only.
- Create: `src/test/java/cn/yy/myrent/controller/AiRecommendControllerWebMvcTest.java`
  Responsibility: cover preview interaction request validation and stage-shaped responses.
- Create: `src/test/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateCollectorTest.java`
  Responsibility: cover optional filter behavior for preview candidate collection.
- Create: `src/test/java/cn/yy/myrent/service/ai/AiPreviewServiceTest.java`
  Responsibility: verify preview group generation, supported field usage, and slot patches.
- Modify: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`
  Responsibility: verify stage transitions, preview fallback, preview selection handling, and final search branching.
- Modify: `src/test/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStoreTest.java`
  Responsibility: verify stage persistence.

### Frontend

- Modify: `frontend/src/api/aiRecommend.js`
  Responsibility: continue sending `/ai-recommend/chat` requests with either `message` or `interaction`.
- Create: `frontend/src/components/ai/AiPreviewPanel.vue`
  Responsibility: render grouped preview directions and emit one structured selection.
- Modify: `frontend/src/views/AiRecommendView.vue`
  Responsibility: render stage-driven middle state, post preview selection payloads, and keep final recommendation rendering gated by `SEARCH`.
- Modify: `frontend/src/views/__tests__/AiRecommendView.spec.js`
  Responsibility: verify preview rendering, selection payload, and search transition.

---

### Task 1: Expand the `/ai-recommend` API contract for stage and preview interaction

**Files:**
- Modify: `src/main/java/cn/yy/myrent/dto/AiRecommendChatReqDTO.java`
- Create: `src/main/java/cn/yy/myrent/dto/AiRecommendInteractionDTO.java`
- Create: `src/main/java/cn/yy/myrent/dto/AiRecommendInteractionSlotPatchDTO.java`
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendStage.java`
- Modify: `src/main/java/cn/yy/myrent/vo/AiRecommendChatVO.java`
- Create: `src/main/java/cn/yy/myrent/vo/AiPreviewVO.java`
- Create: `src/main/java/cn/yy/myrent/vo/AiPreviewGroupVO.java`
- Create: `src/main/java/cn/yy/myrent/vo/AiPreviewSlotPatchVO.java`
- Modify: `src/main/java/cn/yy/myrent/controller/AiRecommendController.java`
- Modify: `src/test/java/cn/yy/myrent/controller/AiRecommendControllerWebMvcTest.java`

- [ ] **Step 1: Write the failing controller tests for stage and preview interaction**

```java
package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.service.ai.AiRecommendService;
import cn.yy.myrent.vo.AiPreviewGroupVO;
import cn.yy.myrent.vo.AiPreviewSlotPatchVO;
import cn.yy.myrent.vo.AiPreviewVO;
import cn.yy.myrent.vo.AiRecommendChatVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiRecommendController.class)
class AiRecommendControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiRecommendService aiRecommendService;

    @MockBean
    private JwtTokenUtil jwtTokenUtil;

    @Test
    void chatShouldRejectRequestWithoutMessageOrInteraction() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        mockMvc.perform(post("/ai-recommend/chat")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(aiRecommendService);
    }

    @Test
    void chatShouldAcceptPreviewSelectionPayload() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        AiPreviewSlotPatchVO slotPatch = new AiPreviewSlotPatchVO();
        slotPatch.setPriority("COMMUTE");
        slotPatch.setPreferences(List.of("nearSubway"));

        AiPreviewGroupVO group = new AiPreviewGroupVO();
        group.setGroupKey("near_metro");
        group.setTitle("更靠近地铁");
        group.setSummary("通勤更方便，但首月成本通常更高一些。");
        group.setHighlights(List.of("近地铁", "通勤更短"));
        group.setSampleCount(6);
        group.setSlotPatch(slotPatch);

        AiPreviewVO preview = new AiPreviewVO();
        preview.setLocationName("豫园");
        preview.setCandidateCount(18);
        preview.setGroups(List.of(group));

        AiRecommendChatVO vo = new AiRecommendChatVO();
        vo.setSessionId("ai-u1001");
        vo.setStage("REFINE");
        vo.setAssistantReply("我先按近地铁方向继续收窄。");
        vo.setPreview(preview);

        given(aiRecommendService.chat(any(Long.class), any())).willReturn(vo);

        mockMvc.perform(post("/ai-recommend/chat")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "interaction": {
                                    "type": "PREVIEW_SELECTION",
                                    "groupKey": "near_metro",
                                    "label": "先看近地铁的",
                                    "slotPatch": {
                                      "priority": "COMMUTE",
                                      "preferences": ["nearSubway"]
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.stage").value("REFINE"))
                .andExpect(jsonPath("$.data.preview.groups[0].groupKey").value("near_metro"));
    }
}
```

- [ ] **Step 2: Run the controller test to verify it fails**

Run: `mvn "-Dtest=AiRecommendControllerWebMvcTest" test`  
Expected: FAIL because the DTO and VO contract does not yet support structured preview selection or `stage` / `preview`.

- [ ] **Step 3: Add the new DTOs, enum, and response VOs**

```java
// src/main/java/cn/yy/myrent/dto/AiRecommendChatReqDTO.java
package cn.yy.myrent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class AiRecommendChatReqDTO {

    private String message;

    @Valid
    private AiRecommendInteractionDTO interaction;

    @AssertTrue(message = "message or interaction must be provided")
    public boolean hasUsableInput() {
        return StringUtils.hasText(message) || interaction != null;
    }
}
```

```java
// src/main/java/cn/yy/myrent/dto/AiRecommendInteractionDTO.java
package cn.yy.myrent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiRecommendInteractionDTO {

    @NotBlank(message = "interaction.type cannot be blank")
    private String type;

    @NotBlank(message = "interaction.groupKey cannot be blank")
    private String groupKey;

    @NotBlank(message = "interaction.label cannot be blank")
    private String label;

    @Valid
    private AiRecommendInteractionSlotPatchDTO slotPatch;
}
```

```java
// src/main/java/cn/yy/myrent/dto/AiRecommendInteractionSlotPatchDTO.java
package cn.yy.myrent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiRecommendInteractionSlotPatchDTO {

    private String priority;

    private String rentMode;

    private Integer budgetYuan;

    private String budgetScope;

    private String locationName;

    private List<String> preferences = new ArrayList<>();
}
```

```java
// src/main/java/cn/yy/myrent/service/ai/AiRecommendStage.java
package cn.yy.myrent.service.ai;

public enum AiRecommendStage {
    ASK,
    PREVIEW,
    REFINE,
    SEARCH
}
```

```java
// src/main/java/cn/yy/myrent/vo/AiPreviewSlotPatchVO.java
package cn.yy.myrent.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiPreviewSlotPatchVO {

    private String priority;

    private String rentMode;

    private Integer budgetYuan;

    private String budgetScope;

    private String locationName;

    private List<String> preferences = new ArrayList<>();
}
```

```java
// src/main/java/cn/yy/myrent/vo/AiPreviewGroupVO.java
package cn.yy.myrent.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiPreviewGroupVO {

    private String groupKey;

    private String title;

    private String summary;

    private List<String> highlights = new ArrayList<>();

    private Integer sampleCount;

    private List<Long> sampleHouseIds = new ArrayList<>();

    private AiPreviewSlotPatchVO slotPatch;
}
```

```java
// src/main/java/cn/yy/myrent/vo/AiPreviewVO.java
package cn.yy.myrent.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiPreviewVO {

    private String locationName;

    private Integer candidateCount;

    private List<AiPreviewGroupVO> groups = new ArrayList<>();
}
```

```java
// src/main/java/cn/yy/myrent/vo/AiRecommendChatVO.java
package cn.yy.myrent.vo;

import lombok.Data;

import java.util.List;

@Data
public class AiRecommendChatVO {

    private String sessionId;

    private String stage;

    private String assistantReply;

    private AiRecommendSlotsVO slots;

    private List<String> missingSlots;

    private AiPreviewVO preview;

    private SmartGuideResultVO recommendation;
}
```

- [ ] **Step 4: Keep the controller endpoint shape stable while accepting the new contract**

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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai-recommend")
@RequiredArgsConstructor
public class AiRecommendController {

    private final AiRecommendService aiRecommendService;

    @PostMapping("/chat")
    public Result<AiRecommendChatVO> chat(@Valid @RequestBody AiRecommendChatReqDTO reqDTO) {
        return Result.success(aiRecommendService.chat(UserContext.getCurrentUserId(), reqDTO));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : "invalid request";
        return Result.error(400, message);
    }
}
```

- [ ] **Step 5: Run the controller test to verify it passes**

Run: `mvn "-Dtest=AiRecommendControllerWebMvcTest" test`  
Expected: PASS with both the new validation rule and the preview interaction request accepted.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/yy/myrent/dto/AiRecommendChatReqDTO.java src/main/java/cn/yy/myrent/dto/AiRecommendInteractionDTO.java src/main/java/cn/yy/myrent/dto/AiRecommendInteractionSlotPatchDTO.java src/main/java/cn/yy/myrent/service/ai/AiRecommendStage.java src/main/java/cn/yy/myrent/vo/AiRecommendChatVO.java src/main/java/cn/yy/myrent/vo/AiPreviewVO.java src/main/java/cn/yy/myrent/vo/AiPreviewGroupVO.java src/main/java/cn/yy/myrent/vo/AiPreviewSlotPatchVO.java src/main/java/cn/yy/myrent/controller/AiRecommendController.java src/test/java/cn/yy/myrent/controller/AiRecommendControllerWebMvcTest.java
git commit -m "feat(ai): add preview interaction request and stage response contract"
```

---

### Task 2: Extract a shared candidate collection foundation from smart guide

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateQuery.java`
- Create: `src/main/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateBundle.java`
- Create: `src/main/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateCollector.java`
- Modify: `src/main/java/cn/yy/myrent/service/smartguide/SmartGuideRecommendationService.java`
- Create: `src/test/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateCollectorTest.java`

- [ ] **Step 1: Write the failing collector tests for preview-friendly optional filters**

```java
package cn.yy.myrent.service.smartguide;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.location.LocationResolveService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartGuideCandidateCollectorTest {

    @Mock
    private HouseMapper houseMapper;

    @Mock
    private LocationResolveService locationResolveService;

    @InjectMocks
    private SmartGuideCandidateCollector collector;

    @Test
    void collectShouldSkipRentTypeAndBudgetFiltersWhenPreviewQueryDoesNotProvideThem() {
        when(locationResolveService.resolveRequired("豫园"))
                .thenReturn(new LocationResolveService.ResolvedLocation("豫园", 31.227, 121.492));
        when(houseMapper.selectSmartGuideCandidateIds(isNull(), anyInt(), isNull(), anyBoolean(),
                isNull(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(101L, 102L));
        when(houseMapper.selectBatchIds(List.of(101L, 102L))).thenReturn(List.of(
                new House().setId(101L).setTitle("豫园近地铁整租").setPrice(420000).setRentType(1).setNearSubway(1),
                new House().setId(102L).setTitle("豫园预算友好合租").setPrice(260000).setRentType(2).setNearSubway(0)
        ));

        SmartGuideCandidateBundle bundle = collector.collect(SmartGuideCandidateQuery.builder()
                .locationName("豫园")
                .size(12)
                .build());

        assertEquals("豫园", bundle.locationName());
        assertEquals(2, bundle.candidates().size());
        assertFalse(bundle.candidates().isEmpty());
        verify(houseMapper).selectSmartGuideCandidateIds(isNull(), anyInt(), isNull(), anyBoolean(),
                isNull(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void collectShouldApplyRentTypeAndBudgetWhenSearchQueryProvidesThem() {
        when(locationResolveService.resolveRequired("豫园"))
                .thenReturn(new LocationResolveService.ResolvedLocation("豫园", 31.227, 121.492));
        when(houseMapper.selectSmartGuideCandidateIds(isNull(), anyInt(), anyInt(), anyBoolean(),
                anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(201L));
        when(houseMapper.selectBatchIds(List.of(201L))).thenReturn(List.of(
                new House().setId(201L).setTitle("豫园整租一居").setPrice(350000).setRentType(1)
        ));

        SmartGuideCandidateBundle bundle = collector.collect(SmartGuideCandidateQuery.builder()
                .locationName("豫园")
                .budgetYuan(3500)
                .budgetScope("RENT_ONLY")
                .rentMode("WHOLE")
                .size(10)
                .build());

        assertNotNull(bundle);
        assertEquals(1, bundle.candidates().size());
    }
}
```

- [ ] **Step 2: Run the collector test to verify it fails**

Run: `mvn "-Dtest=SmartGuideCandidateCollectorTest" test`  
Expected: FAIL because the shared collector types do not exist and current candidate collection is buried inside `SmartGuideRecommendationService`.

- [ ] **Step 3: Create the shared candidate query and bundle types**

```java
// src/main/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateQuery.java
package cn.yy.myrent.service.smartguide;

import lombok.Builder;

@Builder
public record SmartGuideCandidateQuery(
        String locationName,
        Integer budgetYuan,
        String budgetScope,
        String rentMode,
        Integer size
) {
}
```

```java
// src/main/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateBundle.java
package cn.yy.myrent.service.smartguide;

import cn.yy.myrent.entity.House;

import java.util.List;

public record SmartGuideCandidateBundle(
        String locationName,
        double targetLatitude,
        double targetLongitude,
        boolean esAvailable,
        List<House> candidates
) {
}
```

- [ ] **Step 4: Implement the collector by moving candidate retrieval out of the recommendation service**

```java
// src/main/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateCollector.java
package cn.yy.myrent.service.smartguide;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.location.LocationResolveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SmartGuideCandidateCollector {

    private static final int HOUSE_STATUS_AVAILABLE = 1;
    private static final int DEFAULT_PREVIEW_SCAN_LIMIT = 24;
    private static final double DEFAULT_RADIUS_KM = 5.0d;

    private final HouseMapper houseMapper;
    private final LocationResolveService locationResolveService;

    public SmartGuideCandidateBundle collect(SmartGuideCandidateQuery query) {
        LocationResolveService.ResolvedLocation location = locationResolveService.resolveRequired(query.locationName());
        Integer rentType = resolveRentTypeCode(query.rentMode());
        Integer maxComparableCostCent = query.budgetYuan() == null ? null : query.budgetYuan() * 100;
        boolean totalCostScope = "TOTAL".equalsIgnoreCase(query.budgetScope());
        BoundingBox box = buildBoundingBox(location.latitude(), location.longitude(), DEFAULT_RADIUS_KM);
        int limit = query.size() == null ? DEFAULT_PREVIEW_SCAN_LIMIT : query.size();

        List<Long> ids = houseMapper.selectSmartGuideCandidateIds(
                null,
                HOUSE_STATUS_AVAILABLE,
                rentType,
                totalCostScope,
                maxComparableCostCent,
                location.latitude(),
                location.longitude(),
                box.minLatitude(),
                box.maxLatitude(),
                box.minLongitude(),
                box.maxLongitude(),
                DEFAULT_RADIUS_KM,
                limit
        );

        List<House> houses = loadHousesByIdsInOrder(ids);
        return new SmartGuideCandidateBundle(location.name(), location.latitude(), location.longitude(), false, houses);
    }

    private List<House> loadHousesByIdsInOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, House> houseMap = new LinkedHashMap<>();
        for (House house : houseMapper.selectBatchIds(ids)) {
            if (house != null && house.getId() != null) {
                houseMap.put(house.getId(), house);
            }
        }
        return ids.stream().map(houseMap::get).filter(h -> h != null).toList();
    }

    private Integer resolveRentTypeCode(String rentMode) {
        if ("WHOLE".equalsIgnoreCase(rentMode)) {
            return 1;
        }
        if ("SHARED".equalsIgnoreCase(rentMode)) {
            return 2;
        }
        return null;
    }

    private BoundingBox buildBoundingBox(double latitude, double longitude, double radiusKm) {
        double latitudeDelta = radiusKm / 111.0d;
        double safeCos = Math.max(Math.cos(Math.toRadians(latitude)), 0.01d);
        double longitudeDelta = radiusKm / (111.0d * safeCos);
        return new BoundingBox(
                Math.max(latitude - latitudeDelta, -90.0d),
                Math.min(latitude + latitudeDelta, 90.0d),
                Math.max(longitude - longitudeDelta, -180.0d),
                Math.min(longitude + longitudeDelta, 180.0d)
        );
    }

    private record BoundingBox(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {
    }
}
```

- [ ] **Step 5: Make the existing smart guide service consume the collector instead of owning candidate retrieval**

```java
// inside SmartGuideRecommendationService.recommend(...)
SmartGuideCandidateBundle candidateBundle = candidateCollector.collect(SmartGuideCandidateQuery.builder()
        .locationName(queryContext.locationName())
        .budgetYuan(queryContext.budgetYuan())
        .budgetScope(queryContext.totalCostScope() ? "TOTAL" : "RENT_ONLY")
        .rentMode(queryContext.rentMode())
        .size(200)
        .build());

List<House> rankedCandidates = candidateBundle.candidates();
```

Use the extracted collector to replace the current inline candidate query logic. Keep the public `recommend(SmartGuideReqDTO reqDTO)` behavior unchanged.

- [ ] **Step 6: Run the collector test to verify it passes**

Run: `mvn "-Dtest=SmartGuideCandidateCollectorTest" test`  
Expected: PASS with preview queries supporting missing `budgetYuan` and missing `rentMode`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateQuery.java src/main/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateBundle.java src/main/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateCollector.java src/main/java/cn/yy/myrent/service/smartguide/SmartGuideRecommendationService.java src/test/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateCollectorTest.java
git commit -m "refactor(search): extract shared smart guide candidate collector"
```

---

### Task 3: Build the preview summary service on top of shared candidates

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/ai/AiPreviewService.java`
- Create: `src/main/java/cn/yy/myrent/service/ai/AiPreviewServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/vo/AiPreviewVO.java`
- Modify: `src/main/java/cn/yy/myrent/vo/AiPreviewGroupVO.java`
- Modify: `src/main/java/cn/yy/myrent/vo/AiPreviewSlotPatchVO.java`
- Create: `src/test/java/cn/yy/myrent/service/ai/AiPreviewServiceTest.java`

- [ ] **Step 1: Write the failing preview service tests**

```java
package cn.yy.myrent.service.ai;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.smartguide.SmartGuideCandidateBundle;
import cn.yy.myrent.service.smartguide.SmartGuideCandidateCollector;
import cn.yy.myrent.service.smartguide.SmartGuideCandidateQuery;
import cn.yy.myrent.vo.AiPreviewGroupVO;
import cn.yy.myrent.vo.AiPreviewVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPreviewServiceTest {

    @Mock
    private SmartGuideCandidateCollector candidateCollector;

    @InjectMocks
    private AiPreviewServiceImpl previewService;

    @Test
    void buildPreviewShouldCreateFactualDirectionGroupsFromRealCandidates() {
        when(candidateCollector.collect(any(SmartGuideCandidateQuery.class))).thenReturn(new SmartGuideCandidateBundle(
                "豫园",
                31.227,
                121.492,
                false,
                List.of(
                        new House().setId(101L).setTitle("近地铁整租").setPrice(420000).setTotalCost(520000).setRentType(1).setNearSubway(1).setHasBalcony(1),
                        new House().setId(102L).setTitle("预算友好合租").setPrice(260000).setTotalCost(300000).setRentType(2).setNearSubway(0).setPrivateBathroom(1),
                        new House().setId(103L).setTitle("学生免押合租").setPrice(240000).setTotalCost(240000).setRentType(2).setSupportStudentDepositFree(1)
                )
        ));

        AiPreviewVO preview = previewService.build("豫园", null, "RENT_ONLY", null);

        assertEquals("豫园", preview.getLocationName());
        assertTrue(preview.getGroups().size() >= 2);
        assertTrue(preview.getGroups().stream().map(AiPreviewGroupVO::getGroupKey).toList().contains("near_metro"));
        assertTrue(preview.getGroups().stream().map(AiPreviewGroupVO::getGroupKey).toList().contains("lower_total_cost"));
    }

    @Test
    void buildPreviewShouldOnlyUseSupportedPreviewClaims() {
        when(candidateCollector.collect(any(SmartGuideCandidateQuery.class))).thenReturn(new SmartGuideCandidateBundle(
                "豫园",
                31.227,
                121.492,
                false,
                List.of(
                        new House().setId(201L).setTitle("地铁口整租").setPrice(430000).setTotalCost(530000).setRentType(1).setNearSubway(1),
                        new House().setId(202L).setTitle("低总成本合租").setPrice(230000).setTotalCost(260000).setRentType(2)
                )
        ));

        AiPreviewVO preview = previewService.build("豫园", null, "RENT_ONLY", null);

        String mergedSummary = preview.getGroups().stream()
                .map(AiPreviewGroupVO::getSummary)
                .reduce("", (left, right) -> left + right);

        assertTrue(!mergedSummary.contains("安静"));
        assertTrue(!mergedSummary.contains("面积更大"));
        assertTrue(!mergedSummary.contains("采光"));
    }
}
```

- [ ] **Step 2: Run the preview service test to verify it fails**

Run: `mvn "-Dtest=AiPreviewServiceTest" test`  
Expected: FAIL because the preview service and group-building logic do not exist yet.

- [ ] **Step 3: Add the preview service interface and implementation skeleton**

```java
// src/main/java/cn/yy/myrent/service/ai/AiPreviewService.java
package cn.yy.myrent.service.ai;

import cn.yy.myrent.vo.AiPreviewVO;

public interface AiPreviewService {

    AiPreviewVO build(String locationName, Integer budgetYuan, String budgetScope, String rentMode);
}
```

```java
// src/main/java/cn/yy/myrent/service/ai/AiPreviewServiceImpl.java
package cn.yy.myrent.service.ai;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.smartguide.SmartGuideCandidateCollector;
import cn.yy.myrent.service.smartguide.SmartGuideCandidateQuery;
import cn.yy.myrent.vo.AiPreviewGroupVO;
import cn.yy.myrent.vo.AiPreviewSlotPatchVO;
import cn.yy.myrent.vo.AiPreviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiPreviewServiceImpl implements AiPreviewService {

    private final SmartGuideCandidateCollector candidateCollector;

    @Override
    public AiPreviewVO build(String locationName, Integer budgetYuan, String budgetScope, String rentMode) {
        var bundle = candidateCollector.collect(SmartGuideCandidateQuery.builder()
                .locationName(locationName)
                .budgetYuan(budgetYuan)
                .budgetScope(budgetScope)
                .rentMode(rentMode)
                .size(18)
                .build());

        AiPreviewVO preview = new AiPreviewVO();
        preview.setLocationName(bundle.locationName());
        preview.setCandidateCount(bundle.candidates().size());
        preview.setGroups(buildGroups(bundle.candidates(), budgetScope));
        return preview;
    }

    private List<AiPreviewGroupVO> buildGroups(List<House> houses, String budgetScope) {
        List<AiPreviewGroupVO> groups = new ArrayList<>();
        maybeAddNearMetroGroup(groups, houses);
        maybeAddLowerTotalCostGroup(groups, houses, budgetScope);
        maybeAddRentModeGroup(groups, houses);
        return groups.stream().limit(3).toList();
    }

    private void maybeAddNearMetroGroup(List<AiPreviewGroupVO> groups, List<House> houses) {
        List<House> nearMetro = houses.stream().filter(h -> truthy(h.getNearSubway())).toList();
        if (nearMetro.size() < 2) {
            return;
        }
        AiPreviewSlotPatchVO patch = new AiPreviewSlotPatchVO();
        patch.setPriority("COMMUTE");
        patch.setPreferences(List.of("nearSubway"));

        AiPreviewGroupVO group = new AiPreviewGroupVO();
        group.setGroupKey("near_metro");
        group.setTitle("更靠近地铁");
        group.setSummary("通勤更方便，但首月成本通常更高一些。");
        group.setHighlights(List.of("近地铁", "通勤更短"));
        group.setSampleCount(nearMetro.size());
        group.setSampleHouseIds(nearMetro.stream().limit(3).map(House::getId).toList());
        group.setSlotPatch(patch);
        groups.add(group);
    }

    private void maybeAddLowerTotalCostGroup(List<AiPreviewGroupVO> groups, List<House> houses, String budgetScope) {
        if (houses.size() < 2) {
            return;
        }
        List<House> sorted = houses.stream()
                .sorted(Comparator.comparingInt(this::comparableCost))
                .toList();
        List<House> cheaper = sorted.subList(0, Math.max(2, sorted.size() / 2));

        AiPreviewSlotPatchVO patch = new AiPreviewSlotPatchVO();
        patch.setPriority("PRICE");

        AiPreviewGroupVO group = new AiPreviewGroupVO();
        group.setGroupKey("lower_total_cost");
        group.setTitle("首月成本更低");
        group.setSummary("预算压力更小，但通勤和地铁优势可能没有那么明显。");
        group.setHighlights(List.of("首月成本低", "预算压力更小"));
        group.setSampleCount(cheaper.size());
        group.setSampleHouseIds(cheaper.stream().limit(3).map(House::getId).toList());
        group.setSlotPatch(patch);
        groups.add(group);
    }

    private void maybeAddRentModeGroup(List<AiPreviewGroupVO> groups, List<House> houses) {
        long wholeCount = houses.stream().filter(h -> Integer.valueOf(1).equals(h.getRentType())).count();
        long sharedCount = houses.stream().filter(h -> Integer.valueOf(2).equals(h.getRentType())).count();
        if (wholeCount >= 2) {
            groups.add(buildRentModeGroup("whole_rent", "整租为主", "整租选择更多，但预算通常更高。", "WHOLE", houses));
        } else if (sharedCount >= 2) {
            groups.add(buildRentModeGroup("shared_rent", "合租为主", "合租选择更多，通常对预算更友好。", "SHARED", houses));
        }
    }

    private AiPreviewGroupVO buildRentModeGroup(String key, String title, String summary, String rentMode, List<House> houses) {
        AiPreviewSlotPatchVO patch = new AiPreviewSlotPatchVO();
        patch.setRentMode(rentMode);

        AiPreviewGroupVO group = new AiPreviewGroupVO();
        group.setGroupKey(key);
        group.setTitle(title);
        group.setSummary(summary);
        group.setHighlights(List.of(rentMode.equals("WHOLE") ? "整租更多" : "合租更多"));
        group.setSampleCount((int) houses.stream().filter(h -> rentMode.equals("WHOLE") ? Integer.valueOf(1).equals(h.getRentType()) : Integer.valueOf(2).equals(h.getRentType())).count());
        group.setSampleHouseIds(houses.stream().filter(h -> rentMode.equals("WHOLE") ? Integer.valueOf(1).equals(h.getRentType()) : Integer.valueOf(2).equals(h.getRentType())).limit(3).map(House::getId).toList());
        group.setSlotPatch(patch);
        return group;
    }

    private int comparableCost(House house) {
        if (house.getTotalCost() != null) {
            return house.getTotalCost();
        }
        int price = house.getPrice() == null ? 0 : house.getPrice();
        int deposit = house.getDepositAmount() == null ? 0 : house.getDepositAmount();
        return price + deposit;
    }

    private boolean truthy(Integer value) {
        return value != null && value == 1;
    }
}
```

- [ ] **Step 4: Keep preview language factual and supported by current fields**

Only emit summaries from these dimensions in the implementation above:

- `nearSubway`
- `price`
- `depositAmount`
- `totalCost`
- `rentType`
- `hasBalcony`
- `privateBathroom`
- `supportStudentDepositFree`

Do not add strings such as `安静`, `面积更大`, `采光更好`, or `宠物友好` anywhere in `AiPreviewServiceImpl`.

- [ ] **Step 5: Run the preview service test to verify it passes**

Run: `mvn "-Dtest=AiPreviewServiceTest" test`  
Expected: PASS with preview groups grounded in current house fields only.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/ai/AiPreviewService.java src/main/java/cn/yy/myrent/service/ai/AiPreviewServiceImpl.java src/main/java/cn/yy/myrent/vo/AiPreviewVO.java src/main/java/cn/yy/myrent/vo/AiPreviewGroupVO.java src/main/java/cn/yy/myrent/vo/AiPreviewSlotPatchVO.java src/test/java/cn/yy/myrent/service/ai/AiPreviewServiceTest.java
git commit -m "feat(ai): add factual listing preview grouping service"
```

---

### Task 4: Add the backend stage machine and wire preview into the AI orchestration flow

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendSessionState.java`
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendStateStore.java`
- Modify: `src/main/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStore.java`
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java`
- Modify: `src/main/resources/prompts/ai-recommend/system.txt`
- Modify: `src/main/resources/prompts/ai-recommend/user-context.txt`
- Modify: `src/test/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStoreTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`

- [ ] **Step 1: Write the failing service tests for `PREVIEW`, `REFINE`, and preview selection transitions**

```java
@Test
void chatShouldEnterPreviewWhenLocationExistsButSearchIsNotReady() {
    AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
    when(stateStore.loadOrCreate(1001L)).thenReturn(session);
    when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
            .thenReturn(AiRecommendDecision.builder()
                    .reply("我先看看豫园附近真实房源。")
                    .slots(AiRecommendSlots.builder().city("上海").locationName("豫园").build())
                    .build());

    AiPreviewVO preview = new AiPreviewVO();
    preview.setLocationName("豫园");
    preview.setCandidateCount(12);
    preview.setGroups(List.of(new AiPreviewGroupVO()));
    when(previewService.build("豫园", null, "RENT_ONLY", null)).thenReturn(preview);

    AiRecommendChatVO result = aiRecommendService.chat(1001L, req("我想在豫园租房"));

    assertEquals("PREVIEW", result.getStage());
    assertEquals("豫园", result.getPreview().getLocationName());
}

@Test
void chatShouldTurnPreviewSelectionIntoSearchWhenHardSlotsBecomeReady() {
    AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
    session.setSlots(AiRecommendSlots.builder()
            .city("上海")
            .locationName("豫园")
            .budgetYuan(3500)
            .budgetScope("RENT_ONLY")
            .build());
    session.setStage("PREVIEW");
    when(stateStore.loadOrCreate(1001L)).thenReturn(session);
    when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
            .thenReturn(AiRecommendDecision.builder()
                    .reply("我按近地铁方向继续找。")
                    .slots(AiRecommendSlots.builder().build())
                    .build());
    when(houseService.smartGuide(any(SmartGuideReqDTO.class))).thenReturn(new SmartGuideResultVO());

    AiRecommendChatVO result = aiRecommendService.chat(1001L, interactionReq("near_metro", "先看近地铁的", "COMMUTE", "WHOLE", List.of("nearSubway")));

    assertEquals("SEARCH", result.getStage());
    assertNotNull(result.getRecommendation());
}

@Test
void previewSelectionShouldStayInRefineWhenRentModeIsStillMissing() {
    AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
    session.setSlots(AiRecommendSlots.builder()
            .city("上海")
            .locationName("豫园")
            .budgetYuan(3500)
            .budgetScope("RENT_ONLY")
            .build());
    session.setStage("PREVIEW");
    when(stateStore.loadOrCreate(1001L)).thenReturn(session);
    when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
            .thenReturn(AiRecommendDecision.builder()
                    .reply("我先按近地铁方向继续收窄。")
                    .slots(AiRecommendSlots.builder().build())
                    .build());

    AiPreviewVO preview = new AiPreviewVO();
    preview.setLocationName("豫园");
    preview.setCandidateCount(10);
    preview.setGroups(List.of(new AiPreviewGroupVO()));
    when(previewService.build("豫园", 3500, "RENT_ONLY", null)).thenReturn(preview);

    AiRecommendChatVO result = aiRecommendService.chat(1001L, interactionReq("near_metro", "先看近地铁的", "COMMUTE", null, List.of("nearSubway")));

    assertEquals("REFINE", result.getStage());
    assertNotNull(result.getPreview());
}
```

- [ ] **Step 2: Run the service tests to verify they fail**

Run: `mvn "-Dtest=AiRecommendServiceTest,RedisAiRecommendStateStoreTest" test`  
Expected: FAIL because session state does not persist stage, preview service is not wired in, and stage derivation still assumes only `ASK / SEARCH`.

- [ ] **Step 3: Persist the last backend stage in session state**

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

    private Long userId;

    private String sessionId;

    private String summary;

    private String stage;

    private AiRecommendSlots slots;

    @Builder.Default
    private List<AiRecommendTurn> history = new ArrayList<>();

    public static AiRecommendSessionState empty(Long userId) {
        return AiRecommendSessionState.builder()
                .userId(userId)
                .sessionId("ai-u" + userId)
                .summary("")
                .stage(AiRecommendStage.ASK.name())
                .slots(new AiRecommendSlots())
                .history(new ArrayList<>())
                .build();
    }
}
```

```java
// RedisAiRecommendStateStore save/load fragments
statePayload.put("stage", safeStage(state.getStage()));
...
resolved.setStage(readText(payload.get("stage"), AiRecommendStage.ASK.name()));
```

- [ ] **Step 4: Teach the model prompts about backend-owned preview and refinement**

```text
# src/main/resources/prompts/ai-recommend/system.txt
You are responsible for language understanding and response wording only.
Backend owns stage transitions and real execution truth.
You may receive turns that describe a user selecting one preview direction.
Never claim that you already searched or found listings unless backend preview or recommendation data is already present in context.
If backend preview context exists, summarize it naturally and help the user choose or confirm a direction.
There must be no fabricated listings, unsupported qualities, or completed backend actions.
```

```text
# src/main/resources/prompts/ai-recommend/user-context.txt
Current slots:
${slots}

Session summary:
${summary}

Recent history:
${recentHistory}

Preview digest:
${previewDigest}

Latest user message:
${userMessage}

Follow this output format exactly:
${format}
```

- [ ] **Step 5: Add deterministic stage derivation and preview interaction handling to `AiRecommendServiceImpl`**

```java
// constructor dependencies
private final AiPreviewService previewService;
```

```java
public AiRecommendChatVO chat(Long userId, AiRecommendChatReqDTO reqDTO) {
    AiRecommendSessionState state = normalizeState(stateStore.loadOrCreate(userId), userId);
    TurnInput input = normalizeInput(reqDTO);
    appendUser(state, input.transcriptMessage());

    AiRecommendDecision decision = decisionClient.decide(buildPromptState(state, null), input.promptMessage());
    AiRecommendSlots mergedSlots = applyInputPatch(mergeSlots(state.getSlots(), decision.getSlots()), input.slotPatch());
    state.setSlots(mergedSlots);

    List<String> missingSlots = buildMissingSlots(mergedSlots);
    AiRecommendStage stage = deriveStage(state.getStage(), input, mergedSlots, missingSlots);

    AiPreviewVO preview = null;
    SmartGuideResultVO recommendation = null;
    String assistantReply = decision.getReply();

    if (stage == AiRecommendStage.PREVIEW || stage == AiRecommendStage.REFINE) {
        preview = previewService.build(
                mergedSlots.getLocationName(),
                mergedSlots.getBudgetYuan(),
                mergedSlots.getBudgetScope(),
                mergedSlots.getRentMode()
        );
        if (preview == null || preview.getGroups().isEmpty()) {
            stage = AiRecommendStage.ASK;
            assistantReply = "我还没法形成可靠的预览，先告诉我更具体的区域或预算。";
        }
    }

    if (stage == AiRecommendStage.SEARCH) {
        recommendation = houseService.smartGuide(buildSmartGuideReq(mergedSlots));
    }

    assistantReply = finalizeReply(stage, assistantReply, preview, recommendation, missingSlots, mergedSlots);
    state.setStage(stage.name());
    state.setSummary(summaryBuilder.build(mergedSlots, missingSlots));
    appendAssistant(state, assistantReply);
    stateStore.save(state);
    return toChatVO(state, stage, assistantReply, missingSlots, preview, recommendation);
}
```

```java
private AiRecommendStage deriveStage(String lastStage,
                                     TurnInput input,
                                     AiRecommendSlots slots,
                                     List<String> missingSlots) {
    if (!hasResolvableLocation(slots.getLocationName())) {
        return AiRecommendStage.ASK;
    }
    if (missingSlots.isEmpty()) {
        return AiRecommendStage.SEARCH;
    }
    if (input.isPreviewSelection()) {
        return AiRecommendStage.REFINE;
    }
    if (AiRecommendStage.PREVIEW.name().equals(lastStage) || AiRecommendStage.REFINE.name().equals(lastStage)) {
        return AiRecommendStage.REFINE;
    }
    return AiRecommendStage.PREVIEW;
}
```

- [ ] **Step 6: Run the service and state-store tests to verify they pass**

Run: `mvn "-Dtest=AiRecommendServiceTest,RedisAiRecommendStateStoreTest" test`  
Expected: PASS with stage persistence and preview transitions covered.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/ai/AiRecommendSessionState.java src/main/java/cn/yy/myrent/service/ai/AiRecommendStateStore.java src/main/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStore.java src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java src/main/resources/prompts/ai-recommend/system.txt src/main/resources/prompts/ai-recommend/user-context.txt src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java src/test/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStoreTest.java
git commit -m "feat(ai): add guided preview stage machine"
```

---

### Task 5: Render preview directions on the AI page and post structured selection payloads

**Files:**
- Modify: `frontend/src/api/aiRecommend.js`
- Create: `frontend/src/components/ai/AiPreviewPanel.vue`
- Modify: `frontend/src/views/AiRecommendView.vue`
- Modify: `frontend/src/views/__tests__/AiRecommendView.spec.js`

- [ ] **Step 1: Write the failing frontend test for preview rendering and selection**

```javascript
import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import AiRecommendView from '@/views/AiRecommendView.vue'

const fetchAiRecommendSession = vi.fn()
const chatAiRecommend = vi.fn()
const resetAiRecommendSession = vi.fn()

vi.mock('@/api/aiRecommend', () => ({
  fetchAiRecommendSession: (...args) => fetchAiRecommendSession(...args),
  chatAiRecommend: (...args) => chatAiRecommend(...args),
  resetAiRecommendSession: (...args) => resetAiRecommendSession(...args)
}))

describe('AiRecommendView preview flow', () => {
  beforeEach(() => {
    fetchAiRecommendSession.mockReset()
    chatAiRecommend.mockReset()
    resetAiRecommendSession.mockReset()

    fetchAiRecommendSession.mockResolvedValue({
      sessionId: 'ai-u1001',
      stage: 'PREVIEW',
      assistantReply: '我先看了下豫园附近，大致有两类方向。',
      slots: {
        city: '上海',
        locationName: '豫园',
        budgetScope: 'RENT_ONLY'
      },
      missingSlots: ['budgetYuan', 'rentMode'],
      preview: {
        locationName: '豫园',
        candidateCount: 18,
        groups: [
          {
            groupKey: 'near_metro',
            title: '更靠近地铁',
            summary: '通勤更方便，但首月成本通常更高一些。',
            highlights: ['近地铁', '通勤更短'],
            sampleCount: 6,
            slotPatch: {
              priority: 'COMMUTE',
              preferences: ['nearSubway']
            }
          }
        ]
      }
    })

    chatAiRecommend.mockResolvedValue({
      sessionId: 'ai-u1001',
      stage: 'REFINE',
      assistantReply: '我先按近地铁方向继续收窄。',
      slots: {
        city: '上海',
        locationName: '豫园',
        budgetScope: 'RENT_ONLY',
        priority: 'COMMUTE',
        preferences: ['nearSubway']
      },
      missingSlots: ['budgetYuan', 'rentMode'],
      preview: {
        locationName: '豫园',
        candidateCount: 12,
        groups: []
      }
    })
  })

  async function mountView() {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/ai-recommend', component: AiRecommendView }]
    })
    router.push('/ai-recommend')
    await router.isReady()
    const wrapper = mount(AiRecommendView, {
      global: { plugins: [router] }
    })
    await flushPromises()
    return wrapper
  }

  it('renders preview groups and posts a structured interaction payload when one is selected', async () => {
    const wrapper = await mountView()

    expect(wrapper.text()).toContain('更靠近地铁')
    expect(wrapper.text()).not.toContain('真实房源推荐')

    await wrapper.get('[data-test=\"preview-select-near_metro\"]').trigger('click')
    await flushPromises()

    expect(chatAiRecommend).toHaveBeenCalledWith({
      interaction: {
        type: 'PREVIEW_SELECTION',
        groupKey: 'near_metro',
        label: '更靠近地铁',
        slotPatch: {
          priority: 'COMMUTE',
          preferences: ['nearSubway']
        }
      }
    })
  })
})
```

- [ ] **Step 2: Run the frontend test to verify it fails**

Run: `npm --prefix frontend run test:run -- src/views/__tests__/AiRecommendView.spec.js`  
Expected: FAIL because the view does not yet render preview groups or send structured interaction payloads.

- [ ] **Step 3: Create the preview panel component**

```vue
<!-- frontend/src/components/ai/AiPreviewPanel.vue -->
<template>
  <section class="preview-panel">
    <header class="preview-head">
      <div>
        <h3>先挑一个方向继续找</h3>
        <p v-if="preview?.locationName" class="preview-copy">
          我先看了下 {{ preview.locationName }} 附近的真实房源，当前大致有这些方向。
        </p>
      </div>
      <span v-if="preview?.candidateCount" class="preview-count">
        {{ preview.candidateCount }} 套候选
      </span>
    </header>

    <div class="preview-list">
      <article
        v-for="group in groups"
        :key="group.groupKey"
        class="preview-card"
      >
        <div class="preview-card__head">
          <h4>{{ group.title }}</h4>
          <span v-if="group.sampleCount" class="preview-card__meta">{{ group.sampleCount }} 套</span>
        </div>
        <p class="preview-card__summary">{{ group.summary }}</p>
        <div v-if="group.highlights?.length" class="preview-card__tags">
          <span v-for="item in group.highlights" :key="item" class="preview-card__tag">{{ item }}</span>
        </div>
        <button
          class="primary-btn preview-card__cta"
          type="button"
          :data-test="`preview-select-${group.groupKey}`"
          @click="$emit('select-group', group)"
        >
          先看这类
        </button>
      </article>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  preview: {
    type: Object,
    default: null
  }
})

defineEmits(['select-group'])

const groups = computed(() => props.preview?.groups || [])
</script>
```

- [ ] **Step 4: Wire stage-driven rendering and selection posting into the AI view**

```javascript
// frontend/src/api/aiRecommend.js
import http from './http'

export function fetchAiRecommendSession() {
  return http.get('/ai-recommend/session')
}

export function chatAiRecommend(payload) {
  return http.post('/ai-recommend/chat', payload)
}

export function resetAiRecommendSession() {
  return http.post('/ai-recommend/reset')
}
```

```vue
<!-- frontend/src/views/AiRecommendView.vue -->
<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { chatAiRecommend, fetchAiRecommendSession, resetAiRecommendSession } from '@/api/aiRecommend'
import AiChatBubble from '@/components/ai/AiChatBubble.vue'
import AiPreviewPanel from '@/components/ai/AiPreviewPanel.vue'
import AiQuickPromptChips from '@/components/ai/AiQuickPromptChips.vue'
import AiRecommendationPanel from '@/components/ai/AiRecommendationPanel.vue'
import AiRequirementSummary from '@/components/ai/AiRequirementSummary.vue'
import DogAssistantIcon from '@/components/icons/DogAssistantIcon.vue'

const router = useRouter()
const transcript = ref([])
const slots = ref({})
const missingSlots = ref([])
const preview = ref(null)
const recommendation = ref(null)
const stage = ref('ASK')
const draft = ref('')
const loading = ref(false)
const errorText = ref('')

async function sendPreviewSelection(group) {
  if (!group || loading.value) {
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const result = await chatAiRecommend({
      interaction: {
        type: 'PREVIEW_SELECTION',
        groupKey: group.groupKey,
        label: group.title,
        slotPatch: group.slotPatch || {}
      }
    })
    applyResponse(result)
  } catch (error) {
    errorText.value = error?.message || '选择方向失败，请稍后再试'
  } finally {
    loading.value = false
  }
}

function applyResponse(payload, options = {}) {
  stage.value = payload?.stage || 'ASK'
  slots.value = payload?.slots || {}
  missingSlots.value = payload?.missingSlots || []
  preview.value = payload?.preview || null
  recommendation.value = payload?.recommendation || null
  const assistantMessage = payload?.assistantReply
    ? { role: 'assistant', text: payload.assistantReply, stage: stage.value }
    : null
  if (options.reset) {
    transcript.value = assistantMessage ? [assistantMessage] : []
    return
  }
  if (assistantMessage) {
    transcript.value.push(assistantMessage)
  }
}
</script>

<template>
  <div class="page ai-page">
    <!-- existing hero and transcript markup stays -->
    <AiPreviewPanel
      v-if="stage === 'PREVIEW' || stage === 'REFINE'"
      :preview="preview"
      @select-group="sendPreviewSelection"
    />
    <AiRecommendationPanel
      v-if="stage === 'SEARCH' && recommendation"
      :recommendation="recommendation"
      @open-house="openHouse"
    />
  </div>
</template>
```

- [ ] **Step 5: Run the frontend test to verify it passes**

Run: `npm --prefix frontend run test:run -- src/views/__tests__/AiRecommendView.spec.js`  
Expected: PASS with preview rendering and selection payload covered.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/aiRecommend.js frontend/src/components/ai/AiPreviewPanel.vue frontend/src/views/AiRecommendView.vue frontend/src/views/__tests__/AiRecommendView.spec.js
git commit -m "feat(frontend): render ai preview directions and selection flow"
```

---

### Task 6: Run focused regression coverage for the full guided preview flow

**Files:**
- Modify: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`
- Modify: `src/test/java/cn/yy/myrent/controller/AiRecommendControllerWebMvcTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/ai/AiPreviewServiceTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateCollectorTest.java`
- Modify: `frontend/src/views/__tests__/AiRecommendView.spec.js`

- [ ] **Step 1: Add final backend regression cases for preview fallback and final search handoff**

```java
@Test
void previewShouldFallBackToAskWhenCollectorReturnsNoUsableGroups() {
    AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
    when(stateStore.loadOrCreate(1001L)).thenReturn(session);
    when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
            .thenReturn(AiRecommendDecision.builder()
                    .reply("我先看看豫园附近。")
                    .slots(AiRecommendSlots.builder().locationName("豫园").build())
                    .build());

    AiPreviewVO preview = new AiPreviewVO();
    preview.setLocationName("豫园");
    preview.setCandidateCount(0);
    preview.setGroups(List.of());
    when(previewService.build("豫园", null, "RENT_ONLY", null)).thenReturn(preview);

    AiRecommendChatVO result = aiRecommendService.chat(1001L, req("豫园租房"));

    assertEquals("ASK", result.getStage());
    assertNull(result.getPreview());
}

@Test
void finalSearchShouldStillComeFromSmartGuide() {
    AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
    when(stateStore.loadOrCreate(1001L)).thenReturn(session);
    when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
            .thenReturn(AiRecommendDecision.builder()
                    .reply("我按你的条件正式找房。")
                    .slots(AiRecommendSlots.builder()
                            .locationName("豫园")
                            .budgetYuan(3500)
                            .budgetScope("RENT_ONLY")
                            .rentMode("WHOLE")
                            .build())
                    .build());
    when(houseService.smartGuide(any(SmartGuideReqDTO.class))).thenReturn(new SmartGuideResultVO());

    AiRecommendChatVO result = aiRecommendService.chat(1001L, req("预算3500，豫园整租"));

    assertEquals("SEARCH", result.getStage());
    verify(houseService).smartGuide(any(SmartGuideReqDTO.class));
}
```

- [ ] **Step 2: Run the focused backend suite**

Run: `mvn "-Dtest=AiRecommendControllerWebMvcTest,AiRecommendServiceTest,AiPreviewServiceTest,SmartGuideCandidateCollectorTest,RedisAiRecommendStateStoreTest" test`  
Expected: PASS with controller, preview, state machine, and candidate collector coverage all green.

- [ ] **Step 3: Run the focused frontend suite**

Run: `npm --prefix frontend run test:run -- src/views/__tests__/AiRecommendView.spec.js`  
Expected: PASS with preview rendering and structured selection still green after backend response changes.

- [ ] **Step 4: Run the combined regression commands before closing the feature**

Run: `mvn "-Dtest=AiRecommendControllerWebMvcTest,AiRecommendServiceTest,AiPreviewServiceTest,SmartGuideCandidateCollectorTest,RedisAiRecommendStateStoreTest" test && npm --prefix frontend run test:run -- src/views/__tests__/AiRecommendView.spec.js`  
Expected: PASS for both backend and frontend focused suites.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/cn/yy/myrent/controller/AiRecommendControllerWebMvcTest.java src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java src/test/java/cn/yy/myrent/service/ai/AiPreviewServiceTest.java src/test/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateCollectorTest.java src/test/java/cn/yy/myrent/service/ai/RedisAiRecommendStateStoreTest.java frontend/src/views/__tests__/AiRecommendView.spec.js
git commit -m "test(ai): cover guided preview recommendation flow"
```

---

## Spec Coverage Check

- State model `ASK / PREVIEW / REFINE / SEARCH`: covered by Task 1 and Task 4.
- Preview based on real candidate listings: covered by Task 2 and Task 3.
- Reuse of current `smartGuide` foundation: covered by Task 2 and Task 4.
- No second independent search system: enforced by Task 2 extraction and Task 4 final-search handoff.
- Structured preview selection input: covered by Task 1 and Task 5.
- Stage-shaped response with optional `preview` and `recommendation`: covered by Task 1 and Task 4.
- Frontend preview middle state: covered by Task 5.
- Factual preview language only from supported fields: covered by Task 3 and Task 6.

No approved spec requirement is left without a corresponding task.

## Placeholder Scan

- No unresolved placeholder markers remain.
- Each task contains concrete files, concrete test cases, concrete commands, and concrete code snippets.
- No step says “write tests for the above” without showing the test code.

## Type Consistency Check

- The request contract consistently uses `AiRecommendInteractionDTO` and `AiRecommendInteractionSlotPatchDTO`.
- The response contract consistently uses `stage`, `preview`, and `recommendation`.
- The backend stage enum is consistently `AiRecommendStage`.
- Preview group patches are consistently represented as `AiPreviewSlotPatchVO` on the response side and `AiRecommendInteractionSlotPatchDTO` on the request side.
- Shared retrieval consistently uses `SmartGuideCandidateQuery`, `SmartGuideCandidateBundle`, and `SmartGuideCandidateCollector`.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-29-ai-guided-preview-implementation.md`.

Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
