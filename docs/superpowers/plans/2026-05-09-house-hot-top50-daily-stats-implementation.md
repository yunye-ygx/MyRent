# House Hot Top50 Daily Stats Implementation Plan

> **For agentic workers:** This is a retroactive implementation plan. The implementation has already been completed; checklist items are marked as done to document the executed sequence.

**Goal:** Optimize hot houses by limiting Redis to Top200 candidates, limiting public reads to Top50, and moving recent behavior aggregation to a daily stats table.

**Architecture:** Keep MySQL as the source of truth and Redis as a derived city-level ranking cache. Behavior writes update `house_hot_daily_stats`; hot rebuild reads recent counts from this stats table plus long-term total favorites from `house_favorite`. Scheduled rebuild remains the reconciliation path for Redis drift and rolling 7-day windows.

**Tech Stack:** Spring Boot, MyBatis-Plus, MySQL, Redis `StringRedisTemplate`, JUnit 5, Mockito, Maven.

---

## File Map

- Created `src/main/java/cn/yy/myrent/entity/HouseHotDailyStats.java`
  - Entity mapping for `house_hot_daily_stats`.
- Created `src/main/java/cn/yy/myrent/mapper/HouseHotDailyStatsMapper.java`
  - Mapper for stats upsert and recent city aggregation.
- Created `src/main/resources/mapper/HouseHotDailyStatsMapper.xml`
  - SQL for `upsertDelta` and `selectRecentAggRowsByCity`.
- Created `src/main/java/cn/yy/myrent/service/hot/HouseHotDailyStatsAggRow.java`
  - Recent behavior aggregation projection.
- Created `src/main/java/cn/yy/myrent/service/hot/HouseHotDailyStatsService.java`
  - Small service for browse/favorite/consult stats deltas.
- Modified `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
  - Top200 candidate limit, Top50 query boundary, daily stats aggregation, total favorite quality score.
- Modified `src/main/java/cn/yy/myrent/mapper/HouseFavoriteMapper.java`
  - Added total favorite aggregation by house IDs.
- Modified `src/main/resources/mapper/HouseFavoriteMapper.xml`
  - Added SQL for total active favorites.
- Modified `src/main/java/cn/yy/myrent/service/impl/HouseHistoryServiceImpl.java`
  - Increment browse stats only on first daily browse insert.
- Modified `src/main/java/cn/yy/myrent/service/impl/HouseFavoriteServiceImpl.java`
  - Increment favorite stats on activation and decrement on cancel.
- Modified `src/main/java/cn/yy/myrent/service/impl/ChatSessionServiceImpl.java`
  - Increment consult stats only for newly created chat sessions.
- Created `sql/rent-schema/house_hot_daily_stats.sql`
  - Standalone table schema.
- Modified `sql/rent-schema/rent-schema-all.sql`
  - Added the daily stats table to combined schema.
- Modified tests:
  - `src/test/java/cn/yy/myrent/service/hot/HouseHotServiceTest.java`
  - `src/test/java/cn/yy/myrent/service/hot/HouseHotDailyStatsServiceTest.java`
  - `src/test/java/cn/yy/myrent/service/impl/HouseHistoryServiceImplTest.java`
  - `src/test/java/cn/yy/myrent/service/impl/HouseFavoriteServiceImplTest.java`
  - `src/test/java/cn/yy/myrent/service/impl/ChatSessionServiceImplTest.java`
  - `src/test/java/cn/yy/myrent/controller/AiRecommendControllerWebMvcTest.java`

---

## Task 1: Limit Redis Candidate Pool and Query Boundary

**Files:**

- Modify `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
- Modify `src/test/java/cn/yy/myrent/service/hot/HouseHotServiceTest.java`

- [x] **Step 1: Add Hot200 and Hot50 constants**

Implemented in `HouseHotService`:

```java
private static final int HOT_CANDIDATE_LIMIT = 200;
private static final int HOT_DISPLAY_LIMIT = 50;
```

- [x] **Step 2: Sort and limit rebuild candidates**

Implemented rebuild candidate sorting:

```java
List<HouseHotCandidate> candidates = availableHouses.stream()
        .filter(house -> house.getId() != null)
        .map(house -> buildHouseHotCandidate(house, favoriteAggMap, statsAggMap))
        .sorted((left, right) -> {
            int scoreCompare = Double.compare(right.snapshot().getHotScore(), left.snapshot().getHotScore());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return Long.compare(right.house().getId(), left.house().getId());
        })
        .limit(HOT_CANDIDATE_LIMIT)
        .collect(Collectors.toList());
```

- [x] **Step 3: Clip public query to Top50**

Implemented query boundary:

```java
long start = (long) pageIndex * pageSize;
if (start >= HOT_DISPLAY_LIMIT) {
    return Collections.emptyList();
}
long end = Math.min(start + pageSize - 1, HOT_DISPLAY_LIMIT - 1);
```

- [x] **Step 4: Add tests**

Added coverage for:

- rebuild writes only Top200 candidates
- query does not read beyond Top50

- [x] **Step 5: Verify**

Command:

```bash
rtk mvn "-Dtest=HouseHotServiceTest" test
```

Expected and observed result: `BUILD SUCCESS`.

---

## Task 2: Add Daily Stats Table and Service

**Files:**

- Create `src/main/java/cn/yy/myrent/entity/HouseHotDailyStats.java`
- Create `src/main/java/cn/yy/myrent/mapper/HouseHotDailyStatsMapper.java`
- Create `src/main/resources/mapper/HouseHotDailyStatsMapper.xml`
- Create `src/main/java/cn/yy/myrent/service/hot/HouseHotDailyStatsAggRow.java`
- Create `src/main/java/cn/yy/myrent/service/hot/HouseHotDailyStatsService.java`
- Create `src/test/java/cn/yy/myrent/service/hot/HouseHotDailyStatsServiceTest.java`

- [x] **Step 1: Add stats entity**

Created `HouseHotDailyStats` for `house_hot_daily_stats`.

- [x] **Step 2: Add mapper API**

Implemented:

```java
int upsertDelta(Long houseId,
                String city,
                LocalDate statDate,
                Long browseDelta,
                Long favoriteDelta,
                Long consultDelta);

List<HouseHotDailyStatsAggRow> selectRecentAggRowsByCity(String city, LocalDate startDate);
```

- [x] **Step 3: Add MySQL upsert SQL**

Implemented `ON DUPLICATE KEY UPDATE` with non-negative counters:

```sql
browse_count = greatest(0, browse_count + values(browse_count)),
favorite_count = greatest(0, favorite_count + values(favorite_count)),
consult_count = greatest(0, consult_count + values(consult_count))
```

- [x] **Step 4: Add service methods**

Implemented:

```java
incrementBrowse(houseId, city, statDate)
incrementFavorite(houseId, city, statDate)
decrementFavorite(houseId, city, statDate)
incrementConsult(houseId, city, statDate)
```

- [x] **Step 5: Add tests**

Covered:

- browse increment delta
- favorite increment delta
- favorite decrement delta
- invalid input no-op

- [x] **Step 6: Verify**

Command:

```bash
rtk mvn "-Dtest=HouseHotDailyStatsServiceTest" test
```

Expected and observed result: `BUILD SUCCESS`.

---

## Task 3: Switch Hot Rebuild to Daily Stats

**Files:**

- Modify `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
- Modify `src/main/java/cn/yy/myrent/mapper/HouseFavoriteMapper.java`
- Modify `src/main/resources/mapper/HouseFavoriteMapper.xml`
- Modify `src/test/java/cn/yy/myrent/service/hot/HouseHotServiceTest.java`

- [x] **Step 1: Inject `HouseHotDailyStatsMapper`**

`HouseHotService` now depends on `HouseHotDailyStatsMapper`.

- [x] **Step 2: Remove raw recent behavior dependencies from rebuild**

`HouseHotService` no longer uses `HouseHistoryMapper` or `ChatSessionMapper` for recent hot rebuild counts.

- [x] **Step 3: Read recent counts from daily stats**

Implemented:

```java
Map<Long, HouseHotDailyStatsAggRow> statsAggMap = houseHotDailyStatsMapper
        .selectRecentAggRowsByCity(city, startDate)
        .stream()
        .filter(row -> row != null && row.houseId() != null)
        .collect(Collectors.toMap(HouseHotDailyStatsAggRow::houseId, row -> row, (left, right) -> left));
```

- [x] **Step 4: Add total favorite aggregation**

Added:

```java
List<HouseFavoriteAggRow> selectFavoriteTotalAggRowsByHouseIds(List<Long> houseIds);
```

This avoids using the old recent favorite aggregation now that recent favorite comes from daily stats.

- [x] **Step 5: Implement combined score**

Implemented:

```java
return recentConsultCount * CONSULT_WEIGHT
        + recentFavoriteCount * FAVORITE_WEIGHT
        + recentBrowseCount * BROWSE_WEIGHT
        + longTermFavoriteQualityScore(totalFavoriteCount)
        + freshnessBonus;
```

- [x] **Step 6: Verify**

Command:

```bash
rtk mvn "-Dtest=HouseHotServiceTest" test
```

Expected and observed result: `BUILD SUCCESS`.

---

## Task 4: Wire Behavior Writes to Daily Stats

**Files:**

- Modify `src/main/java/cn/yy/myrent/service/impl/HouseHistoryServiceImpl.java`
- Modify `src/main/java/cn/yy/myrent/service/impl/HouseFavoriteServiceImpl.java`
- Modify `src/main/java/cn/yy/myrent/service/impl/ChatSessionServiceImpl.java`
- Modify related tests

- [x] **Step 1: Browse writes stats only on first daily insert**

Implemented in `HouseHistoryServiceImpl.recordBrowse`:

```java
if (existing == null) {
    baseMapper.insert(history);
    houseHotDailyStatsService.incrementBrowse(houseId, house.getCity(), browseDate);
}
```

- [x] **Step 2: Favorite activation writes stats after commit**

Implemented in `HouseFavoriteServiceImpl`:

```java
if (favoriteChanged) {
    incrementFavoriteSignalsAfterCommit(house.getCity(), houseId, now);
}
```

The helper increments both Redis score and daily stats after commit.

- [x] **Step 3: Unfavorite decrements original favorite day**

Implemented:

```java
LocalDateTime favoriteTime = existing.getFavoriteTime();
boolean updated = this.updateById(existing);
if (updated && favoriteTime != null) {
    houseHotDailyStatsService.decrementFavorite(houseId, house.getCity(), favoriteTime.toLocalDate());
}
```

- [x] **Step 4: New consult session writes stats after commit**

Implemented in `ChatSessionServiceImpl.afterCommit`:

```java
if (finalCreatedNewSession && finalHouse != null) {
    houseHotService.incrementConsultScore(finalHouse.getCity(), houseId);
    houseHotDailyStatsService.incrementConsult(houseId, finalHouse.getCity(), now.toLocalDate());
}
```

- [x] **Step 5: Add tests**

Covered:

- first browse increments stats
- repeat same-day browse does not increment stats
- favorite activation increments stats
- already active favorite does not increment stats
- unfavorite decrements stats using original favorite date
- new chat session increments consult stats
- existing chat session does not increment consult stats

- [x] **Step 6: Verify**

Command:

```bash
rtk mvn "-Dtest=HouseHistoryServiceImplTest,HouseFavoriteServiceImplTest,ChatSessionServiceImplTest" test
```

Expected and observed result: `BUILD SUCCESS`.

---

## Task 5: Add SQL Schema

**Files:**

- Create `sql/rent-schema/house_hot_daily_stats.sql`
- Modify `sql/rent-schema/rent-schema-all.sql`

- [x] **Step 1: Add standalone schema**

Created:

```sql
CREATE TABLE `house_hot_daily_stats` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `house_id` bigint NOT NULL COMMENT 'house id',
  `city` varchar(32) NOT NULL COMMENT 'house city',
  `stat_date` date NOT NULL COMMENT 'stat date',
  `browse_count` bigint NOT NULL DEFAULT 0 COMMENT 'daily dedup browse count',
  `favorite_count` bigint NOT NULL DEFAULT 0 COMMENT 'daily active favorite count',
  `consult_count` bigint NOT NULL DEFAULT 0 COMMENT 'daily consult count',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_house_stat_date` (`house_id`, `stat_date`),
  KEY `idx_city_stat_date` (`city`, `stat_date`),
  KEY `idx_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='house hot daily behavior stats';
```

- [x] **Step 2: Add combined schema entry**

Added the same table to `rent-schema-all.sql`.

---

## Task 6: Keep WebMVC Slice Tests Isolated

**Files:**

- Modify `src/test/java/cn/yy/myrent/controller/AiRecommendControllerWebMvcTest.java`

- [x] **Step 1: Add mock for new mapper**

Because `HouseHotDailyStatsMapper` is now a mapper bean, the WebMVC slice test needs a mock bean:

```java
@MockBean
private HouseHotDailyStatsMapper houseHotDailyStatsMapper;
```

- [x] **Step 2: Verify**

Command:

```bash
rtk mvn "-Dtest=AiRecommendControllerWebMvcTest" test
```

Expected and observed result: `BUILD SUCCESS`.

---

## Task 7: Final Verification

- [x] **Step 1: Run focused feature suite**

Command:

```bash
rtk mvn "-Dtest=HouseHotServiceTest,HouseHotDailyStatsServiceTest,HouseHistoryServiceImplTest,HouseFavoriteServiceImplTest,ChatSessionServiceImplTest,AiRecommendControllerWebMvcTest" test
```

Observed result:

```text
Tests run: 46, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- [x] **Step 2: Run full test suite**

Command:

```bash
rtk mvn test
```

Observed result:

- Hot-related tests passed.
- `AiRecommendControllerWebMvcTest` issue from the new mapper was fixed.
- Full suite still fails in `OrderTimeoutTaskConsumerTest`, which is outside this hot-house change. The failure is caused by the order-timeout test still mocking an older payment repair path and by direct unit invocation without a Spring transaction proxy.

---

## Follow-Up Notes

- If this feature is later pushed toward production scale, add a historical backfill job to initialize `house_hot_daily_stats` from existing behavior tables.
- If behavior write volume grows, move stats updates to an async event pipeline with retry.
- If Redis memory becomes tighter, evaluate a stricter Top-K promotion strategy, but keep the scheduled rebuild as the correctness path.
