# House Keyword Search Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (preferred in this repo) or superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new keyword-search API that performs parallel location recall and text recall, revalidates candidates from MySQL, ranks them with location-first logic, and wires the homepage plus house-list search boxes to that API.

**Architecture:** Keep `POST /house/nearby` unchanged and introduce a new `POST /house/search` flow. The backend uses Elasticsearch only for fast dual-path recall, merges evidence by `houseId`, reloads authoritative house rows from MySQL, then ranks and returns `HouseSearchResultVO`. The frontend adds a new API helper, updates `useHouseFeed` to use keyword search instead of nearby search, and switches the house list page to use the new endpoint when the top keyword box is submitted.

**Tech Stack:** Spring Boot 3.5, MyBatis-Plus, Spring Data Elasticsearch, JUnit 5, Mockito, Vue 3, Vitest, Vue Test Utils, Axios

---

## File Map

### Backend

- Create: `src/main/java/cn/yy/myrent/dto/HouseKeywordSearchReqDTO.java`
  Responsibility: request body for the new keyword-search endpoint.
- Create: `src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java`
  Responsibility: orchestrate dual recall, candidate merge, MySQL validation, ranking, and `HouseSearchResultVO` assembly.
- Modify: `src/main/java/cn/yy/myrent/controller/HouseController.java`
  Responsibility: expose `POST /house/search`.
- Modify: `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`
  Responsibility: verify request validation and controller contract for the new endpoint.
- Create: `src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java`
  Responsibility: verify dual recall merge, DB truth filtering, degradation semantics, and ordering.

### Frontend

- Modify: `frontend/src/api/house.js`
  Responsibility: add the new keyword-search API helper.
- Modify: `frontend/src/composables/useHouseFeed.js`
  Responsibility: switch the homepage feed from nearby search semantics to keyword search semantics.
- Modify: `frontend/src/composables/__tests__/useHouseFeed.spec.js`
  Responsibility: verify the updated search mode and request payload.
- Modify: `frontend/src/views/HomeView.vue`
  Responsibility: call the new keyword-search API through `useHouseFeed`.
- Modify: `frontend/src/views/__tests__/HomeView.spec.js`
  Responsibility: verify the homepage still renders and still routes to detail after the feed refactor.
- Modify: `frontend/src/views/HouseListView.vue`
  Responsibility: call the new keyword-search endpoint when the top keyword search box is submitted, while preserving the existing structured filter flow when keyword is empty.
- Modify: `frontend/src/views/__tests__/HouseListView.spec.js`
  Responsibility: verify top keyword submit uses the new endpoint and structured filter auto-search still uses `list-filter`.

## Task 1: Add the backend endpoint contract for keyword search

**Files:**
- Create: `src/main/java/cn/yy/myrent/dto/HouseKeywordSearchReqDTO.java`
- Modify: `src/main/java/cn/yy/myrent/controller/HouseController.java`
- Modify: `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`

- [ ] **Step 1: Write the failing controller tests**

```java
package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.dto.HouseKeywordSearchReqDTO;
import cn.yy.myrent.entity.House;
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
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.service.search.HouseKeywordSearchService;
import cn.yy.myrent.sync.house.service.HouseEsSyncService;
import cn.yy.myrent.vo.HouseSearchResultVO;
import cn.yy.myrent.vo.HouseVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HouseController.class)
class HouseControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IHouseService houseService;

    @MockBean
    private HouseKeywordSearchService houseKeywordSearchService;

    @MockBean
    private IHouseCommandService houseCommandService;

    @MockBean
    private IHouseHistoryService houseHistoryService;

    @MockBean
    private IReviewService reviewService;

    @MockBean
    private HouseEsSyncService houseEsSyncService;

    @MockBean
    private HouseHotService houseHotService;

    @MockBean
    private JwtTokenUtil jwtTokenUtil;

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
    void searchShouldDefaultPageAndSize() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        HouseVO item = new HouseVO();
        item.setId(7L);
        item.setTitle("天河公园单间");
        item.setPrice(BigDecimal.valueOf(3200));

        HouseSearchResultVO result = new HouseSearchResultVO();
        result.setHouses(List.of(item));
        result.setFallbackSource("KEYWORD_SEARCH");

        given(houseKeywordSearchService.search(any(HouseKeywordSearchReqDTO.class))).willReturn(result);

        mockMvc.perform(post("/house/search")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "keyword": "天河公园单间"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.houses[0].id").value(7))
                .andExpect(jsonPath("$.data.houses[0].title").value("天河公园单间"))
                .andExpect(jsonPath("$.data.fallbackSource").value("KEYWORD_SEARCH"));

        ArgumentCaptor<HouseKeywordSearchReqDTO> captor = ArgumentCaptor.forClass(HouseKeywordSearchReqDTO.class);
        verify(houseKeywordSearchService).search(captor.capture());
        assertEquals("天河公园单间", captor.getValue().getKeyword());
        assertEquals(1, captor.getValue().getPage());
        assertEquals(10, captor.getValue().getSize());
    }

    @Test
    void searchShouldRequireKeyword() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        mockMvc.perform(post("/house/search")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "size": 10
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(houseKeywordSearchService);
    }
}
```

- [ ] **Step 2: Run the controller test to verify it fails**

Run: `mvn -Dtest=HouseControllerWebMvcTest test`

Expected: FAIL because `POST /house/search` does not exist, `HouseKeywordSearchReqDTO` does not exist, and `HouseController` does not inject `HouseKeywordSearchService`.

- [ ] **Step 3: Add the request DTO and controller endpoint**

```java
// src/main/java/cn/yy/myrent/dto/HouseKeywordSearchReqDTO.java
package cn.yy.myrent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HouseKeywordSearchReqDTO {

    @NotBlank(message = "keyword cannot be blank")
    private String keyword;

    @Min(value = 1, message = "page must be at least 1")
    private Integer page = 1;

    @Min(value = 1, message = "size must be at least 1")
    @Max(value = 50, message = "size cannot be greater than 50")
    private Integer size = 10;
}
```

```java
// src/main/java/cn/yy/myrent/controller/HouseController.java
package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.dto.HouseKeywordSearchReqDTO;
import cn.yy.myrent.service.search.HouseKeywordSearchService;
import cn.yy.myrent.vo.HouseSearchResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RequiredArgsConstructor
public class HouseController {

    private final HouseKeywordSearchService houseKeywordSearchService;

    @PostMapping("/search")
    public Result<HouseSearchResultVO> search(@Valid @RequestBody HouseKeywordSearchReqDTO reqDTO) {
        if (reqDTO.getPage() == null) {
            reqDTO.setPage(1);
        }
        if (reqDTO.getSize() == null) {
            reqDTO.setSize(10);
        }
        return Result.success(houseKeywordSearchService.search(reqDTO));
    }
}
```

- [ ] **Step 4: Run the controller test to verify it passes**

Run: `mvn -Dtest=HouseControllerWebMvcTest test`

Expected: PASS with `POST /house/search` returning a successful `HouseSearchResultVO` wrapper and rejecting blank keyword input.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/dto/HouseKeywordSearchReqDTO.java src/main/java/cn/yy/myrent/controller/HouseController.java src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java
git commit -m "feat: add house keyword search endpoint contract"
```

## Task 2: Implement the keyword-search backend orchestration

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java`
- Create: `src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java`

- [ ] **Step 1: Write the failing service tests**

```java
package cn.yy.myrent.service.search;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.dto.HouseKeywordSearchReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IUserService;
import cn.yy.myrent.service.location.LocationResolveService;
import cn.yy.myrent.vo.HouseSearchResultVO;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseKeywordSearchServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private HouseMapper houseMapper;

    @Mock
    private LocationResolveService locationResolveService;

    @Mock
    private IUserService userService;

    @InjectMocks
    private HouseKeywordSearchService houseKeywordSearchService;

    @Test
    void searchShouldMergeDualRecallAndDropUnavailableDbRows() {
        HouseDoc locationDoc = new HouseDoc();
        locationDoc.setId(11L);
        locationDoc.setStatus(1);
        locationDoc.setTitle("体育西路地铁口单间");

        HouseDoc sharedDoc = new HouseDoc();
        sharedDoc.setId(12L);
        sharedDoc.setStatus(1);
        sharedDoc.setTitle("天河公园精装单间");

        HouseDoc textDoc = new HouseDoc();
        textDoc.setId(13L);
        textDoc.setStatus(1);
        textDoc.setTitle("天河公园主卧");

        SearchHit<HouseDoc> locationHitOne = mock(SearchHit.class);
        SearchHit<HouseDoc> locationHitTwo = mock(SearchHit.class);
        SearchHit<HouseDoc> textHitOne = mock(SearchHit.class);
        SearchHit<HouseDoc> textHitTwo = mock(SearchHit.class);

        when(locationHitOne.getContent()).thenReturn(locationDoc);
        when(locationHitOne.getSortValues()).thenReturn(List.of(120.0));
        when(locationHitTwo.getContent()).thenReturn(sharedDoc);
        when(locationHitTwo.getSortValues()).thenReturn(List.of(260.0));
        when(textHitOne.getContent()).thenReturn(sharedDoc);
        when(textHitTwo.getContent()).thenReturn(textDoc);

        SearchHits<HouseDoc> locationHits = mock(SearchHits.class);
        SearchHits<HouseDoc> textHits = mock(SearchHits.class);
        when(locationHits.iterator()).thenReturn(List.of(locationHitOne, locationHitTwo).iterator());
        when(textHits.iterator()).thenReturn(List.of(textHitOne, textHitTwo).iterator());

        when(locationResolveService.resolveRequired("天河公园单间"))
                .thenReturn(new LocationResolveService.ResolvedLocation("天河公园", 23.145d, 113.333d));
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class)))
                .thenReturn(locationHits)
                .thenReturn(textHits);

        House availableLocation = new House();
        availableLocation.setId(11L);
        availableLocation.setPublisherUserId(1001L);
        availableLocation.setTitle("体育西路地铁口单间");
        availableLocation.setCity("广州");
        availableLocation.setRegion("天河");
        availableLocation.setPrice(300000);
        availableLocation.setDepositAmount(300000);
        availableLocation.setStatus(1);
        availableLocation.setCreateTime(LocalDateTime.of(2026, 4, 25, 10, 0));

        House dualHit = new House();
        dualHit.setId(12L);
        dualHit.setPublisherUserId(1002L);
        dualHit.setTitle("天河公园精装单间");
        dualHit.setCity("广州");
        dualHit.setRegion("天河");
        dualHit.setPrice(320000);
        dualHit.setDepositAmount(320000);
        dualHit.setStatus(1);
        dualHit.setCreateTime(LocalDateTime.of(2026, 4, 25, 11, 0));

        House unavailableTextOnly = new House();
        unavailableTextOnly.setId(13L);
        unavailableTextOnly.setPublisherUserId(1003L);
        unavailableTextOnly.setTitle("天河公园主卧");
        unavailableTextOnly.setStatus(2);

        when(houseMapper.selectBatchIds(List.of(11L, 12L, 13L)))
                .thenReturn(List.of(dualHit, unavailableTextOnly, availableLocation));

        User publisherOne = new User();
        publisherOne.setId(1001L);
        publisherOne.setName("房东A");
        User publisherTwo = new User();
        publisherTwo.setId(1002L);
        publisherTwo.setName("房东B");
        when(userService.listByIds(List.of(1002L, 1001L))).thenReturn(List.of(publisherTwo, publisherOne));

        HouseKeywordSearchReqDTO reqDTO = new HouseKeywordSearchReqDTO();
        reqDTO.setKeyword("天河公园单间");
        reqDTO.setPage(1);
        reqDTO.setSize(2);

        HouseSearchResultVO result = houseKeywordSearchService.search(reqDTO);

        assertEquals(2, result.getHouses().size());
        assertEquals(12L, result.getHouses().get(0).getId());
        assertEquals(11L, result.getHouses().get(1).getId());
        assertEquals("KEYWORD_SEARCH", result.getFallbackSource());
        assertEquals(Boolean.FALSE, result.getEsDown());
    }

    @Test
    void searchShouldMarkDegradedWhenTextRecallFailsButLocationRecallSucceeds() {
        HouseDoc locationDoc = new HouseDoc();
        locationDoc.setId(31L);
        locationDoc.setStatus(1);
        locationDoc.setTitle("体育西路地铁口单间");

        SearchHit<HouseDoc> locationHit = mock(SearchHit.class);
        when(locationHit.getContent()).thenReturn(locationDoc);
        when(locationHit.getSortValues()).thenReturn(List.of(88.0));

        SearchHits<HouseDoc> locationHits = mock(SearchHits.class);
        when(locationHits.iterator()).thenReturn(List.of(locationHit).iterator());

        when(locationResolveService.resolveRequired("体育西路"))
                .thenReturn(new LocationResolveService.ResolvedLocation("体育西路", 23.132d, 113.321d));
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class)))
                .thenReturn(locationHits)
                .thenThrow(new RuntimeException("ES text path down"));

        House dbHouse = new House();
        dbHouse.setId(31L);
        dbHouse.setPublisherUserId(2001L);
        dbHouse.setTitle("体育西路地铁口单间");
        dbHouse.setCity("广州");
        dbHouse.setRegion("天河");
        dbHouse.setPrice(280000);
        dbHouse.setDepositAmount(280000);
        dbHouse.setStatus(1);
        dbHouse.setCreateTime(LocalDateTime.of(2026, 4, 25, 9, 0));
        when(houseMapper.selectBatchIds(List.of(31L))).thenReturn(List.of(dbHouse));

        User publisher = new User();
        publisher.setId(2001L);
        publisher.setName("房东C");
        when(userService.listByIds(List.of(2001L))).thenReturn(List.of(publisher));

        HouseKeywordSearchReqDTO reqDTO = new HouseKeywordSearchReqDTO();
        reqDTO.setKeyword("体育西路");
        reqDTO.setPage(1);
        reqDTO.setSize(1);

        HouseSearchResultVO result = houseKeywordSearchService.search(reqDTO);

        assertEquals(1, result.getHouses().size());
        assertEquals(Boolean.TRUE, result.getEsDown());
        assertEquals("KEYWORD_SEARCH_DEGRADED", result.getFallbackSource());
    }

    @Test
    void searchShouldOversampleEachRecallPathWithSizeTimesThree() {
        SearchHits<HouseDoc> emptyHits = mock(SearchHits.class);
        when(emptyHits.iterator()).thenReturn(List.<SearchHit<HouseDoc>>of().iterator());

        when(locationResolveService.resolveRequired("天河公园")).thenThrow(new IllegalArgumentException("not found"));
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class))).thenReturn(emptyHits);

        HouseKeywordSearchReqDTO reqDTO = new HouseKeywordSearchReqDTO();
        reqDTO.setKeyword("天河公园");
        reqDTO.setPage(1);
        reqDTO.setSize(4);

        houseKeywordSearchService.search(reqDTO);

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(HouseDoc.class));

        Pageable pageable = queryCaptor.getValue().getPageable();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(12, pageable.getPageSize());
    }
}
```

- [ ] **Step 2: Run the service test to verify it fails**

Run: `mvn -Dtest=HouseKeywordSearchServiceTest test`

Expected: FAIL because `HouseKeywordSearchService` does not exist and the search flow has not been implemented.

- [ ] **Step 3: Implement the keyword-search service**

```java
// src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java
package cn.yy.myrent.service.search;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.dto.HouseKeywordSearchReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IUserService;
import cn.yy.myrent.service.location.LocationResolveService;
import cn.yy.myrent.vo.HouseSearchResultVO;
import cn.yy.myrent.vo.HouseVO;
import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HouseKeywordSearchService {

    private static final int HOUSE_STATUS_AVAILABLE = 1;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final int OVERSAMPLE_MULTIPLIER = 3;
    private static final double LOCATION_RADIUS_KM = 10.0d;

    private final ElasticsearchOperations elasticsearchOperations;
    private final HouseMapper houseMapper;
    private final LocationResolveService locationResolveService;
    private final IUserService userService;

    public HouseSearchResultVO search(HouseKeywordSearchReqDTO reqDTO) {
        String keyword = reqDTO.getKeyword().trim();
        int page = reqDTO.getPage() == null ? DEFAULT_PAGE : Math.max(reqDTO.getPage(), 1);
        int size = reqDTO.getSize() == null ? DEFAULT_SIZE : Math.min(Math.max(reqDTO.getSize(), 1), MAX_SIZE);
        int recallSize = size * OVERSAMPLE_MULTIPLIER;

        CompletableFuture<RecallEnvelope> locationFuture =
                CompletableFuture.supplyAsync(() -> searchByLocation(keyword, recallSize));
        CompletableFuture<RecallEnvelope> textFuture =
                CompletableFuture.supplyAsync(() -> searchByText(keyword, recallSize));

        RecallEnvelope locationEnvelope = locationFuture.join();
        RecallEnvelope textEnvelope = textFuture.join();

        Map<Long, RecallEvidence> evidenceMap = mergeEvidence(locationEnvelope.evidence(), textEnvelope.evidence());
        List<House> availableHouses = loadAvailableHouses(evidenceMap.keySet());

        List<RankedHouse> rankedHouses = availableHouses.stream()
                .map(house -> new RankedHouse(house, computeScore(evidenceMap.get(house.getId()))))
                .sorted(Comparator
                        .comparing(RankedHouse::score).reversed()
                        .thenComparing(item -> item.house().getCreateTime(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.house().getId(), Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        int fromIndex = Math.max((page - 1) * size, 0);
        int toIndex = Math.min(fromIndex + size, rankedHouses.size());
        List<House> pageHouses = fromIndex >= rankedHouses.size()
                ? List.of()
                : rankedHouses.subList(fromIndex, toIndex).stream().map(RankedHouse::house).toList();

        HouseSearchResultVO result = new HouseSearchResultVO();
        result.setHouses(enrichPublisherNames(toHouseVos(pageHouses)));
        boolean degraded = locationEnvelope.degraded() || textEnvelope.degraded();
        result.setEsDown(degraded);
        result.setFallbackSource(degraded ? "KEYWORD_SEARCH_DEGRADED" : "KEYWORD_SEARCH");
        result.setTipMessage(pageHouses.isEmpty() ? "当前未找到匹配房源" : null);
        return result;
    }

    private RecallEnvelope searchByLocation(String keyword, int recallSize) {
        try {
            LocationResolveService.ResolvedLocation resolved = locationResolveService.resolveRequired(keyword);
            Query query = Query.of(q -> q.bool(b -> b
                    .must(m -> m.term(t -> t.field("status").value(HOUSE_STATUS_AVAILABLE)))
                    .filter(f -> f.geoDistance(g -> g
                            .field("location")
                            .distance(LOCATION_RADIUS_KM + "km")
                            .location(loc -> loc.latlon(ll -> ll.lat(resolved.latitude()).lon(resolved.longitude())))))
            ));

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withSort(SortOptions.of(s -> s.geoDistance(g -> g
                            .field("location")
                            .location(loc -> loc.latlon(ll -> ll.lat(resolved.latitude()).lon(resolved.longitude())))
                            .order(SortOrder.Asc)
                            .unit(DistanceUnit.Meters))))
                    .withPageable(PageRequest.of(0, recallSize))
                    .build();

            SearchHits<HouseDoc> hits = elasticsearchOperations.search(nativeQuery, HouseDoc.class);
            Map<Long, RecallEvidence> evidence = new LinkedHashMap<>();
            int rank = 0;
            for (SearchHit<HouseDoc> hit : hits) {
                HouseDoc doc = hit.getContent();
                if (doc == null || doc.getId() == null) {
                    continue;
                }
                Double distance = hit.getSortValues().isEmpty() ? null : ((Number) hit.getSortValues().get(0)).doubleValue();
                evidence.put(doc.getId(), new RecallEvidence(doc.getId(), true, false, distance, rank++, null, null));
            }
            return new RecallEnvelope(evidence, false);
        } catch (IllegalArgumentException ignored) {
            return new RecallEnvelope(new LinkedHashMap<>(), false);
        } catch (Exception ex) {
            log.warn("keyword search location recall failed, keyword={}", keyword, ex);
            return new RecallEnvelope(new LinkedHashMap<>(), true);
        }
    }

    private RecallEnvelope searchByText(String keyword, int recallSize) {
        try {
            Query query = Query.of(q -> q.bool(b -> b
                    .must(m -> m.term(t -> t.field("status").value(HOUSE_STATUS_AVAILABLE)))
                    .must(m -> m.match(mm -> mm.field("title").query(keyword)))
            ));

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withSort(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))))
                    .withSort(SortOptions.of(s -> s.field(f -> f.field("createTime").order(SortOrder.Desc))))
                    .withPageable(PageRequest.of(0, recallSize))
                    .build();

            SearchHits<HouseDoc> hits = elasticsearchOperations.search(nativeQuery, HouseDoc.class);
            Map<Long, RecallEvidence> evidence = new LinkedHashMap<>();
            int rank = 0;
            for (SearchHit<HouseDoc> hit : hits) {
                HouseDoc doc = hit.getContent();
                if (doc == null || doc.getId() == null) {
                    continue;
                }
                evidence.put(doc.getId(), new RecallEvidence(doc.getId(), false, true, null, null, rank++, hit.getScore()));
            }
            return new RecallEnvelope(evidence, false);
        } catch (Exception ex) {
            log.warn("keyword search text recall failed, keyword={}", keyword, ex);
            return new RecallEnvelope(new LinkedHashMap<>(), true);
        }
    }

    private Map<Long, RecallEvidence> mergeEvidence(Map<Long, RecallEvidence> locationEvidence,
                                                    Map<Long, RecallEvidence> textEvidence) {
        Map<Long, RecallEvidence> merged = new LinkedHashMap<>(locationEvidence);
        for (Map.Entry<Long, RecallEvidence> entry : textEvidence.entrySet()) {
            merged.merge(entry.getKey(), entry.getValue(), this::mergeEvidenceItem);
        }
        return merged;
    }

    private RecallEvidence mergeEvidenceItem(RecallEvidence left, RecallEvidence right) {
        return new RecallEvidence(
                left.houseId(),
                left.locationMatched() || right.locationMatched(),
                left.textMatched() || right.textMatched(),
                left.locationDistanceMeters() != null ? left.locationDistanceMeters() : right.locationDistanceMeters(),
                left.locationRank() != null ? left.locationRank() : right.locationRank(),
                left.textRank() != null ? left.textRank() : right.textRank(),
                left.textScore() != null ? left.textScore() : right.textScore()
        );
    }

    private List<House> loadAvailableHouses(Iterable<Long> candidateIds) {
        List<Long> ids = new ArrayList<>();
        candidateIds.forEach(ids::add);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, House> houseMap = houseMapper.selectBatchIds(ids).stream()
                .filter(Objects::nonNull)
                .filter(house -> house.getId() != null)
                .filter(house -> house.getStatus() != null && house.getStatus() == HOUSE_STATUS_AVAILABLE)
                .collect(Collectors.toMap(House::getId, house -> house, (left, right) -> left, LinkedHashMap::new));

        List<House> ordered = new ArrayList<>();
        for (Long id : ids) {
            House house = houseMap.get(id);
            if (house != null) {
                ordered.add(house);
            }
        }
        return ordered;
    }

    private double computeScore(RecallEvidence evidence) {
        if (evidence == null) {
            return 0;
        }
        double total = 0;
        if (evidence.locationMatched()) {
            total += 1000;
        }
        if (evidence.textMatched()) {
            total += 600;
        }
        if (evidence.locationMatched() && evidence.textMatched()) {
            total += 200;
        }
        if (evidence.locationDistanceMeters() != null) {
            total += Math.max(0, 120 - evidence.locationDistanceMeters() / 20.0d);
        }
        if (evidence.textRank() != null) {
            total += Math.max(0, 80 - evidence.textRank() * 5.0d);
        }
        return total;
    }

    private List<HouseVO> toHouseVos(List<House> houses) {
        return houses.stream().map(this::convertHouseToVo).toList();
    }

    private List<HouseVO> enrichPublisherNames(List<HouseVO> houses) {
        if (houses.isEmpty()) {
            return houses;
        }

        List<Long> publisherIds = houses.stream()
                .map(HouseVO::getPublisherUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, String> userNameMap = userService.listByIds(publisherIds).stream()
                .filter(Objects::nonNull)
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(
                        User::getId,
                        user -> StringUtils.hasText(user.getName()) ? user.getName() : "未知发布者",
                        (left, right) -> left
                ));

        houses.forEach(house -> house.setPublisherName(userNameMap.getOrDefault(house.getPublisherUserId(), "未知发布者")));
        return houses;
    }

    private HouseVO convertHouseToVo(House house) {
        HouseVO vo = new HouseVO();
        vo.setId(house.getId());
        vo.setPublisherUserId(house.getPublisherUserId());
        vo.setTitle(house.getTitle());
        vo.setCity(house.getCity());
        vo.setRegion(house.getRegion());
        vo.setNearSubway(house.getNearSubway() != null && house.getNearSubway() == 1);
        vo.setPrivateBathroom(house.getPrivateBathroom() != null && house.getPrivateBathroom() == 1);
        vo.setHasBalcony(house.getHasBalcony() != null && house.getHasBalcony() == 1);
        vo.setCivilWaterElectric(house.getCivilWaterElectric() != null && house.getCivilWaterElectric() == 1);
        vo.setStatus(house.getStatus());
        if (house.getPrice() != null) {
            vo.setPrice(BigDecimal.valueOf(house.getPrice()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        if (house.getDepositAmount() != null) {
            vo.setDepositAmount(BigDecimal.valueOf(house.getDepositAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        return vo;
    }

    private record RecallEnvelope(Map<Long, RecallEvidence> evidence, boolean degraded) {
    }

    private record RecallEvidence(Long houseId,
                                  boolean locationMatched,
                                  boolean textMatched,
                                  Double locationDistanceMeters,
                                  Integer locationRank,
                                  Integer textRank,
                                  Float textScore) {
    }

    private record RankedHouse(House house, double score) {
    }
}
```

- [ ] **Step 4: Run the service test to verify it passes**

Run: `mvn -Dtest=HouseKeywordSearchServiceTest test`

Expected: PASS with coverage for merged dual recall, DB truth filtering, degraded single-path behavior, and `size * 3` oversampling.

- [ ] **Step 5: Run the combined backend test slice**

Run: `mvn -Dtest=HouseControllerWebMvcTest,HouseKeywordSearchServiceTest test`

Expected: PASS with the endpoint contract and orchestration both green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java
git commit -m "feat: implement house keyword search backend"
```

## Task 3: Switch the homepage feed from nearby mode to keyword-search mode

**Files:**
- Modify: `frontend/src/api/house.js`
- Modify: `frontend/src/composables/useHouseFeed.js`
- Modify: `frontend/src/composables/__tests__/useHouseFeed.spec.js`
- Modify: `frontend/src/views/HomeView.vue`
- Modify: `frontend/src/views/__tests__/HomeView.spec.js`

- [ ] **Step 1: Write the failing frontend tests**

```javascript
// frontend/src/composables/__tests__/useHouseFeed.spec.js
import { nextTick } from 'vue'
import { useHouseFeed } from '@/composables/useHouseFeed'

describe('useHouseFeed', () => {
  it('resets pagination when switching from hot to keyword mode', async () => {
    const hotLoader = vi.fn().mockResolvedValue({ houses: [{ id: 1 }] })
    const searchLoader = vi.fn().mockResolvedValue({ houses: [{ id: 2 }] })
    const feed = useHouseFeed({ hotLoader, searchLoader, defaultCity: '广州' })

    await feed.loadNext()
    feed.activateSearch('天河公园')
    await nextTick()

    expect(feed.mode.value).toBe('search')
    expect(feed.houses.value).toEqual([])
    expect(feed.current.value).toBe(1)
  })

  it('uses keyword payloads in search mode', async () => {
    const hotLoader = vi.fn().mockResolvedValue({ houses: [{ id: 1 }] })
    const searchLoader = vi.fn().mockResolvedValue({ houses: [{ id: 2 }] })
    const feed = useHouseFeed({ hotLoader, searchLoader, defaultCity: '广州' })

    feed.activateSearch('天河公园')
    await feed.loadNext()

    expect(searchLoader).toHaveBeenCalledWith({
      keyword: '天河公园',
      page: 1,
      size: 10
    })
  })
})
```

```javascript
// frontend/src/views/__tests__/HomeView.spec.js
import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

const activateSearch = vi.fn()

vi.mock('@/composables/useHouseFeed', () => ({
  useHouseFeed: () => ({
    houses: ref([{ id: 1, title: '大学城朝南单间', price: 1280, area: 18, status: 1 }]),
    loading: ref(false),
    error: ref(''),
    mode: ref('hot'),
    resultTip: ref('步行可达大学的优质房源'),
    loadNext: vi.fn(),
    activateSearch,
    activateHot: vi.fn()
  })
}))

describe('HomeView', () => {
  beforeEach(() => {
    activateSearch.mockReset()
  })

  it('submits keyword search through the feed search mode', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/houses', component: { template: '<div />' } },
        { path: '/house/:id', component: { template: '<div />' } }
      ]
    })

    const wrapper = mount(HomeView, {
      global: {
        plugins: [router]
      }
    })

    await wrapper.get('#home-search').setValue('天河公园')
    await wrapper.get('.hero-search').trigger('submit')

    expect(activateSearch).toHaveBeenCalledWith('天河公园')
  })
})
```

- [ ] **Step 2: Run the frontend tests to verify they fail**

Run: `npm run test:run -- src/composables/__tests__/useHouseFeed.spec.js src/views/__tests__/HomeView.spec.js`

Workdir: `frontend`

Expected: FAIL because `useHouseFeed` still uses nearby semantics and `HomeView` still calls `activateNearby`.

- [ ] **Step 3: Add the keyword-search API helper and refactor the feed**

```javascript
// frontend/src/api/house.js
import http from './http'

export function fetchHouseKeywordSearch(payload) {
  return http.post('/house/search', payload)
}
```

```javascript
// frontend/src/composables/useHouseFeed.js
import { ref } from 'vue'
import { formatRequestError } from '@/utils/format'

export function useHouseFeed({ hotLoader, searchLoader }) {
  const houses = ref([])
  const loading = ref(false)
  const error = ref('')
  const current = ref(1)
  const size = ref(10)
  const hasMore = ref(true)
  const mode = ref('hot')
  const activeKeyword = ref('')
  const resultTip = ref('')

  function resetPaging() {
    houses.value = []
    current.value = 1
    hasMore.value = true
    error.value = ''
  }

  async function loadNext() {
    if (loading.value || !hasMore.value) {
      return
    }

    loading.value = true
    error.value = ''

    try {
      const result = mode.value === 'search'
        ? await searchLoader({
            keyword: activeKeyword.value,
            page: current.value,
            size: size.value
          })
        : await hotLoader({
            page: current.value,
            size: size.value
          })

      const records = result?.houses || []
      houses.value = [...houses.value, ...records]
      hasMore.value = records.length >= size.value
      current.value += 1
      resultTip.value = result?.tipMessage || ''
    } catch (err) {
      error.value = formatRequestError(err, '房源服务暂时不可用，请稍后再试。')
      hasMore.value = false
    } finally {
      loading.value = false
    }
  }

  function activateSearch(keyword) {
    mode.value = 'search'
    activeKeyword.value = keyword
    resultTip.value = ''
    resetPaging()
  }

  function activateHot() {
    mode.value = 'hot'
    activeKeyword.value = ''
    resultTip.value = ''
    resetPaging()
  }

  return {
    houses,
    loading,
    error,
    current,
    size,
    hasMore,
    mode,
    activeKeyword,
    resultTip,
    resetPaging,
    loadNext,
    activateSearch,
    activateHot
  }
}
```

```vue
<!-- frontend/src/views/HomeView.vue -->
<script setup>
import { computed, defineComponent, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchHotHousePage, fetchHouseKeywordSearch } from '@/api/house'
import { useHouseFeed } from '@/composables/useHouseFeed'

const feed = useHouseFeed({
  hotLoader: fetchHotHousePage,
  searchLoader: fetchHouseKeywordSearch
})

async function handleSearch(keyword) {
  if (!keyword) {
    feed.activateHot()
    await feed.loadNext()
    return
  }

  feed.activateSearch(keyword)
  await feed.loadNext()
}
</script>
```

- [ ] **Step 4: Run the frontend tests to verify they pass**

Run: `npm run test:run -- src/composables/__tests__/useHouseFeed.spec.js src/views/__tests__/HomeView.spec.js`

Workdir: `frontend`

Expected: PASS with `useHouseFeed` sending keyword payloads in search mode and `HomeView` using the new feed entry point.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/house.js frontend/src/composables/useHouseFeed.js frontend/src/composables/__tests__/useHouseFeed.spec.js frontend/src/views/HomeView.vue frontend/src/views/__tests__/HomeView.spec.js
git commit -m "feat: wire homepage to house keyword search"
```

## Task 4: Switch the house list page top search box to the new keyword-search API

**Files:**
- Modify: `frontend/src/views/HouseListView.vue`
- Modify: `frontend/src/views/__tests__/HouseListView.spec.js`

- [ ] **Step 1: Write the failing list-page tests**

```javascript
// frontend/src/views/__tests__/HouseListView.spec.js
import { reactive } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import HouseListView from '@/views/HouseListView.vue'
import { fetchHouseKeywordSearch, fetchHouseListFilter } from '@/api/house'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/house', () => ({
  fetchHouseListFilter: vi.fn(),
  fetchHouseKeywordSearch: vi.fn()
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: vi.fn()
}))

async function flushPromises() {
  for (let index = 0; index < 8; index += 1) {
    await Promise.resolve()
  }
}

describe('HouseListView', () => {
  const authStore = reactive({
    currentCity: '广州',
    switchCity: vi.fn()
  })

  beforeEach(() => {
    vi.useFakeTimers()
    authStore.currentCity = '广州'
    authStore.switchCity.mockReset()
    useAuthStore.mockReturnValue(authStore)

    fetchHouseListFilter.mockResolvedValue({
      tipMessage: '结构化筛选已更新',
      records: [{ id: 101, title: '珠江新城地铁口两居', price: 4200, city: '广州', region: '天河', status: 1 }]
    })

    fetchHouseKeywordSearch.mockResolvedValue({
      houses: [{ id: 102, title: '天河公园精装单间', price: 3200, city: '广州', region: '天河', status: 1 }],
      fallbackSource: 'KEYWORD_SEARCH'
    })
  })

  afterEach(() => {
    vi.runOnlyPendingTimers()
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  async function mountView() {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/houses', component: HouseListView },
        { path: '/house/:id', component: { template: '<div />' } }
      ]
    })

    router.push('/houses')
    await router.isReady()

    const wrapper = mount(HouseListView, {
      global: {
        plugins: [router],
        stubs: {
          LoadingState: { props: ['text'], template: '<div data-test="loading">{{ text }}</div>' },
          EmptyState: { props: ['title', 'description'], template: '<div data-test="empty-state">{{ title }}{{ description }}</div>' }
        }
      }
    })

    await flushPromises()
    return wrapper
  }

  it('uses the keyword-search endpoint when the top search box has text', async () => {
    const wrapper = await mountView()

    fetchHouseKeywordSearch.mockClear()
    fetchHouseListFilter.mockClear()

    await wrapper.get('[data-test="house-keyword"]').setValue('天河公园')
    await wrapper.get('[data-test="house-search-submit"]').trigger('click')
    await flushPromises()

    expect(fetchHouseKeywordSearch).toHaveBeenCalledWith({
      keyword: '天河公园',
      page: 1,
      size: 10
    })
    expect(fetchHouseListFilter).not.toHaveBeenCalled()
  })

  it('keeps structured auto-search on list-filter when keyword is empty', async () => {
    const wrapper = await mountView()

    fetchHouseListFilter.mockClear()

    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    await checkboxes[0].setValue(true)
    vi.advanceTimersByTime(300)
    await flushPromises()

    expect(fetchHouseListFilter).toHaveBeenCalledWith({
      city: '广州',
      region: '',
      rentType: null,
      minPriceYuan: null,
      maxPriceYuan: null,
      nearSubway: true,
      privateBathroom: false,
      hasBalcony: false,
      civilWaterElectric: false,
      page: 1,
      size: 10
    })
  })
})
```

- [ ] **Step 2: Run the list-page test to verify it fails**

Run: `npm run test:run -- src/views/__tests__/HouseListView.spec.js`

Workdir: `frontend`

Expected: FAIL because `HouseListView` still filters keyword locally and does not call the new backend endpoint.

- [ ] **Step 3: Implement keyword mode in the house list page**

```vue
<!-- frontend/src/views/HouseListView.vue -->
<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchHouseKeywordSearch, fetchHouseListFilter } from '@/api/house'
import { DEFAULT_CITY, HOT_CITY_OPTIONS, getRegionsByCity } from '@/config/cityFilters'
import { useAuthStore } from '@/stores/auth'

const filteredHouses = computed(() => houses.value)

watch(
  () => [
    filters.locationName,
    filters.pricePreset,
    filters.rentMode,
    filters.nearSubway,
    filters.privateBathroom,
    filters.hasBalcony,
    filters.civilWaterElectric
  ],
  () => {
    if (filters.keyword.trim()) {
      return
    }
    queueAutoSearch()
  }
)

async function submitFilterSearch({ force = false } = {}) {
  const keyword = filters.keyword.trim()
  if (keyword) {
    const keywordPayload = {
      keyword,
      page: 1,
      size: 10
    }
    const requestKey = JSON.stringify(keywordPayload)
    if (!force && lastRequestKey.value === requestKey) {
      return
    }

    lastRequestKey.value = requestKey
    loading.value = true
    loadError.value = ''
    currentMode.value = 'keyword'

    try {
      const result = await fetchHouseKeywordSearch(keywordPayload)
      houses.value = normalizeHouseRecords(extractRecords(result))
      lastFilterPayload.value = keywordPayload
      resultMessage.value = result?.tipMessage || `关键词搜索结果已刷新：${keyword}`
    } catch (error) {
      houses.value = []
      lastRequestKey.value = ''
      loadError.value = error?.message || '关键词搜索接口暂时不可用，请稍后重试。'
      resultMessage.value = loadError.value
    } finally {
      loading.value = false
    }
    return
  }

  const payload = buildFilterPayload()
  const requestKey = JSON.stringify(payload)
  if (!force && lastRequestKey.value === requestKey) {
    return
  }

  lastRequestKey.value = requestKey
  loading.value = true
  loadError.value = ''
  currentMode.value = 'filter'

  try {
    const result = await fetchHouseListFilter(payload)
    houses.value = normalizeHouseRecords(extractRecords(result))
    lastFilterPayload.value = payload
    resultMessage.value = result?.tipMessage || `已按 ${payload.city}${payload.region ? ` ${payload.region}` : ''} 刷新房源`
  } catch (error) {
    houses.value = []
    lastRequestKey.value = ''
    lastFilterPayload.value = payload
    loadError.value = error?.message || '房源筛选接口暂时不可用，请稍后重试。'
    resultMessage.value = loadError.value
  } finally {
    loading.value = false
  }
}
</script>
```

- [ ] **Step 4: Run the list-page test to verify it passes**

Run: `npm run test:run -- src/views/__tests__/HouseListView.spec.js`

Workdir: `frontend`

Expected: PASS with top keyword submit calling `fetchHouseKeywordSearch` and empty-keyword structured filters still using `fetchHouseListFilter`.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/HouseListView.vue frontend/src/views/__tests__/HouseListView.spec.js
git commit -m "feat: use keyword search on house list page"
```

## Task 5: Run focused regression verification

**Files:**
- Modify: `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java`
- Modify: `frontend/src/composables/__tests__/useHouseFeed.spec.js`
- Modify: `frontend/src/views/__tests__/HomeView.spec.js`
- Modify: `frontend/src/views/__tests__/HouseListView.spec.js`

- [ ] **Step 1: Add one final regression assertion if anything is still missing**

```java
// src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java
@Test
void searchShouldReturnEmptyWhenBothRecallPathsFail() {
    when(locationResolveService.resolveRequired("天河公园")).thenThrow(new RuntimeException("location path down"));
    when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class)))
            .thenThrow(new RuntimeException("text path down"));

    HouseKeywordSearchReqDTO reqDTO = new HouseKeywordSearchReqDTO();
    reqDTO.setKeyword("天河公园");
    reqDTO.setPage(1);
    reqDTO.setSize(10);

    HouseSearchResultVO result = houseKeywordSearchService.search(reqDTO);

    assertTrue(result.getHouses().isEmpty());
    assertEquals(Boolean.TRUE, result.getEsDown());
    assertEquals("KEYWORD_SEARCH_DEGRADED", result.getFallbackSource());
}
```

```javascript
// frontend/src/views/__tests__/HouseListView.spec.js
it('returns to structured mode after clearing the keyword and resetting filters', async () => {
  const wrapper = await mountView()

  await wrapper.get('[data-test="house-keyword"]').setValue('')
  await wrapper.find('.toolbar-reset').trigger('click')
  await flushPromises()

  expect(fetchHouseListFilter).toHaveBeenCalled()
})
```

- [ ] **Step 2: Run the full focused backend verification**

Run: `mvn -Dtest=HouseControllerWebMvcTest,HouseKeywordSearchServiceTest test`

Expected: PASS with controller validation, dual recall orchestration, degradation handling, and DB truth filtering covered.

- [ ] **Step 3: Run the full focused frontend verification**

Run: `npm run test:run -- src/composables/__tests__/useHouseFeed.spec.js src/views/__tests__/HomeView.spec.js src/views/__tests__/HouseListView.spec.js`

Workdir: `frontend`

Expected: PASS with homepage keyword mode, list-page keyword mode, and structured fallback behavior all green.

- [ ] **Step 4: Run one manual smoke test**

Run: `npm run dev`

Workdir: `frontend`

Manual check:
- In the homepage search box, enter a keyword such as `天河公园` and confirm the network call goes to `POST /house/search`.
- Confirm the homepage cards update with the keyword-search result.
- Open `/houses`, enter a keyword in the top input, and confirm the page uses `POST /house/search`.
- Clear the keyword, toggle a structured filter, and confirm the page switches back to `POST /house/list-filter`.
- Click a returned house card and confirm routing to `/house/:id`.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java frontend/src/composables/__tests__/useHouseFeed.spec.js frontend/src/views/__tests__/HomeView.spec.js frontend/src/views/__tests__/HouseListView.spec.js
git commit -m "test: verify house keyword search flow"
```
