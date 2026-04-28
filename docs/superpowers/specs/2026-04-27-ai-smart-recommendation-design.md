# AI Smart Recommendation Design

Date: 2026-04-27

## Background

The current project already has a usable non-LLM recommendation backbone:

- `POST /house/smart-guide` returns real house recommendations from backend filtering and ranking
- `SmartGuideRecommendationService` already handles candidate recall, relaxation, scoring, and recommendation reasons
- the frontend already has a structured smart-guide page inside `MapView.vue`
- the project also already has standard chat infrastructure, but it is designed for user-to-user messaging rather than an AI assistant

The new requirement is not to replace the current recommendation engine with AI. The requirement is to add a simple AI assistant that can:

1. chat with the user in natural language
2. ask follow-up questions when information is insufficient
3. decide whether to continue asking or start querying real houses
4. call the existing smart-guide capability once conditions are usable
5. explain results in a more human, advisory style

The confirmed product direction for this iteration is:

- add a dedicated AI assistant entry
- expose it in the top navigation as a prominent center item named `智能推荐`
- use a small dog icon as the feature symbol
- make the desktop navigation entry visually protrude in a half-round shape
- keep the first version simple and controlled
- use Spring AI to orchestrate an existing LLM rather than training a custom model

## Goals

- Add a dedicated `智能推荐` page for AI-assisted rental recommendation.
- Add a prominent desktop top-nav entry placed in the center with a dog icon and a half-round protruding treatment.
- Let the assistant support multi-turn requirement collection instead of only one-shot form submission.
- Let the assistant output structured decisions so the backend can safely decide whether to:
  - ask another question
  - give general advice
  - query real houses
- Reuse the current `SmartGuideRecommendationService` for real recommendation retrieval.
- Keep the first version implementation-friendly on the current branch without introducing a large new subsystem.

## Non-Goals

- No model training, fine-tuning, or custom model development.
- No vector database or RAG knowledge base in this iteration.
- No integration into the current user-to-user message center or chat session list.
- No streaming response, voice input, image input, or agent-style autonomous tool loops in this iteration.
- No automatic rewrite of the existing `MapView` into the AI assistant route.
- No persistence of AI conversation into new MySQL tables in this iteration.
- No hard promise that the assistant can provide city-authentic district advice for every city if project data is missing or unsupported.

## Confirmed Product Decisions

### Product Entry

- The AI feature is a dedicated page rather than an extension of the existing message center.
- The feature name is `智能推荐`.
- The desktop top navigation must show `智能推荐` in the center as the most visually prominent item.
- The visual mark is a small dog icon.
- The desktop nav item should protrude upward in a half-round or capsule-emphasis shape rather than look like a normal flat link.

### Page Boundary

- The AI page is separate from the current house list page and separate from the current `MapView` smart-guide flow.
- The current structured smart-guide flow remains available and acts as an internal backend capability, not the primary user-facing AI surface.

### AI Boundary

- Spring AI is used as the application integration layer for an existing model provider.
- The LLM is not allowed to fabricate real houses.
- The LLM decides what should happen next, but backend code executes the real action.
- Real house lookup still comes from backend services, not from model memory.

### State Boundary

- The first version should store AI conversation state outside MySQL.
- Redis is the recommended first storage because the project already depends on Redis and this keeps schema impact low.
- If Redis is unavailable in a local environment, an in-memory fallback can be used for development only.

## User Experience Design

## Desktop Navigation

Desktop top navigation should evolve from a symmetric flat link row into a layout with one highlighted center action.

Recommended behavior:

- keep existing brand area on the left
- keep city selector and profile area on the right
- render the navigation list with the `智能推荐` item visually centered
- style the `智能推荐` nav item as a raised capsule with a half-round protruding silhouette
- include a dog icon above or beside the label
- active state should feel stronger than other nav items

Recommended visual direction:

- the raised item should overlap the nav container slightly upward
- the base shape should be circular-top or dome-like rather than a plain pill
- the dog icon should be simple, friendly, and product-like rather than cartoon-heavy
- the highlighted item should use a warmer accent than the normal nav links so the feature feels intentionally promoted

### Mobile Navigation

The mobile tab bar should include `智能推荐` as a normal first-class tab item.

This iteration does not require a protruding half-round mobile center tab. A standard mobile tab is sufficient as long as:

- it has the dog icon
- it uses the same `智能推荐` label
- it is visually consistent with the rest of the mobile navigation

## AI Page Structure

Recommended new route:

- `/ai-recommend`

Recommended page composition:

1. assistant hero card
2. chat transcript area
3. quick prompt chips
4. recommendation card area
5. current known requirements summary

### Hero Card

The top of the page should quickly explain the value:

- this assistant can ask questions first
- it can narrow requirements gradually
- it only recommends real houses after conditions are clear enough

The first assistant message should be present immediately on page load, for example:

> 我可以先帮你判断预算、区域和整租/合租方向。你先告诉我现在最在意什么，预算、通勤还是居住品质？

### Quick Prompt Chips

Add 4 to 6 starter chips so users are not forced to type from scratch.

Examples:

- `预算 3000 左右，想整租`
- `想住地铁附近`
- `目前没想好住哪，先给我建议`
- `通勤方便最重要`
- `预算有限，接受合租`

### Chat Transcript

The transcript is the primary interaction mode.

Messages should support:

- assistant text bubble
- user text bubble
- inline status row when the assistant starts querying houses
- inline recommendation result block after search completes

### Requirement Summary

A compact summary block should show the currently captured slots, for example:

- 城市：上海
- 预算：3500
- 区域：未确定
- 租住方式：整租
- 优先级：通勤

This gives users confidence that the assistant is collecting information rather than free-chatting aimlessly.

## Conversation Model

The first version should be implemented as a controlled conversation state machine, not as an open-ended autonomous agent.

Each turn should produce one of three actions:

- `ASK`
- `ADVISE`
- `SEARCH`

### ASK

Meaning:

- information is still insufficient for a real house query
- the assistant should ask 1 to 2 focused follow-up questions

Example situation:

- user says only `我想在上海租房`

### ADVISE

Meaning:

- the assistant can give high-level non-binding guidance without querying real houses
- this is used when the user is still exploring and wants orientation rather than immediate results

Example situation:

- user says `我没想好住哪，先给我点建议`

Important rule:

- `ADVISE` must never pretend to be based on real current house query results

### SEARCH

Meaning:

- information is sufficient to call backend search/recommendation capability

For the first version, the minimum practical search trigger should be:

- a usable budget
- a rent mode
- a usable location target that can be converted into the current smart-guide query

Because the existing `SmartGuideRecommendationService` still fundamentally expects a location-like target, the AI page should not promise true city-only search in this iteration.

## Slot Model

Recommended slots:

- `city`
- `budgetYuan`
- `budgetScope`
- `rentMode`
- `locationName`
- `priority`
- `preferences`

Recommended semantics:

- `city`: optional city context, default from current frontend city when omitted
- `budgetYuan`: integer monthly budget
- `budgetScope`: `RENT_ONLY` or `TOTAL`
- `rentMode`: `WHOLE` or `SHARED`
- `locationName`: area, metro station, or other location term that can map to the current smart-guide query
- `priority`: one of `PRICE`, `COMMUTE`, `QUALITY`
- `preferences`: lightweight natural-language preferences such as `独卫`, `近地铁`, `采光好`

The assistant should gradually fill these slots over multiple turns.

## LLM Output Contract

The backend must not depend on free-form model text alone. The model must return a structured decision.

Recommended internal response contract:

```json
{
  "action": "ASK",
  "reply": "可以，我先帮你缩小范围。你的预算大概多少？另外你更倾向整租还是合租？",
  "slots": {
    "city": "上海",
    "budgetYuan": null,
    "budgetScope": "RENT_ONLY",
    "rentMode": null,
    "locationName": null,
    "priority": null,
    "preferences": []
  },
  "missingSlots": ["budgetYuan", "rentMode", "locationName"]
}
```

Rules:

- `action` is mandatory
- `reply` is mandatory
- `slots` is mandatory
- `missingSlots` is mandatory
- the model must not return recommendation cards directly
- the model must not invent house ids, prices, publishers, or availability

## Prompt Strategy

The system prompt should define:

- the assistant role as a rental recommendation advisor
- the required output JSON schema
- the rule that real houses can only come from backend search
- the rule that insufficient information must trigger focused follow-up questions
- the rule that only 1 to 2 questions may be asked per turn
- the rule that pre-search advice must be clearly general guidance rather than real-time result analysis

Recommended core prompt constraints:

- do not fabricate listings
- do not claim to have queried the database unless the backend already provided real search results
- when information is insufficient, ask concise follow-up questions
- when the user lacks a clear area target, provide orientation first and then continue to narrow the search
- prefer actionable trade-off advice over generic encouragement

## Backend Architecture

The backend should add a dedicated AI recommendation orchestration layer instead of mixing the logic into `HouseServiceImpl`.

Recommended additions:

- `AiRecommendController`
- `AiRecommendService`
- `AiRecommendStateStore`
- `AiRecommendPromptBuilder`
- `AiRecommendDecision`
- `AiRecommendSlots`
- `AiRecommendChatReqDTO`
- `AiRecommendChatVO`

### Recommended Responsibility Split

#### Controller

Responsibilities:

- receive page-init or chat-turn requests
- wrap responses in existing `Result<T>`
- translate validation exceptions into user-friendly API errors

#### Service

Responsibilities:

- load conversation state
- call the LLM through Spring AI
- validate and normalize model output
- branch by `ASK` / `ADVISE` / `SEARCH`
- call `SmartGuideRecommendationService` when action is `SEARCH`
- optionally call the LLM a second time to turn real search results into advisory language
- persist updated state

#### State Store

Responsibilities:

- load conversation slots by user/session id
- save updated slots
- store lightweight message history needed for prompt context
- control TTL

#### Prompt Builder

Responsibilities:

- construct the system prompt
- supply current slot state
- supply recent chat turns
- provide clear output schema instructions

## Spring AI Integration Shape

Spring AI is used here as an orchestration and model-integration layer, not as model training infrastructure.

Recommended approach:

- add Spring AI dependency for a compatible chat model client
- configure one provider through application properties
- keep provider naming abstract in business code so the project can switch vendors later

The first version should use a simple request-response flow:

1. backend assembles prompt and conversation context
2. Spring AI calls the configured model
3. backend parses the structured response into `AiRecommendDecision`
4. backend executes the action

The first version does not need tool-calling. Backend-controlled branching is simpler and safer.

## Search Integration with Existing Smart Guide

The assistant must reuse the current backend truth source:

- `HouseController -> IHouseService.smartGuide(...)`
- `SmartGuideRecommendationService`

Recommended mapping from AI slots into current search request:

- `budgetYuan -> SmartGuideReqDTO.budgetYuan`
- `budgetScope -> SmartGuideReqDTO.budgetScope`
- `rentMode -> SmartGuideReqDTO.rentMode`
- `locationName -> SmartGuideReqDTO.locationName`
- `page = 1`
- `size = 5` or `10`

Important limitation:

- the current smart-guide contract is still location-target-driven
- therefore a pure city-only request should remain in `ASK` or `ADVISE` until a usable area, station, or nearby target is identified

This limitation must be reflected honestly in both prompt and frontend copy.

## API Design

Recommended base path:

- `/ai-recommend`

Recommended endpoints:

### 1. Start or Resume Session

- `GET /ai-recommend/session`

Purpose:

- load current assistant state
- return opening assistant message if no prior state exists

### 2. Chat Turn

- `POST /ai-recommend/chat`

Recommended request:

- `message`
- optional `sessionId`

Recommended response:

- assistant reply text
- action type
- updated slot summary
- optional recommendation payload when action becomes `SEARCH`

### 3. Reset Session

- `POST /ai-recommend/reset`

Purpose:

- clear current slot state and transcript
- start over cleanly

## Suggested Response Contract

Recommended page-facing response:

```json
{
  "sessionId": "ai-u1001",
  "action": "SEARCH",
  "assistantReply": "我先按你的预算和区域帮你筛一批更适合通勤的房源。",
  "slots": {
    "city": "上海",
    "budgetYuan": 3500,
    "budgetScope": "RENT_ONLY",
    "rentMode": "WHOLE",
    "locationName": "浦东",
    "priority": "COMMUTE",
    "preferences": ["近地铁"]
  },
  "recommendation": {
    "matchedExpectation": true,
    "relaxedBudget": false,
    "tipMessage": "已找到符合条件的房源，并按综合评分排序。",
    "recommendations": []
  }
}
```

This contract lets the frontend render:

- the latest assistant bubble
- the current captured requirements
- recommendation cards when present

## State Storage Strategy

Recommended Redis keys:

- `ai:recommend:state:{userId}`
- `ai:recommend:history:{userId}`

Recommended TTL:

- `24h` to `72h`

Stored content:

- latest slot state
- small recent message window, such as last 8 to 12 turns

This keeps the first version lightweight and avoids schema changes.

## Frontend Implementation Shape

Recommended frontend additions:

- `frontend/src/views/AiRecommendView.vue`
- `frontend/src/api/aiRecommend.js`
- `frontend/src/components/ai/AiChatBubble.vue`
- `frontend/src/components/ai/AiQuickPromptChips.vue`
- `frontend/src/components/ai/AiRequirementSummary.vue`
- `frontend/src/components/ai/AiRecommendationPanel.vue`

Recommended frontend modifications:

- `frontend/src/router/index.js`
- `frontend/src/design/site.js`
- `frontend/src/components/layout/AppTopNav.vue`
- `frontend/src/components/AppTabBar.vue`

### Route Design

Recommended route:

- `/ai-recommend`

Recommendation:

- create a dedicated `AiRecommendView.vue` rather than continuing to overload `MapView.vue`
- if necessary, the old `/map` route can remain temporarily for backward compatibility, but it should not be the primary AI entry

### Page Behavior

Recommended first-load flow:

1. user enters `/ai-recommend`
2. frontend requests `GET /ai-recommend/session`
3. backend returns slot state and opening assistant message
4. user sends a text message
5. frontend posts to `/ai-recommend/chat`
6. response may contain:
   - follow-up question
   - general advice
   - real recommendation cards

## Error Handling and Safety Rules

### LLM Failure

- if the LLM call fails, the API should return a user-facing fallback message
- fallback message should be explicit and short, for example:
  - `智能推荐暂时不可用，请稍后重试`
- do not silently fake a successful assistant decision

### Malformed LLM Output

- if the model returns invalid JSON or missing mandatory fields, the backend should treat it as a recoverable error
- log the raw response
- return a safe fallback message

### Unsupported Search Readiness

- if the model requests `SEARCH` but required slots are still unusable for `SmartGuideReqDTO`, backend must downgrade to `ASK`
- backend validation is authoritative over model intent

### Truthfulness

- the assistant may give general pre-search advice
- the assistant may only discuss specific houses after backend search results are actually returned

## Testing Strategy

## Backend Tests

Add tests for:

1. new session returns opening assistant message
2. `ASK` response updates slots but does not call `SmartGuideRecommendationService`
3. `ADVISE` response returns text only and does not call house query
4. `SEARCH` response calls `SmartGuideRecommendationService`
5. malformed LLM output returns safe fallback message
6. backend downgrades invalid `SEARCH` to `ASK`
7. Redis state load/save works for normal conversation progression

## Frontend Tests

Add tests for:

1. top nav includes `智能推荐` entry
2. highlighted center nav item renders special class
3. mobile tab includes `智能推荐`
4. AI page initial request renders opening assistant bubble
5. sending a message appends user bubble and assistant bubble
6. recommendation payload renders existing-style house cards
7. reset action clears current summary and transcript state

## Risks and Trade-Offs

### 1. LLM Advice vs Real Data Mismatch

The assistant may give general city-level advice before real search. That is acceptable only if it never presents such advice as real-time house data.

### 2. Current Smart-Guide Location Dependency

The current recommendation core still depends on a usable location target. This means the AI page can feel more flexible than the underlying search engine. The prompt and UX must hide that mismatch carefully but honestly.

### 3. Scope Creep into Full Agent Design

If this iteration expands into tool-calling, long-term memory, or message-center integration, complexity will rise sharply. The first version must stay on a controlled state-machine path.

### 4. Navigation Visual Complexity

The raised half-round nav item is a meaningful visual change. It must be implemented without breaking current layout balance, overflow behavior, or mobile fallbacks.

## Recommended Implementation Order

1. add Spring AI dependency and provider configuration
2. define AI decision DTOs, slot model, and response contract
3. implement Redis-backed state store
4. implement `AiRecommendService` with `ASK` / `ADVISE` / `SEARCH` branching
5. wire `SEARCH` to existing `SmartGuideRecommendationService`
6. add new controller endpoints
7. create `AiRecommendView.vue` and page-level components
8. add `智能推荐` navigation entry and special center styling
9. add backend and frontend tests

## Success Criteria

This feature is successful when:

- users can enter a dedicated `智能推荐` page from a prominent navigation entry
- the desktop top nav shows `智能推荐` as a visually raised center action with a dog icon
- the assistant can continue the conversation when user information is incomplete
- the assistant can return general pre-search advice without pretending it is based on queried houses
- the backend can safely decide whether to ask, advise, or search based on structured LLM output
- real recommendations still come from the existing `SmartGuideRecommendationService`
- no training, vector database, or new MySQL schema is required for the first version
