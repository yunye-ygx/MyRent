# House Hot Redis Rebuild Script Design

## Goal

Provide a repeatable way to rebuild the Redis hot-house cache from the current MySQL `house` data so the existing `/house/hot` chain can show all currently rentable houses again during testing.

This design does **not** change the hot ranking algorithm and does **not** introduce a second source of truth for Redis writes.

## Problem

The current frontend listing flow still depends on `/house/hot`.

`/house/hot` reads Redis hot ranking data instead of querying MySQL or Elasticsearch directly. The ranking data is rebuilt by `HouseHotService.rebuildHotRanking()`, which scans MySQL rentable houses and rewrites Redis keys. If Redis already contains stale or partial ranking data, the UI can keep showing too few houses even when MySQL has more rentable houses.

## Recommended Approach

Add a small backend trigger plus an executable script:

1. Add a temporary admin-only rebuild endpoint that calls the existing `HouseHotService.rebuildHotRanking()`.
2. Add a PowerShell script under `scripts/` that invokes that endpoint.

The script is only a trigger. The actual rebuild logic stays inside the existing Java service.

## Alternatives Considered

### 1. Direct Redis write script

Rejected.

This would duplicate the ranking logic outside the application, increase drift risk, and make later debugging harder.

### 2. Manually clear Redis and rely on lazy rebuild

Rejected.

This depends on the next `/house/hot` request to repopulate the cache and gives weaker operational feedback.

### 3. Trigger existing rebuild logic from application code

Chosen.

This reuses the current source of truth and keeps ranking behavior consistent with normal runtime behavior.

## Design

### Backend

Add a temporary endpoint under `/house/hot/rebuild` or similar that:

- calls `HouseHotService.rebuildHotRanking()`
- returns success immediately after the rebuild completes
- is clearly labeled as a temporary testing/admin capability

Minimal response is enough. Returning the current rentable-house count is optional but useful.

### Script

Add a PowerShell script in `scripts/` that:

- targets the local backend base URL
- calls the rebuild endpoint
- prints a clear success or failure message

The script should avoid embedding business logic. It should only invoke the backend trigger.

### Safety

- Do not modify `/house/hot` query semantics.
- Do not write Redis keys from the script directly.
- Keep the rebuild idempotent by relying on the existing service, which already rewrites the ranking keys.

## Data Flow

1. User runs the script.
2. Script calls the temporary rebuild endpoint.
3. Endpoint invokes `HouseHotService.rebuildHotRanking()`.
4. Service scans MySQL rentable houses and rewrites Redis hot ranking and snapshot keys.
5. Existing `/house/hot` requests start reading the refreshed Redis data.

## Testing

### Backend

Add a focused test for the new trigger endpoint, mocking the service call if needed.

### Script

Manual verification is sufficient:

1. Ensure backend is running.
2. Run the script.
3. Confirm success output.
4. Confirm `/house/hot` returns more houses or the Redis rebuild log appears.

## Scope

In scope:

- temporary rebuild endpoint
- trigger script
- minimal tests for the endpoint

Out of scope:

- switching frontend default flow away from `/house/hot`
- redesigning hot ranking
- direct Redis synchronization scripts
- permanent admin tooling
