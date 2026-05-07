# House Search / Smart Guide / AI Recommend Three-Layer Refactor Design

Date: 2026-05-06

## Background

The current house discovery stack exposes four user-facing capabilities that overlap in responsibility but diverge in implementation:

- `/house/search` performs keyword-oriented dual recall and lightweight scoring inside `HouseKeywordSearchService`
- `/house/list-filter` performs structured filtering and returns a sorted list without a reusable ranking abstraction
- `/house/smart-guide` performs candidate collection, budget/radius relaxation, ranking, and recommendation reasons inside one recommendation-oriented flow
- `/ai-recommend/*` performs conversation state management and eventually delegates to `houseService.smartGuide(...)`

This produces short-term delivery speed, but it creates long-term problems:

- recall logic is duplicated conceptually across keyword search and smart guide
- ranking logic is not expressed as a reusable domain service with explicit inputs and outputs
- recommendation reasons are generated too close to the score calculator instead of being exposed as structured ranking evidence
- AI recommendation depends on a recommendation-oriented black box instead of on stable recall/ranking outputs
- search page and AI assistant both need sorted results, but they do not currently share a common ranking contract

The next iteration should keep the current external routes and preserve compatibility, while restructuring the backend into three reusable layers:

1. recall layer
2. ranking layer
3. LLM support layer

This iteration intentionally does not introduce a separate orchestration layer abstraction. Existing controllers and services remain the entry points, but they should delegate to the new layers internally.

## Confirmed Product Decisions

The following decisions are fixed for this design:

1. Existing public routes remain in place:
   - `/house/search`
   - `/house/list-filter`
   - `/house/smart-guide`
   - `/ai-recommend/*`
2. Existing route semantics remain distinct. External APIs are not merged.
3. Internal implementation should be refactored so recall and ranking capabilities are reusable across search page and AI assistant flows.
4. Search page results must be sorted by the new ranking layer.
5. AI assistant results must be sorted by the new ranking layer.
6. `/house/search` and `/house/list-filter` should keep their current top-level response shape as much as possible.
7. `/house/smart-guide` keeps its recommendation semantics, including relaxed-budget / relaxed-radius behavior and `tipMessage` semantics.
8. AI recommendation text must be grounded in backend-provided ranking evidence and must not fabricate scores or recommendation reasons.
9. This iteration should cover tag-oriented preference fields already present in the project:
   - `nearSubway`
   - `privateBathroom`
   - `hasBalcony`
   - `civilWaterElectric`
   - `supportStudentDepositFree`
10. This stage is design-only. No implementation or commit is part of this spec itself.

## Goals

- Introduce a reusable recall layer that can serve keyword search, structured list filtering, smart guide, and AI recommendation.
- Introduce a reusable ranking layer that uses a common scoring framework with multiple ranking profiles.
- Preserve the distinction between search-oriented and recommendation-oriented external APIs while reusing internal capabilities.
- Expose structured ranking evidence so the LLM can explain backend truth rather than invent it.
- Keep existing route compatibility and minimize frontend breakage.
- Preserve smart guide relaxation semantics and search-page sorted results.

## Non-Goals

- No public route removal in this iteration.
- No public route merge in this iteration.
- No new external AI tool-calling workflow.
- No new database table required for the first refactor.
- No review-score, landlord-score, or house-quality-score integration in this iteration beyond clear extension points.
- No full orchestration layer extraction in this iteration.
- No frontend redesign or mandatory frontend field consumption changes in this iteration.

## Current Problems by Route

### `/house/search`

Current behavior:

- performs location recall and text recall in parallel
- merges evidence
- computes an internal total score directly in `HouseKeywordSearchService`
- returns only sorted `HouseVO` results

Problems:

- recall evidence is not exposed as a reusable contract
- score calculation is embedded in the route-specific service
- the search-specific service cannot be reused by smart guide or AI recommendation without carrying search route assumptions

### `/house/list-filter`

Current behavior:

- performs ES filter query first and DB fallback second
- returns sorted `HouseVO` results
- does not currently go through reusable recall or ranking contracts

Problems:

- list filtering has no explicit ranking evidence model
- structured preferences such as `nearSubway` are used as filters only, not as reusable ranking dimensions
- the route is not aligned with the same candidate/ranking vocabulary as other discovery paths

### `/house/smart-guide`

Current behavior:

- resolves location
- collects candidates
- applies exact stage and relaxed stage logic
- computes score and reasons
- returns recommendation items with `score` and `reasons`

Problems:

- candidate collection, ranking, relaxation semantics, and recommendation item assembly are too tightly coupled
- recommendation reasons are user-facing strings too early in the pipeline
- ranking outputs are not shaped for reuse by `/house/search`, `/house/list-filter`, or `/ai-recommend`

### `/ai-recommend/*`

Current behavior:

- performs slot extraction and stage control
- delegates to `houseService.smartGuide(...)` when search-ready
- depends on smart-guide output as a recommendation black box

Problems:

- AI recommendation cannot independently consume ranking evidence
- LLM grounding is weaker than it should be because ranking evidence is not explicitly modeled as LLM-support input
- recommendation explanation is tied to smart-guide output rather than to a generic ranking truth contract

## Core Architectural Change

The current implementation effectively behaves like this:

1. route-specific service gathers inputs
2. route-specific service performs its own recall logic
3. route-specific service performs embedded scoring logic
4. route-specific service assembles route-specific output
5. AI path depends on smart-guide as a monolithic recommendation capability

The new implementation should behave like this:

1. route-specific adapter translates request DTO into a unified recall query
2. recall layer returns candidates plus structured recall evidence
3. route-specific adapter chooses a ranking profile
4. ranking layer computes total score, score breakdown, and reason codes
5. route-specific adapter assembles the externally compatible response shape
6. AI path uses ranking outputs to build LLM support payloads
7. LLM generates natural-language explanation constrained by backend-provided ranking truth

This is the central design principle for this refactor:

- external routes stay separate
- internal truth about candidate qualification is owned by recall layer
- internal truth about ranking is owned by ranking layer
- internal truth for recommendation explanation is owned by backend LLM support payload assembly

## Layer Definitions

## 1. Recall Layer

### Responsibility

The recall layer answers one question:

- which houses are eligible to enter the candidate pool for this request

It should not perform final ranking. It should not produce user-facing recommendation wording.

### Inputs

The recall layer should accept a unified query object that can represent:

- keyword search intent
- structured filter intent
- smart guide recommendation intent
- AI recommendation search-ready intent

Recommended core fields:

- `keyword`
- `locationName`
- `city`
- `region`
- `budgetYuan`
- `budgetScope`
- `rentMode`
- `rentType`
- `nearSubway`
- `privateBathroom`
- `hasBalcony`
- `civilWaterElectric`
- `supportStudentDepositFree`
- `page`
- `size`
- `recallProfile`

### Recall Profiles

Recommended recall profiles for this iteration:

- `KEYWORD_SEARCH`
- `LIST_FILTER`
- `SMART_GUIDE`
- `AI_RECOMMEND`

These profiles exist to control recall behavior such as:

- whether keyword text recall is active
- whether structured filters are mandatory filters or soft evidence
- whether relaxed-budget / relaxed-radius stages are allowed
- candidate oversampling limits

### Outputs

The recall layer should return a `RecallResult` containing:

- `totalCandidateCountBeforePage`
- `candidates`
- `esAvailable`
- `degraded`
- optional route-neutral metadata about applied relaxation or fallback behavior

Each candidate should contain:

- base house entity or normalized house data
- `RecallEvidence`
- `matchTier`

Recommended `RecallEvidence` fields for this iteration:

- `locationMatched`
- `textMatched`
- `locationDistanceMeters`
- `locationRank`
- `textRank`
- `textScore`
- `exactConstraintMatched`
- `relaxedBudgetApplied`
- `relaxedRadiusApplied`
- `nearSubwayMatched`
- `privateBathroomMatched`
- `hasBalconyMatched`
- `civilWaterElectricMatched`
- `supportStudentDepositFreeMatched`

Recommended `matchTier` values:

- `EXACT`
- `RELAXED_BUDGET`
- `RELAXED_RADIUS`
- `RELAXED_BUDGET_AND_RADIUS`
- `TEXT_ONLY`
- `LOCATION_ONLY`
- `FILTER_ONLY`

### Route Mapping

- `/house/search` should call recall layer with `KEYWORD_SEARCH`
- `/house/list-filter` should call recall layer with `LIST_FILTER`
- `/house/smart-guide` should call recall layer with `SMART_GUIDE`
- `/ai-recommend` search-ready path should call recall layer with `AI_RECOMMEND`

### Migration Source

The first implementation should reuse logic from:

- `HouseKeywordSearchService` dual recall
- `SmartGuideCandidateCollector` exact/relaxed candidate collection
- `HouseServiceImpl.filterList(...)` structured list-filter query behavior

## 2. Ranking Layer

### Responsibility

The ranking layer answers one question:

- among the recalled candidates, which houses are most suitable for this request

It owns final ranking truth. It should output structured evidence, not only a total score.

### Why One Ranking Service with Multiple Profiles

The scoring framework should be shared, but route scenarios have different goals:

- search page needs stable, understandable, pageable sorting
- smart guide and AI recommendation need stronger top-N recommendation quality

Therefore the implementation should use:

- one ranking service
- one shared scoring model
- multiple ranking profiles with different weights and constraints

Recommended profiles for this iteration:

- `SEARCH_DEFAULT`
- `AI_RECOMMEND_DEFAULT`

`/house/search` and `/house/list-filter` should use `SEARCH_DEFAULT`.
`/house/smart-guide` and `/ai-recommend` should use `AI_RECOMMEND_DEFAULT`.

### Inputs

The ranking layer should accept:

- unified ranking query context
- `RecallResult`
- selected `RankingProfile`

Recommended context fields:

- `budgetYuan`
- `budgetScope`
- `rentMode`
- `rentType`
- `locationName`
- target coordinates if already resolved
- structured preference booleans
- optional AI-derived priority hints already available from current slots

### Outputs

The ranking layer should return `RankResult` containing ordered candidates. Each ranked item should contain:

- `house`
- `totalScore`
- `ScoreBreakdown`
- `reasonCodes`

Recommended `ScoreBreakdown` fields for this iteration:

- `recallScore`
- `textRelevanceScore`
- `locationDistanceScore`
- `budgetCloseScore`
- `rentModeMatchScore`
- `nearSubwayScore`
- `privateBathroomScore`
- `hasBalconyScore`
- `civilWaterElectricScore`
- `supportStudentDepositFreeScore`
- `relaxationPenaltyOrAdjustment`
- `freshnessScore`

Recommended `ReasonCode` values for this iteration:

- `TEXT_MATCHED`
- `LOCATION_MATCHED`
- `TEXT_AND_LOCATION_MATCHED`
- `BUDGET_CLOSE`
- `BUDGET_WITHIN_RANGE`
- `RENT_MODE_MATCH`
- `NEAR_TARGET_LOCATION`
- `NEAR_SUBWAY_MATCH`
- `PRIVATE_BATHROOM_MATCH`
- `HAS_BALCONY_MATCH`
- `CIVIL_WATER_ELECTRIC_MATCH`
- `STUDENT_DEPOSIT_FREE_MATCH`
- `RELAXED_BUDGET_CANDIDATE`
- `RELAXED_RADIUS_CANDIDATE`
- `RECENT_LISTING`

### Ranking Semantics

The ranking layer should follow these principles:

1. hard incompatibilities should be filtered in recall where possible, not hidden in ranking
2. ranking should prefer exact candidates over relaxed candidates unless relaxed candidates materially outperform in other dimensions and route semantics allow it
3. search routes should favor stable list ordering and avoid overreacting to weak preference differences
4. smart guide and AI routes should favor stronger top-N recommendation quality
5. reason codes must be derivable directly from actual scoring evidence

### Search vs AI Profile Distinction

This iteration should explicitly preserve two ranking profiles because the route goals differ even when the fields overlap:

- `SEARCH_DEFAULT`
  - more stable
  - more balanced
  - better for pageable result lists
  - lower sensitivity to inferred preference priority
- `AI_RECOMMEND_DEFAULT`
  - more top-N focused
  - higher emphasis on budget closeness and core preference fit
  - suitable for LLM explanation of why a small set of results was recommended first

### Migration Source

The first implementation should reuse logic from:

- `HouseKeywordSearchService.buildScore(...)`
- `SmartGuideScoreCalculator`
- current smart-guide recommendation reason semantics
- current list-filter route filters as structured preference evidence where applicable

## 3. LLM Support Layer

### Responsibility

The LLM support layer answers one question:

- how do we package backend ranking truth so the model can explain it naturally without fabricating facts

It does not rank houses. It does not query the database again. It does not invent reason fields.

### Scope in This Iteration

This iteration should not introduce a general orchestration layer, but it should introduce a backend service that converts ranked recommendation results into model-safe context.

Recommended name direction:

- `AiRecommendRankingPayloadBuilder`
- or `LlmRecommendationSupportService`

### Inputs

- AI conversation slots / summary context already available in `AiRecommendServiceImpl`
- ranked recommendation results from ranking layer
- top N houses selected for recommendation output

### Outputs

Recommended payload contents:

- user intent summary
- current slots summary
- list of top ranked houses
- each house's total score
- each house's score breakdown
- each house's reason codes
- optional backend-rendered short factual snippets that are safe for the model to reuse

### LLM Constraints

Prompting and payload design must enforce:

- only recommend houses that backend supplied
- only cite ranking reasons supported by `reasonCodes` or safe factual snippets
- do not invent new scores
- do not claim search facts that backend did not provide
- natural-language explanation may reorder sentence phrasing, but not ranking truth

### Route Mapping

This layer is primarily for `/ai-recommend`.

`/house/smart-guide` may optionally expose enough structured fields that future prompt assembly can also reuse it, but the current route remains a backend recommendation API rather than a direct model payload API.

## Proposed Internal Contracts

## Recall Result Contract

Recommended internal shape:

```json
{
  "candidates": [
    {
      "houseId": 101,
      "house": { "...": "normalized house fields" },
      "matchTier": "EXACT",
      "recallEvidence": {
        "locationMatched": true,
        "textMatched": true,
        "locationDistanceMeters": 620,
        "locationRank": 2,
        "textRank": 5,
        "textScore": 8.4,
        "exactConstraintMatched": true,
        "relaxedBudgetApplied": false,
        "relaxedRadiusApplied": false,
        "nearSubwayMatched": true,
        "privateBathroomMatched": true,
        "hasBalconyMatched": false,
        "civilWaterElectricMatched": true,
        "supportStudentDepositFreeMatched": false
      }
    }
  ],
  "esAvailable": true,
  "degraded": false
}
```

## Rank Result Contract

Recommended internal shape:

```json
{
  "items": [
    {
      "houseId": 101,
      "totalScore": 8920.5,
      "scoreBreakdown": {
        "recallScore": 1000,
        "textRelevanceScore": 80,
        "locationDistanceScore": 76.2,
        "budgetCloseScore": 87.5,
        "rentModeMatchScore": 100,
        "nearSubwayScore": 100,
        "privateBathroomScore": 100,
        "hasBalconyScore": 0,
        "civilWaterElectricScore": 100,
        "supportStudentDepositFreeScore": 0,
        "relaxationPenaltyOrAdjustment": 0,
        "freshnessScore": 60
      },
      "reasonCodes": [
        "TEXT_AND_LOCATION_MATCHED",
        "BUDGET_CLOSE",
        "RENT_MODE_MATCH",
        "NEAR_SUBWAY_MATCH",
        "PRIVATE_BATHROOM_MATCH",
        "CIVIL_WATER_ELECTRIC_MATCH"
      ]
    }
  ]
}
```

## Route-by-Route Design

## `/house/search`

### Current Contract Direction

Keep:

- route path
- main `HouseSearchResultVO` shape
- `houses` list as primary payload

Additive extension allowed:

- optional per-house ranking evidence field if needed in the VO layer
- optional route-neutral metadata if helpful for debugging or future frontend use

### Internal Flow After Refactor

1. build `RecallQuery` from `HouseKeywordSearchReqDTO`
2. call recall layer with `KEYWORD_SEARCH`
3. call ranking layer with `SEARCH_DEFAULT`
4. paginate ranked results as route semantics require
5. convert ranked items to `HouseVO`
6. preserve existing top-level result fields such as `total`, `esDown`, `fallbackSource`, `tipMessage`

### Compatibility Notes

- sort order changes are allowed because the explicit goal is to move to new ranking truth
- top-level response structure should not be broken
- additional internal ranking data should not force frontend changes in this iteration

## `/house/list-filter`

### Current Contract Direction

Keep:

- route path
- main `HouseSearchResultVO` shape

### Internal Flow After Refactor

1. build `RecallQuery` from `HouseListFilterReqDTO`
2. call recall layer with `LIST_FILTER`
3. call ranking layer with `SEARCH_DEFAULT`
4. return sorted `HouseVO` list
5. preserve existing fallback semantics where possible

### Notes

- list-filter currently treats some preference booleans as pure filters; after refactor they may still act as filters where the route requires exact behavior
- when included in eligible results, these same fields should also feed ranking evidence so search sorting remains coherent

## `/house/smart-guide`

### Current Contract Direction

Keep:

- route path
- `SmartGuideResultVO` shape
- budget relaxation semantics
- tip message semantics

Additive extension allowed:

- recommendation items may include more structured ranking evidence internally or as optional outward fields if needed

### Internal Flow After Refactor

1. build `RecallQuery` from `SmartGuideReqDTO`
2. call recall layer with `SMART_GUIDE`
3. recall layer performs exact and relaxed candidate collection semantics
4. call ranking layer with `AI_RECOMMEND_DEFAULT`
5. assemble `SmartGuideResultVO`
6. retain `relaxedBudget`, `relaxedBudgetYuan`, `matchedExpectation`, and `tipMessage`
7. map reason codes and breakdown into current outward `reasons` format while preserving compatibility

### Tip Message Rules

Current smart-guide user-facing semantics should remain:

- ES degraded messages
- relaxed-from-empty / relaxed-from-few-result messages
- exact-match success guidance

These messages should be derived from recall/ranking metadata instead of from monolithic smart-guide-only internal logic.

## `/ai-recommend/*`

### Current Contract Direction

Keep:

- route paths
- main `AiRecommendChatVO` shape
- stage and slot semantics

### Internal Flow After Refactor

1. AI conversation remains responsible for slot extraction and stage progression
2. once search-ready, build `RecallQuery` from merged slots
3. call recall layer with `AI_RECOMMEND`
4. call ranking layer with `AI_RECOMMEND_DEFAULT`
5. build LLM support payload from ranked results
6. generate assistant reply grounded in ranking truth
7. keep `SmartGuideResultVO` or compatible recommendation object in the outward response for frontend continuity

### Important Constraint

The AI route should no longer conceptually depend on smart-guide as a monolithic black box. It may still reuse smart-guide assembly utilities during migration, but the architectural source of truth becomes:

- recall result
- rank result
- LLM support payload

## Data Model and VO Direction

## New Internal Domain Objects

Recommended new internal objects for this iteration:

- `RecallQuery`
- `RecallResult`
- `RecallCandidate`
- `RecallEvidence`
- `MatchTier`
- `RecallProfile`
- `RankingQuery`
- `RankResult`
- `RankedHouse`
- `ScoreBreakdown`
- `ReasonCode`
- `RankingProfile`
- `LlmRecommendationPayload`

These objects are internal contracts and should not replace DTO/VO route models directly.

## Existing VO Compatibility Direction

### `HouseSearchResultVO`

Keep current fields:

- `total`
- `houses`
- `esDown`
- `fallbackSource`
- `tipMessage`

Potential additive extension:

- if needed later, `houses` items may carry optional ranking explanation fields through `HouseVO`
- this iteration does not require frontend to consume them

### `HouseVO`

Keep current fields intact.

Optional additive extension direction for future use:

- `matchTier`
- `reasonCodes`
- lightweight search explanation fields

These should remain optional and non-breaking.

### `SmartGuideItemVO`

Keep current fields:

- `score`
- `reasons`
- location and cost fields

Potential additive extension:

- internal mapping from structured `ScoreBreakdown` and `ReasonCode`
- optional hidden or future outward ranking detail fields if needed later

### `AiRecommendChatVO`

Keep current fields:

- `stage`
- `assistantReply`
- `slots`
- `missingSlots`
- `preview`
- `recommendation`

The `recommendation` field remains outwardly compatible, but its generation source changes to the new layered model.

## Failure Handling

The new layered structure should make failures explicit by layer.

### Recall Layer Failure Rules

1. if ES recall fails, recall layer marks degraded status and uses DB fallback where profile semantics allow it
2. degraded recall must be visible to route adapters so they can preserve route-specific `tipMessage` or fallback fields
3. recall layer must never fabricate evidence values for queries that did not run successfully

### Ranking Layer Failure Rules

1. if ranking cannot compute a specialized dimension, it should default that dimension safely rather than failing the entire route
2. if some optional evidence is missing, `ScoreBreakdown` should still be complete with safe default values
3. reason codes must only be emitted when supported by actual evidence

### LLM Support Layer Failure Rules

1. if LLM invocation fails, AI route should still return ranked recommendation data through backend-generated fallback text
2. if LLM output is malformed or ungrounded, backend should replace the reply with a safe fallback reply
3. ranking truth must remain usable even when LLM text generation fails

## Testing Strategy

### Recall Layer Tests

Add or update tests for:

1. keyword dual recall normalization into `RecallEvidence`
2. list-filter recall behavior under ES success and DB fallback
3. smart-guide exact and relaxed candidate collection expressed through `matchTier` and relaxation flags
4. tag-oriented evidence population for:
   - `nearSubway`
   - `privateBathroom`
   - `hasBalcony`
   - `civilWaterElectric`
   - `supportStudentDepositFree`
5. degraded recall metadata propagation

### Ranking Layer Tests

Add or update tests for:

1. `SEARCH_DEFAULT` ordering with balanced weights
2. `AI_RECOMMEND_DEFAULT` ordering with stronger recommendation-oriented weights
3. budget closeness scoring
4. rent-mode match scoring
5. tag-match scoring
6. relaxed candidate penalty/adjustment handling
7. reason-code generation correctness
8. stable sorting tie-break behavior

### Route Regression Tests

Add or update tests for:

1. `/house/search` still returning compatible top-level payload while using new layered internals
2. `/house/list-filter` returning sorted results under new ranking layer
3. `/house/smart-guide` preserving relaxation and tip semantics
4. `/ai-recommend` grounding recommendation replies in backend ranking outputs
5. AI fallback text when LLM generation fails

## Implementation Phasing

This design should be implemented incrementally.

### Phase 1: Recall Extraction

- introduce unified recall contracts
- adapt keyword search and smart-guide candidate collection into reusable recall services
- integrate list-filter into unified recall vocabulary
- keep existing route outputs unchanged

### Phase 2: Ranking Extraction

- introduce ranking contracts
- implement shared ranking service and two ranking profiles
- migrate `/house/search`, `/house/list-filter`, and `/house/smart-guide` onto ranking outputs
- keep existing outward VOs stable

### Phase 3: LLM Support Integration

- add LLM support payload builder for ranked recommendation results
- migrate `/ai-recommend` recommendation path away from smart-guide black-box dependence
- preserve existing chat route compatibility and response shape

## Risks and Trade-Offs

### 1. Internal Abstraction Expansion

Introducing layered contracts adds new classes and mapping code. This is intentional overhead in exchange for better separation and future maintainability.

### 2. Sorting Changes on Existing Routes

Even with compatible payload shapes, some route result ordering will change because the ranking truth is being formalized. This should be treated as an intended behavioral improvement and validated with focused regression tests.

### 3. Search vs Recommendation Profile Drift

If weights evolve independently without discipline, the two profiles may become hard to reason about. Keep one scoring framework and a limited number of profiles.

### 4. Reason-Code Explosion

If too many reason codes are introduced early, prompt grounding and frontend interpretation become noisy. Keep the first iteration focused on existing route and tag semantics.

## Success Criteria

This refactor is successful when:

- existing routes remain in place and compatible at the top level
- `/house/search`, `/house/list-filter`, `/house/smart-guide`, and `/ai-recommend` all depend on reusable recall and ranking capabilities internally
- recall outputs include structured recall evidence rather than hiding everything inside route-specific services
- ranking outputs include total score, score breakdown, and reason codes
- search page results are sorted by the new ranking layer
- smart-guide and AI recommendation results are sorted by the new ranking layer
- AI recommendation text is grounded in backend ranking evidence rather than in model invention
- smart-guide relaxation semantics and tip-message behavior remain available after refactor
