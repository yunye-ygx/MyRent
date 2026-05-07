# House Discovery Three-Layer Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor house search, list-filter, smart-guide, and AI recommend so they share reusable recall, ranking, and LLM-support layers while keeping existing public routes and top-level response contracts compatible.

**Architecture:** Introduce a new internal `service.discovery` package that owns candidate recall contracts and ranking contracts. Route-specific services remain as adapters: keyword search and list-filter call the shared recall layer plus `SEARCH_DEFAULT`, while smart-guide and AI recommend call the shared recall layer plus `AI_RECOMMEND_DEFAULT`; AI recommend additionally converts ranked results into a grounded LLM payload before generating reply text.

**Tech Stack:** Spring Boot, Spring Data Elasticsearch, MyBatis-Plus, Spring AI, JUnit 5, Mockito, Maven

---

## File Map

**Create:**
- `src/main/java/cn/yy/myrent/service/discovery/HouseRecallProfile.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRecallMatchTier.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRecallQuery.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRecallEvidence.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRecallCandidate.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRecallResult.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRecallService.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRecallServiceImpl.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRankingProfile.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseReasonCode.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseScoreBreakdown.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRankQuery.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRankedItem.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRankResult.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRankingService.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRankingServiceImpl.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendRankingPayload.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendRankingPayloadBuilder.java`
- `src/test/java/cn/yy/myrent/service/discovery/HouseRecallServiceTest.java`
- `src/test/java/cn/yy/myrent/service/discovery/HouseRankingServiceTest.java`
- `src/test/java/cn/yy/myrent/service/ai/AiRecommendRankingPayloadBuilderTest.java`
- `src/test/java/cn/yy/myrent/service/smartguide/SmartGuideRecommendationServiceTest.java`

**Modify:**
- `src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java`
- `src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java`
- `src/main/java/cn/yy/myrent/service/smartguide/SmartGuideRecommendationService.java`
- `src/main/java/cn/yy/myrent/service/score/SmartGuideScoreCalculator.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java`
- `src/main/java/cn/yy/myrent/vo/SmartGuideItemVO.java`
- `src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java`
- `src/test/java/cn/yy/myrent/service/impl/HouseServiceImplListFilterTest.java`
- `src/test/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateCollectorTest.java`
- `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`
- `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`

**Keep unchanged externally:**
- `src/main/java/cn/yy/myrent/controller/HouseController.java`
- `src/main/java/cn/yy/myrent/controller/AiRecommendController.java`
- `src/main/java/cn/yy/myrent/vo/HouseSearchResultVO.java`
- `src/main/java/cn/yy/myrent/vo/SmartGuideResultVO.java`
- `src/main/java/cn/yy/myrent/vo/AiRecommendChatVO.java`

---

### Task 1: Introduce Shared Recall Contracts and Test the New Recall Vocabulary

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseRecallProfile.java`
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseRecallMatchTier.java`
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseRecallQuery.java`
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseRecallEvidence.java`
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseRecallCandidate.java`
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseRecallResult.java`
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseRecallService.java`
- Test: `src/test/java/cn/yy/myrent/service/discovery/HouseRecallServiceTest.java`

- [ ] **Step 1: Write the failing recall contract tests**

```java
package cn.yy.myrent.service.discovery;

import cn.yy.myrent.entity.House;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseRecallServiceTest {

    @Test
    void recallEvidenceShouldRetainKeywordLocationRelaxationAndTagSignals() {
        House house = new House();
        house.setId(101L);

        HouseRecallEvidence evidence = HouseRecallEvidence.builder()
                .locationMatched(true)
                .textMatched(true)
                .locationDistanceMeters(620.0d)
                .locationRank(2)
                .textRank(5)
                .textScore(8.4f)
                .exactConstraintMatched(true)
                .relaxedBudgetApplied(false)
                .relaxedRadiusApplied(true)
                .nearSubwayMatched(true)
                .privateBathroomMatched(true)
                .hasBalconyMatched(false)
                .civilWaterElectricMatched(true)
                .supportStudentDepositFreeMatched(false)
                .build();

        HouseRecallCandidate candidate = new HouseRecallCandidate(
                house,
                HouseRecallMatchTier.RELAXED_RADIUS,
                evidence
        );

        HouseRecallResult result = new HouseRecallResult(List.of(candidate), true, false);

        assertEquals(1, result.candidates().size());
        assertEquals(HouseRecallMatchTier.RELAXED_RADIUS, result.candidates().get(0).matchTier());
        assertTrue(result.candidates().get(0).recallEvidence().locationMatched());
        assertTrue(result.candidates().get(0).recallEvidence().textMatched());
        assertTrue(result.candidates().get(0).recallEvidence().privateBathroomMatched());
        assertTrue(result.candidates().get(0).recallEvidence().relaxedRadiusApplied());
    }

    @Test
    void recallQueryShouldSupportKeywordFilterAndRecommendationInputs() {
        HouseRecallQuery query = HouseRecallQuery.builder()
                .keyword("豫园整租")
                .locationName("豫园")
                .city("上海")
                .region("黄浦")
                .budgetYuan(3500)
                .budgetScope("RENT_ONLY")
                .rentMode("WHOLE")
                .nearSubway(true)
                .privateBathroom(true)
                .page(1)
                .size(10)
                .recallProfile(HouseRecallProfile.AI_RECOMMEND)
                .build();

        assertEquals("豫园整租", query.keyword());
        assertEquals("豫园", query.locationName());
        assertEquals(HouseRecallProfile.AI_RECOMMEND, query.recallProfile());
        assertTrue(query.nearSubway());
        assertTrue(query.privateBathroom());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn "-Dtest=HouseRecallServiceTest" test`  
Expected: FAIL because the `service.discovery` recall contract classes do not exist yet.

- [ ] **Step 3: Create the shared recall contract types**

```java
package cn.yy.myrent.service.discovery;

public enum HouseRecallProfile {
    KEYWORD_SEARCH,
    LIST_FILTER,
    SMART_GUIDE,
    AI_RECOMMEND
}
```

```java
package cn.yy.myrent.service.discovery;

public enum HouseRecallMatchTier {
    EXACT,
    RELAXED_BUDGET,
    RELAXED_RADIUS,
    RELAXED_BUDGET_AND_RADIUS,
    TEXT_ONLY,
    LOCATION_ONLY,
    FILTER_ONLY
}
```

```java
package cn.yy.myrent.service.discovery;

import lombok.Builder;

@Builder
public record HouseRecallQuery(
        String keyword,
        String locationName,
        String city,
        String region,
        Integer budgetYuan,
        String budgetScope,
        String rentMode,
        Integer rentType,
        Boolean nearSubway,
        Boolean privateBathroom,
        Boolean hasBalcony,
        Boolean civilWaterElectric,
        Boolean supportStudentDepositFree,
        Integer page,
        Integer size,
        HouseRecallProfile recallProfile
) {
}
```

```java
package cn.yy.myrent.service.discovery;

import lombok.Builder;

@Builder
public record HouseRecallEvidence(
        boolean locationMatched,
        boolean textMatched,
        Double locationDistanceMeters,
        Integer locationRank,
        Integer textRank,
        Float textScore,
        boolean exactConstraintMatched,
        boolean relaxedBudgetApplied,
        boolean relaxedRadiusApplied,
        boolean nearSubwayMatched,
        boolean privateBathroomMatched,
        boolean hasBalconyMatched,
        boolean civilWaterElectricMatched,
        boolean supportStudentDepositFreeMatched
) {
}
```

```java
package cn.yy.myrent.service.discovery;

import cn.yy.myrent.entity.House;

public record HouseRecallCandidate(
        House house,
        HouseRecallMatchTier matchTier,
        HouseRecallEvidence recallEvidence
) {
}
```

```java
package cn.yy.myrent.service.discovery;

import java.util.List;

public record HouseRecallResult(
        List<HouseRecallCandidate> candidates,
        boolean esAvailable,
        boolean degraded
) {
}
```

```java
package cn.yy.myrent.service.discovery;

public interface HouseRecallService {

    HouseRecallResult recall(HouseRecallQuery query);
}
```

- [ ] **Step 4: Run test to verify the recall contracts compile and pass**

Run: `mvn "-Dtest=HouseRecallServiceTest" test`  
Expected: PASS with 2 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/discovery src/test/java/cn/yy/myrent/service/discovery/HouseRecallServiceTest.java
git commit -m "feat: add shared house recall contracts"
```

### Task 2: Implement the Shared Recall Service and Migrate Keyword Search plus List Filter to It

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseRecallServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java`
- Modify: `src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java`
- Modify: `src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/impl/HouseServiceImplListFilterTest.java`

- [ ] **Step 1: Write the failing shared-recall integration tests**

```java
@Test
void searchShouldDelegateCandidateCollectionToSharedRecallService() {
    HouseRecallCandidate locationAndTextCandidate = new HouseRecallCandidate(
            availableHouse(12L, "天河公园精装单间", 320000, 320000, 1),
            HouseRecallMatchTier.EXACT,
            HouseRecallEvidence.builder()
                    .locationMatched(true)
                    .textMatched(true)
                    .locationDistanceMeters(260.0d)
                    .locationRank(1)
                    .textRank(0)
                    .textScore(2.1f)
                    .exactConstraintMatched(true)
                    .build()
    );

    when(houseRecallService.recall(any(HouseRecallQuery.class)))
            .thenReturn(new HouseRecallResult(List.of(locationAndTextCandidate), true, false));

    HouseSearchResultVO result = houseKeywordSearchService.search(keywordReq("天河公园单间", 1, 2));

    assertEquals(1, result.getHouses().size());
    assertEquals(12L, result.getHouses().get(0).getId());
    verify(houseRecallService).recall(any(HouseRecallQuery.class));
}

@Test
void filterListShouldDelegateStructuredCandidateCollectionToSharedRecallService() {
    HouseRecallCandidate filterCandidate = new HouseRecallCandidate(
            availableHouse(2L, "姑苏区品质一居", 320000, 100000, 1),
            HouseRecallMatchTier.FILTER_ONLY,
            HouseRecallEvidence.builder()
                    .exactConstraintMatched(true)
                    .nearSubwayMatched(true)
                    .privateBathroomMatched(true)
                    .hasBalconyMatched(true)
                    .civilWaterElectricMatched(true)
                    .supportStudentDepositFreeMatched(true)
                    .build()
    );

    when(houseRecallService.recall(any(HouseRecallQuery.class)))
            .thenReturn(new HouseRecallResult(List.of(filterCandidate), true, false));

    HouseSearchResultVO result = houseService.filterList(filterReq());

    assertEquals(1, result.getHouses().size());
    assertEquals("ES_FILTER", result.getFallbackSource());
    verify(houseRecallService).recall(any(HouseRecallQuery.class));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn "-Dtest=HouseKeywordSearchServiceTest,HouseServiceImplListFilterTest" test`  
Expected: FAIL because `HouseKeywordSearchService` and `HouseServiceImpl.filterList(...)` do not yet depend on `HouseRecallService`.

- [ ] **Step 3: Implement `HouseRecallServiceImpl` by extracting existing keyword-search, list-filter, and smart-guide candidate collection logic**

```java
package cn.yy.myrent.service.discovery;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.location.LocationResolveService;
import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HouseRecallServiceImpl implements HouseRecallService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final HouseMapper houseMapper;
    private final LocationResolveService locationResolveService;

    @Override
    public HouseRecallResult recall(HouseRecallQuery query) {
        return switch (query.recallProfile()) {
            case KEYWORD_SEARCH -> recallKeyword(query);
            case LIST_FILTER -> recallListFilter(query);
            case SMART_GUIDE, AI_RECOMMEND -> recallRecommendation(query);
        };
    }

    private HouseRecallResult recallKeyword(HouseRecallQuery query) {
        Map<Long, HouseRecallEvidence> mergedEvidence = mergeKeywordEvidence(query);
        List<HouseRecallCandidate> candidates = loadCandidatesInMergedOrder(mergedEvidence);
        return new HouseRecallResult(candidates, true, false);
    }

    private HouseRecallResult recallListFilter(HouseRecallQuery query) {
        List<House> houses = selectStructuredCandidates(query);
        List<HouseRecallCandidate> candidates = houses.stream()
                .map(house -> new HouseRecallCandidate(
                        house,
                        HouseRecallMatchTier.FILTER_ONLY,
                        HouseRecallEvidence.builder()
                                .exactConstraintMatched(true)
                                .nearSubwayMatched(Boolean.TRUE.equals(query.nearSubway()) && house.getNearSubway() != null && house.getNearSubway() == 1)
                                .privateBathroomMatched(Boolean.TRUE.equals(query.privateBathroom()) && house.getPrivateBathroom() != null && house.getPrivateBathroom() == 1)
                                .hasBalconyMatched(Boolean.TRUE.equals(query.hasBalcony()) && house.getHasBalcony() != null && house.getHasBalcony() == 1)
                                .civilWaterElectricMatched(Boolean.TRUE.equals(query.civilWaterElectric()) && house.getCivilWaterElectric() != null && house.getCivilWaterElectric() == 1)
                                .supportStudentDepositFreeMatched(Boolean.TRUE.equals(query.supportStudentDepositFree()) && house.getSupportStudentDepositFree() != null && house.getSupportStudentDepositFree() == 1)
                                .build()
                ))
                .toList();
        return new HouseRecallResult(candidates, true, false);
    }
}
```

- [ ] **Step 4: Wire `HouseKeywordSearchService` and `HouseServiceImpl.filterList(...)` to the shared recall service**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class HouseKeywordSearchService {

    private final HouseRecallService houseRecallService;
    private final HouseRankingService houseRankingService;
    private final IUserService userService;

    public HouseSearchResultVO search(HouseKeywordSearchReqDTO reqDTO) {
        HouseRecallResult recallResult = houseRecallService.recall(HouseRecallQuery.builder()
                .keyword(reqDTO.getKeyword().trim())
                .page(reqDTO.getPage())
                .size(reqDTO.getSize())
                .recallProfile(HouseRecallProfile.KEYWORD_SEARCH)
                .build());

        HouseRankResult rankResult = houseRankingService.rank(
                HouseRankQuery.builder()
                        .rankingProfile(HouseRankingProfile.SEARCH_DEFAULT)
                        .page(reqDTO.getPage())
                        .size(reqDTO.getSize())
                        .build(),
                recallResult
        );

        HouseSearchResultVO result = new HouseSearchResultVO();
        result.setTotal((long) rankResult.items().size());
        result.setHouses(enrichPublisherNames(toHouseVos(rankResult.pageSlice())));
        result.setEsDown(recallResult.degraded());
        result.setFallbackSource(recallResult.degraded() ? "KEYWORD_SEARCH_DEGRADED" : "KEYWORD_SEARCH");
        result.setTipMessage(rankResult.pageSlice().isEmpty() ? "当前未找到匹配房源" : null);
        return result;
    }
}
```

```java
@Override
public HouseSearchResultVO filterList(HouseListFilterReqDTO reqDTO) {
    int page = reqDTO == null || reqDTO.getPage() == null ? 1 : reqDTO.getPage();
    int size = reqDTO == null || reqDTO.getSize() == null ? 8 : reqDTO.getSize();

    HouseRecallResult recallResult = houseRecallService.recall(HouseRecallQuery.builder()
            .city(reqDTO == null ? null : reqDTO.getCity())
            .region(reqDTO == null ? null : reqDTO.getRegion())
            .rentType(reqDTO == null ? null : reqDTO.getRentType())
            .nearSubway(reqDTO == null ? null : reqDTO.getNearSubway())
            .privateBathroom(reqDTO == null ? null : reqDTO.getPrivateBathroom())
            .hasBalcony(reqDTO == null ? null : reqDTO.getHasBalcony())
            .civilWaterElectric(reqDTO == null ? null : reqDTO.getCivilWaterElectric())
            .supportStudentDepositFree(reqDTO == null ? null : reqDTO.getSupportStudentDepositFree())
            .page(page)
            .size(size)
            .recallProfile(HouseRecallProfile.LIST_FILTER)
            .build());

    HouseRankResult rankResult = houseRankingService.rank(
            HouseRankQuery.builder()
                    .page(page)
                    .size(size)
                    .rankingProfile(HouseRankingProfile.SEARCH_DEFAULT)
                    .nearSubway(reqDTO == null ? null : reqDTO.getNearSubway())
                    .privateBathroom(reqDTO == null ? null : reqDTO.getPrivateBathroom())
                    .hasBalcony(reqDTO == null ? null : reqDTO.getHasBalcony())
                    .civilWaterElectric(reqDTO == null ? null : reqDTO.getCivilWaterElectric())
                    .supportStudentDepositFree(reqDTO == null ? null : reqDTO.getSupportStudentDepositFree())
                    .build(),
            recallResult
    );

    return buildSearchResult(
            toHouseVos(rankResult.pageSlice()),
            (long) rankResult.items().size(),
            recallResult.degraded(),
            recallResult.degraded() ? "DB_FILTER" : "ES_FILTER",
            null
    );
}
```

- [ ] **Step 5: Run tests and commit**

Run: `mvn "-Dtest=HouseKeywordSearchServiceTest,HouseServiceImplListFilterTest" test`  
Expected: PASS with the keyword-search and list-filter tests green against the shared recall layer.

```bash
git add src/main/java/cn/yy/myrent/service/discovery/HouseRecallServiceImpl.java src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java src/test/java/cn/yy/myrent/service/impl/HouseServiceImplListFilterTest.java
git commit -m "refactor: share house recall across search and filters"
```

### Task 3: Add the Shared Ranking Layer with Search and AI Recommendation Profiles

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseRankingProfile.java`
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseReasonCode.java`
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseScoreBreakdown.java`
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseRankQuery.java`
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseRankedItem.java`
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseRankResult.java`
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseRankingService.java`
- Create: `src/main/java/cn/yy/myrent/service/discovery/HouseRankingServiceImpl.java`
- Test: `src/test/java/cn/yy/myrent/service/discovery/HouseRankingServiceTest.java`

- [ ] **Step 1: Write the failing ranking-profile tests**

```java
package cn.yy.myrent.service.discovery;

import cn.yy.myrent.entity.House;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HouseRankingServiceTest {

    private final HouseRankingService houseRankingService = new HouseRankingServiceImpl();

    @Test
    void aiRecommendProfileShouldPreferBudgetCloserCandidateOverSlightlyNearerOne() {
        House nearButExpensive = house(1L, 360000, 1, 1, 1, 0, 1);
        House cheaperButFarther = house(2L, 340000, 1, 1, 0, 1, 1);

        HouseRecallResult recallResult = new HouseRecallResult(List.of(
                candidate(nearButExpensive, 200.0d, true, true, true, false, true),
                candidate(cheaperButFarther, 700.0d, true, true, true, true, true)
        ), true, false);

        HouseRankResult result = houseRankingService.rank(
                HouseRankQuery.builder()
                        .budgetYuan(3500)
                        .rentMode("WHOLE")
                        .nearSubway(true)
                        .privateBathroom(true)
                        .rankingProfile(HouseRankingProfile.AI_RECOMMEND_DEFAULT)
                        .page(1)
                        .size(10)
                        .build(),
                recallResult
        );

        assertEquals(2L, result.items().get(0).house().getId());
        assertEquals(1L, result.items().get(1).house().getId());
    }

    @Test
    void searchProfileShouldKeepBalancedOrderingAndExposeReasonCodes() {
        House balanced = house(3L, 350000, 1, 1, 1, 1, 1);
        House freshButPartial = house(4L, 355000, 1, 0, 1, 0, 1);

        HouseRankResult result = houseRankingService.rank(
                HouseRankQuery.builder()
                        .budgetYuan(3500)
                        .rentMode("WHOLE")
                        .nearSubway(true)
                        .privateBathroom(true)
                        .rankingProfile(HouseRankingProfile.SEARCH_DEFAULT)
                        .page(1)
                        .size(10)
                        .build(),
                new HouseRecallResult(List.of(
                        candidate(balanced, 500.0d, true, true, true, true, true),
                        candidate(freshButPartial, 200.0d, true, true, true, false, true)
                ), true, false)
        );

        assertEquals(3L, result.items().get(0).house().getId());
        assertEquals(HouseReasonCode.BUDGET_CLOSE, result.items().get(0).reasonCodes().get(0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn "-Dtest=HouseRankingServiceTest" test`  
Expected: FAIL because ranking classes and profile-aware ranking behavior do not exist yet.

- [ ] **Step 3: Implement the ranking contracts and the profile-aware service**

```java
package cn.yy.myrent.service.discovery;

public enum HouseRankingProfile {
    SEARCH_DEFAULT,
    AI_RECOMMEND_DEFAULT
}
```

```java
package cn.yy.myrent.service.discovery;

public enum HouseReasonCode {
    TEXT_MATCHED,
    LOCATION_MATCHED,
    TEXT_AND_LOCATION_MATCHED,
    BUDGET_CLOSE,
    BUDGET_WITHIN_RANGE,
    RENT_MODE_MATCH,
    NEAR_TARGET_LOCATION,
    NEAR_SUBWAY_MATCH,
    PRIVATE_BATHROOM_MATCH,
    HAS_BALCONY_MATCH,
    CIVIL_WATER_ELECTRIC_MATCH,
    STUDENT_DEPOSIT_FREE_MATCH,
    RELAXED_BUDGET_CANDIDATE,
    RELAXED_RADIUS_CANDIDATE,
    RECENT_LISTING
}
```

```java
package cn.yy.myrent.service.discovery;

import lombok.Builder;

@Builder
public record HouseScoreBreakdown(
        double recallScore,
        double textRelevanceScore,
        double locationDistanceScore,
        double budgetCloseScore,
        double rentModeMatchScore,
        double nearSubwayScore,
        double privateBathroomScore,
        double hasBalconyScore,
        double civilWaterElectricScore,
        double supportStudentDepositFreeScore,
        double relaxationPenaltyOrAdjustment,
        double freshnessScore
) {
}
```

```java
package cn.yy.myrent.service.discovery;

import cn.yy.myrent.entity.House;

import java.util.List;

public record HouseRankedItem(
        House house,
        double totalScore,
        HouseScoreBreakdown scoreBreakdown,
        List<HouseReasonCode> reasonCodes
) {
}
```

```java
package cn.yy.myrent.service.discovery;

import lombok.Builder;

@Builder
public record HouseRankQuery(
        Integer budgetYuan,
        String budgetScope,
        String rentMode,
        Integer page,
        Integer size,
        Boolean nearSubway,
        Boolean privateBathroom,
        Boolean hasBalcony,
        Boolean civilWaterElectric,
        Boolean supportStudentDepositFree,
        HouseRankingProfile rankingProfile
) {
}
```

```java
package cn.yy.myrent.service.discovery;

import java.util.List;

public record HouseRankResult(List<HouseRankedItem> items) {

    public List<HouseRankedItem> pageSlice() {
        return items;
    }
}
```

```java
package cn.yy.myrent.service.discovery;

public interface HouseRankingService {

    HouseRankResult rank(HouseRankQuery query, HouseRecallResult recallResult);
}
```

```java
package cn.yy.myrent.service.discovery;

import cn.yy.myrent.entity.House;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class HouseRankingServiceImpl implements HouseRankingService {

    @Override
    public HouseRankResult rank(HouseRankQuery query, HouseRecallResult recallResult) {
        List<HouseRankedItem> items = recallResult.candidates().stream()
                .map(candidate -> rankOne(query, candidate))
                .sorted(Comparator
                        .comparingDouble(HouseRankedItem::totalScore).reversed()
                        .thenComparing(item -> item.house().getCreateTime(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.house().getId(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return new HouseRankResult(items);
    }

    private HouseRankedItem rankOne(HouseRankQuery query, HouseRecallCandidate candidate) {
        HouseScoreBreakdown breakdown = buildBreakdown(query, candidate);
        double total = switch (query.rankingProfile()) {
            case SEARCH_DEFAULT -> breakdown.recallScore() * 0.20
                    + breakdown.textRelevanceScore() * 0.10
                    + breakdown.locationDistanceScore() * 0.20
                    + breakdown.budgetCloseScore() * 0.20
                    + breakdown.rentModeMatchScore() * 0.10
                    + breakdown.nearSubwayScore() * 0.05
                    + breakdown.privateBathroomScore() * 0.05
                    + breakdown.hasBalconyScore() * 0.03
                    + breakdown.civilWaterElectricScore() * 0.03
                    + breakdown.supportStudentDepositFreeScore() * 0.02
                    + breakdown.freshnessScore() * 0.02
                    - breakdown.relaxationPenaltyOrAdjustment();
            case AI_RECOMMEND_DEFAULT -> breakdown.recallScore() * 0.12
                    + breakdown.textRelevanceScore() * 0.08
                    + breakdown.locationDistanceScore() * 0.18
                    + breakdown.budgetCloseScore() * 0.28
                    + breakdown.rentModeMatchScore() * 0.12
                    + breakdown.nearSubwayScore() * 0.06
                    + breakdown.privateBathroomScore() * 0.06
                    + breakdown.hasBalconyScore() * 0.03
                    + breakdown.civilWaterElectricScore() * 0.03
                    + breakdown.supportStudentDepositFreeScore() * 0.02
                    + breakdown.freshnessScore() * 0.02
                    - breakdown.relaxationPenaltyOrAdjustment();
        };
        return new HouseRankedItem(candidate.house(), total, breakdown, buildReasonCodes(candidate, breakdown));
    }

    private double freshnessScore(House house) {
        if (house == null || house.getCreateTime() == null) {
            return 0;
        }
        long ageDays = ChronoUnit.DAYS.between(house.getCreateTime(), LocalDateTime.now());
        return Math.max(0, 100 - ageDays * 5);
    }

    private List<HouseReasonCode> buildReasonCodes(HouseRecallCandidate candidate, HouseScoreBreakdown breakdown) {
        List<HouseReasonCode> reasonCodes = new ArrayList<>();
        if (candidate.recallEvidence().locationMatched() && candidate.recallEvidence().textMatched()) {
            reasonCodes.add(HouseReasonCode.TEXT_AND_LOCATION_MATCHED);
        } else if (candidate.recallEvidence().locationMatched()) {
            reasonCodes.add(HouseReasonCode.LOCATION_MATCHED);
        } else if (candidate.recallEvidence().textMatched()) {
            reasonCodes.add(HouseReasonCode.TEXT_MATCHED);
        }
        if (breakdown.budgetCloseScore() >= 80) {
            reasonCodes.add(HouseReasonCode.BUDGET_CLOSE);
        }
        if (candidate.recallEvidence().nearSubwayMatched()) {
            reasonCodes.add(HouseReasonCode.NEAR_SUBWAY_MATCH);
        }
        if (candidate.recallEvidence().privateBathroomMatched()) {
            reasonCodes.add(HouseReasonCode.PRIVATE_BATHROOM_MATCH);
        }
        if (candidate.recallEvidence().relaxedBudgetApplied()) {
            reasonCodes.add(HouseReasonCode.RELAXED_BUDGET_CANDIDATE);
        }
        if (candidate.recallEvidence().relaxedRadiusApplied()) {
            reasonCodes.add(HouseReasonCode.RELAXED_RADIUS_CANDIDATE);
        }
        return reasonCodes;
    }
}
```

- [ ] **Step 4: Run ranking tests to verify the two profiles behave differently and emit reasons**

Run: `mvn "-Dtest=HouseRankingServiceTest" test`  
Expected: PASS with profile-specific ordering and reason-code assertions green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/discovery src/test/java/cn/yy/myrent/service/discovery/HouseRankingServiceTest.java
git commit -m "feat: add shared house ranking service"
```

### Task 4: Migrate Smart Guide to Shared Recall plus Ranking While Preserving Relaxation and Tip Semantics

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/smartguide/SmartGuideRecommendationService.java`
- Modify: `src/main/java/cn/yy/myrent/service/score/SmartGuideScoreCalculator.java`
- Modify: `src/main/java/cn/yy/myrent/vo/SmartGuideItemVO.java`
- Create: `src/test/java/cn/yy/myrent/service/smartguide/SmartGuideRecommendationServiceTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateCollectorTest.java`

- [ ] **Step 1: Write the failing smart-guide migration tests**

```java
package cn.yy.myrent.service.smartguide;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.discovery.HouseRankQuery;
import cn.yy.myrent.service.discovery.HouseRankResult;
import cn.yy.myrent.service.discovery.HouseRankedItem;
import cn.yy.myrent.service.discovery.HouseRankingProfile;
import cn.yy.myrent.service.discovery.HouseRankingService;
import cn.yy.myrent.service.discovery.HouseRecallCandidate;
import cn.yy.myrent.service.discovery.HouseRecallEvidence;
import cn.yy.myrent.service.discovery.HouseRecallMatchTier;
import cn.yy.myrent.service.discovery.HouseRecallProfile;
import cn.yy.myrent.service.discovery.HouseRecallQuery;
import cn.yy.myrent.service.discovery.HouseRecallResult;
import cn.yy.myrent.service.discovery.HouseReasonCode;
import cn.yy.myrent.service.discovery.HouseScoreBreakdown;
import cn.yy.myrent.vo.SmartGuideResultVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartGuideRecommendationServiceTest {

    @Mock
    private HouseRecallService houseRecallService;

    @Mock
    private HouseRankingService houseRankingService;

    @InjectMocks
    private SmartGuideRecommendationService service;

    @Test
    void recommendShouldUseSharedRecallAndAiRankingProfileAndKeepRelaxedTip() {
        House house = new House().setId(201L).setTitle("Yuyuan one bedroom").setPrice(350000).setDepositAmount(100000);
        HouseRecallCandidate candidate = new HouseRecallCandidate(
                house,
                HouseRecallMatchTier.RELAXED_BUDGET,
                HouseRecallEvidence.builder()
                        .locationMatched(true)
                        .exactConstraintMatched(false)
                        .relaxedBudgetApplied(true)
                        .nearSubwayMatched(true)
                        .build()
        );
        when(houseRecallService.recall(any(HouseRecallQuery.class)))
                .thenReturn(new HouseRecallResult(List.of(candidate), true, false));
        when(houseRankingService.rank(any(HouseRankQuery.class), any(HouseRecallResult.class)))
                .thenReturn(new HouseRankResult(List.of(
                        new HouseRankedItem(
                                house,
                                91.2d,
                                HouseScoreBreakdown.builder().budgetCloseScore(88).nearSubwayScore(100).build(),
                                List.of(HouseReasonCode.BUDGET_CLOSE, HouseReasonCode.NEAR_SUBWAY_MATCH, HouseReasonCode.RELAXED_BUDGET_CANDIDATE)
                        )
                )));

        SmartGuideResultVO result = service.recommend(smartGuideReq());

        assertEquals(1, result.getRecommendations().size());
        assertTrue(result.getTipMessage().contains("放宽"));

        ArgumentCaptor<HouseRankQuery> rankQueryCaptor = ArgumentCaptor.forClass(HouseRankQuery.class);
        verify(houseRankingService).rank(rankQueryCaptor.capture(), any(HouseRecallResult.class));
        assertEquals(HouseRankingProfile.AI_RECOMMEND_DEFAULT, rankQueryCaptor.getValue().rankingProfile());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn "-Dtest=SmartGuideRecommendationServiceTest,SmartGuideCandidateCollectorTest" test`  
Expected: FAIL because `SmartGuideRecommendationService` still depends on smart-guide-specific scoring flow instead of shared recall plus ranking.

- [ ] **Step 3: Replace smart-guide scoring assembly with shared recall plus ranking**

```java
@Service
@RequiredArgsConstructor
public class SmartGuideRecommendationService {

    private final HouseRecallService houseRecallService;
    private final HouseRankingService houseRankingService;

    public SmartGuideResultVO recommend(SmartGuideReqDTO reqDTO) {
        validateRequest(reqDTO);

        HouseRecallResult recallResult = houseRecallService.recall(HouseRecallQuery.builder()
                .locationName(resolveRequestedLocationName(reqDTO))
                .budgetYuan(reqDTO.getBudgetYuan())
                .budgetScope(reqDTO.getBudgetScope())
                .rentMode(reqDTO.getRentMode())
                .page(reqDTO.getPage())
                .size(reqDTO.getSize())
                .recallProfile(HouseRecallProfile.SMART_GUIDE)
                .build());

        HouseRankResult rankResult = houseRankingService.rank(
                HouseRankQuery.builder()
                        .budgetYuan(reqDTO.getBudgetYuan())
                        .budgetScope(reqDTO.getBudgetScope())
                        .rentMode(reqDTO.getRentMode())
                        .page(reqDTO.getPage())
                        .size(reqDTO.getSize())
                        .rankingProfile(HouseRankingProfile.AI_RECOMMEND_DEFAULT)
                        .build(),
                recallResult
        );

        SmartGuideResultVO result = new SmartGuideResultVO();
        result.setOriginalBudgetYuan(reqDTO.getBudgetYuan());
        result.setRelaxedBudget(rankResult.items().stream().anyMatch(item ->
                item.reasonCodes().contains(HouseReasonCode.RELAXED_BUDGET_CANDIDATE)));
        result.setRecommendations(rankResult.items().stream()
                .skip(Math.max((reqDTO.getPage() - 1L) * reqDTO.getSize(), 0))
                .limit(reqDTO.getSize())
                .map(this::toItemVo)
                .toList());
        result.setTipMessage(resolveTipMessage(recallResult, rankResult));
        result.setMatchedExpectation(rankResult.items().stream().anyMatch(item ->
                item.reasonCodes().contains(HouseReasonCode.BUDGET_CLOSE)));
        return result;
    }
}
```

- [ ] **Step 4: Map shared reason codes into outward `SmartGuideItemVO.reasons`**

```java
private SmartGuideItemVO toItemVo(HouseRankedItem rankedItem) {
    SmartGuideItemVO item = new SmartGuideItemVO();
    item.setHouseId(rankedItem.house().getId());
    item.setPublisherUserId(rankedItem.house().getPublisherUserId());
    item.setTitle(rankedItem.house().getTitle());
    item.setStatus(rankedItem.house().getStatus());
    item.setPrice(convertCentToYuan(rankedItem.house().getPrice()));
    item.setDepositAmount(convertCentToYuan(rankedItem.house().getDepositAmount()));
    item.setTotalCost(convertCentToYuan(resolveComparableCostCent(rankedItem.house(), true)));
    item.setScore(BigDecimal.valueOf(rankedItem.totalScore()));
    item.setReasons(rankedItem.reasonCodes().stream()
            .map(this::renderReason)
            .toList());
    return item;
}

private String renderReason(HouseReasonCode code) {
    return switch (code) {
        case BUDGET_CLOSE -> "预算贴近";
        case RENT_MODE_MATCH -> "租住方式匹配";
        case NEAR_TARGET_LOCATION -> "距离目标位置较近";
        case NEAR_SUBWAY_MATCH -> "符合近地铁偏好";
        case PRIVATE_BATHROOM_MATCH -> "符合独卫偏好";
        case HAS_BALCONY_MATCH -> "符合阳台偏好";
        case CIVIL_WATER_ELECTRIC_MATCH -> "符合民水民电偏好";
        case STUDENT_DEPOSIT_FREE_MATCH -> "符合学生免押偏好";
        case RELAXED_BUDGET_CANDIDATE -> "来自放宽预算补充结果";
        case RELAXED_RADIUS_CANDIDATE -> "来自扩圈补充结果";
        default -> "综合匹配度较高";
    };
}
```

- [ ] **Step 5: Run tests and commit**

Run: `mvn "-Dtest=SmartGuideRecommendationServiceTest,SmartGuideCandidateCollectorTest" test`  
Expected: PASS with preserved smart-guide semantics and shared ranking integration.

```bash
git add src/main/java/cn/yy/myrent/service/smartguide/SmartGuideRecommendationService.java src/main/java/cn/yy/myrent/service/score/SmartGuideScoreCalculator.java src/main/java/cn/yy/myrent/vo/SmartGuideItemVO.java src/test/java/cn/yy/myrent/service/smartguide/SmartGuideRecommendationServiceTest.java src/test/java/cn/yy/myrent/service/smartguide/SmartGuideCandidateCollectorTest.java
git commit -m "refactor: migrate smart guide to shared ranking"
```

### Task 5: Add the AI Recommendation LLM-Support Layer and Ground `/ai-recommend` in Ranked Results

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendRankingPayload.java`
- Create: `src/main/java/cn/yy/myrent/service/ai/AiRecommendRankingPayloadBuilder.java`
- Create: `src/test/java/cn/yy/myrent/service/ai/AiRecommendRankingPayloadBuilderTest.java`
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java`
- Modify: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`

- [ ] **Step 1: Write the failing AI grounding tests**

```java
@Test
void chatShouldBuildGroundedRankingPayloadBeforeComposingRecommendationReply() {
    AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
    session.setSlots(AiRecommendSlots.builder()
            .locationName("豫园")
            .budgetYuan(3500)
            .budgetScope("RENT_ONLY")
            .rentMode("WHOLE")
            .preferences(List.of("nearSubway", "privateBathroom"))
            .build());

    when(stateStore.loadOrCreate(1001L)).thenReturn(session);
    when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
            .thenReturn(AiRecommendDecision.builder().reply("我来帮你看几套更适合的房源。").slots(AiRecommendSlots.builder().build()).build());
    when(houseRecallService.recall(any(HouseRecallQuery.class))).thenReturn(sharedRecallResult());
    when(houseRankingService.rank(any(HouseRankQuery.class), any(HouseRecallResult.class))).thenReturn(sharedRankResult());
    when(rankingPayloadBuilder.build(any(AiRecommendSlots.class), any(HouseRankResult.class))).thenReturn(
            new AiRecommendRankingPayload(
                    "预算卡得较紧，优先整租、近地铁、独卫",
                    List.of("houseId=201 score=94.2 reasons=[BUDGET_CLOSE,NEAR_SUBWAY_MATCH,PRIVATE_BATHROOM_MATCH]")
            )
    );

    AiRecommendChatVO result = aiRecommendService.chat(1001L, req("帮我推荐"));

    assertEquals("SEARCH", result.getStage());
    assertNotNull(result.getRecommendation());
    verify(rankingPayloadBuilder).build(any(AiRecommendSlots.class), any(HouseRankResult.class));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn "-Dtest=AiRecommendRankingPayloadBuilderTest,AiRecommendServiceTest" test`  
Expected: FAIL because AI recommendation still depends on `houseService.smartGuide(...)` and does not build a ranking-grounded LLM payload.

- [ ] **Step 3: Implement the payload builder**

```java
package cn.yy.myrent.service.ai;

import java.util.List;

public record AiRecommendRankingPayload(
        String userIntentSummary,
        List<String> rankedFacts
) {
}
```

```java
package cn.yy.myrent.service.ai;

import cn.yy.myrent.service.discovery.HouseRankResult;
import cn.yy.myrent.service.discovery.HouseRankedItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiRecommendRankingPayloadBuilder {

    public AiRecommendRankingPayload build(AiRecommendSlots slots, HouseRankResult rankResult) {
        String summary = "预算 " + slots.getBudgetYuan()
                + "，租住方式 " + slots.getRentMode()
                + "，目标区域 " + slots.getLocationName()
                + "，偏好 " + String.join(",", slots.getPreferences());
        List<String> rankedFacts = rankResult.items().stream()
                .limit(5)
                .map(this::renderFact)
                .toList();
        return new AiRecommendRankingPayload(summary, rankedFacts);
    }

    private String renderFact(HouseRankedItem item) {
        return "houseId=" + item.house().getId()
                + " score=" + item.totalScore()
                + " reasons=" + item.reasonCodes();
    }
}
```

- [ ] **Step 4: Replace `houseService.smartGuide(...)` in `AiRecommendServiceImpl` with shared recall plus ranking plus payload building**

```java
if (stage == AiRecommendStage.SEARCH) {
    HouseRecallResult recallResult = houseRecallService.recall(HouseRecallQuery.builder()
            .locationName(mergedSlots.getLocationName())
            .budgetYuan(mergedSlots.getBudgetYuan())
            .budgetScope(mergedSlots.getBudgetScope())
            .rentMode(mergedSlots.getRentMode())
            .nearSubway(hasPreference(mergedSlots, "nearSubway"))
            .privateBathroom(hasPreference(mergedSlots, "privateBathroom"))
            .hasBalcony(hasPreference(mergedSlots, "hasBalcony"))
            .civilWaterElectric(hasPreference(mergedSlots, "civilWaterElectric"))
            .supportStudentDepositFree(hasPreference(mergedSlots, "supportStudentDepositFree"))
            .page(1)
            .size(10)
            .recallProfile(HouseRecallProfile.AI_RECOMMEND)
            .build());

    HouseRankResult rankResult = houseRankingService.rank(
            HouseRankQuery.builder()
                    .budgetYuan(mergedSlots.getBudgetYuan())
                    .budgetScope(mergedSlots.getBudgetScope())
                    .rentMode(mergedSlots.getRentMode())
                    .nearSubway(hasPreference(mergedSlots, "nearSubway"))
                    .privateBathroom(hasPreference(mergedSlots, "privateBathroom"))
                    .hasBalcony(hasPreference(mergedSlots, "hasBalcony"))
                    .civilWaterElectric(hasPreference(mergedSlots, "civilWaterElectric"))
                    .supportStudentDepositFree(hasPreference(mergedSlots, "supportStudentDepositFree"))
                    .rankingProfile(HouseRankingProfile.AI_RECOMMEND_DEFAULT)
                    .page(1)
                    .size(10)
                    .build(),
            recallResult
    );

    AiRecommendRankingPayload rankingPayload = rankingPayloadBuilder.build(mergedSlots, rankResult);
    recommendation = smartGuideAssembler.fromRankResult(mergedSlots, recallResult, rankResult);
    assistantReply = buildRecommendationReply(decision.getReply(), rankingPayload, recommendation);
}
```

- [ ] **Step 5: Run tests and commit**

Run: `mvn "-Dtest=AiRecommendRankingPayloadBuilderTest,AiRecommendServiceTest" test`  
Expected: PASS with AI recommendation grounded in ranked evidence rather than in `houseService.smartGuide(...)`.

```bash
git add src/main/java/cn/yy/myrent/service/ai/AiRecommendRankingPayload.java src/main/java/cn/yy/myrent/service/ai/AiRecommendRankingPayloadBuilder.java src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java src/test/java/cn/yy/myrent/service/ai/AiRecommendRankingPayloadBuilderTest.java src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java
git commit -m "refactor: ground ai recommend on shared ranking"
```

### Task 6: Lock Controller and Cross-Route Regression Coverage Around the Refactor

**Files:**
- Modify: `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`
- Modify: `src/test/java/cn/yy/myrent/controller/AiRecommendControllerWebMvcTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/impl/HouseServiceImplListFilterTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/smartguide/SmartGuideRecommendationServiceTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`

- [ ] **Step 1: Add controller-level assertions that the public route surface stays compatible**

```java
@Test
void smartGuideShouldKeepExistingResponseFieldsAfterInternalRefactor() throws Exception {
    SmartGuideResultVO result = new SmartGuideResultVO();
    result.setOriginalBudgetYuan(3500);
    result.setRelaxedBudget(Boolean.TRUE);
    result.setRelaxedBudgetYuan(3800);
    result.setMatchedExpectation(Boolean.TRUE);
    result.setTipMessage("完全符合条件的房源较少，已补充放宽条件后的备选结果。");
    result.setRecommendations(List.of(new SmartGuideItemVO()));

    given(houseService.smartGuide(any(SmartGuideReqDTO.class))).willReturn(result);

    mockMvc.perform(post("/house/smart-guide")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "budgetYuan": 3500,
                              "budgetScope": "RENT_ONLY",
                              "rentMode": "WHOLE",
                              "locationName": "豫园"
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.originalBudgetYuan").value(3500))
            .andExpect(jsonPath("$.data.relaxedBudget").value(true))
            .andExpect(jsonPath("$.data.tipMessage").isString())
            .andExpect(jsonPath("$.data.recommendations").isArray());
}
```

- [ ] **Step 2: Run the focused web and service regression suite**

Run: `mvn "-Dtest=HouseControllerWebMvcTest,AiRecommendControllerWebMvcTest,HouseKeywordSearchServiceTest,HouseServiceImplListFilterTest,SmartGuideRecommendationServiceTest,AiRecommendServiceTest" test`  
Expected: FAIL first if any public contract assertions or route adapters still depend on removed logic.

- [ ] **Step 3: Fix the route adapters and helper methods until the focused regression suite passes**

```java
private HouseRecallQuery toKeywordRecallQuery(HouseKeywordSearchReqDTO reqDTO) {
    return HouseRecallQuery.builder()
            .keyword(reqDTO.getKeyword())
            .page(reqDTO.getPage())
            .size(reqDTO.getSize())
            .recallProfile(HouseRecallProfile.KEYWORD_SEARCH)
            .build();
}

private HouseRankQuery toSearchRankQuery(HouseKeywordSearchReqDTO reqDTO) {
    return HouseRankQuery.builder()
            .page(reqDTO.getPage())
            .size(reqDTO.getSize())
            .rankingProfile(HouseRankingProfile.SEARCH_DEFAULT)
            .build();
}
```

```java
private boolean hasPreference(AiRecommendSlots slots, String key) {
    return slots.getPreferences() != null && slots.getPreferences().stream().anyMatch(key::equalsIgnoreCase);
}
```

- [ ] **Step 4: Run the full backend verification command**

Run: `mvn test`  
Expected: PASS with all backend tests green, including the new discovery-layer tests and the existing route/service suites.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java src/test/java/cn/yy/myrent/controller/AiRecommendControllerWebMvcTest.java src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java src/test/java/cn/yy/myrent/service/impl/HouseServiceImplListFilterTest.java src/test/java/cn/yy/myrent/service/smartguide/SmartGuideRecommendationServiceTest.java src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java
git commit -m "test: lock route compatibility for house discovery refactor"
```

## Self-Review

- Spec coverage: the plan covers all four affected routes, introduces shared recall contracts, introduces shared ranking contracts with two profiles, preserves smart-guide relaxation semantics, and grounds AI recommendation in a dedicated LLM-support payload builder.
- Placeholder scan: no `TODO`, `TBD`, or “implement later” placeholders remain in tasks.
- Type consistency: `HouseRecall*`, `HouseRank*`, and `AiRecommendRankingPayload*` names are consistent across tasks; route adapters always call `HouseRecallService.recall(...)` followed by `HouseRankingService.rank(...)`.
