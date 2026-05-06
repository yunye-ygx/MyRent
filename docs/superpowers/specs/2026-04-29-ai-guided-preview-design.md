# AI Guided Preview Design

Date: 2026-04-29

## Background

The current AI rental assistant already supports a minimum end-to-end loop:

- the frontend opens a dedicated AI recommendation page
- backend stores conversation state in Redis
- the model extracts slots and writes a reply
- backend decides whether the turn becomes `ASK`, `ADVISE`, or `SEARCH`
- backend calls the existing `smartGuide` recommendation flow when required slots are present

This version is functional, but the user experience is still too form-like:

- the conversation tends to ask for `budget + location + rentMode` too directly
- users do not get guided through tradeoffs before formal search
- real listing data only appears after hard search readiness
- the product feels like slot collection instead of assisted decision-making

The next iteration should introduce a guided preview stage that is grounded in real candidate listings, while keeping backend control and reusing the existing search stack.

## Confirmed Product Decisions

The following decisions are fixed for this iteration:

1. Scope is limited to AI assistant conversation flow redesign.
2. This spec does not include new house table fields or tag system expansion.
3. Preview must be based on real candidate listings, not on generic area knowledge alone.
4. Preview should reuse the existing `smartGuide` retrieval/scoring stack as much as possible.
5. The system should not introduce a second independent search system for preview.
6. When the user clicks a preview direction, the click is treated as structured preference input.
7. After a preview click:
   - if hard search conditions are ready, backend should enter formal `SEARCH`
   - if hard search conditions are still incomplete, backend should continue with a short refinement step
8. Backend remains the only authority for workflow truth.
9. The model may provide a non-authoritative stage hint, but backend is free to ignore it.
10. This stage is design-only; no implementation or commit is part of this spec itself.

## Problem Statement

The current AI assistant is optimized for readiness validation:

1. extract slots
2. check whether required slots are complete
3. ask again if not complete
4. search only when complete

That architecture is safe, but the dialogue quality is weak. A user who says "I want to rent near Yuyuan in Shanghai" is usually still forced into direct follow-up collection before seeing any grounded options.

The product should instead behave like:

1. identify whether the user gave enough information to inspect a real area
2. fetch a small real candidate set
3. summarize a few concrete directions from those listings
4. let the user choose a direction
5. only then move into formal recommendation search

This keeps the experience grounded in real data without making the model the workflow owner.

## Goals

- Introduce a real-listing preview stage between pure questioning and formal search.
- Improve conversation quality from rigid slot collection to guided narrowing.
- Reuse the current `smartGuide` retrieval and scoring foundation instead of duplicating search logic.
- Keep backend as the single source of truth for stage transitions.
- Add a frontend middle state that can show preview directions before full recommendation cards.
- Ensure every preview claim is tied to actual fields or computed signals already available in the system.

## Non-Goals

- No new `house` table fields in this iteration.
- No preview copy based on unsupported qualities such as "quiet", "larger area", "better sunlight", or "pet-friendly" unless those are already backed by data in a future iteration.
- No fully autonomous tool-calling agent design.
- No route redesign outside the current AI recommendation entry.
- No new search engine or vector retrieval layer.
- No replacement of the existing final `smartGuide` recommendation experience.

## Core Product Change

The current user-facing mental model is:

- "Tell me enough conditions, then I will search."

The new mental model should be:

- "Tell me where or roughly what you want, I will first look at real nearby options, summarize a few directions, and then help you decide what to search."

This is the central design principle for this iteration:

- preview is real-data-grounded guidance
- final recommendation remains formal search
- backend controls both transitions

## State Model

The assistant should move to a four-stage model:

- `ASK`
- `PREVIEW`
- `REFINE`
- `SEARCH`

### `ASK`

Meaning:

- there is not enough information to inspect a real area yet
- the assistant should continue the conversation, but not mechanically ask the same three fields every time

Typical conditions:

- no resolvable `locationName`
- user request is too vague
- location lookup fails and no fallback candidate set can be built

### `PREVIEW`

Meaning:

- backend has a resolvable location and can inspect real candidate listings
- formal search may still be premature because the user has not yet narrowed preferences enough

Typical conditions:

- `locationName` is available
- at least one of `budgetYuan` or `rentMode` is still missing, or the user clearly wants guidance before full search

### `REFINE`

Meaning:

- preview directions have already been shown
- backend is waiting for the user to choose or clarify a direction

Typical conditions:

- preview was generated successfully
- user clicked a direction but hard search conditions are still incomplete
- or the user responded with incremental preferences such as "near subway", "whole rent", or "balcony if possible"

### `SEARCH`

Meaning:

- hard search readiness is satisfied
- backend can call the final recommendation flow

Required search slots for this iteration remain:

- `locationName`
- `budgetYuan`
- `rentMode`

## Transition Rules

Backend should derive the effective stage using deterministic rules.

### Entry Rules

1. `ASK`
- no resolvable `locationName`

2. `PREVIEW`
- `locationName` is resolved
- and formal search is not yet ready

3. `REFINE`
- preview was returned on the previous turn
- and the current turn is a preview selection or preference refinement
- and formal search is still not ready

4. `SEARCH`
- `locationName + budgetYuan + rentMode` are all usable

### Fallback Rules

1. If location resolution fails:
- fall back to `ASK`

2. If preview retrieval returns no usable candidates:
- fall back to `ASK`
- explain that the assistant could not form a reliable preview for the requested area

3. If formal search returns too few or too scattered results:
- backend may downgrade the next turn into `REFINE`
- the user should be nudged to tighten or redirect preferences

### Model Hint Rule

The model may optionally return a `nextStepHint` such as:

- `ASK`
- `PREVIEW`
- `SEARCH`

But backend remains authoritative. A model hint must never force a search that fails backend readiness rules.

## Preview Design

Preview is the new product capability introduced by this spec.

### Preview Objective

Preview is not a mini result list. Its purpose is to summarize a small real candidate set into a few understandable directions so the user can choose what to optimize for.

### Preview Data Source

Preview must be built from real listings only.

The implementation should reuse the current `smartGuide` stack wherever practical:

- location resolution
- candidate retrieval
- distance and commute estimation
- cost comparison
- ranking-related signals

This does not mean preview must reuse the exact final response object. It means preview should reuse the same retrieval foundation and candidate truth source.

### Shared Search Foundation

The design should converge on a shared internal candidate pipeline:

1. resolve location
2. collect candidate listings
3. compute ranking and explanation signals
4. assemble either:
   - preview summary groups
   - or final recommendation cards

This avoids two logically separate search systems.

## Preview Grouping Rules

Preview groups must only use currently supported signals.

Allowed factual dimensions in this iteration:

- `price`
- `depositAmount`
- `totalCost`
- `rentType`
- `nearSubway`
- `hasBalcony`
- `privateBathroom`
- `civilWaterElectric`
- `supportStudentDepositFree`
- computed distance / commute estimates from the existing scoring flow

Unsupported claims in this iteration:

- larger area
- quieter
- better sunlight
- newer decoration
- pet-friendly
- better school district

Those are explicitly out of scope because the current data model does not support them reliably.

### Recommended Group Types

The preview builder should generate 2 to 3 groups per turn, selected from the real candidate mix. Example group families:

1. commute-oriented
- "Closer to subway"
- "Shorter estimated commute"

2. budget-oriented
- "Lower first-month cost"
- "Closer to your target rent"

3. rent mode oriented
- "Mostly whole-rent options"
- "Mostly shared-rent options"

4. feature oriented
- "More balcony options"
- "More private bathroom options"
- "Student deposit-free options"

The exact group set should depend on actual candidate diversity, not on a fixed template.

### Preview Group Requirements

Each preview group should include:

- a stable `groupKey`
- a concise title
- a short summary sentence
- 2 to 4 factual highlight tags
- sample count
- optional representative listing ids for later drill-down

Preview groups should be comparative and grounded. Example:

- "Closer to subway: commute is easier, but first-month cost is usually higher."

They must not contain fabricated qualitative claims.

## Request Contract Direction

The current `/ai-recommend/chat` endpoint accepts a plain text message only. This is not enough for preview selection.

The new request direction should support two input modes:

1. free-text chat message
2. structured preview selection

Recommended request shape:

```json
{
  "message": "I want to rent near Yuyuan",
  "interaction": null
}
```

```json
{
  "message": null,
  "interaction": {
    "type": "PREVIEW_SELECTION",
    "groupKey": "near_metro",
    "label": "先看近地铁的",
    "slotPatch": {
      "priority": "COMMUTE",
      "preferences": ["nearSubway"]
    }
  }
}
```

### Interaction Handling Rule

When `interaction.type = PREVIEW_SELECTION`:

- backend should treat it as structured preference input
- backend may synthesize a readable history entry for transcript continuity
- backend should merge the provided preference patch before deriving the next stage

The click should not be downgraded into a fake natural-language turn internally if structured data is available.

## Response Contract Direction

The current page-facing response shape is centered on:

- `action`
- `assistantReply`
- `slots`
- `missingSlots`
- optional `recommendation`

This iteration should move to a stage-oriented response:

```json
{
  "sessionId": "ai-u1001",
  "stage": "PREVIEW",
  "assistantReply": "I checked the real listings near Yuyuan and found a few directions.",
  "slots": {},
  "missingSlots": [],
  "preview": {},
  "recommendation": null
}
```

### Required Response Fields

- `sessionId`
- `stage`
- `assistantReply`
- `slots`
- `missingSlots`
- `preview`
- `recommendation`

Rules:

- `preview` is populated only for `PREVIEW` or `REFINE`
- `recommendation` is populated only for `SEARCH`
- `missingSlots` remains useful for summary rendering and backend debugging

### Compatibility Note

If implementation needs a short transition period, the old `action` field may be retained temporarily as a compatibility alias. But `stage` is the new source of truth in the design.

## Preview Response Shape

Recommended preview response:

```json
{
  "locationName": "豫园",
  "candidateCount": 18,
  "groups": [
    {
      "groupKey": "near_metro",
      "title": "更靠近地铁",
      "summary": "通勤更方便，但首月成本通常更高一些。",
      "highlights": ["近地铁", "通勤更短", "整租占比更高"],
      "sampleCount": 6,
      "sampleHouseIds": [101, 205, 331]
    },
    {
      "groupKey": "lower_total_cost",
      "title": "首月成本更低",
      "summary": "预算压力更小，但地铁和通勤优势没有那么明显。",
      "highlights": ["首月成本低", "合租更多"],
      "sampleCount": 8,
      "sampleHouseIds": [118, 122, 309]
    }
  ]
}
```

## Frontend Experience

### Current Problem

The current AI page effectively has only two visible states:

- conversation
- final recommendation panel

That makes the product jump directly from dialogue into result list.

### New Middle State

The page should support a dedicated preview card between conversation and full recommendation.

The rendering model becomes:

1. `ASK`
- show conversation
- show known slot summary

2. `PREVIEW` / `REFINE`
- show conversation
- show known slot summary
- show preview direction card

3. `SEARCH`
- show conversation
- show known slot summary
- show final recommendation list

### Preview Card Requirements

Each preview group should render as a selectable direction card with:

- title
- short summary
- highlight tags
- optional sample count
- one clear CTA

Example CTAs:

- "先看这类"
- "按这个方向继续找"

### Click Behavior

When the user clicks a preview group:

1. frontend sends structured interaction payload
2. backend merges that selection into state
3. backend derives next stage
4. frontend renders either:
   - another short refinement turn
   - or formal search results

The click should feel like preference selection, not like opening a listing directly.

## Backend Responsibility Split

### Model Responsibilities

The model should handle:

- natural-language understanding
- slot extraction and update suggestion
- natural-language reply generation
- optional non-binding `nextStepHint`

The model must not:

- claim real preview results unless backend has already supplied them
- invent listings or unsupported attributes
- decide actual execution stage

### Backend Responsibilities

Backend should own:

- stage derivation
- location readiness check
- preview trigger
- preview candidate retrieval
- preview group construction
- formal search trigger
- final reply override when model wording conflicts with backend truth

This preserves the architecture principle established in the previous AI redesign:

- model owns language
- backend owns workflow truth

## Existing Class-Level Refactoring Direction

### `AiRecommendServiceImpl`

Should become the clear stage orchestrator:

1. load state
2. append user text or structured interaction
3. call the model for slot/reply understanding when needed
4. merge and normalize slots
5. derive stage
6. if stage is `PREVIEW` or `REFINE`, build preview response
7. if stage is `SEARCH`, run formal `smartGuide`
8. accept or override final reply
9. save updated state

### `SmartGuideRecommendationService`

Should remain the formal recommendation engine.

Its retrieval/scoring internals should become more reusable so preview can share:

- candidate collection
- distance/commute signals
- budget closeness signals

Without duplicating search truth.

### New Preview Assembler Layer

A new backend layer should assemble preview groups from shared candidate data. This is a new response-shaping capability, not a second search engine.

## Failure Handling

### Preview Failure Rules

1. If location cannot be resolved:
- do not pretend preview exists
- return `ASK`

2. If preview retrieval fails:
- fall back to `ASK`
- explain that the assistant could not form a reliable preview yet

3. If preview candidate count is too low for meaningful grouping:
- skip preview
- continue with `ASK` or move straight to `SEARCH` only if hard conditions are already complete

### Search Failure Rule

If formal `smartGuide` fails after preview or refinement:

- do not lose the current state
- return a safe fallback reply
- keep the conversation in a recoverable stage, usually `REFINE`

## Observability Requirements

Recommended logs and metrics for this iteration:

- whether location was resolved
- derived stage per turn
- preview candidate count
- preview group keys emitted
- whether the turn source was free text or preview selection
- whether formal search executed
- whether reply override occurred

This is necessary because the new experience adds a visible intermediate stage that will otherwise be hard to debug.

## Testing Strategy

### Backend Tests

Add or update tests for:

1. `ASK` when no resolvable location exists
2. `PREVIEW` when location exists but search is not ready
3. `SEARCH` when `locationName + budgetYuan + rentMode` are all usable
4. preview selection merging into structured preferences
5. transition from `PREVIEW` click to `SEARCH`
6. transition from `PREVIEW` click to `REFINE` when hard slots remain missing
7. preview fallback when candidate retrieval fails
8. preview summary generation using only supported fields
9. reply override when the model implies unsupported execution

### Frontend Tests

Add or update tests for:

1. rendering preview card when `stage=PREVIEW`
2. not rendering final recommendation card during preview
3. sending structured interaction payload on preview selection
4. rendering final recommendation card when `stage=SEARCH`

## Risks and Trade-Offs

### 1. Preview Quality vs Data Limits

Because this iteration does not add new house fields, preview language must stay disciplined. This limits richness, but keeps the product truthful.

### 2. Shared Pipeline Pressure

Reusing the `smartGuide` foundation avoids a second search system, but it may force some internal refactoring to separate candidate collection from final response assembly.

### 3. Stage Complexity

Adding `PREVIEW` and `REFINE` improves UX, but also increases orchestration complexity. Deterministic backend rules are therefore required.

### 4. Model Hint Ambiguity

Allowing a weak `nextStepHint` can improve conversational flexibility, but it must stay non-authoritative or the system will regress into model-driven workflow instability.

## Success Criteria

This design is successful when:

- a user can mention a resolvable area and receive a grounded preview before formal search
- preview is built from real candidate listings
- preview directions are factual and supported by current fields
- clicking a direction behaves like structured preference input
- backend remains the authority for `ASK / PREVIEW / REFINE / SEARCH`
- final recommendations still come from the existing formal recommendation pipeline
- the user experience feels guided rather than interrogative
