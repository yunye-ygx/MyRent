# Mine Order Status Tabs Design

Date: 2026-04-22

## Background

The current renter order page at `frontend/src/views/mine/MineOrderView.vue` renders all order records in one mixed list. That is functional, but it does not match the desired product behavior for users who want to focus on a single order state.

The requested change is a frontend-only interaction redesign for the existing "My Contract / Orders" entry under "Mine". The page should remain a single route, but the list should be segmented with visible status navigation.

The current frontend already exposes enough fields to support this redesign without changing routes or backend APIs:

- order state via `order.status`
- review capability via `canReview`, `canEditReview`, `reviewId`, `hasReview`
- refund progression via `latestRefundStatus`

## Goals

- Keep the order page on the existing `/mine/orders` route.
- Add a first-level status navigation row above the order list.
- Show only the orders relevant to the selected first-level tab.
- Add a second-level navigation row under `REVIEW`.
- Add a second-level navigation row under `REFUND`.
- Reuse the existing order actions, loading flow, pagination flow, and page shell.

## Non-Goals

- No new standalone routes such as `/mine/orders/paid`.
- No backend API changes for tab-specific filtering.
- No redesign of the entire "Mine" area.
- No changes to payment, refund submission, review submission, or order completion business rules.
- No `ALL` tab in this iteration.

## Confirmed Product Decisions

### First-Level Tabs

The page will expose exactly these first-level tabs, in this order:

1. `UNPAID`
2. `PAID`
3. `CANCELLED`
4. `REVIEW`
5. `REFUND`

There is no `ALL` tab.

### Second-Level Tabs

Second-level tabs appear only for these first-level selections:

- `REVIEW`
  - `PENDING_REVIEW`
  - `REVIEWED`
- `REFUND`
  - `IN_PROGRESS`
  - `FINISHED`

All other first-level tabs render the order list directly without a second navigation row.

### Default Selection

The page will default to:

- first-level tab: `UNPAID`
- second-level tab under `REVIEW`: `PENDING_REVIEW`
- second-level tab under `REFUND`: `IN_PROGRESS`

When the user switches away from `REVIEW` or `REFUND` and later comes back, the first implementation should reset to the default subtab instead of preserving the previous one. This keeps component state simpler.

## State Mapping

The current frontend status helpers indicate these order states:

- `0`: unpaid
- `1`: paid
- `2`: timeout closed
- `3`: cancelled
- `4`: refunded
- `5`: completed
- `6`: reviewed

The current refund helper indicates these refund states:

- `0`, `1`: processing
- `2`: refund success
- `3`: retrying
- `4`: refund failed
- `5`: manual processing
- `6`: refund cancelled

## Recommended Filtering Rules

The tab model should be explicit and deterministic. Each tab is defined by frontend predicates over the loaded order records.

### `UNPAID`

Show orders where:

- `order.status === 0`

### `PAID`

Show orders where:

- `order.status === 1`
- `order.latestRefundStatus === null`

This intentionally excludes paid orders that have already entered any refund flow. Those orders belong under `REFUND`, not under `PAID`.

### `CANCELLED`

Show orders where:

- `order.status === 3`

This tab is only for order cancellation. It does not include refund-cancelled records.

### `REVIEW > PENDING_REVIEW`

Show orders where:

- `order.canReview === true`
- refund is not in a blocking state

Use the existing refund-blocking rule already present in `MineOrderView.vue`:

- blocking refund states: `0`, `1`, `2`, `3`, `5`

This keeps the behavior aligned with the current button logic and avoids showing a review entry for an order that is still under refund handling.

### `REVIEW > REVIEWED`

Show orders where at least one of the following is true:

- `order.status === 6`
- `order.hasReview === true`
- `Boolean(order.reviewId)`

This keeps the page resilient if some historical data exposes review metadata even when the main order status is not perfectly normalized.

### `REFUND > IN_PROGRESS`

Show orders where:

- `order.latestRefundStatus` is one of `0`, `1`, `3`, `5`

This covers processing, retrying, and manual-handling paths.

### `REFUND > FINISHED`

Show orders where:

- `order.latestRefundStatus` is one of `2`, `4`, `6`

This means the refund flow has reached a terminal state. The required terminal outcomes are:

- refund success
- refund failed
- refund cancelled

## Interaction Design

### Page Structure

The page keeps the current top bar:

- back button
- page title
- refresh button

Below the top bar, add:

1. first-level status tab row
2. optional second-level tab row when `REVIEW` or `REFUND` is selected
3. filtered order list
4. empty state or pagination area

The hierarchy remains:

- page context first
- filter context second
- content list third

### Tab Behavior

- clicking a first-level tab updates the active tab immediately
- switching first-level tabs resets pagination state and reloads the first page
- clicking a second-level tab under `REVIEW` or `REFUND` updates the list immediately
- switching second-level tabs also resets pagination state and reloads the first page
- the list area should never show mixed states for the active tab

### Empty-State Behavior

The page should continue using the existing `EmptyState` component, but the copy should become tab-aware.

Recommended empty-state mapping:

- `UNPAID`: no unpaid orders yet
- `PAID`: no paid orders yet
- `CANCELLED`: no cancelled orders yet
- `REVIEW > PENDING_REVIEW`: no orders waiting for review
- `REVIEW > REVIEWED`: no reviewed orders yet
- `REFUND > IN_PROGRESS`: no refunds in progress
- `REFUND > FINISHED`: no finished refund records yet

The CTA can continue to point to `/home`.

## Data-Loading Strategy

This redesign should stay frontend-only and preserve the current API contract.

Recommended approach:

1. keep using `fetchMyOrderPage`
2. keep enriching records with house titles and refund status
3. apply tab filtering in the page component before rendering
4. keep pagination behavior, but treat the active tab as part of local page state

Because filtering is done on the frontend, switching tabs must reset:

- `current`
- `hasMore`
- `orders`
- error message state

This keeps each tab focused on records loaded for that filter session and avoids carrying visually unrelated data across tabs.

## Component and State Shape

The current page can stay as a single component. The redesign does not need route splitting.

Recommended local state additions:

- `activePrimaryTab`
- `activeReviewTab`
- `activeRefundTab`

Recommended derived helpers:

- `showSecondaryTabs`
- `visibleOrders`
- `emptyStateConfig`

Recommended extracted constants:

- first-level tab config
- review subtab config
- refund subtab config

Recommended extracted predicate helpers:

- `isUnpaidOrder`
- `isPaidOrder`
- `isCancelledOrder`
- `isPendingReviewOrder`
- `isReviewedOrder`
- `isRefundInProgressOrder`
- `isRefundFinishedOrder`

This keeps the template readable and reduces the risk of scattering status logic across unrelated button conditions.

## Styling Direction

The order page already uses the shared surface/card style. The new tab navigation should follow that language rather than introducing a new visual system.

Recommended styling constraints:

- tabs should read as compact segmented navigation, not as full-width page buttons
- active state must be visually obvious
- inactive state should remain readable but quieter
- the second-level tab row should look related to the first-level row, but slightly lighter in emphasis
- mobile must support horizontal wrap or horizontal scroll without breaking the card layout

## Error Handling

The existing error handling should remain:

- load failure shows an error text block
- reload continues to work

Additional UI rules:

- tab switching should clear stale error state before the next load
- empty state should not be confused with error state
- if loading a new tab, the navigation remains visible and only the list area changes

## Testing Strategy

Update `frontend/src/views/__tests__/MineOrderView.spec.js` to cover the navigation and filtering rules.

Required test coverage:

- unpaid tab shows only unpaid orders
- paid tab excludes records with any refund status
- cancelled tab shows only cancelled orders
- review tab defaults to `PENDING_REVIEW`
- `REVIEW > PENDING_REVIEW` shows review-eligible orders
- `REVIEW > REVIEWED` shows reviewed orders
- refund tab defaults to `IN_PROGRESS`
- `REFUND > IN_PROGRESS` shows in-progress refund records
- `REFUND > FINISHED` shows success, failure, and cancelled refund records
- switching tabs changes the rendered list and empty state correctly

Keep the existing action-button tests where possible so this redesign does not regress:

- continue payment
- complete order
- go review
- edit review
- apply refund

## Risks and Trade-Offs

### Frontend Filtering on Paginated Data

The main trade-off of this design is that tab grouping is still built on top of the current paginated list API rather than a backend-filtered query.

This is acceptable for the current stage because:

- the user explicitly wants a frontend-page modification
- the route should remain unchanged
- the existing page already operates on relatively small page sizes
- the implementation risk stays low

If the order dataset later becomes large, the next iteration should move the active tab into request parameters and let the backend filter server-side.

### Status Overlap Avoidance

The design intentionally prevents confusing overlap in the most important place:

- a paid order with refund history is no longer shown under `PAID`

This keeps the user mental model cleaner, even if the backend still stores that order as `status === 1`.

## Implementation Recommendation

Implement the redesign inside the existing `MineOrderView.vue` component with local tab state, extracted status predicates, and tab-aware data reload behavior.

This is the best fit for the current codebase because:

- it preserves the single-page route the user requested
- it minimizes risk to existing payment, review, and refund actions
- it keeps the change limited to the frontend order page and its tests
- it leaves a clean path to future query-param or backend-filtered upgrades

## Success Criteria

The change is successful when:

- the order page no longer shows all statuses mixed together
- first-level tabs clearly separate unpaid, paid, cancelled, review, and refund contexts
- `REVIEW` exposes `PENDING_REVIEW` and `REVIEWED`
- `REFUND` exposes `IN_PROGRESS` and `FINISHED`
- each tab renders only the intended records
- the existing action buttons still behave correctly for the records shown
