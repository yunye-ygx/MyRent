# House Hot Top50 Daily Stats Design

> Retroactive spec: this document records the design that was implemented in the current Hot200/Hot50 + daily stats change.

## Background

The hot-house module started as a search fallback: when a city search has no direct result, return city-level hot houses instead of an empty page. During implementation, the feature became more than a simple fallback because the ranking needs to balance:

- recent demand, such as browse, favorite, and consult behavior in the last 7 days
- long-term quality, such as total active favorites
- Redis memory and query boundaries
- rebuild cost when behavior tables become large

The current implementation keeps MySQL as the source of truth and Redis as a derived ranking cache.

## Goals

- Store only a bounded candidate pool in Redis: Top200 per city.
- Expose only Top50 to the read API.
- Combine recent behavior and long-term quality in the hot score.
- Move recent behavior aggregation from raw behavior tables to a daily stats table.
- Keep behavior writes simple and synchronous with the existing business write path.
- Preserve scheduled rebuild as the final correction mechanism for Redis drift.

## Non-Goals

- No personalized recommendation.
- No global ranking across cities.
- No realtime browse increment to Redis.
- No top-K streaming competition algorithm in this iteration.
- No async behavior event pipeline or outbox for daily stats in this iteration.
- No historical backfill job in this change.

## Core Model

Hot ranking is city scoped.

Redis keys:

- `house:hot:rank:city:{city}`: ZSet, member is `houseId`, score is `hotScore`.
- `house:hot:snapshot:city:{city}`: Hash, field is `houseId`, value is score snapshot JSON.
- `house:hot:cities`: Set, cities that currently have or had hot cache.

Redis stores only Top200 candidates for each city. The public query boundary is Top50.

## Score Formula

The implemented score is:

```text
hot_score = recent_consult_count * 5
          + recent_favorite_count * 3
          + recent_browse_count * 1
          + ln(1 + total_favorite_count) * 2
          + freshness_bonus
```

Recent behavior comes from `house_hot_daily_stats` over the last 7 days.

Long-term quality currently uses only total active favorites. Total browse and total consult are intentionally excluded because they are noisier and easier to inflate over a long period.

Freshness bonus:

- created within 0 to 3 days: `+8`
- created within 4 to 7 days: `+4`
- older than 7 days: `+0`

## Daily Stats Table

Table: `house_hot_daily_stats`

Columns:

- `house_id`
- `city`
- `stat_date`
- `browse_count`
- `favorite_count`
- `consult_count`
- `create_time`
- `update_time`

Unique key:

- `(house_id, stat_date)`

Query index:

- `(city, stat_date)`

The unique key keeps one stats row per house per day. The city is stored for fast city-level aggregation during hot rebuild.

## Behavior Write Semantics

### Browse

Source behavior: `HouseHistoryServiceImpl.recordBrowse`.

Rule:

- only the first browse by the same user for the same house on the same day inserts a `house_history` row
- only that first insert increments `house_hot_daily_stats.browse_count`
- repeat same-day browse updates `last_browse_time` but does not increment daily stats

This means browse is daily deduplicated by `(user_id, house_id, browse_date)`.

### Favorite

Source behavior: `HouseFavoriteServiceImpl.favorite` and `unfavorite`.

Rule:

- missing relation -> active: `favorite_count +1`
- inactive relation -> active: `favorite_count +1`
- already active: no stats change
- active -> canceled: `favorite_count -1`, using the original `favorite_time.toLocalDate()`

Favorite activation also increments Redis ZSet score after transaction commit.

### Consult

Source behavior: `ChatSessionServiceImpl.sendMessage`.

Rule:

- new chat session: `consult_count +1`
- message in existing session: no stats change
- duplicate-key fallback that resolves to an existing session: no consult increment

New chat session also increments Redis ZSet score after transaction commit.

## Rebuild Flow

City rebuild flow:

```text
rebuildHotRanking(city)
-> query available houses in city
-> query total active favorite count for those houses
-> query house_hot_daily_stats aggregation by city and last 7 days
-> build candidates and snapshots
-> sort by hotScore desc, tie by houseId desc
-> keep Top200
-> write temp Redis ZSet and temp snapshot Hash
-> rename temp keys to live keys
```

The rebuild path no longer reads raw browse, favorite, or consult behavior tables for recent counts.

Raw table usage after this change:

- `house_favorite`: still used for total active favorite count
- `house_hot_daily_stats`: used for recent browse/favorite/consult counts

## Query Flow

```text
queryHotHouses(city, pageIndex, pageSize)
-> reject blank city
-> calculate start/end offset
-> if start >= 50, return empty list
-> clip end to 49
-> read Redis ZSet by score desc
-> query house table by IDs
-> filter status=1 and matching city
-> attach score and snapshot counts
```

The API cannot read beyond Top50 even though Redis stores Top200.

## Consistency Model

This design is eventually consistent:

- MySQL tables are authoritative.
- Redis ranking is a cache.
- Daily stats are updated during behavior writes.
- Scheduled rebuild recalculates exact Redis ranking from MySQL.
- Redis realtime increments improve freshness but scheduled rebuild corrects drift.

If Redis increment fails, the hot rank can be temporarily stale. A later rebuild repairs it.

## Interview Explanation

The feature can be explained as:

```text
I started with hot houses as a search fallback, but then found that a usable hot ranking needs both recency and stability.
So I split the design into two layers:
Redis stores a bounded Top200 candidate pool and exposes only Top50.
MySQL stores daily behavior stats so rebuild does not repeatedly aggregate raw behavior tables.
The score combines recent 7-day behavior with long-term favorite quality.
Behavior writes update daily stats, and scheduled rebuild remains the source of correction.
```

## Acceptance Criteria

- Redis stores at most Top200 hot candidates per city.
- Public hot query does not read beyond Top50.
- Recent browse/favorite/consult counts come from `house_hot_daily_stats`.
- Total favorite count comes from active rows in `house_favorite`.
- Browse stats increment only on first user-house-day browse.
- Favorite stats increment on activation and decrement on cancel.
- Consult stats increment only on new session creation.
- Scheduled rebuild can reconstruct the city hot ranking from DB state.
- Focused tests for hot service, daily stats service, browse, favorite, consult, and WebMVC slice pass.
