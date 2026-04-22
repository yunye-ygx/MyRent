# House Browse History Design

Date: 2026-04-22

## Background

The current "Mine" page at `frontend/src/views/MineView.vue` still routes the `浏览记录` entry to a placeholder page. The project already has a real house detail page, favorite page, and authenticated routing, but there is no persistent browsing-history capability yet.

The desired product behavior is closer to an e-commerce "footprint" page than to a simple "recently viewed" list:

- entering a house detail page should record a browse event
- history should be grouped and filtered by day
- the page should show only house image and price for each history item
- the history page should expose a date picker panel under a `筛选浏览时间` button
- the calendar should indicate which days in the current month have browse records
- users should only be able to click days that actually have history

The confirmed data rule is not "keep only the latest browse per house". Instead, browsing history is retained at day granularity:

- same user + same house + same day: keep one record
- same user + same house + different day: create another record

This rule matches the intended calendar interaction and makes a Redis bitmap a clean fit for "which days are clickable".

## Goals

- Replace the placeholder `浏览记录` entry with a real `/mine/history` page.
- Record house browsing automatically when a logged-in user opens a house detail page successfully.
- Persist history at day granularity.
- Show history grouped by browse day.
- Show only image and price in the history grid cards.
- Add a left-aligned `筛选浏览时间` control that expands an in-page calendar panel.
- Mark days with history in the calendar and disable days without history.
- Support querying the records for a selected day without returning unrelated days.

## Non-Goals

- No full "recently viewed across all content types" system.
- No cross-device or anonymous history merge.
- No deletion-management UI in this iteration.
- No title, publisher, deposit, or status fields in the history card UI.
- No "all-time recent list" ranking logic beyond browse-day grouping and browse-time ordering.
- No server-side monthly archive browsing beyond the currently viewed month and explicit day selection.

## Confirmed Product Decisions

### Recording Rule

Browsing history is stored at day granularity:

1. each record represents one user-house-day tuple
2. duplicate opens on the same day do not insert another row
3. duplicate opens on later days insert a new row
4. same-day duplicate opens should refresh the "latest browse time within that day"

### History Page Layout

The page should contain:

1. top bar with back button, title, refresh button
2. `筛选浏览时间` button aligned to the left near the top
3. expandable calendar panel directly below that button
4. grouped date sections such as `4月17日`
5. a mobile-friendly grid of image-and-price cards under each date section

### Calendar Behavior

- closed by default
- toggled by clicking `筛选浏览时间`
- expands inline instead of using a system picker modal
- shows a monthly calendar
- visually marks days that have records
- allows clicking only on marked days
- clicking a valid day collapses the panel and reloads the list for that day
- the user can reopen the panel to choose another day
- an "all history" reset control should be available in or near the filter area so the user can return to grouped full-history mode

## Data Model

## Table: `house_history`

Recommended fields:

- `id`
- `user_id`
- `house_id`
- `browse_date`
- `last_browse_time`
- `create_time`
- `update_time`

### Semantics

- `browse_date` is the local calendar date used for grouping and unique-day deduplication
- `last_browse_time` is the latest timestamp within that day and is used for ordering records inside the day

### Constraints and Indexes

Required indexes:

1. unique index on `(user_id, house_id, browse_date)`
2. index on `(user_id, browse_date)`
3. index on `(user_id, last_browse_time)`

These indexes support:

- same-day deduplication
- day-specific list queries
- reverse chronological history views

### Time-Zone Rule

Day boundaries must be defined on the backend in a single application time zone and used consistently for:

- `browse_date`
- bitmap day offsets
- day-group titles
- history day filtering

The frontend should treat dates returned by the backend as authoritative and should not attempt to recompute day grouping independently from raw timestamps.

## Redis Bitmap Design

Redis bitmap is appropriate for the monthly "which days have history" question because the history rule is now day-based and append-friendly.

Recommended monthly key shape:

- `house_history:calendar:{userId}:{yyyyMM}`

Example:

- `house_history:calendar:10001:202604`

Bit semantics:

- bit `day - 1` equals `1`: the user has at least one browse-history record on that calendar day
- bit `day - 1` equals `0`: no records exist for that day

### Write Behavior

When the user opens house detail successfully:

1. determine current local `browse_date`
2. upsert the `house_history` row for `(user_id, house_id, browse_date)`
3. update `last_browse_time`
4. set the bitmap bit for that month/day to `1`

Because records are not moved across days, there is no "old day becomes stale after overwrite" problem. That is why bitmap semantics are clean in this design.

### Source of Truth

MySQL remains the source of truth for history records and day-specific lists.

Redis bitmap is a secondary acceleration layer for:

- monthly clickable-day lookup
- fast calendar rendering

If the bitmap is temporarily missing or cold, the backend may rebuild the month response from MySQL and optionally repopulate Redis.

## Backend API Design

## 1. Record Browse History

The recording operation should not be exposed as a separate frontend API. It should happen inside the existing house-detail read flow after the house is found and the current user is known.

Recommended integration point:

- `GET /house/{id}`

Behavior:

1. if the request is unauthenticated, skip history recording
2. if the house does not exist or the detail request fails, skip history recording
3. if the detail request succeeds for a logged-in user, upsert that day's history row and set the bitmap day bit

This avoids an extra frontend call and makes recording hard to forget.

## 2. Calendar Availability API

Endpoint:

- `GET /house-history/calendar`

Parameters:

- `year`
- `month`

Response:

- monthly calendar availability for the current user
- recommended response shape:
  - `year`
  - `month`
  - `activeDays`

Example:

```json
{
  "year": 2026,
  "month": 4,
  "activeDays": [6, 9, 10, 11, 12, 13, 14, 15, 16, 17, 22]
}
```

Why day numbers instead of full date strings:

- simpler for calendar rendering
- aligns directly with bitmap offsets
- avoids repeated string parsing on the frontend

## 3. History List API

Endpoint:

- `GET /house-history/mine`

Parameters:

- `current`
- `size`
- optional `browseDate` in `YYYY-MM-DD`

Behavior:

- without `browseDate`: return paginated history records across days in reverse chronological order
- with `browseDate`: return only that day's records for the current user

Response records should be purpose-built history view objects rather than raw `House` entities.

Recommended record fields:

- `historyId`
- `houseId`
- `browseDate`
- `lastBrowseTime`
- `price`
- `cover`

Optional fields that can be included without being rendered immediately:

- `houseStatus`

Fields intentionally not required:

- publisher name
- house title
- deposit amount
- favorite count

## 4. Optional Clear/Manage APIs

No clear-history or delete-history APIs are required in this iteration.

## Backend Implementation Shape

Recommended additions:

- `entity/HouseHistory.java`
- `mapper/HouseHistoryMapper.java`
- `service/IHouseHistoryService.java`
- `service/impl/HouseHistoryServiceImpl.java`
- `controller/HouseHistoryController.java`
- `vo/HouseHistoryItemVO.java`
- `vo/HouseHistoryCalendarVO.java`

Recommended responsibilities:

- `HouseHistoryService.recordBrowse(houseId, userId)`
  - compute `browse_date`
  - upsert row
  - set bitmap
- `HouseHistoryService.getCalendar(userId, year, month)`
  - read bitmap first
  - fall back to MySQL if needed
- `HouseHistoryService.getMyHistory(userId, current, size, browseDate)`
  - query history rows
  - join/fetch required house snapshot fields
  - preserve per-day ordering

### House Data Join Strategy

For the history list, the backend should enrich history rows with only the fields needed by the UI. A dedicated query or a two-step `history -> houseIds -> houseMap` flow is both acceptable.

The design should not reuse the favorite-page controller pattern blindly because that pattern returns full `House` records and expects the frontend to enrich publisher names. The browse-history page intentionally needs a smaller payload and different grouping semantics.

## Frontend Route and API Changes

## Route Changes

Add route:

- `/mine/history`

Files affected:

- `frontend/src/router/index.js`
- `frontend/src/views/MineView.vue`

`MineView.vue` should route the `history` entry to `/mine/history` instead of `/placeholder/history`.

## Frontend API Module Changes

Recommended additions in `frontend/src/api/house.js` or a dedicated `frontend/src/api/history.js`:

- `fetchMyBrowseHistory(params)`
- `fetchBrowseHistoryCalendar(params)`

Either location is acceptable, but a dedicated `history.js` file is cleaner if more history-related APIs are expected later.

## Frontend Page Design

## New Page: `MineHistoryView.vue`

Recommended local state:

- `loading`
- `calendarLoading`
- `error`
- `items`
- `current`
- `size`
- `hasMore`
- `calendarOpen`
- `selectedDate`
- `calendarYear`
- `calendarMonth`
- `activeDays`

Recommended derived state:

- `groupedSections`
- `filterButtonText`
- `canLoadMore`
- `hasFilter`

### Loading Strategy

On initial page load:

1. request current month calendar availability
2. request history list without `browseDate`
3. render grouped sections

When the calendar opens and the user flips month:

1. request the new month availability
2. keep current list visible until a date is selected

When the user selects a date:

1. verify the day is active
2. set `selectedDate`
3. collapse the calendar
4. reset pagination
5. request `GET /house-history/mine?browseDate=...`

When the user clears the filter:

1. clear `selectedDate`
2. reset pagination
3. request unfiltered history list again

### Grouping Rules

Unfiltered mode:

- group records by `browseDate`
- render each group under a date heading such as `4月17日`
- sort groups descending by date
- sort items within a day descending by `lastBrowseTime`

Filtered mode:

- render only the selected day section
- preserve card grid layout

## History Card Design

Create a lightweight card component rather than reusing `HouseCard.vue`.

Recommended component:

- `frontend/src/components/house/MineHistoryCard.vue`

Card contents:

- cover image
- price text

Card interactions:

- click card -> navigate to `/house/:houseId`

Card exclusions:

- no publisher
- no title
- no distance
- no deposit
- no tags

This prevents fighting the richer `HouseCard` template, which is optimized for search/list pages instead of history footprints.

## Calendar UI Design

The calendar panel should resemble the reference interaction:

- filter button in a soft pill style
- expanding white panel under the button
- month switch controls
- weekday header
- day cells in a 7-column grid
- active days marked with a small orange dot
- selected day shown with a filled accent circle
- inactive days visually muted and not clickable

Recommended calendar behavior details:

- keep the panel inside the page flow instead of absolute-positioning it over the viewport
- on mobile, the expanded panel should push the history list downward
- clicking outside the panel does not need to close it in the first version; toggling with the button is enough
- the currently selected date remains highlighted when the panel reopens

## Error and Empty-State Behavior

Recommended empty states:

- no overall history: "暂无浏览记录"
- selected date has no records: normally unreachable if inactive dates are not clickable, but still handle defensively

Recommended error handling:

- history-list request failure shows page-level error text
- calendar request failure shows a lighter inline error near the filter area and keeps the list usable
- refresh should retry both list and current visible month availability

## Testing Strategy

## Backend Tests

Add tests for:

- same user + same house + same day records only one row
- same user + same house + different day records multiple rows
- successful house detail request records browse history
- unauthenticated detail request does not record history
- `/house-history/calendar` returns the correct active days
- `/house-history/mine?browseDate=...` returns only selected-day records
- unfiltered history returns reverse chronological order

## Frontend Tests

Add tests for:

- `MineView` routes the history menu item to `/mine/history`
- `MineHistoryView` loads history on mount
- clicking `筛选浏览时间` expands the calendar panel
- inactive dates are not clickable
- clicking an active date reloads the list with that date filter
- unfiltered mode groups records by day
- history cards render only image and price
- clicking a history card navigates to the detail page

## Risks and Trade-Offs

### Data Volume

This design stores multiple rows for the same house across different days. That increases row count compared with a strict "latest only" design, but it better matches user expectations for daily footprints and is acceptable for a browse-history domain.

### Bitmap Consistency

Bitmap is only safe because day-based records are append-friendly. If future product changes revert to "latest only" semantics, the bitmap design would need to be revisited.

### House Snapshot vs Live House Data

The design currently assumes the history list can read live house data such as current price and current cover. If the product later requires showing historical price-at-browse-time, the table would need snapshot fields. That is out of scope for this iteration.

## Recommended Implementation Order

1. add `house_history` schema and backend entity/mapper/service/controller
2. integrate browse recording into successful house-detail reads
3. expose calendar and history-list APIs
4. add frontend route and convert Mine entry from placeholder to real page
5. implement `MineHistoryView.vue`
6. add lightweight history card and in-page calendar UI
7. add tests for backend and frontend flows

## Success Criteria

The change is successful when:

- opening a house detail as a logged-in user records that house in today's history
- same-day repeat browsing does not create duplicate rows
- next-day repeat browsing creates a new row for the new day
- `Mine > 浏览记录` opens a real history page
- the page shows grouped daily history cards with image and price only
- clicking `筛选浏览时间` expands a calendar
- only days with history are clickable
- selecting a day reloads that day's history records correctly
