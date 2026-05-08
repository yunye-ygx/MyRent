# House Hot Optimization Design

## Background

The current hot-house feature already uses a city-level Redis ranking:

- `house:hot:rank:city:{city}` stores house IDs ordered by hot score.
- `house:hot:snapshot:city:{city}` stores score details for debugging and response enrichment.
- `house:hot:cities` records cities that currently have hot ranking cache.

The V1 design is intentionally simple: scheduled rebuilds calculate hot score from DB facts, then write Redis. This is easy to understand, but it has several practical weaknesses:

- a request for one city can trigger rebuilding all cities
- rented or delisted houses may remain in Redis until the next rebuild
- scheduled rebuild work can become heavy as data grows
- favorite and consult changes are not reflected until the next rebuild

This optimization keeps DB as the source of truth and Redis as a derived cache. It improves correctness and freshness without turning the project into a full production recommendation system.

## Goals

- Remove unavailable houses from the hot ranking soon after house status changes.
- Rebuild only the requested city when a city hot cache is missing.
- Let scheduled tasks rebuild cities one by one instead of treating all cities as one large rebuild.
- Add lightweight realtime score updates for high-value actions: favorite and consult.
- Keep scheduled rebuilds as the final correction mechanism for Redis drift.

## Non-Goals

- No personalized recommendation.
- No fully realtime ranking for every behavior.
- No production-grade outbox framework in this iteration.
- No complex diff-based rebuild.
- No click/browse realtime score update in this iteration.
- No strategy-pattern abstraction unless the code naturally needs it later.

## Target Architecture

```text
DB facts remain authoritative.

Redis hot ranking is updated by three paths:

1. city rebuild path
   - rebuildHotRanking(city)
   - recalculates a city ranking from DB

2. house status sync path
   - house becomes unavailable
   - remove houseId from hot rank and snapshot
   - reuse the existing house DB -> ES sync event pipeline

3. high-value action increment path
   - favorite success or new consult session
   - increment Redis ZSet score

Scheduled rebuild still exists.
Its job is to correct stale Redis data, handle 7-day window rolling, and recover from missed realtime updates.
```

## P1: House Status Changes Remove Unavailable Houses From Redis

### Problem

If a house is rented, delisted, or otherwise becomes unavailable, it may still exist in `house:hot:rank:city:{city}` until the next scheduled rebuild. For search-empty fallback, this is a correctness issue: the system should not recommend unavailable houses.

### Design

Do not create a separate DB -> Redis synchronization pipeline for hot houses.

Reuse the existing house synchronization pipeline:

```text
HouseCommandServiceImpl
-> HouseSyncDispatcher
-> existing core/normal dispatch strategy
-> local task or MQ compensation path
-> house.sync.exchange
-> existing ES sync consumer
-> new hot Redis sync consumer
```

The upstream house write flow should remain almost unchanged. The status change already dispatches a house sync event, so the hot-house optimization should attach to that event instead of introducing another outbox, another dispatcher, or another independent retry mechanism.

Recommended RabbitMQ shape:

```text
house.sync.exchange
    -> house.sync.queue       // existing ES consumer
    -> house.hot.sync.queue   // new Redis hot consumer
```

The new queue receives the same `HouseSyncMessage`. The Redis hot consumer reads `houseId`, queries the latest house row if needed, and removes unavailable houses from hot Redis.

The Redis hot consumer handles the event:

```text
house = selectById(houseId)
if house is missing or house.status != AVAILABLE:
    remove houseId from hot rank and snapshot
```

If the event does not reliably contain the old city, the first iteration can remove the house ID from all tracked city hot keys:

```text
cities = SMEMBERS house:hot:cities
for city in cities:
    zRem house:hot:rank:city:{city} houseId
    hDel house:hot:snapshot:city:{city} houseId
```

This is acceptable for the current project scale and avoids missing stale data when city changes.

### Common Engineering Issues

- **Reuse boundary**: reuse existing house sync dispatch, MQ, and compensation mechanisms. Only add Redis hot handling as a downstream consumer.
- **Transaction timing**: keep the same transaction guarantee as the current house sync path. Core house events already use the local task path; normal events already dispatch after commit.
- **Idempotency**: `zRem` and `hDel` are naturally idempotent. Repeated messages are safe.
- **Sync failure**: rely on the existing MQ retry/compensation behavior where available. Scheduled rebuild is the final fallback for Redis hot cache.
- **Consumer isolation**: ES sync failure should not block Redis hot sync, and Redis hot sync failure should not block ES sync. A separate queue bound to the same exchange is preferred.
- **City change**: if old city is unknown, remove from all tracked city keys.
- **Read-side safety**: `queryHotHouses` should still query DB and filter `status=1` and matching city before returning results.

### First Iteration Boundary

Only remove unavailable houses. Do not add available houses back into the hot ranking in this consumer. Available houses can enter the ranking during the next city rebuild.

Do not rebuild the existing outbox/local-task design. This iteration should extend the existing house sync pipeline rather than replacing it.

## P2: Add `rebuildHotRanking(city)`

### Problem

When a request for one city finds that Redis has no city hot cache, rebuilding all cities is unnecessarily heavy.

### Design

Extract the core rebuild ability into a city-scoped method:

```java
public void rebuildHotRanking(String city)
```

This method:

- validates `city`
- queries rentable houses only in that city
- calculates recent favorite, browse, and consult counts for those house IDs
- deletes only this city's `rank` and `snapshot`
- writes only this city's `rank` and `snapshot`
- updates `house:hot:cities`
- if the city has no rentable houses, clears this city's hot keys

Request-side cache miss flow:

```text
hasHotRankingCache(city) == false
-> rebuildHotRanking(city)
-> queryHotHouses(city, pageIndex, pageSize)
```

### Common Engineering Issues

- **Empty city**: return empty results and do not rebuild when city is blank.
- **No rentable houses**: clear this city key to avoid stale recommendations.
- **Cache miss concurrency**: multiple requests may rebuild the same city at the same time. First iteration may accept this. Later add a Redis lock if needed.
- **City naming**: keep city values consistent with the `house.city` field.
- **Paging**: a later page being empty is not the same as cache missing. Cache existence should still be checked with `zCard`.

### First Iteration Boundary

Do not introduce a strategy pattern. A direct city-scoped method is easier to read and easier to explain.

## P3: Scheduled Task Rebuilds Cities One By One

### Problem

The scheduled task currently calls the global rebuild flow. If one city rebuild is slow or fails, it affects the whole refresh. It also makes the rebuild scope harder to reason about.

### Design

Keep a wrapper method for scheduled refresh:

```java
public void rebuildAllHotRankings()
```

Its job is only orchestration:

```text
cities = query cities that should have hot ranking
for city in cities:
    try:
        rebuildHotRanking(city)
    catch exception:
        log city failure and continue
```

The scheduled task calls:

```java
houseHotService.rebuildAllHotRankings();
```

The city list should come from DB, preferably distinct cities from currently rentable houses. This avoids relying on Redis as the source of city truth.

### Common Engineering Issues

- **Single city failure**: log and continue with the next city.
- **City disappeared**: if a city used to have hot cache but now has no rentable houses, clear its old keys.
- **Task and request overlap**: a request may rebuild a city while the scheduled task also rebuilds it. First iteration can accept this. Later add city-level locking.
- **Observability**: log city, candidate count, and rebuild result.

### First Iteration Boundary

No batching or rate limiting yet. If city count grows later, the scheduled task can rebuild cities in batches.

## P4: Favorite And Consult Realtime Increment

### Problem

Scheduled rebuild makes hot score eventually correct, but high-value actions are not reflected immediately.

### Design

Only high-value, lower-frequency actions update Redis in realtime:

- favorite activation: `ZINCRBY +3`
- new consult session: `ZINCRBY +5`

Browse/click remains scheduled-statistics-only because it is more frequent and noisy.

Realtime increment should happen after the business transaction commits:

```text
favorite relation changes from inactive/missing to active
-> afterCommit
-> increment city rank score

new chat session created for a house
-> afterCommit
-> increment city rank score
```

The scheduled rebuild remains responsible for recalculating the correct score from DB.

### Common Engineering Issues

- **Duplicate favorite calls**: only increment when favorite state actually changes into active.
- **Duplicate consult messages**: only increment when a new chat session is created, not for every message.
- **Redis update failure**: log and rely on scheduled rebuild to correct the score.
- **Snapshot mismatch**: first iteration may update only the ZSet score. Snapshot counts can stay as the last rebuild snapshot.
- **7-day window**: realtime increments do not expire by themselves. Scheduled rebuild corrects old scores when events leave the 7-day window.

### First Iteration Boundary

Do not implement realtime browse increments. Do not add complex compensation tables for Redis increment failures.

## Data Flow

### Request Hot Houses

```text
request city hot houses
-> check zCard house:hot:rank:city:{city}
-> if missing, rebuildHotRanking(city)
-> read Redis ZSet by score desc
-> query DB by house IDs
-> filter status=1 and matching city
-> read snapshot
-> return HouseVO list
```

### Scheduled Rebuild

```text
scheduled task
-> query DB distinct rentable cities
-> merge with tracked Redis cities if stale-city cleanup is needed
-> for each city:
       rebuildHotRanking(city)
```

### House Status Sync

```text
house status update committed
-> existing house sync dispatcher publishes HouseSyncMessage
-> house.sync.exchange routes to ES sync queue and hot sync queue
-> hot Redis consumer receives the same house sync message
-> if house is unavailable:
       remove houseId from city rank and snapshot
```

### Realtime Favorite / Consult Increment

```text
favorite or new consult committed
-> increment city ZSet score
-> scheduled rebuild later recalculates exact score
```

## Redis Keys

Current valid hot keys:

- `house:hot:rank:city:{city}`: ZSet, member is `houseId`, score is hot score.
- `house:hot:snapshot:city:{city}`: Hash, field is `houseId`, value is score detail JSON.
- `house:hot:cities`: Set, tracked cities that have or had hot cache.

No global hot ranking key should be introduced.

## Acceptance Criteria

- When a city hot cache is missing, only that city is rebuilt.
- Scheduled refresh rebuilds cities one by one and logs city-level failures without stopping the whole task.
- If a house becomes unavailable, the existing house sync event pipeline can drive Redis hot rank/snapshot removal.
- Repeated status-sync messages are safe.
- The Redis hot sync implementation reuses the existing house sync dispatch/MQ compensation model instead of introducing a separate outbox pipeline.
- Favorite activation and new consult session can increment the city ZSet score after commit.
- Hot query still filters DB status and city before returning results.
- The implementation does not reintroduce global hot keys.

## Interview Explanation

This design can be explained as:

```text
I treat Redis hot ranking as a derived cache, not the source of truth.
The city rebuild path guarantees correctness from DB.
The status-sync path prevents unavailable houses from staying in recommendation results.
The realtime increment path improves freshness for high-value actions.
The scheduled task remains as final calibration for stale cache, missed messages, and the rolling 7-day window.
```
