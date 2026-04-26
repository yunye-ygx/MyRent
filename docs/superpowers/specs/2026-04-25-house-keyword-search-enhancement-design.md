# House Keyword Search Enhancement Design

Date: 2026-04-25

## Background

The current main house search flow is still location-centric:

- `POST /house/nearby` accepts `latitude/longitude` or `locationName`
- the backend resolves location names through `location_dict`
- Elasticsearch is currently used for nearby geo search
- text search exists only in the lightweight `POST /house/suggest` endpoint

This means the current main search does not support a true keyword search experience where one input can simultaneously trigger:

1. location understanding
2. text retrieval
3. merged ranking

The desired enhancement is to add a new keyword-oriented search interface instead of changing the semantic meaning of the existing nearby endpoint.

The confirmed product direction is:

- create a new search endpoint
- keep `POST /house/nearby` unchanged
- run location recall and text recall in parallel
- deduplicate by `houseId`
- use Elasticsearch only for fast recall
- use MySQL as the final source of truth
- do not implement hot-house fallback in this iteration
- keep suggest enhancement out of scope for this task

## Goals

- Add a new keyword search API dedicated to search-box input.
- Support concurrent recall from:
  - location parsing + nearby recall
  - text search recall
- Merge and deduplicate candidates by `houseId`.
- Re-check candidate validity from MySQL before final ranking and response.
- Rank final results with a location-first strategy.
- Reuse the current `HouseSearchResultVO` response contract so frontend integration cost stays low.

## Non-Goals

- No change to the semantic meaning of `POST /house/nearby`.
- No change to the current suggest flow beyond keeping it compatible.
- No hot-house fallback or recommendation fallback when the new search returns empty.
- No frontend exposure of internal score details in this iteration.
- No configurable radius from the frontend in this iteration.
- No redesign of the full house list filter flow.

## Confirmed Product Decisions

### API Boundary

- The enhancement must use a new endpoint instead of reusing `POST /house/nearby`.
- The new endpoint is a keyword-search flow, not a nearby-search flow.

### Recall Strategy

- Recall must run in two parallel paths:
  - location recall
  - text recall
- Location recall uses `location_dict` parsing first.
- If location parsing succeeds, the system performs nearby geo recall around the resolved point.
- If location parsing fails, the location path is treated as empty rather than as an error.

### Nearby Radius

- The new keyword search uses a fixed default radius of `10km`.
- Radius is not exposed to the frontend in this version.

### Ranking Strategy

- Final ranking is location-first.
- Location hit receives the primary ranking advantage.
- Text hit still contributes to ranking.
- Dual-hit candidates should rank better than comparable single-hit candidates.
- Detailed score values remain an internal implementation detail.

### Truth Source

- Elasticsearch is only a fast recall layer.
- MySQL is the final source of truth for returned houses.
- Candidates recalled from Elasticsearch must be revalidated against MySQL before final ranking and return.

### Oversampling

- If the frontend requests `size = N`, each recall path should oversample with `N * 3`.
- This compensates for deduplication and MySQL filtering.

### Response Contract

- The new API should still return `HouseSearchResultVO`.
- Internal scoring evidence should not be exposed in the response in this version.

## Recommended Architecture

The recommended implementation is a dedicated keyword-search service instead of continuing to expand `HouseServiceImpl`.

Recommended shape:

- controller exposes the new endpoint
- service contract delegates to a dedicated keyword-search capability
- a new service handles:
  - parallel recall
  - candidate merge
  - MySQL validation
  - final ranking
  - response assembly

This matches the project's existing pattern used by smart-guide:

1. Elasticsearch performs fast candidate recall
2. MySQL rechecks truth and availability
3. backend performs final ranking on the validated set

## API Design

## Endpoint

- `POST /house/search`

## Request DTO

Recommended request DTO fields:

- `keyword`
- `page`
- `size`

Recommended semantics:

- `keyword` is required and trimmed
- `page` defaults to `1`
- `size` defaults to `10`
- `size` should be capped to a safe upper bound such as `50`

No location coordinates, explicit radius, or score-debug switches are required in this version.

## Response DTO

Reuse:

- `HouseSearchResultVO`

Meaning in this flow:

- `houses`: final ranked houses after MySQL validation
- `esDown`: whether the dual-recall path was degraded by Elasticsearch errors
- `fallbackSource`: optional keyword-search source marker if needed
- `tipMessage`: optional message for partial degradation or empty result guidance

## End-to-End Search Flow

The new search flow should execute in this order:

1. validate and normalize the request
2. launch location recall and text recall in parallel
3. oversample each path with `size * 3`
4. merge candidate evidence by `houseId`
5. query MySQL with candidate ids
6. filter out houses that are no longer rentable
7. rank only the MySQL-validated houses
8. paginate or slice as required by the endpoint semantics
9. convert to `HouseVO`
10. return `HouseSearchResultVO`

## Recall Path Design

## 1. Location Recall

Location recall should work like this:

1. normalize the input keyword
2. try `location_dict` resolution through the existing location resolver
3. if no location match exists, return an empty location candidate set
4. if a location match exists, perform Elasticsearch nearby recall around the resolved point with radius `10km`
5. collect candidate evidence for each recalled `houseId`

Expected evidence for the location path:

- `houseId`
- `locationMatched = true`
- optional proximity evidence such as distance or location-rank

The location path does not return final houses directly. It only contributes candidates and recall evidence.

## 2. Text Recall

Text recall should:

1. query Elasticsearch using title or keyword fields
2. return up to `size * 3` candidate ids
3. collect text-hit evidence for each candidate

Expected evidence for the text path:

- `houseId`
- `textMatched = true`
- optional text-quality evidence such as ES score or recall rank

As with the location path, text recall contributes candidates and evidence, not final truth.

## Candidate Evidence Model

The candidate stage should preserve evidence rather than premature scoring.

Recommended internal evidence fields:

- `houseId`
- `locationMatched`
- `textMatched`
- optional `locationDistanceMeters`
- optional `locationRank`
- optional `textRank`
- optional `textEsScore`

This keeps the model simple:

- recall phase stores proof of how the house was found
- ranking phase derives the final score from that evidence

## Merge and Deduplication

The candidate merge stage should use `houseId` as the deduplication key.

Behavior:

- if a house appears in only one path, preserve that path's evidence
- if a house appears in both paths, merge evidence into one candidate
- do not lose either hit marker during merge

The merged candidate set is still not final truth. It is only a deduplicated candidate pool.

## MySQL Truth Validation

After candidate merge, the system must query MySQL by candidate `houseId`.

Purpose:

- filter out stale Elasticsearch results
- remove houses that have already been taken down or changed status
- use MySQL as the authoritative source of house fields

Required behavior:

- only houses with rentable status should remain
- any candidate missing from MySQL or no longer rentable is discarded
- ranking happens only after this filtering step

This is stricter and cleaner than ranking before the truth check because invalid candidates never enter the final ranking set.

## Final Ranking Design

Final ranking should happen after MySQL validation.

Ranking principles:

- location-first priority
- text-hit contribution
- dual-hit bonus
- optional fine-grained ordering from location proximity or text quality

Stable tie-break order:

1. final score descending
2. `createTime` descending
3. `id` descending

Detailed formula is intentionally left as an implementation detail so the team can tune it without changing the external contract.

## Pagination Semantics

Because each recall path oversamples with `size * 3`, the system should have enough validated candidates for the requested page in normal cases.

Recommended pagination semantics for the first version:

- apply oversampling per recall path based on requested page size
- build the merged candidate pool
- validate through MySQL
- rank the validated pool
- return the requested page slice from the ranked result

The design target for this iteration is explicit `page` and `size` support rather than a first-page-only shortcut.

## Error Handling and Degradation

This feature does not include hot-house fallback, but it should still degrade gracefully.

### Location Parsing Failure

- not treated as an exception
- location path becomes empty
- text path continues normally

### Single-Path Elasticsearch Failure

- if location recall fails, continue with text recall only
- if text recall fails, continue with location recall only
- set degradation indicators in logs and response metadata as appropriate

### Double Elasticsearch Failure

- if both recall paths fail, return an empty result
- do not switch to hot-house fallback in this iteration

### MySQL Validation Outcome

- if Elasticsearch produced candidates but MySQL filters them all out, return an empty result
- this is a normal empty-result case, not a server error

## Response Metadata Guidance

`HouseSearchResultVO` already contains `esDown`, `fallbackSource`, and `tipMessage`.

Recommended semantics in this keyword-search flow:

- `esDown = false`
  - both recall paths completed normally
- `esDown = true`
  - one or more Elasticsearch recall paths failed and the result is degraded

`fallbackSource` can remain optional. If used, recommended values should be keyword-search-specific instead of reusing hot-house values.

Examples:

- `KEYWORD_SEARCH`
- `KEYWORD_SEARCH_DEGRADED`

`tipMessage` should be used sparingly:

- optional degradation explanation when one path failed
- optional empty-state guidance when no result remains after MySQL validation

## Backend Implementation Shape

Recommended additions or modifications:

- create a new keyword-search request DTO
- add a new controller endpoint under `HouseController`
- add a new service contract for keyword search
- create a dedicated keyword-search service or helper service
- reuse `LocationResolveService` for location parsing
- reuse Elasticsearch operations for both recall paths
- reuse MySQL batch house loading for final truth validation

Recommended responsibility split:

- controller:
  - request validation
  - response wrapping
- keyword-search service:
  - orchestration of the full search flow
- helper methods:
  - location recall
  - text recall
  - candidate merge
  - MySQL validation
  - final ranking
  - response conversion

## Frontend Impact

The frontend impact should be intentionally small in this iteration.

Recommended change:

- add a new API helper that calls the new keyword-search endpoint
- update the main search-box flow to use the new endpoint for result retrieval
- keep the existing suggest flow unchanged for now

Because the response still uses `HouseSearchResultVO`, existing list rendering can remain largely unchanged.

## Testing Strategy

## Controller Tests

Add tests for:

- request validation for blank keyword
- successful response wrapping with `HouseSearchResultVO`
- correct endpoint contract and JSON shape

## Service Tests

Add focused tests for these cases:

1. location path hits, text path empty
2. text path hits, location path empty
3. both paths hit different houses
4. both paths hit the same house and merge by `houseId`
5. both paths hit, but MySQL filters some houses out
6. both paths hit, but MySQL filters all houses out
7. location path fails while text path succeeds
8. text path fails while location path succeeds
9. both paths fail and return empty
10. oversampling uses `size * 3`
11. final ranking preserves location-first ordering
12. dual-hit candidates rank ahead of comparable single-hit candidates
13. same-score candidates break ties by `createTime desc, id desc`

## Regression Tests

The new work must not change behavior of:

- `POST /house/nearby`
- `POST /house/suggest`
- `POST /house/list-filter`
- hot-house flow

## Risks and Trade-Offs

### 1. Service Boundary Drift

If this feature is implemented directly inside `HouseServiceImpl`, that class will become even harder to reason about. A dedicated keyword-search service is the safer boundary.

### 2. Elasticsearch and MySQL Divergence

This design explicitly accepts that Elasticsearch can be stale. That is why MySQL validation is mandatory before final ranking.

### 3. Oversampling Cost

Using `size * 3` increases Elasticsearch recall volume, but it is justified because:

- deduplication removes overlap
- MySQL truth filtering removes stale houses
- final page quality is more important than minimal prefilter volume for this feature

### 4. Page-Depth Quality

If later pages become a major use case, candidate-pool size may need tuning beyond the initial `size * 3` policy. The first version should still keep page semantics explicit and testable.

## Recommended Implementation Order

1. add request DTO and controller endpoint for the new keyword search API
2. create the dedicated keyword-search service shell
3. implement location recall and text recall in parallel
4. add candidate merge and evidence preservation
5. add MySQL truth validation
6. add final ranking and response assembly
7. wire frontend main search to the new endpoint
8. add focused backend and frontend regression tests

## Success Criteria

The enhancement is successful when:

- the main search box can call a new keyword-search endpoint
- the backend runs location recall and text recall in parallel
- candidates are deduplicated by `houseId`
- recalled candidates are revalidated against MySQL before return
- unavailable houses in MySQL do not appear in results even if Elasticsearch still has stale documents
- final ordering is location-first
- dual-hit results rank better than comparable single-hit results
- existing nearby search and suggest flows remain available and semantically unchanged
