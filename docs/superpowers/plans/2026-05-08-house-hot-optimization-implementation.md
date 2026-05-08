# House Hot Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optimize hot-house correctness and rebuild cost by adding city-scoped rebuild, scheduled per-city rebuild, Redis hot removal on house status sync, and lightweight realtime increments for favorite/consult actions.

**Architecture:** Keep MySQL as the source of truth and Redis hot ranking as derived cache. Reuse the existing house sync event pipeline for DB -> Redis hot removal by adding a downstream hot consumer queue; do not introduce a new outbox or dispatcher. `rebuildHotRanking(city)` becomes the core rebuild method, while scheduled refresh loops over cities and request cache miss rebuilds only the requested city.

**Tech Stack:** Spring Boot, MyBatis-Plus, Redis `StringRedisTemplate`, RabbitMQ listener/manual ack, JUnit 5, Mockito, Maven.

---

## File Map

- Modify `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
  - Add city-scoped rebuild.
  - Add all-city scheduled rebuild wrapper.
  - Add hot rank removal method for unavailable houses.
  - Add favorite/consult score increment methods.
- Modify `src/main/java/cn/yy/myrent/mapper/HouseMapper.java`
  - Add DB methods for distinct rentable cities and city-scoped rentable houses.
- Modify `src/main/resources/mapper/HouseMapper.xml`
  - Add SQL for distinct rentable cities and city-scoped rentable house query.
- Modify `src/main/java/cn/yy/myrent/mapper/HouseFavoriteMapper.java`
  - Add city-scoped favorite aggregation by house IDs.
- Modify `src/main/resources/mapper/HouseFavoriteMapper.xml`
  - Add city-scoped favorite aggregation SQL.
- Modify `src/main/java/cn/yy/myrent/mapper/HouseHistoryMapper.java`
  - Add browse aggregation by house IDs.
- Modify `src/main/resources/mapper/HouseHistoryMapper.xml`
  - Add browse aggregation SQL.
- Modify `src/main/java/cn/yy/myrent/mapper/ChatSessionMapper.java`
  - Add consult aggregation by house IDs.
- Modify `src/main/resources/mapper/ChatSessionMapper.xml`
  - Add consult aggregation SQL.
- Modify `src/main/java/cn/yy/myrent/config/RabbitMQConfig.java`
  - Add `house.hot.sync.queue` bound to existing `house.sync.exchange`.
- Create `src/main/java/cn/yy/myrent/sync/house/service/HouseHotSyncService.java`
  - Interface for house sync event -> hot Redis action.
- Create `src/main/java/cn/yy/myrent/sync/house/service/impl/HouseHotSyncServiceImpl.java`
  - Query latest house and remove unavailable houses from hot Redis.
- Create `src/main/java/cn/yy/myrent/consumer/HouseHotSyncConsumer.java`
  - Consume existing `HouseSyncMessage` from new hot queue.
- Modify `src/main/java/cn/yy/myrent/sync/house/HouseHotRefreshTask.java`
  - Call `rebuildAllHotRankings()`.
- Modify `src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java`
  - Cache miss calls `rebuildHotRanking(city)`.
- Modify `src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java`
  - Search fallback cache miss calls `rebuildHotRanking(city)`.
- Modify `src/main/java/cn/yy/myrent/service/impl/HouseFavoriteServiceImpl.java`
  - Re-add after-commit favorite increment only when favorite state changes active.
- Modify `src/main/java/cn/yy/myrent/service/impl/ChatSessionServiceImpl.java`
  - Re-add after-commit consult increment only when a new session is created.
- Modify tests:
  - `src/test/java/cn/yy/myrent/service/hot/HouseHotServiceTest.java`
  - `src/test/java/cn/yy/myrent/service/impl/HouseFavoriteServiceImplTest.java`
  - `src/test/java/cn/yy/myrent/service/impl/ChatSessionServiceImplTest.java`
  - Create `src/test/java/cn/yy/myrent/sync/house/service/impl/HouseHotSyncServiceImplTest.java`
  - Create `src/test/java/cn/yy/myrent/consumer/HouseHotSyncConsumerTest.java`

---

## Task 1: Add City-Scoped Hot Rebuild Core

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
- Modify: `src/main/java/cn/yy/myrent/mapper/HouseMapper.java`
- Modify: `src/main/resources/mapper/HouseMapper.xml`
- Modify: `src/main/java/cn/yy/myrent/mapper/HouseFavoriteMapper.java`
- Modify: `src/main/resources/mapper/HouseFavoriteMapper.xml`
- Modify: `src/main/java/cn/yy/myrent/mapper/HouseHistoryMapper.java`
- Modify: `src/main/resources/mapper/HouseHistoryMapper.xml`
- Modify: `src/main/java/cn/yy/myrent/mapper/ChatSessionMapper.java`
- Modify: `src/main/resources/mapper/ChatSessionMapper.xml`
- Test: `src/test/java/cn/yy/myrent/service/hot/HouseHotServiceTest.java`

- [ ] **Step 1: Add failing tests for city-scoped rebuild**

Add these tests to `HouseHotServiceTest`.

```java
@Test
void rebuildHotRankingByCityShouldOnlyRebuildRequestedCity() throws Exception {
    when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
    when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
    when(houseMapper.selectAvailableHousesByCity("shanghai")).thenReturn(List.of(
            new House().setId(21L).setCity("shanghai").setStatus(1).setCreateTime(LocalDateTime.now().minusDays(5))
    ));
    when(houseFavoriteMapper.selectFavoriteAggRowsByHouseIds(any(), any())).thenReturn(List.of(
            favoriteAggRow(21L, 1L, 1L)
    ));
    when(houseHistoryMapper.selectBrowseCountsSinceByHouseIds(any(), any())).thenReturn(List.of(
            new HouseSignalCountRow(21L, 1L)
    ));
    when(chatSessionMapper.selectConsultCountsSinceByHouseIds(any(), any())).thenReturn(List.of());

    service.rebuildHotRanking("shanghai");

    verify(houseMapper).selectAvailableHousesByCity("shanghai");
    verify(stringRedisTemplate).delete("house:hot:rank:city:shanghai");
    verify(stringRedisTemplate).delete("house:hot:snapshot:city:shanghai");
    verify(zSetOperations).add("house:hot:rank:city:shanghai", "21", 8D);
    verify(hashOperations).put("house:hot:snapshot:city:shanghai", "21", "{}");
    verify(setOperations).add("house:hot:cities", "shanghai");
}

@Test
void rebuildHotRankingByCityShouldClearCityWhenNoAvailableHouses() {
    when(houseMapper.selectAvailableHousesByCity("shanghai")).thenReturn(Collections.emptyList());

    service.rebuildHotRanking("shanghai");

    verify(stringRedisTemplate).delete("house:hot:rank:city:shanghai");
    verify(stringRedisTemplate).delete("house:hot:snapshot:city:shanghai");
}

@Test
void rebuildHotRankingByCityShouldSkipBlankCity() {
    service.rebuildHotRanking(" ");

    verify(houseMapper, never()).selectAvailableHousesByCity(anyString());
}
```

Also add imports if missing:

```java
import static org.mockito.Mockito.never;
```

- [ ] **Step 2: Run the city rebuild tests and verify they fail**

Run:

```bash
rtk mvn "-Dtest=HouseHotServiceTest" test
```

Expected: compilation fails because `rebuildHotRanking(String)` and new mapper methods do not exist.

- [ ] **Step 3: Add mapper methods**

In `HouseMapper.java`, add:

```java
List<House> selectAvailableHousesByCity(@Param("city") String city);

List<String> selectAvailableCities();
```

In `HouseFavoriteMapper.java`, add:

```java
List<HouseFavoriteAggRow> selectFavoriteAggRowsByHouseIds(@Param("recentSince") LocalDateTime recentSince,
                                                          @Param("houseIds") List<Long> houseIds);
```

In `HouseHistoryMapper.java`, add:

```java
List<HouseSignalCountRow> selectBrowseCountsSinceByHouseIds(@Param("startDate") LocalDate startDate,
                                                            @Param("houseIds") List<Long> houseIds);
```

In `ChatSessionMapper.java`, add:

```java
List<HouseSignalCountRow> selectConsultCountsSinceByHouseIds(@Param("recentSince") LocalDateTime recentSince,
                                                             @Param("houseIds") List<Long> houseIds);
```

- [ ] **Step 4: Add mapper SQL**

In `HouseMapper.xml`, add:

```xml
<select id="selectAvailableHousesByCity" resultType="cn.yy.myrent.entity.House">
    select *
    from house
    where status = 1
      and city = #{city}
    order by id asc
</select>

<select id="selectAvailableCities" resultType="java.lang.String">
    select distinct city
    from house
    where status = 1
      and city is not null
      and city != ''
    order by city asc
</select>
```

In `HouseFavoriteMapper.xml`, add:

```xml
<select id="selectFavoriteAggRowsByHouseIds" resultType="cn.yy.myrent.service.hot.HouseFavoriteAggRow">
    select house_id as houseId,
           count(*) as totalFavoriteCount,
           sum(case when favorite_time &gt;= #{recentSince} then 1 else 0 end) as recentFavoriteCount
    from house_favorite
    where status = 1
      and house_id in
      <foreach collection="houseIds" item="houseId" open="(" separator="," close=")">
          #{houseId}
      </foreach>
    group by house_id
</select>
```

In `HouseHistoryMapper.xml`, add:

```xml
<select id="selectBrowseCountsSinceByHouseIds" resultType="cn.yy.myrent.service.hot.HouseSignalCountRow">
    select house_id as houseId, count(*) as count
    from house_history
    where browse_date &gt;= #{startDate}
      and house_id in
      <foreach collection="houseIds" item="houseId" open="(" separator="," close=")">
          #{houseId}
      </foreach>
    group by house_id
</select>
```

In `ChatSessionMapper.xml`, add:

```xml
<select id="selectConsultCountsSinceByHouseIds" resultType="cn.yy.myrent.service.hot.HouseSignalCountRow">
    select house_id as houseId, count(*) as count
    from chat_session
    where house_id is not null
      and create_time &gt;= #{recentSince}
      and house_id in
      <foreach collection="houseIds" item="houseId" open="(" separator="," close=")">
          #{houseId}
      </foreach>
    group by house_id
</select>
```

- [ ] **Step 5: Implement city-scoped rebuild**

In `HouseHotService.java`, add:

```java
public void rebuildHotRanking(String city) {
    if (!StringUtils.hasText(city)) {
        return;
    }

    LocalDate startDate = LocalDate.now().minusDays(6);
    LocalDateTime recentSince = LocalDateTime.now().minusDays(7);
    List<House> availableHouses = houseMapper.selectAvailableHousesByCity(city);
    if (CollectionUtils.isEmpty(availableHouses)) {
        clearCityCaches(List.of(city));
        log.info("skip rebuild city hot ranking because no available house exists, city={}", city);
        return;
    }

    List<Long> houseIds = availableHouses.stream()
            .map(House::getId)
            .filter(id -> id != null)
            .collect(Collectors.toList());
    if (CollectionUtils.isEmpty(houseIds)) {
        clearCityCaches(List.of(city));
        return;
    }

    Map<Long, HouseFavoriteAggRow> favoriteAggMap = houseFavoriteMapper
            .selectFavoriteAggRowsByHouseIds(recentSince, houseIds)
            .stream()
            .collect(Collectors.toMap(HouseFavoriteAggRow::getHouseId, row -> row, (left, right) -> left));
    Map<Long, Long> recentBrowseMap = toCountMap(houseHistoryMapper.selectBrowseCountsSinceByHouseIds(startDate, houseIds));
    Map<Long, Long> recentConsultMap = toCountMap(chatSessionMapper.selectConsultCountsSinceByHouseIds(recentSince, houseIds));

    clearCityCaches(List.of(city));
    stringRedisTemplate.opsForSet().add(HOT_CITY_INDEX_KEY, city);

    int candidateCount = 0;
    for (House house : availableHouses) {
        if (house.getId() == null) {
            continue;
        }
        writeHouseHotRanking(city, house, favoriteAggMap, recentBrowseMap, recentConsultMap);
        candidateCount++;
    }

    log.info("rebuild city hot ranking finished, city={}, candidateCount={}", city, candidateCount);
}
```

Extract the repeated per-house write logic from existing `rebuildHotRanking()` into:

```java
private void writeHouseHotRanking(String city,
                                  House house,
                                  Map<Long, HouseFavoriteAggRow> favoriteAggMap,
                                  Map<Long, Long> recentBrowseMap,
                                  Map<Long, Long> recentConsultMap) {
    HouseFavoriteAggRow favoriteAgg = favoriteAggMap.get(house.getId());
    long totalFavoriteCount = favoriteAgg == null || favoriteAgg.getTotalFavoriteCount() == null
            ? 0L : favoriteAgg.getTotalFavoriteCount();
    long recentFavoriteCount = favoriteAgg == null || favoriteAgg.getRecentFavoriteCount() == null
            ? 0L : favoriteAgg.getRecentFavoriteCount();
    long recentBrowseCount = recentBrowseMap.getOrDefault(house.getId(), 0L);
    long recentConsultCount = recentConsultMap.getOrDefault(house.getId(), 0L);
    double freshnessBonus = freshnessBonus(house.getCreateTime());
    double hotScore = calculateHotScore(recentFavoriteCount, recentConsultCount, recentBrowseCount, freshnessBonus);

    HouseHotScoreSnapshot snapshot = new HouseHotScoreSnapshot();
    snapshot.setHouseId(house.getId());
    snapshot.setTotalFavoriteCount(totalFavoriteCount);
    snapshot.setRecentFavoriteCount(recentFavoriteCount);
    snapshot.setRecentBrowseCount(recentBrowseCount);
    snapshot.setRecentConsultCount(recentConsultCount);
    snapshot.setFreshnessBonus(freshnessBonus);
    snapshot.setHotScore(hotScore);

    stringRedisTemplate.opsForZSet().add(hotRankKey(city), String.valueOf(house.getId()), hotScore);
    writeSnapshot(city, snapshot);
}
```

Update existing `rebuildHotRanking()` to call the helper inside its loop.

- [ ] **Step 6: Run hot service tests**

Run:

```bash
rtk mvn "-Dtest=HouseHotServiceTest" test
```

Expected: `BUILD SUCCESS`.

---

## Task 2: Add Scheduled Per-City Rebuild

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
- Modify: `src/main/java/cn/yy/myrent/sync/house/HouseHotRefreshTask.java`
- Test: `src/test/java/cn/yy/myrent/service/hot/HouseHotServiceTest.java`

- [ ] **Step 1: Add failing tests for all-city wrapper**

Add these tests to `HouseHotServiceTest`.

```java
@Test
void rebuildAllHotRankingsShouldRebuildAvailableCitiesAndClearStaleTrackedCities() {
    when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
    when(setOperations.members("house:hot:cities")).thenReturn(Set.of("shanghai", "hangzhou"));
    when(houseMapper.selectAvailableCities()).thenReturn(List.of("shanghai"));
    when(houseMapper.selectAvailableHousesByCity("shanghai")).thenReturn(Collections.emptyList());

    service.rebuildAllHotRankings();

    verify(houseMapper).selectAvailableCities();
    verify(houseMapper).selectAvailableHousesByCity("shanghai");
    verify(stringRedisTemplate).delete("house:hot:rank:city:hangzhou");
    verify(stringRedisTemplate).delete("house:hot:snapshot:city:hangzhou");
}
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
rtk mvn "-Dtest=HouseHotServiceTest" test
```

Expected: compilation fails because `rebuildAllHotRankings()` does not exist.

- [ ] **Step 3: Implement all-city wrapper**

Add to `HouseHotService.java`:

```java
public void rebuildAllHotRankings() {
    Set<String> trackedCities = loadTrackedCities();
    Set<String> citiesToRebuild = new LinkedHashSet<>(houseMapper.selectAvailableCities());

    Set<String> staleCities = new LinkedHashSet<>(trackedCities);
    staleCities.removeAll(citiesToRebuild);
    clearCityCaches(staleCities);

    replaceTrackedCities(citiesToRebuild);
    for (String city : citiesToRebuild) {
        try {
            rebuildHotRanking(city);
        } catch (Exception e) {
            log.warn("rebuild city hot ranking failed, city={}", city, e);
        }
    }
}
```

Keep existing no-arg `rebuildHotRanking()` as a compatibility wrapper:

```java
public void rebuildHotRanking() {
    rebuildAllHotRankings();
}
```

If this causes existing tests to need adjustment, update old no-arg rebuild tests to call `rebuildAllHotRankings()` for scheduled behavior or call `rebuildHotRanking(city)` for city behavior.

- [ ] **Step 4: Update scheduled task**

In `HouseHotRefreshTask.java`, change:

```java
houseHotService.rebuildHotRanking();
```

to:

```java
houseHotService.rebuildAllHotRankings();
```

- [ ] **Step 5: Run hot service tests**

Run:

```bash
rtk mvn "-Dtest=HouseHotServiceTest" test
```

Expected: `BUILD SUCCESS`.

---

## Task 3: Request Cache Miss Rebuilds Only Requested City

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java`
- Test: existing tests in `src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java`
- Test: add/update house service test if an existing hot fallback unit test exists

- [ ] **Step 1: Update request-side rebuild calls**

In `HouseServiceImpl.searchHotFromRedis`, replace:

```java
houseHotService.rebuildHotRanking();
```

with:

```java
houseHotService.rebuildHotRanking(city);
```

In `HouseKeywordSearchService.buildCityHotFallbackResult`, replace:

```java
houseHotService.rebuildHotRanking();
```

with:

```java
houseHotService.rebuildHotRanking(city);
```

- [ ] **Step 2: Update tests to verify city rebuild**

In `HouseKeywordSearchServiceTest`, find the fallback test that stubs hot cache miss. Ensure it verifies:

```java
verify(houseHotService).rebuildHotRanking("苏州");
verify(houseHotService).queryHotHouses("苏州", 0, 2);
```

If a test currently verifies `rebuildHotRanking()` with no args, change it to the city overload.

- [ ] **Step 3: Run search and hot tests**

Run:

```bash
rtk mvn "-Dtest=HouseHotServiceTest,HouseKeywordSearchServiceTest" test
```

Expected: `BUILD SUCCESS`.

---

## Task 4: Reuse House Sync Pipeline For Hot Redis Removal

**Files:**
- Modify: `src/main/java/cn/yy/myrent/config/RabbitMQConfig.java`
- Create: `src/main/java/cn/yy/myrent/sync/house/service/HouseHotSyncService.java`
- Create: `src/main/java/cn/yy/myrent/sync/house/service/impl/HouseHotSyncServiceImpl.java`
- Create: `src/main/java/cn/yy/myrent/consumer/HouseHotSyncConsumer.java`
- Test: `src/test/java/cn/yy/myrent/sync/house/service/impl/HouseHotSyncServiceImplTest.java`
- Test: `src/test/java/cn/yy/myrent/consumer/HouseHotSyncConsumerTest.java`

- [ ] **Step 1: Add hot sync service tests**

Create `HouseHotSyncServiceImplTest.java`:

```java
package cn.yy.myrent.sync.house.service.impl;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseHotSyncServiceImplTest {

    @Mock
    private HouseMapper houseMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @SuppressWarnings("rawtypes")
    @Mock
    private HashOperations hashOperations;

    @InjectMocks
    private HouseHotSyncServiceImpl service;

    @Test
    void syncHouseChangeShouldRemoveUnavailableHouseFromAllTrackedCities() {
        when(houseMapper.selectById(7L)).thenReturn(new House().setId(7L).setStatus(2).setCity("shanghai"));
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("house:hot:cities")).thenReturn(Set.of("shanghai", "hangzhou"));
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        service.syncHouseChange(7L);

        verify(zSetOperations).remove("house:hot:rank:city:shanghai", "7");
        verify(hashOperations).delete("house:hot:snapshot:city:shanghai", "7");
        verify(zSetOperations).remove("house:hot:rank:city:hangzhou", "7");
        verify(hashOperations).delete("house:hot:snapshot:city:hangzhou", "7");
    }

    @Test
    void syncHouseChangeShouldRemoveDeletedHouseFromAllTrackedCities() {
        when(houseMapper.selectById(7L)).thenReturn(null);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("house:hot:cities")).thenReturn(Set.of("shanghai"));
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        service.syncHouseChange(7L);

        verify(zSetOperations).remove("house:hot:rank:city:shanghai", "7");
        verify(hashOperations).delete("house:hot:snapshot:city:shanghai", "7");
    }
}
```

- [ ] **Step 2: Run service test and verify it fails**

Run:

```bash
rtk mvn "-Dtest=HouseHotSyncServiceImplTest" test
```

Expected: compilation fails because the service does not exist.

- [ ] **Step 3: Add hot sync queue constants and binding**

In `RabbitMQConfig.java`, add:

```java
public static final String HOUSE_HOT_SYNC_QUEUE = "house.hot.sync.queue";
```

Add beans:

```java
@Bean
public Queue houseHotSyncQueue() {
    return QueueBuilder.durable(HOUSE_HOT_SYNC_QUEUE).build();
}

@Bean
public Binding houseHotSyncBinding(@Qualifier("houseHotSyncQueue") Queue houseHotSyncQueue,
                                   @Qualifier("houseSyncExchange") DirectExchange houseSyncExchange) {
    return BindingBuilder.bind(houseHotSyncQueue).to(houseSyncExchange).with(HOUSE_SYNC_ROUTING_KEY);
}
```

- [ ] **Step 4: Create hot sync service**

Create `HouseHotSyncService.java`:

```java
package cn.yy.myrent.sync.house.service;

public interface HouseHotSyncService {

    void syncHouseChange(Long houseId);
}
```

Create `HouseHotSyncServiceImpl.java`:

```java
package cn.yy.myrent.sync.house.service.impl;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.sync.house.service.HouseHotSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class HouseHotSyncServiceImpl implements HouseHotSyncService {

    private static final int HOUSE_STATUS_AVAILABLE = 1;

    private final HouseMapper houseMapper;
    private final HouseHotService houseHotService;

    @Override
    public void syncHouseChange(Long houseId) {
        if (houseId == null) {
            return;
        }

        House house = houseMapper.selectById(houseId);
        if (house == null || house.getStatus() == null || house.getStatus() != HOUSE_STATUS_AVAILABLE) {
            houseHotService.removeHouseFromAllHotRankings(houseId);
            log.info("removed unavailable house from hot rankings, houseId={}", houseId);
        }
    }
}
```

Add to `HouseHotService.java`:

```java
public void removeHouseFromAllHotRankings(Long houseId) {
    if (houseId == null) {
        return;
    }
    Set<String> cities = loadTrackedCities();
    if (CollectionUtils.isEmpty(cities)) {
        return;
    }
    String houseIdValue = String.valueOf(houseId);
    for (String city : cities) {
        stringRedisTemplate.opsForZSet().remove(hotRankKey(city), houseIdValue);
        stringRedisTemplate.opsForHash().delete(snapshotKey(city), houseIdValue);
    }
}
```

If using this service-level delegation, adjust `HouseHotSyncServiceImplTest` to mock `HouseHotService` instead of Redis operations:

```java
verify(houseHotService).removeHouseFromAllHotRankings(7L);
```

Prefer this delegation because Redis key details stay inside `HouseHotService`.

- [ ] **Step 5: Add consumer test**

Create `HouseHotSyncConsumerTest.java`:

```java
package cn.yy.myrent.consumer;

import cn.yy.myrent.sync.house.model.HouseSyncMessage;
import cn.yy.myrent.sync.house.service.HouseHotSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HouseHotSyncConsumerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HouseHotSyncService houseHotSyncService;

    @Mock
    private Channel channel;

    @InjectMocks
    private HouseHotSyncConsumer consumer;

    @Test
    void consumeShouldSyncHouseChangeAndAck() throws Exception {
        HouseSyncMessage syncMessage = new HouseSyncMessage();
        syncMessage.setHouseId(7L);
        syncMessage.setEventType("HOUSE_ES_UPSERT");
        syncMessage.setMessageId("m1");
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(99L);
        Message message = new Message(new byte[0], properties);
        String body = "{\"houseId\":7}";

        org.mockito.Mockito.when(objectMapper.readValue(body, HouseSyncMessage.class)).thenReturn(syncMessage);

        consumer.consume(body, message, channel);

        verify(houseHotSyncService).syncHouseChange(7L);
        verify(channel).basicAck(99L, false);
    }
}
```

- [ ] **Step 6: Create hot sync consumer**

Create `HouseHotSyncConsumer.java`:

```java
package cn.yy.myrent.consumer;

import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.sync.house.model.HouseSyncMessage;
import cn.yy.myrent.sync.house.service.HouseHotSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class HouseHotSyncConsumer {

    private final ObjectMapper objectMapper;
    private final HouseHotSyncService houseHotSyncService;

    @RabbitListener(queues = RabbitMQConfig.HOUSE_HOT_SYNC_QUEUE, ackMode = "MANUAL")
    public void consume(String body, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            HouseSyncMessage syncMessage = objectMapper.readValue(body, HouseSyncMessage.class);
            houseHotSyncService.syncHouseChange(syncMessage.getHouseId());
            channel.basicAck(deliveryTag, false);
            log.info("house hot sync message consumed, houseId={}, eventType={}, messageId={}",
                    syncMessage.getHouseId(), syncMessage.getEventType(), syncMessage.getMessageId());
        } catch (Exception e) {
            log.error("house hot sync message consume failed, deliveryTag={}, body={}", deliveryTag, body, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
```

- [ ] **Step 7: Run hot sync tests**

Run:

```bash
rtk mvn "-Dtest=HouseHotSyncServiceImplTest,HouseHotSyncConsumerTest" test
```

Expected: `BUILD SUCCESS`.

---

## Task 5: Add Favorite Realtime Increment

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
- Modify: `src/main/java/cn/yy/myrent/service/impl/HouseFavoriteServiceImpl.java`
- Test: `src/test/java/cn/yy/myrent/service/impl/HouseFavoriteServiceImplTest.java`

- [ ] **Step 1: Add failing tests**

In `HouseFavoriteServiceImplTest`, add a `HouseHotService` mock field:

```java
@Mock
private HouseHotService houseHotService;
```

Add tests:

```java
@Test
void favoriteShouldIncrementHotScoreAfterCommitWhenCreatingNewActiveRelation() {
    when(houseMapper.selectById(7L)).thenReturn(new House().setId(7L).setCity("shanghai").setStatus(1));

    @SuppressWarnings("unchecked")
    LambdaQueryChainWrapper<HouseFavorite> queryChain =
            Mockito.mock(LambdaQueryChainWrapper.class, Answers.RETURNS_SELF);
    when(queryChain.eq(any(), any())).thenReturn(queryChain);
    when(queryChain.one()).thenReturn(null);

    HouseFavoriteServiceImpl serviceSpy = Mockito.spy(houseFavoriteService);
    doReturn(queryChain).when(serviceSpy).lambdaQuery();
    doReturn(true).when(serviceSpy).save(any(HouseFavorite.class));
    doReturn(statusVo(7L, true, 1L)).when(serviceSpy).getFavoriteStatus(7L, 1001L);

    TransactionSynchronizationManager.initSynchronization();
    try {
        serviceSpy.favorite(7L, 1001L);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());

        verify(houseHotService).incrementFavoriteScore("shanghai", 7L);
    } finally {
        TransactionSynchronizationManager.clearSynchronization();
    }
}

@Test
void favoriteShouldNotIncrementHotScoreWhenRelationAlreadyActive() {
    when(houseMapper.selectById(7L)).thenReturn(new House().setId(7L).setCity("shanghai").setStatus(1));

    @SuppressWarnings("unchecked")
    LambdaQueryChainWrapper<HouseFavorite> queryChain =
            Mockito.mock(LambdaQueryChainWrapper.class, Answers.RETURNS_SELF);
    when(queryChain.eq(any(), any())).thenReturn(queryChain);
    when(queryChain.one()).thenReturn(new HouseFavorite().setId(9L).setHouseId(7L).setUserId(1001L).setStatus(1));

    HouseFavoriteServiceImpl serviceSpy = Mockito.spy(houseFavoriteService);
    doReturn(queryChain).when(serviceSpy).lambdaQuery();
    doReturn(statusVo(7L, true, 1L)).when(serviceSpy).getFavoriteStatus(7L, 1001L);

    serviceSpy.favorite(7L, 1001L);

    verify(houseHotService, never()).incrementFavoriteScore(any(), any());
}
```

Add imports:

```java
import org.springframework.transaction.support.TransactionSynchronizationManager;
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
rtk mvn "-Dtest=HouseFavoriteServiceImplTest" test
```

Expected: compilation fails because `incrementFavoriteScore` does not exist or assertion fails because service does not call it.

- [ ] **Step 3: Add Redis increment method**

In `HouseHotService.java`, add:

```java
public void incrementFavoriteScore(String city, Long houseId) {
    if (!StringUtils.hasText(city) || houseId == null) {
        return;
    }
    stringRedisTemplate.opsForZSet().incrementScore(hotRankKey(city), String.valueOf(houseId), FAVORITE_WEIGHT);
}
```

- [ ] **Step 4: Re-add favorite after-commit hook**

In `HouseFavoriteServiceImpl.java`, inject `HouseHotService`:

```java
private final HouseHotService houseHotService;
```

Track `favoriteChanged`:

```java
boolean favoriteChanged = false;
...
favoriteChanged = this.save(favorite);
...
favoriteChanged = this.lambdaUpdate()...update();
```

After mutation:

```java
if (favoriteChanged) {
    incrementFavoriteHotScoreAfterCommit(house.getCity(), houseId);
}
```

Add helper:

```java
private void incrementFavoriteHotScoreAfterCommit(String city, Long houseId) {
    if (!StringUtils.hasText(city) || houseId == null) {
        return;
    }
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        houseHotService.incrementFavoriteScore(city, houseId);
        return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            houseHotService.incrementFavoriteScore(city, houseId);
        }
    });
}
```

Add imports:

```java
import cn.yy.myrent.service.hot.HouseHotService;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
```

- [ ] **Step 5: Run favorite tests**

Run:

```bash
rtk mvn "-Dtest=HouseFavoriteServiceImplTest" test
```

Expected: `BUILD SUCCESS`.

---

## Task 6: Add Consult Realtime Increment

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
- Modify: `src/main/java/cn/yy/myrent/service/impl/ChatSessionServiceImpl.java`
- Test: `src/test/java/cn/yy/myrent/service/impl/ChatSessionServiceImplTest.java`

- [ ] **Step 1: Add failing consult tests**

In `ChatSessionServiceImplTest`, add:

```java
@Mock
private HouseHotService houseHotService;
```

Add import:

```java
import cn.yy.myrent.service.hot.HouseHotService;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
```

In `sendMessageShouldCreateSessionAndPushAfterCommit`, after executing synchronizations, add:

```java
verify(houseHotService).incrementConsultScore(house.getCity(), 7L);
```

In `sendMessageShouldUpdateExistingSessionAndPushAfterCommit`, after executing synchronizations, add:

```java
verify(houseHotService, never()).incrementConsultScore(anyString(), anyLong());
```

In duplicate-key recovery test, after executing synchronizations, add:

```java
verify(houseHotService, never()).incrementConsultScore(anyString(), anyLong());
```

- [ ] **Step 2: Run consult tests and verify they fail**

Run:

```bash
rtk mvn "-Dtest=ChatSessionServiceImplTest" test
```

Expected: compilation fails because `incrementConsultScore` does not exist or assertion fails because service does not call it.

- [ ] **Step 3: Add Redis increment method**

In `HouseHotService.java`, add:

```java
public void incrementConsultScore(String city, Long houseId) {
    if (!StringUtils.hasText(city) || houseId == null) {
        return;
    }
    stringRedisTemplate.opsForZSet().incrementScore(hotRankKey(city), String.valueOf(houseId), CONSULT_WEIGHT);
}
```

- [ ] **Step 4: Re-add consult after-commit hook**

In `ChatSessionServiceImpl.java`, inject:

```java
@Autowired
private HouseHotService houseHotService;
```

Add import:

```java
import cn.yy.myrent.service.hot.HouseHotService;
```

Before registering afterCommit:

```java
boolean finalCreatedNewSession = createdNewSession;
House finalHouse = house;
```

Inside `afterCommit()`, before websocket push:

```java
if (finalCreatedNewSession && finalHouse != null) {
    try {
        houseHotService.incrementConsultScore(finalHouse.getCity(), houseId);
    } catch (Exception e) {
        log.error("increment consult hot rank failed, messageId={}, houseId={}",
                chatMessage.getId(), houseId, e);
    }
}
```

- [ ] **Step 5: Run consult tests**

Run:

```bash
rtk mvn "-Dtest=ChatSessionServiceImplTest" test
```

Expected: `BUILD SUCCESS`.

---

## Task 7: Final Verification

**Files:**
- Verify all changed files.

- [ ] **Step 1: Verify old global hot keys are not reintroduced**

Run:

```bash
rtk git grep -n -e 'rank:global' -e 'house:hot:metric' -e 'house:hot:dedup' -e 'delta:favorite' -- src/main/java src/test/java
```

Expected: no matches.

- [ ] **Step 2: Run focused hot feature test suite**

Run:

```bash
rtk mvn "-Dtest=HouseHotServiceTest,HouseHotSyncServiceImplTest,HouseHotSyncConsumerTest,HouseFavoriteServiceImplTest,ChatSessionServiceImplTest,HouseKeywordSearchServiceTest" test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Review git diff**

Run:

```bash
rtk git diff --stat
rtk git diff -- src/main/java/cn/yy/myrent/service/hot/HouseHotService.java src/main/java/cn/yy/myrent/sync/house src/main/java/cn/yy/myrent/consumer src/main/java/cn/yy/myrent/config/RabbitMQConfig.java
```

Expected:

- no global hot ranking key
- request-side rebuild uses `rebuildHotRanking(city)`
- scheduled task uses `rebuildAllHotRankings()`
- hot Redis sync uses existing `HouseSyncMessage`
- realtime increments only apply to favorite activation and new consult sessions

