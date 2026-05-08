# House Hot V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a city-level hot-house ranking that reuses existing browse, favorite, and consult facts; supports homepage and search-empty fallback; updates favorite/consult scores in Redis in realtime; and rebuilds the full city ranking on a schedule.

**Architecture:** Keep MySQL tables as the source of truth for house eligibility and behavior facts, compute city-level hot scores into Redis ZSets, and use scheduled rebuild as the final reconciliation path. Realtime writes only apply to favorite and consult; browse stays delayed and only affects ranking through scheduled rebuild.

**Tech Stack:** Spring Boot 3, MyBatis-Plus, MySQL, Redis ZSet/Hash, JUnit 5, Mockito, Vue 3, Pinia, Vitest

---

## File Map

**Backend core**

- Modify: `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
- Modify: `src/main/java/cn/yy/myrent/service/hot/HouseHotScoreSnapshot.java`
- Create: `src/main/java/cn/yy/myrent/service/hot/HouseSignalCountRow.java`
- Modify: `src/main/java/cn/yy/myrent/sync/house/HouseHotRefreshTask.java`

**Backend data access**

- Modify: `src/main/java/cn/yy/myrent/mapper/HouseHistoryMapper.java`
- Modify: `src/main/resources/mapper/HouseHistoryMapper.xml`
- Modify: `src/main/java/cn/yy/myrent/mapper/ChatSessionMapper.java`
- Modify: `src/main/resources/mapper/ChatSessionMapper.xml`

**Backend write paths**

- Modify: `src/main/java/cn/yy/myrent/service/impl/HouseFavoriteServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/service/impl/ChatSessionServiceImpl.java`

**Backend read paths**

- Modify: `src/main/java/cn/yy/myrent/service/IHouseService.java`
- Modify: `src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/controller/HouseController.java`
- Modify: `src/main/java/cn/yy/myrent/dto/HouseKeywordSearchReqDTO.java`
- Modify: `src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java`

**Frontend city wiring**

- Modify: `frontend/src/composables/useHouseFeed.js`
- Modify: `frontend/src/views/HomeView.vue`
- Modify: `frontend/src/views/HouseListView.vue`

**Tests**

- Create: `src/test/java/cn/yy/myrent/service/hot/HouseHotServiceTest.java`
- Create: `src/test/java/cn/yy/myrent/service/impl/HouseFavoriteServiceImplTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/impl/ChatSessionServiceImplTest.java`
- Modify: `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java`
- Modify: `frontend/src/composables/__tests__/useHouseFeed.spec.js`
- Modify: `frontend/src/views/__tests__/HomeView.spec.js`
- Modify: `frontend/src/views/__tests__/HouseListView.spec.js`

## Task 1: Add 7-day browse and consult aggregation contracts

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/hot/HouseSignalCountRow.java`
- Modify: `src/main/java/cn/yy/myrent/mapper/HouseHistoryMapper.java`
- Modify: `src/main/resources/mapper/HouseHistoryMapper.xml`
- Modify: `src/main/java/cn/yy/myrent/mapper/ChatSessionMapper.java`
- Modify: `src/main/resources/mapper/ChatSessionMapper.xml`
- Test: `src/test/java/cn/yy/myrent/service/hot/HouseHotServiceTest.java`

- [ ] **Step 1: Write the failing aggregation test**

```java
@Test
void rebuildHotRankingByCityShouldUseBrowseAndConsultCountsFromMappers() {
    when(houseMapper.selectList(any())).thenReturn(List.of(
            new House().setId(11L).setCity("南京").setStatus(1).setCreateTime(LocalDateTime.now().minusDays(2)),
            new House().setId(12L).setCity("南京").setStatus(1).setCreateTime(LocalDateTime.now().minusDays(10))
    ));
    when(houseFavoriteMapper.selectFavoriteAggRows(any())).thenReturn(List.of());
    when(houseHistoryMapper.selectBrowseCountsSince(any())).thenReturn(List.of(
            new HouseSignalCountRow(11L, 5L),
            new HouseSignalCountRow(12L, 1L)
    ));
    when(chatSessionMapper.selectConsultCountsSince(any())).thenReturn(List.of(
            new HouseSignalCountRow(11L, 2L)
    ));

    service.rebuildHotRanking();

    verify(houseHistoryMapper).selectBrowseCountsSince(any());
    verify(chatSessionMapper).selectConsultCountsSince(any());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -Dtest=HouseHotServiceTest#rebuildHotRankingByCityShouldUseBrowseAndConsultCountsFromMappers test
```

Expected:

- FAIL because `HouseSignalCountRow` and the new mapper methods do not exist yet

- [ ] **Step 3: Write the minimal DTO and mapper contracts**

```java
public record HouseSignalCountRow(Long houseId, Long count) {
}
```

```java
List<HouseSignalCountRow> selectBrowseCountsSince(@Param("startDate") LocalDate startDate);
```

```xml
<select id="selectBrowseCountsSince" resultType="cn.yy.myrent.service.hot.HouseSignalCountRow">
    select house_id as houseId, count(*) as count
    from house_history
    where browse_date >= #{startDate}
    group by house_id
</select>
```

```java
List<HouseSignalCountRow> selectConsultCountsSince(@Param("recentSince") LocalDateTime recentSince);
```

```xml
<select id="selectConsultCountsSince" resultType="cn.yy.myrent.service.hot.HouseSignalCountRow">
    select house_id as houseId, count(*) as count
    from chat_session
    where house_id is not null
      and create_time >= #{recentSince}
    group by house_id
</select>
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -Dtest=HouseHotServiceTest#rebuildHotRankingByCityShouldUseBrowseAndConsultCountsFromMappers test
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/hot/HouseSignalCountRow.java src/main/java/cn/yy/myrent/mapper/HouseHistoryMapper.java src/main/resources/mapper/HouseHistoryMapper.xml src/main/java/cn/yy/myrent/mapper/ChatSessionMapper.java src/main/resources/mapper/ChatSessionMapper.xml src/test/java/cn/yy/myrent/service/hot/HouseHotServiceTest.java
git commit -m "feat: add hot ranking aggregation mapper contracts"
```

## Task 2: Replace the global hot ranking with city-scoped full-candidate ZSets

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
- Modify: `src/main/java/cn/yy/myrent/service/hot/HouseHotScoreSnapshot.java`
- Test: `src/test/java/cn/yy/myrent/service/hot/HouseHotServiceTest.java`

- [ ] **Step 1: Write the failing city-ranking tests**

```java
@Test
void rebuildHotRankingShouldWriteCitySpecificKeys() {
    when(houseMapper.selectList(any())).thenReturn(List.of(
            new House().setId(11L).setCity("南京").setStatus(1).setCreateTime(LocalDateTime.now().minusDays(1)),
            new House().setId(21L).setCity("上海").setStatus(1).setCreateTime(LocalDateTime.now().minusDays(5))
    ));

    service.rebuildHotRanking();

    verify(zSetOperations).add("house:hot:rank:city:南京", "11", 17.0d);
    verify(zSetOperations).add("house:hot:rank:city:上海", "21", 8.0d);
}

@Test
void queryHotHousesShouldReadFromRequestedCityKey() {
    when(zSetOperations.reverseRangeWithScores("house:hot:rank:city:南京", 0, 9))
            .thenReturn(Set.of(tuple("11", 23d)));

    List<HouseVO> result = service.queryHotHouses("南京", 0, 10);

    assertEquals(1, result.size());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -Dtest=HouseHotServiceTest test
```

Expected:

- FAIL because `queryHotHouses(String, int, int)` and city-specific keys do not exist yet

- [ ] **Step 3: Implement city-scoped ranking and score model**

```java
public static final String HOT_RANK_KEY_PREFIX = "house:hot:rank:city:";
public static final String SNAPSHOT_KEY_PREFIX = "house:hot:snapshot:city:";

public String hotRankKey(String city) {
    return HOT_RANK_KEY_PREFIX + city;
}

public void rebuildHotRanking() {
    LocalDate today = LocalDate.now();
    LocalDate startDate = today.minusDays(6);
    LocalDateTime recentSince = LocalDateTime.now().minusDays(7);
    List<House> availableHouses = loadAvailableHouses();
    Map<Long, Long> browseMap = toCountMap(houseHistoryMapper.selectBrowseCountsSince(startDate));
    Map<Long, Long> consultMap = toCountMap(chatSessionMapper.selectConsultCountsSince(recentSince));
    Map<Long, HouseFavoriteAggRow> favoriteMap = houseFavoriteMapper.selectFavoriteAggRows(recentSince)
            .stream()
            .collect(Collectors.toMap(HouseFavoriteAggRow::getHouseId, row -> row, (left, right) -> left));

    clearExistingCityKeys(availableHouses);
    for (House house : availableHouses) {
        double score = calculateHotScore(
                favorite7d,
                consult7d,
                browse7d,
                freshnessBonus(house.getCreateTime())
        );
        stringRedisTemplate.opsForZSet().add(hotRankKey(house.getCity()), String.valueOf(house.getId()), score);
        writeSnapshot(house.getCity(), snapshot);
    }
}

public List<HouseVO> queryHotHouses(String city, int pageIndex, int pageSize) {
    long start = (long) pageIndex * pageSize;
    long end = start + pageSize - 1;
    Set<ZSetOperations.TypedTuple<String>> tuples =
            stringRedisTemplate.opsForZSet().reverseRangeWithScores(hotRankKey(city), start, end);
    return buildOrderedHouseVOs(city, tuples);
}

public boolean hasHotRankingCache(String city) {
    Long size = stringRedisTemplate.opsForZSet().zCard(hotRankKey(city));
    return size != null && size > 0;
}
```

```java
@Data
public class HouseHotScoreSnapshot {
    private Long houseId;
    private long recentBrowseCount;
    private long recentFavoriteCount;
    private long recentConsultCount;
    private double freshnessBonus;
    private double hotScore;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
mvn -Dtest=HouseHotServiceTest test
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/hot/HouseHotService.java src/main/java/cn/yy/myrent/service/hot/HouseHotScoreSnapshot.java src/test/java/cn/yy/myrent/service/hot/HouseHotServiceTest.java
git commit -m "feat: build city-scoped hot house ranking"
```

## Task 3: Apply realtime Redis score increment for favorites

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/impl/HouseFavoriteServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
- Create: `src/test/java/cn/yy/myrent/service/impl/HouseFavoriteServiceImplTest.java`

- [ ] **Step 1: Write the failing favorite realtime test**

```java
@Test
void favoriteShouldIncrementHotScoreAfterPersistingFavorite() {
    House house = new House().setId(7L).setCity("南京").setStatus(1);
    when(houseMapper.selectById(7L)).thenReturn(house);

    service.favorite(7L, 1001L);

    verify(houseHotService).incrementFavoriteScore("南京", 7L);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -Dtest=HouseFavoriteServiceImplTest#favoriteShouldIncrementHotScoreAfterPersistingFavorite test
```

Expected:

- FAIL because `incrementFavoriteScore` is missing and `HouseFavoriteServiceImpl` does not call `HouseHotService`

- [ ] **Step 3: Add the service hook and Redis increment method**

```java
private final HouseHotService houseHotService;

public HouseFavoriteStatusVO favorite(Long houseId, Long userId) {
    House house = requireHouse(houseId);
    HouseFavoriteStatusVO status = buildStatus(houseId, userId, true);
    houseHotService.incrementFavoriteScore(house.getCity(), houseId);
    return status;
}
```

```java
public void incrementFavoriteScore(String city, Long houseId) {
    if (!StringUtils.hasText(city) || houseId == null) {
        return;
    }
    stringRedisTemplate.opsForZSet().incrementScore(hotRankKey(city), String.valueOf(houseId), 3D);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -Dtest=HouseFavoriteServiceImplTest test
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/impl/HouseFavoriteServiceImpl.java src/main/java/cn/yy/myrent/service/hot/HouseHotService.java src/test/java/cn/yy/myrent/service/impl/HouseFavoriteServiceImplTest.java
git commit -m "feat: increment hot ranking on favorite"
```

## Task 4: Apply realtime Redis score increment for new consult sessions only

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/impl/ChatSessionServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
- Modify: `src/test/java/cn/yy/myrent/service/impl/ChatSessionServiceImplTest.java`

- [ ] **Step 1: Write the failing consult tests**

```java
@Test
void sendMessageShouldIncrementHotScoreWhenCreatingNewSession() {
    ChatMessage result = serviceSpy.sendMessage(messageDTO);
    TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

    verify(houseHotService).incrementConsultScore("南京", 7L);
}

@Test
void sendMessageShouldNotIncrementConsultScoreForExistingSession() {
    serviceSpy.sendMessage(messageDTO);
    TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

    verify(houseHotService, never()).incrementConsultScore(anyString(), anyLong());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -Dtest=ChatSessionServiceImplTest test
```

Expected:

- FAIL because the service still calls the old chat-interaction metric path on every message

- [ ] **Step 3: Replace per-message hot tracking with new-session consult increment**

```java
boolean createdNewSession = false;
if (chatSession == null) {
    long userId1 = Math.min(senderId, receiverId);
    long userId2 = Math.max(senderId, receiverId);
    ChatSession newSession = new ChatSession()
            .setSessionId(sessionId)
            .setUserId1(userId1)
            .setUserId2(userId2)
            .setHouseId(houseId)
            .setLastMsgContent(content)
            .setCreateTime(now)
            .setUpdateTime(now);
    this.save(newSession);
    createdNewSession = true;
}
boolean finalCreatedNewSession = createdNewSession;
House finalHouse = house;
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        if (finalCreatedNewSession && finalHouse != null) {
            houseHotService.incrementConsultScore(finalHouse.getCity(), houseId);
        }
        sessionManager.sendToUser(receiverId, chatMessage);
    }
});
```

```java
public void incrementConsultScore(String city, Long houseId) {
    if (!StringUtils.hasText(city) || houseId == null) {
        return;
    }
    stringRedisTemplate.opsForZSet().incrementScore(hotRankKey(city), String.valueOf(houseId), 5D);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -Dtest=ChatSessionServiceImplTest test
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/impl/ChatSessionServiceImpl.java src/main/java/cn/yy/myrent/service/hot/HouseHotService.java src/test/java/cn/yy/myrent/service/impl/ChatSessionServiceImplTest.java
git commit -m "feat: increment hot ranking on new consult sessions"
```

## Task 5: Use city-aware hot ranking for reads and empty-search fallback

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/IHouseService.java`
- Modify: `src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/controller/HouseController.java`
- Modify: `src/main/java/cn/yy/myrent/dto/HouseKeywordSearchReqDTO.java`
- Modify: `src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java`
- Modify: `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java`

- [ ] **Step 1: Write the failing read-path tests**

```java
@Test
void hotHousesShouldForwardCityToService() throws Exception {
    given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
    given(houseService.hotHouses("南京", 1, 10)).willReturn(new HouseSearchResultVO());

    mockMvc.perform(get("/house/hot")
                    .header("token", "test-token")
                    .param("city", "南京")
                    .param("page", "1")
                    .param("size", "10"))
            .andExpect(status().isOk());

    verify(houseService).hotHouses("南京", 1, 10);
}

@Test
void keywordSearchShouldFallbackToCityHotWhenNoRecallHitsRemain() {
    HouseKeywordSearchReqDTO reqDTO = new HouseKeywordSearchReqDTO();
    reqDTO.setKeyword("空关键词");
    reqDTO.setCity("南京");
    assertEquals("KEYWORD_SEARCH_EMPTY_HOT", result.getFallbackSource());
    assertEquals("已为你展示南京热门可租房源", result.getTipMessage());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -Dtest=HouseControllerWebMvcTest,HouseKeywordSearchServiceTest test
```

Expected:

- FAIL because `city` is not part of the hot endpoint contract or keyword search DTO/service yet

- [ ] **Step 3: Implement city-aware hot reads and keyword fallback**

```java
public interface IHouseService extends IService<House> {
    HouseSearchResultVO hotHouses(String city, Integer page, Integer size);
}
```

```java
@GetMapping("/hot")
public Result<HouseSearchResultVO> hotHouses(
        @RequestParam("city") String city,
        @RequestParam(value = "page", defaultValue = "1") Integer page,
        @RequestParam(value = "size", defaultValue = "10") Integer size) {
    return Result.success(houseService.hotHouses(city, page, size));
}
```

```java
public HouseSearchResultVO hotHouses(String city, Integer page, Integer size) {
    List<HouseVO> hotHouses = searchHotFromRedis(city, pageIndex, pageSize);
    return buildSearchResult(hotHouses, null, false, FALLBACK_SOURCE_REDIS_HOT, null);
}
```

```java
@NotBlank(message = "city cannot be blank")
private String city;
```

```java
if (pageRecords.isEmpty() && StringUtils.hasText(reqDTO.getCity())) {
    List<HouseVO> hotHouses = houseHotService.queryHotHouses(reqDTO.getCity(), page - 1, size);
    result.setHouses(hotHouses);
    result.setTotal((long) hotHouses.size());
    result.setFallbackSource("KEYWORD_SEARCH_EMPTY_HOT");
    result.setTipMessage("已为你展示" + reqDTO.getCity() + "热门可租房源");
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -Dtest=HouseControllerWebMvcTest,HouseKeywordSearchServiceTest test
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/IHouseService.java src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java src/main/java/cn/yy/myrent/controller/HouseController.java src/main/java/cn/yy/myrent/dto/HouseKeywordSearchReqDTO.java src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java
git commit -m "feat: use city hot ranking for homepage and empty search fallback"
```

## Task 6: Schedule full rebuild reconciliation and remove obsolete global paths

**Files:**
- Modify: `src/main/java/cn/yy/myrent/sync/house/HouseHotRefreshTask.java`
- Modify: `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
- Test: `src/test/java/cn/yy/myrent/service/hot/HouseHotServiceTest.java`

- [ ] **Step 1: Write the failing reconciliation test**

```java
@Test
void rebuildHotRankingShouldRemoveNoLongerRentableMembers() {
    when(houseMapper.selectList(any())).thenReturn(List.of(
            new House().setId(11L).setCity("南京").setStatus(1)
    ));
    when(zSetOperations.zCard("house:hot:rank:city:南京")).thenReturn(2L);

    service.rebuildHotRanking();

    verify(stringRedisTemplate).delete("house:hot:rank:city:南京");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -Dtest=HouseHotServiceTest#rebuildHotRankingShouldRemoveNoLongerRentableMembers test
```

Expected:

- FAIL because rebuild still targets the old global key path

- [ ] **Step 3: Implement scheduled reconciliation against the new city keys**

```java
@Scheduled(cron = "0 */10 * * * ?", zone = "Asia/Shanghai")
public void refreshHotRanking() {
    houseHotService.rebuildHotRanking();
    log.info("city hot-house rebuild completed");
}
```

```java
private void resetCityCaches(Set<String> cities) {
    for (String city : cities) {
        stringRedisTemplate.delete(hotRankKey(city));
        stringRedisTemplate.delete(snapshotKey(city));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -Dtest=HouseHotServiceTest test
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/sync/house/HouseHotRefreshTask.java src/main/java/cn/yy/myrent/service/hot/HouseHotService.java src/test/java/cn/yy/myrent/service/hot/HouseHotServiceTest.java
git commit -m "feat: reconcile city hot ranking on a schedule"
```

## Task 7: Pass current city from the frontend to hot and keyword requests

**Files:**
- Modify: `frontend/src/composables/useHouseFeed.js`
- Modify: `frontend/src/views/HomeView.vue`
- Modify: `frontend/src/views/HouseListView.vue`
- Modify: `frontend/src/composables/__tests__/useHouseFeed.spec.js`
- Modify: `frontend/src/views/__tests__/HomeView.spec.js`
- Modify: `frontend/src/views/__tests__/HouseListView.spec.js`

- [ ] **Step 1: Write the failing frontend tests**

```javascript
it('passes current city to the hot loader in hot mode', async () => {
  const hotLoader = vi.fn().mockResolvedValue({ houses: [] })
  const searchLoader = vi.fn()
  const feed = useHouseFeed({ hotLoader, searchLoader, defaultCity: '广州' })

  await feed.loadNext()

  expect(hotLoader).toHaveBeenCalledWith({
    city: '广州',
    page: 1,
    size: 10
  })
})
```

```javascript
it('loads homepage featured houses with authStore.currentCity', async () => {
  expect(useHouseFeed).toHaveBeenCalledWith(
    expect.objectContaining({ defaultCity: '南京' })
  )
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
npm --prefix frontend run test:run -- src/composables/__tests__/useHouseFeed.spec.js src/views/__tests__/HomeView.spec.js src/views/__tests__/HouseListView.spec.js
```

Expected:

- FAIL because `useHouseFeed` does not accept `defaultCity` and homepage does not pass current city

- [ ] **Step 3: Wire the current city through the existing frontend state**

```javascript
export function useHouseFeed({ hotLoader, searchLoader, defaultCity = '' }) {
  const activeCity = ref(defaultCity)
  const result = mode.value === 'search'
    ? await searchLoader({ city: activeCity.value, keyword: activeKeyword.value, page: current.value, size: size.value })
    : await hotLoader({ city: activeCity.value, page: current.value, size: size.value })
}
```

```vue
<script setup>
import { useAuthStore } from '@/stores/auth'
const authStore = useAuthStore()
const feed = useHouseFeed({
  hotLoader: fetchHotHousePage,
  defaultCity: authStore.currentCity
})
</script>
```

```javascript
const payload = {
  city: currentCity.value,
  keyword: filters.keyword,
  page: currentPage.value,
  size: pageSize.value
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
npm --prefix frontend run test:run -- src/composables/__tests__/useHouseFeed.spec.js src/views/__tests__/HomeView.spec.js src/views/__tests__/HouseListView.spec.js
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/composables/useHouseFeed.js frontend/src/views/HomeView.vue frontend/src/views/HouseListView.vue frontend/src/composables/__tests__/useHouseFeed.spec.js frontend/src/views/__tests__/HomeView.spec.js frontend/src/views/__tests__/HouseListView.spec.js
git commit -m "feat: pass current city to hot house requests"
```

## Task 8: Final verification run

**Files:**
- Modify: none
- Test: `src/test/java/cn/yy/myrent/service/hot/HouseHotServiceTest.java`
- Test: `src/test/java/cn/yy/myrent/service/impl/HouseFavoriteServiceImplTest.java`
- Test: `src/test/java/cn/yy/myrent/service/impl/ChatSessionServiceImplTest.java`
- Test: `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`
- Test: `src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java`
- Test: `frontend/src/composables/__tests__/useHouseFeed.spec.js`
- Test: `frontend/src/views/__tests__/HomeView.spec.js`
- Test: `frontend/src/views/__tests__/HouseListView.spec.js`

- [ ] **Step 1: Run focused backend tests**

```bash
mvn -Dtest=HouseHotServiceTest,HouseFavoriteServiceImplTest,ChatSessionServiceImplTest,HouseControllerWebMvcTest,HouseKeywordSearchServiceTest test
```

- [ ] **Step 2: Run focused frontend tests**

```bash
npm --prefix frontend run test:run -- src/composables/__tests__/useHouseFeed.spec.js src/views/__tests__/HomeView.spec.js src/views/__tests__/HouseListView.spec.js
```

- [ ] **Step 3: Run full frontend build**

```bash
npm --prefix frontend run build
```

- [ ] **Step 4: Smoke-test the main flows manually**

```text
1. Switch city in the top nav and refresh homepage; verify `/house/hot?city=南京` or another selected city is used.
2. Favorite a rentable house and confirm its city ranking score increases in Redis.
3. Start a brand-new chat session for a house and confirm consult score increments once.
4. Open the same house detail repeatedly on the same day and verify no realtime Redis browse increment happens.
5. Trigger scheduled rebuild and confirm city ranking can be reconstructed from MySQL facts.
```

- [ ] **Step 5: Commit**

```bash
git status
git commit --allow-empty -m "chore: verify house hot v1 implementation"
```

## Self-Review

### Spec coverage

- city-scoped ranking: covered by Task 2 and Task 5
- full candidate ZSet instead of top-K-only ranking: covered by Task 2
- browse from `house_history`: covered by Task 1 and Task 2
- favorite from `house_favorite`: covered by Task 3
- consult from `chat_session`: covered by Task 1 and Task 4
- realtime favorite and consult increment: covered by Task 3 and Task 4
- delayed browse contribution: covered by Task 2 and Task 6
- homepage and empty-search fallback reuse: covered by Task 5 and Task 7
- scheduled reconciliation: covered by Task 6

### Placeholder scan

- no `TODO`, `TBD`, or “similar to previous task” placeholders remain
- every task lists concrete files, commands, and code snippets

### Type consistency

- score increment methods are consistently named `incrementFavoriteScore` and `incrementConsultScore`
- city-scoped read path uses `hotHouses(String city, Integer page, Integer size)` consistently
- browse aggregation uses `LocalDate`
- consult aggregation uses `LocalDateTime`
