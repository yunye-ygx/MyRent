# Search Reason and AI Weighted Recommend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render lightweight ranking reasons on the search page and upgrade AI recommendation from simple preference presence to bounded weighted preferences with visible primary/secondary recommendation reasons.

**Architecture:** Extend the existing shared discovery layer instead of replacing it. Search keeps `SEARCH_DEFAULT` semantics but exposes recall/ranking evidence outward through additive `HouseVO` fields; AI recommendation keeps the current route surface but introduces a bounded 2.5 weighting model (`HIGH / MEDIUM / LOW + relaxable`) that affects ranking, reason generation, and frontend rendering, while reserving extension hooks for future scheme 3 trade-off reasoning.

**Tech Stack:** Spring Boot, Java, Spring AI, MyBatis-Plus, Vue 3, Vitest, JUnit 5, Mockito, Maven

---

## File Map

**Create:**
- `src/main/java/cn/yy/myrent/service/ai/AiPreferenceWeightLevel.java`
- `src/main/java/cn/yy/myrent/service/ai/AiWeightedPreference.java`
- `src/test/java/cn/yy/myrent/service/ai/AiWeightedPreferenceNormalizationTest.java`

**Modify:**
- `src/main/java/cn/yy/myrent/vo/HouseVO.java`
- `src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java`
- `src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseReasonCode.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRankQuery.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseScoreBreakdown.java`
- `src/main/java/cn/yy/myrent/service/discovery/HouseRankingServiceImpl.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendSlots.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendDecision.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendSummaryBuilder.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendRankingPayload.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendRankingPayloadBuilder.java`
- `src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java`
- `src/main/resources/prompts/ai-recommend/system.txt`
- `src/main/resources/prompts/ai-recommend/user-context.txt`
- `src/main/resources/prompts/ai-recommend/output-format.txt`
- `frontend/src/views/HouseListView.vue`
- `frontend/src/components/ai/AiRecommendationPanel.vue`
- `frontend/src/views/__tests__/HouseListView.spec.js`
- `src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java`
- `src/test/java/cn/yy/myrent/service/impl/HouseServiceImplListFilterTest.java`
- `src/test/java/cn/yy/myrent/service/discovery/HouseRankingServiceTest.java`
- `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`

**Keep outward route compatibility:**
- `src/main/java/cn/yy/myrent/controller/HouseController.java`
- `src/main/java/cn/yy/myrent/controller/AiRecommendController.java`

---

### Task 1: Expose Search Reasons Through `HouseVO`

**Files:**
- Modify: `src/main/java/cn/yy/myrent/vo/HouseVO.java`
- Modify: `src/main/java/cn/yy/myrent/service/search/HouseKeywordSearchService.java`
- Modify: `src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java`
- Modify: `src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/impl/HouseServiceImplListFilterTest.java`

- [ ] **Step 1: Write the failing backend tests for search reason exposure**

Add assertions in `src/test/java/cn/yy/myrent/service/search/HouseKeywordSearchServiceTest.java` that the first returned `HouseVO` now contains lightweight search reasons derived from ranking evidence.

Use assertions like:

```java
assertEquals(List.of("同时命中关键词与位置", "距目标地点约 1.2km"), result.getHouses().get(0).getSearchReasons());
```

Add a list-filter assertion in `src/test/java/cn/yy/myrent/service/impl/HouseServiceImplListFilterTest.java`:

```java
assertEquals(List.of("近地铁条件命中", "独立卫浴条件命中"), result.getHouses().get(0).getSearchReasons());
```

- [ ] **Step 2: Run the focused backend tests to verify they fail**

Run:

```powershell
mvn "-Dtest=HouseKeywordSearchServiceTest,HouseServiceImplListFilterTest" test
```

Expected:

- FAIL because `HouseVO` has no `searchReasons`
- FAIL because search/list services do not map reason fields outward

- [ ] **Step 3: Add additive search-reason fields to `HouseVO`**

Update `src/main/java/cn/yy/myrent/vo/HouseVO.java` to add:

```java
    private java.util.List<String> searchReasons;

    private java.util.List<String> searchReasonCodes;
```

Do not remove any existing fields.

- [ ] **Step 4: Map search reasons in `HouseKeywordSearchService`**

Extend `HouseKeywordSearchService` so it keeps `HouseRankedItem` long enough to build `HouseVO` with search reasons instead of immediately dropping to `House`.

Implementation direction:

- keep `rankResult.currentPageItems()`
- map each item to `HouseVO`
- derive outward search reason strings from:
  - `RECALL_LOCATION_MATCH`
  - `RECALL_TEXT_MATCH`
  - `LOCATION_DISTANCE_ADVANTAGE`
  - `NEAR_SUBWAY_MATCH`

Recommended helper signature:

```java
private HouseVO convertRankedItemToVo(HouseRankedItem rankedItem)
```

Recommended outward mapping rules:

- both `RECALL_LOCATION_MATCH` and `RECALL_TEXT_MATCH` => `同时命中关键词与位置`
- `RECALL_TEXT_MATCH` only => `关键词命中`
- `RECALL_LOCATION_MATCH` only => `位置匹配`
- `LOCATION_DISTANCE_ADVANTAGE` => `距目标地点约 Xkm`
- only include at most 2 reasons

- [ ] **Step 5: Map search reasons in `HouseServiceImpl.filterList(...)`**

When mapping ranked filter results back to `HouseVO`, add search reasons using lightweight filter language:

- `NEAR_SUBWAY_MATCH` => `近地铁条件命中`
- `PRIVATE_BATHROOM_MATCH` => `独立卫浴条件命中`
- `HAS_BALCONY_MATCH` => `阳台条件命中`
- `CIVIL_WATER_ELECTRIC_MATCH` => `民水民电条件命中`
- `SUPPORT_STUDENT_DEPOSIT_FREE_MATCH` => `学生免押条件命中`

Bound to at most 2 outward reasons.

- [ ] **Step 6: Run the focused backend tests to verify they pass**

Run:

```powershell
mvn "-Dtest=HouseKeywordSearchServiceTest,HouseServiceImplListFilterTest" test
```

Expected:

- PASS with new outward `searchReasons`

---

### Task 2: Render Search Reasons on the Search Page

**Files:**
- Modify: `frontend/src/views/HouseListView.vue`
- Modify: `frontend/src/views/__tests__/HouseListView.spec.js`

- [ ] **Step 1: Write the failing frontend tests for search reason rendering**

Add test cases in `frontend/src/views/__tests__/HouseListView.spec.js` that:

1. keyword mode renders summary copy explaining ordering
2. result cards render search reasons separately from feature tags

Mock search result payload example:

```js
fetchHouseKeywordSearch.mockResolvedValue({
  total: 17,
  tipMessage: '已按关键词匹配度和位置相关性排序',
  houses: [{
    id: 201,
    title: '豫园附近一居室',
    city: '上海',
    region: '黄浦',
    rentType: 1,
    nearSubway: true,
    hasBalcony: true,
    searchReasons: ['同时命中关键词与位置', '距目标地点约 1.2km']
  }]
})
```

Assertions:

```js
expect(wrapper.text()).toContain('已按关键词匹配度和位置相关性排序')
expect(wrapper.text()).toContain('同时命中关键词与位置')
expect(wrapper.text()).toContain('距目标地点约 1.2km')
expect(wrapper.text()).toContain('近地铁')
expect(wrapper.text()).toContain('带阳台')
```

- [ ] **Step 2: Run the frontend test to verify it fails**

Run:

```powershell
cd frontend; npm test -- HouseListView.spec.js
```

Expected:

- FAIL because `HouseListView.vue` does not render search reasons

- [ ] **Step 3: Extend `normalizeHouseRecord` to preserve search reasons**

In `frontend/src/views/HouseListView.vue`, update `normalizeHouseRecord` to include:

```js
const searchReasons = Array.isArray(item?.searchReasons)
  ? item.searchReasons.filter(Boolean).slice(0, 2)
  : []
```

and return it in the normalized object:

```js
searchReasons,
```

- [ ] **Step 4: Render page-level search ordering copy**

Update the result summary section in `frontend/src/views/HouseListView.vue` to show:

- keyword mode:
  - `已按关键词匹配度和位置相关性排序`
- filter mode:
  - `已按筛选条件匹配度排序`

Implementation direction:

```js
const orderingHintText = computed(() => {
  if (currentMode.value === 'keyword') {
    return '已按关键词匹配度和位置相关性排序'
  }
  return '已按筛选条件匹配度排序'
})
```

Render this in the summary area above the cards.

- [ ] **Step 5: Render card-level search reasons separately from feature tags**

In each result card:

- add a `search-reason-row` before existing `tag-row`
- render `house.searchReasons`
- keep existing `house.tags` as feature tags

Template direction:

```vue
<div v-if="house.searchReasons?.length" class="search-reason-row">
  <span v-for="reason in house.searchReasons" :key="reason" class="search-reason-tag">{{ reason }}</span>
</div>
<div v-if="house.tags.length" class="tag-row">
  <span v-for="tag in house.tags" :key="tag" class="info-tag">{{ tag }}</span>
</div>
```

- [ ] **Step 6: Add styles for lightweight search reason tags**

In `HouseListView.vue` scoped styles, add a lighter visual style than AI recommendation tags:

```css
.search-reason-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.search-reason-tag {
  border-radius: 999px;
  padding: 5px 10px;
  background: rgba(68, 107, 85, 0.08);
  color: #5b7466;
  font-size: 12px;
  font-weight: 600;
}
```

- [ ] **Step 7: Run the frontend test to verify it passes**

Run:

```powershell
cd frontend; npm test -- HouseListView.spec.js
```

Expected:

- PASS with search reason rendering visible

---

### Task 3: Introduce Bounded AI Preference Weight Types

**Files:**
- Create: `src/main/java/cn/yy/myrent/service/ai/AiPreferenceWeightLevel.java`
- Create: `src/main/java/cn/yy/myrent/service/ai/AiWeightedPreference.java`
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendSlots.java`
- Create: `src/test/java/cn/yy/myrent/service/ai/AiWeightedPreferenceNormalizationTest.java`

- [ ] **Step 1: Write the failing normalization tests**

Create `src/test/java/cn/yy/myrent/service/ai/AiWeightedPreferenceNormalizationTest.java` with expectations like:

```java
assertEquals(AiPreferenceWeightLevel.HIGH, normalized.get(0).getWeightLevel());
assertFalse(normalized.get(0).isRelaxable());
assertEquals(AiPreferenceWeightLevel.LOW, normalized.get(1).getWeightLevel());
assertTrue(normalized.get(1).isRelaxable());
```

Cover at least:

- explicit high preference
- low "nice to have" preference
- relaxable budget hint

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```powershell
mvn "-Dtest=AiWeightedPreferenceNormalizationTest" test
```

Expected:

- FAIL because the weight types do not exist yet

- [ ] **Step 3: Create the bounded AI weight types**

Create `src/main/java/cn/yy/myrent/service/ai/AiPreferenceWeightLevel.java`:

```java
package cn.yy.myrent.service.ai;

public enum AiPreferenceWeightLevel {
    HIGH,
    MEDIUM,
    LOW
}
```

Create `src/main/java/cn/yy/myrent/service/ai/AiWeightedPreference.java`:

```java
package cn.yy.myrent.service.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWeightedPreference {
    private String preferenceKey;
    private AiPreferenceWeightLevel weightLevel;
    private boolean relaxable;
}
```

- [ ] **Step 4: Extend `AiRecommendSlots` to carry weighted preferences and scheme-3 placeholders**

Add to `src/main/java/cn/yy/myrent/service/ai/AiRecommendSlots.java`:

```java
    @Builder.Default
    private List<AiWeightedPreference> weightedPreferences = new ArrayList<>();

    private Boolean budgetRelaxable;

    private Integer budgetRelaxLimitYuan;

    private String tradeoffReason;
```

These last two fields are extension hooks for future scheme 3 and should not drive this iteration's logic yet.

- [ ] **Step 5: Run the focused normalization test to verify it compiles**

Run:

```powershell
mvn "-Dtest=AiWeightedPreferenceNormalizationTest" test
```

Expected:

- PASS or partial PASS if the test is currently only structural

---

### Task 4: Normalize AI Natural-Language Preferences into 2.5 Weights

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendDecision.java`
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendSummaryBuilder.java`
- Modify: `src/main/resources/prompts/ai-recommend/system.txt`
- Modify: `src/main/resources/prompts/ai-recommend/user-context.txt`
- Modify: `src/main/resources/prompts/ai-recommend/output-format.txt`
- Modify: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`

- [ ] **Step 1: Write the failing AI service tests for weighted preference normalization**

Add a new test in `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java` with a user message equivalent to:

- "我更在意通勤，预算可以稍微放一点，阳台最好有"

Mock decision output so slots include:

```java
AiRecommendSlots.builder()
    .locationName("Pudong")
    .budgetYuan(4500)
    .budgetScope("RENT_ONLY")
    .rentMode("WHOLE")
    .priority("COMMUTE")
    .preferences(List.of("nearSubway", "hasBalcony"))
    .budgetRelaxable(true)
    .build()
```

Assert after chat:

- weighted preferences exist
- `nearSubway = HIGH`
- `hasBalcony = LOW`
- budget relaxable is retained

- [ ] **Step 2: Run the focused AI service test to verify it fails**

Run:

```powershell
mvn "-Dtest=AiRecommendServiceTest" test
```

Expected:

- FAIL because weighted preferences are not normalized yet

- [ ] **Step 3: Normalize weighted preferences inside `AiRecommendServiceImpl`**

Add helper methods in `AiRecommendServiceImpl` such as:

```java
private List<AiWeightedPreference> normalizeWeightedPreferences(AiRecommendSlots slots)
private AiPreferenceWeightLevel resolveWeightLevel(AiRecommendSlots slots, String preferenceKey)
```

Normalization rules for this iteration:

- if `priority = COMMUTE` and preference is `nearSubway` => `HIGH`
- if preference exists and is not strongly prioritized => `MEDIUM`
- if preference wording or prompt semantics marks it as nice-to-have => `LOW`
- if `budgetRelaxable = true`, keep that signal in slots

If direct model output for `weightedPreferences` is empty, backend normalization should still synthesize them from `priority + preferences`.

- [ ] **Step 4: Carry weighted preferences into summary and state**

Update `AiRecommendSummaryBuilder` so the summary includes weighted preference hints, for example:

```java
joiner.add("weightedPreferences=" + safeWeightedPreferences);
joiner.add("budgetRelaxable=" + safe(safeSlots.getBudgetRelaxable()));
```

Ensure `normalizeSlots(...)` and `mergeSlots(...)` preserve `weightedPreferences`, `budgetRelaxable`, `budgetRelaxLimitYuan`, and `tradeoffReason`.

- [ ] **Step 5: Update prompts so the model can express stronger/softer preference intent**

Update prompt resources under `src/main/resources/prompts/ai-recommend/` to instruct the model to distinguish:

- strong preference
- normal preference
- nice-to-have
- relaxable budget intent

Do not require the user to speak formally.

- [ ] **Step 6: Run the focused AI service tests to verify they pass**

Run:

```powershell
mvn "-Dtest=AiRecommendServiceTest" test
```

Expected:

- PASS with weighted preference normalization retained in state

---

### Task 5: Apply AI Preference Weights in the Shared Ranking Layer

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/discovery/HouseReasonCode.java`
- Modify: `src/main/java/cn/yy/myrent/service/discovery/HouseRankQuery.java`
- Modify: `src/main/java/cn/yy/myrent/service/discovery/HouseScoreBreakdown.java`
- Modify: `src/main/java/cn/yy/myrent/service/discovery/HouseRankingServiceImpl.java`
- Modify: `src/test/java/cn/yy/myrent/service/discovery/HouseRankingServiceTest.java`

- [ ] **Step 1: Write the failing ranking tests for weighted preferences**

Add tests in `src/test/java/cn/yy/myrent/service/discovery/HouseRankingServiceTest.java` proving:

1. a `HIGH` near-subway preference outranks a `LOW` balcony-only match
2. AI profile emits stronger reason codes for primary-fit matches

Example expectation:

```java
assertEquals(nearSubwayHouse.getId(), result.currentPageItems().get(0).house().getId());
assertTrue(result.currentPageItems().get(0).reasonCodes().contains(HouseReasonCode.PRIMARY_PREFERENCE_MATCH));
```

- [ ] **Step 2: Run the ranking tests to verify they fail**

Run:

```powershell
mvn "-Dtest=HouseRankingServiceTest" test
```

Expected:

- FAIL because rank query and ranking implementation do not yet understand weight levels

- [ ] **Step 3: Extend `HouseRankQuery` and `HouseScoreBreakdown` for weighted preference input**

Add to `HouseRankQuery` optional bounded AI weight inputs, for example:

```java
Map<String, Integer> preferenceWeightMap
Boolean budgetRelaxable
Integer budgetRelaxLimitYuan
```

Add to `HouseScoreBreakdown` new fields for scheme 2.5:

```java
Double primaryPreferenceScore;
Double secondaryPreferenceScore;
Double relaxationAcceptanceScore;
```

Keep them additive and non-breaking.

- [ ] **Step 4: Update ranking logic for bounded weighted preference contribution**

In `HouseRankingServiceImpl`:

- when ranking profile is `AI_RECOMMEND_DEFAULT`, apply stronger bonuses using bounded weight levels
- `HIGH` preference match > `MEDIUM` > `LOW`
- a `LOW` mismatch should not dominate ranking
- budget relaxable should soften penalty compared with strict budget mismatch

Suggested bounded policy:

- `HIGH` feature match: +70 to +90 range
- `MEDIUM` feature match: +35 to +50 range
- `LOW` feature match: +10 to +20 range

Do not implement arbitrary floating user-specific weights.

- [ ] **Step 5: Add new reason code outputs for primary/secondary fit**

Extend `HouseReasonCode` with additive AI-specific semantics:

```java
PRIMARY_PREFERENCE_MATCH,
SECONDARY_PREFERENCE_MATCH,
BUDGET_RELAXED_ACCEPTED
```

Emit these only in AI recommendation ranking profile where applicable.

- [ ] **Step 6: Run the ranking tests to verify they pass**

Run:

```powershell
mvn "-Dtest=HouseRankingServiceTest" test
```

Expected:

- PASS with stronger AI ranking differentiation

---

### Task 6: Surface Primary / Secondary / Relaxation Reasons in AI Recommendation Output

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendRankingPayload.java`
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendRankingPayloadBuilder.java`
- Modify: `src/main/java/cn/yy/myrent/service/ai/AiRecommendServiceImpl.java`
- Modify: `frontend/src/components/ai/AiRecommendationPanel.vue`
- Modify: `frontend/src/views/AiRecommendView.vue`
- Modify: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`

- [ ] **Step 1: Write the failing AI rendering tests**

Add assertions in `AiRecommendServiceTest` that recommendation items now carry clearer reason structure, either directly or via enhanced outward `reasons` ordering:

- primary reason first
- secondary reasons later
- relaxation note included when budget is relaxable / relaxed

If frontend unit tests exist for `AiRecommendView` or `AiRecommendationPanel`, add assertions such as:

```js
expect(wrapper.text()).toContain('优先满足近地铁需求')
expect(wrapper.text()).toContain('带阳台')
expect(wrapper.text()).toContain('预算已轻度放宽')
```

- [ ] **Step 2: Run the focused AI tests to verify they fail**

Run:

```powershell
mvn "-Dtest=AiRecommendServiceTest" test
cd frontend; npm test -- AiRecommendView.spec.js
```

Expected:

- FAIL because AI recommendation reasons are not yet grouped or emphasized enough

- [ ] **Step 3: Separate primary and secondary reasons in backend mapping**

Update `AiRecommendServiceImpl.mapReasons(...)` and related helpers so:

- `PRIMARY_PREFERENCE_MATCH` and dominant reasons become top-level primary text
- weak fit reasons become secondary text
- budget relaxation becomes explicit note

If outward compatibility makes new fields expensive, preserve compatibility by:

- ordering `reasons` as primary first, secondary second
- adding a `recommendationNote` or equivalent top-line summary if needed

- [ ] **Step 4: Extend `AiRecommendRankingPayload` to carry future-ready reason structure**

Add additive fields to payload types to preserve richer semantics and scheme-3 hooks, for example:

```java
private List<String> primaryReasonHighlights = new ArrayList<>();
private List<String> secondaryReasonHighlights = new ArrayList<>();
private List<String> relaxationHighlights = new ArrayList<>();
private String tradeoffSummary;
```

Populate them in `AiRecommendRankingPayloadBuilder`.

- [ ] **Step 5: Strengthen `AiRecommendationPanel.vue` rendering**

Update the panel so each card shows:

1. title + price
2. primary recommendation summary line
3. metrics
4. primary reason tags
5. secondary reason tags
6. optional relaxation note

Keep current compact layout, but add explicit visual distinction:

- primary reasons: stronger green tags
- secondary reasons: lighter neutral tags
- relaxation note: amber or warning tone

- [ ] **Step 6: Run the AI service and frontend tests to verify they pass**

Run:

```powershell
mvn "-Dtest=AiRecommendServiceTest" test
cd frontend; npm test -- AiRecommendView.spec.js
```

Expected:

- PASS with visibly stronger AI recommendation explanations

---

### Task 7: Lock Regression Coverage and Future Scheme-3 Extension Hooks

**Files:**
- Modify: `src/test/java/cn/yy/myrent/service/ai/AiRecommendServiceTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/discovery/HouseRankingServiceTest.java`
- Modify: `frontend/src/views/__tests__/HouseListView.spec.js`

- [ ] **Step 1: Add regression tests for scheme-3 placeholder fields remaining non-blocking**

Add tests asserting that:

- missing `budgetRelaxLimitYuan` does not break current recommendation
- missing `tradeoffReason` does not break current recommendation
- weighted preference list can be empty and backend still normalizes from existing fields

- [ ] **Step 2: Run the focused regression suite**

Run:

```powershell
mvn "-Dtest=HouseKeywordSearchServiceTest,HouseServiceImplListFilterTest,HouseRankingServiceTest,AiRecommendServiceTest" test
cd frontend; npm test -- HouseListView.spec.js AiRecommendView.spec.js
```

Expected:

- PASS with search reason rendering and AI weighted recommendation working together

- [ ] **Step 3: Run full verification**

Run:

```powershell
mvn test
cd frontend; npm test
```

Expected:

- PASS or known unrelated failures only; if unrelated failures exist, document them clearly before completion

## Self-Review

- Spec coverage:
  - search reason rendering is covered by Tasks 1-2
  - AI 2.5 weighting model is covered by Tasks 3-6
  - future scheme-3 extension hooks are covered by Tasks 3 and 7
  - frontend differentiation between search and AI is covered by Tasks 2 and 6
- Placeholder scan:
  - no `TODO`, `TBD`, or "implement later" instructions remain in tasks
  - all tasks include concrete files, commands, and assertions
- Type consistency:
  - `AiPreferenceWeightLevel`, `AiWeightedPreference`, `weightedPreferences`, `budgetRelaxable`, `budgetRelaxLimitYuan`, and `tradeoffReason` are named consistently across tasks
  - search reason field names remain `searchReasons` and `searchReasonCodes`

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-07-search-reason-and-ai-weighted-recommend-implementation.md`. Two execution options:

1. Subagent-Driven (recommended) - I dispatch a fresh subagent per task, review between tasks, fast iteration

2. Inline Execution - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?

