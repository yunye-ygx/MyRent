# House Hot V1 Design

## Problem Statement

The project needs a simple, explainable hot-house mechanism that serves two product entry points:

1. homepage default featured houses
2. fallback recommendations when a city search returns no results

The current goal is not to build a full recommendation system. The goal is to build a city-level, rentable-house ranking that can be implemented with the existing schema and a moderate amount of Redis support.

## Product Definition

`hot houses` in V1 means:

- city-level ranking
- only houses that are currently rentable
- ranking driven by recent activity
- same ranking reused by homepage and search-empty fallback

This is a default recommendation list, not a national hot-search list and not a personalized recommendation list.

## Goals

- avoid empty results for city-based search
- provide a stable homepage featured list
- keep the design explainable and easy to debug
- reuse existing tables as much as possible
- keep V1 simple enough to implement without a heavy event pipeline

## Non-Goals

- personalized recommendation
- global hot ranking across cities
- strong realtime ranking for all behaviors
- top-100 only competitive ranking
- complex compensation and distributed consistency framework in V1

## Candidate Scope

Only houses that satisfy all rules below can participate in the hot ranking:

- `house.city = target city`
- `house.status = 1`

For V1, target city resolution is:

1. use the city explicitly selected or searched by the user
2. if absent, use the user's location city
3. if still absent, frontend should require city selection instead of returning a national hot list

## Signal Sources

V1 reuses existing tables instead of introducing duplicated behavior fact tables.

### Browse Signal

Source table:

- `house_history`

Relevant fields:

- `house_id`
- `browse_date`
- `last_browse_time`

Meaning:

- each row is a deduplicated `(user_id, house_id, browse_date)` browse fact
- repeated views by the same user for the same house on the same day do not create additional rows

Metric used by hot ranking:

- `browse_7d`: count of rows for a house where `browse_date` is within the last 7 days

This is closer to a daily deduplicated browse UV signal than a raw page-view count.

### Favorite Signal

Source table:

- `house_favorite`

Relevant fields:

- `house_id`
- `status`
- `favorite_time`
- `cancel_time`

Meaning:

- this table is the fact source for favorite relationships
- it already stores current active favorite state and last favorite time

Metrics used by hot ranking:

- `favorite_7d`: count of active favorite rows where `favorite_time` is within the last 7 days
- `favorite_total`: optional auxiliary count of active favorites

### Consult Signal

Source table:

- `chat_session`

Relevant fields:

- `house_id`
- `create_time`

Meaning:

- a newly created chat session for a house is treated as one valid consult entry
- V1 does not count chat message volume as consult score

Metric used by hot ranking:

- `consult_7d`: count of sessions for a house where `create_time` is within the last 7 days

## Score Definition

V1 hot score is a weighted score based on recent activity plus a small freshness bonus:

```text
hot_score = consult_7d * 5
          + favorite_7d * 3
          + browse_7d * 1
          + freshness_bonus
```

Rationale:

- consult has the highest intent and should carry the largest weight
- favorite is meaningful but weaker than consult
- browse is useful but noisy, so it stays lightweight

## Freshness Bonus

V1 includes a short-term cold-start bonus for new houses.

Suggested rule:

- created within 0 to 3 days: `+8`
- created within 4 to 7 days: `+4`
- older than 7 days: `+0`

This bonus is temporary. It exists only to help new rentable houses surface before they accumulate real interactions.

## Redis Ranking Model

V1 stores all rentable candidate houses for a city in Redis, not only top 10 or top 100.

Recommended key model:

- `house:hot:rank:city:{city}`

Data structure:

- Redis ZSet
- member: `houseId`
- score: `hot_score`

Important rule:

- Redis stores the city's candidate ranking result
- homepage top 10 and search-fallback top 10 are read views on this full city ranking
- V1 does not maintain a top-K-only competitive structure

## Read Paths

### Homepage

- resolve current city
- read `house:hot:rank:city:{city}`
- fetch top N house ids
- query `house` details and assemble response

### Search Empty Fallback

- execute city search
- if result count is zero, resolve current city
- read `house:hot:rank:city:{city}`
- return top N rentable houses with a fallback tip message

Both entry points must reuse the same city ranking instead of maintaining separate hot-house logic.

## Update Strategy

V1 uses a mixed strategy: delayed processing for low-value high-frequency behavior and realtime increment for high-value lower-frequency behavior.

### Browse

- persist browse behavior through existing `house_history`
- do not update Redis hot ranking in realtime
- let scheduled rebuild recalculated browse contribution

Reason:

- browse is the noisiest and highest-frequency signal
- delaying browse impact reduces ranking volatility and write pressure

### Favorite

- update `house_favorite` first
- after favorite write succeeds, increment Redis hot score in realtime

Suggested realtime increment:

- favorite success: `+3`

For V1, unfavorite does not need mandatory immediate score subtraction. Scheduled rebuild will reconcile the score.

### Consult

- create `chat_session` first
- after new session creation succeeds, increment Redis hot score in realtime

Suggested realtime increment:

- new consult session success: `+5`

Only new session creation counts as a consult entry. Ongoing messages inside an existing session do not repeatedly increase consult score in V1.

## Scheduled Rebuild and Reconciliation

V1 keeps a scheduled rebuild task as the final source of correctness.

Responsibilities:

- recalculate `browse_7d`, `favorite_7d`, `consult_7d`
- recalculate `freshness_bonus`
- rebuild the full city ZSet score for rentable houses
- remove houses that are no longer rentable
- repair any missed or failed realtime score increments

Suggested frequency:

- every 10 minutes to 30 minutes

This scheduled task is required even if realtime increments are enabled, because:

- browse does not update Redis in realtime
- 7-day windows naturally expire
- freshness bonus naturally decays
- rentable status can change
- realtime increments can fail

## Failure Handling

V1 uses eventual consistency, not strict synchronous consistency.

Rules:

- behavior facts must be committed before Redis increment
- Redis increment failure should be logged
- no heavy compensation mechanism is required in V1
- scheduled rebuild is the official reconciliation path

This means:

- if favorite or consult fact is written but Redis increment fails, ranking may be temporarily stale
- scheduled rebuild will restore the correct score later

## Existing Schema Reuse Summary

Reuse directly:

- `house`: city and rentable eligibility
- `house_history`: browse signal
- `house_favorite`: favorite signal
- `chat_session`: consult signal

Do not add in V1:

- duplicated favorite behavior table
- duplicated consult behavior table
- top-100-only competition store
- `score` column in `house`

Reason:

- hot score is a derived ranking result, not a base house attribute

## V1 Boundaries

V1 intentionally excludes:

- realtime browse score updates
- top-K-only promotion comparison logic
- global national ranking
- message-level consult scoring
- advanced anti-abuse controls
- MQ-based async hot-score pipeline
- strong transaction-style Redis compensation

## Open Upgrade Path

If later scale or realtime requirements increase, the system can evolve in this order:

1. keep city full-candidate ZSet, but move rebuild frequency and indexes to better tuned values
2. add proactive removal when house status changes
3. add a dedicated daily metric aggregation table such as `house_hot_metric_daily`
4. add async event processing for favorite and consult increments
5. only when scale truly requires it, evaluate top-K-only ranking structures

## Acceptance Criteria

- homepage can read hot houses by city
- search-empty fallback can read the same hot houses by city
- only rentable houses appear in the ranking
- browse signal is derived from `house_history`
- favorite signal is derived from `house_favorite`
- consult signal is derived from `chat_session`
- favorite and consult can increment Redis score in realtime
- scheduled rebuild can fully reconstruct the city ranking from persistent facts
- Redis ranking remains a cache/result layer, not the only source of truth
