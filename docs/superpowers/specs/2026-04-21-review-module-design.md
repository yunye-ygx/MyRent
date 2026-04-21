# Review Module Design

Date: 2026-04-21

## Background

The current project already supports house detail browsing, deposit order creation, mock payment, refund application, and the renter order list. However, it does not yet support a renter review flow for completed rentals.

For the first release, the goal is not to cover every extreme case. The goal is to ship a production-usable first version with a clean review loop, clear business boundaries, and common error handling.

The confirmed product scope is:

- renters can view reviews on the house detail page
- renters can submit a review only after payment succeeds and the order is completed
- each order can create only one review
- that review can be edited once
- first release excludes landlord replies, images, follow-up reviews, likes, and reports

## Problem Statement

The current order state model is not enough to express the review lifecycle. It distinguishes payment and closure outcomes, but it does not have an explicit "completed" or "reviewed" terminal path for normal fulfilled rentals.

Without that distinction, the system cannot reliably answer:

- when a renter is allowed to review
- when the renter should see "complete order"
- when the renter should see "write review"
- when refund must become unavailable
- how the renter order list should show review progression

The project also has no review table, no review API, and no house detail aggregation for score average or review count.

## Goals

- Add a first-release renter review module that fits the existing order and house flows.
- Make review eligibility depend on paid-and-completed orders.
- Show review summary and recent reviews on the house detail page.
- Enforce one review per order with a database-level uniqueness guarantee.
- Allow one edit after initial submission.
- Keep the first-release implementation small, readable, and aligned with the current service/controller style.

## Non-Goals

- Landlord replies
- Review images
- Follow-up reviews
- Likes
- Reports
- Review moderation workflow
- Anonymous review modes beyond showing the current user nickname
- House auto relisting when an order is completed

## Confirmed Product Decisions

### Review Eligibility

- only the renter who owns the order can review
- only orders in `COMPLETED` state can create the first review
- payment success is still required because only paid orders can reach `COMPLETED`
- once the order becomes `REVIEWED`, the renter can still edit the review one time

### Entry Points

- the renter review entry exists only in "My Orders"
- the house detail page only displays reviews and summary data

### Review Form

- fields: `score` and `content`
- `score` is required and limited to `1-5`
- `content` is required
- reviewer nickname is displayed; avatar is out of scope

### House Detail Display

- show average score
- show review count
- show the latest 5 reviews
- support a later "view more" or pagination path

### Refund Boundary

- once an order is `COMPLETED`, refund is no longer allowed
- once an order is `REVIEWED`, refund is no longer allowed

### House State Boundary

- completing an order does not change house availability in the first release
- review eligibility is based on order state, not house state

## Recommended State Model

Replace the review-relevant order progression with these states:

- `UNPAID`
- `PAID`
- `COMPLETED`
- `REVIEWED`
- `CLOSED_TIMEOUT`
- `USER_CANCELLED`
- `REFUNDED`

Recommended semantic flow:

1. renter creates a deposit order
2. successful payment moves the order to `PAID`
3. renter manually completes the order from "My Orders", moving it to `COMPLETED`
4. renter submits the first review, moving the order to `REVIEWED`
5. renter may edit that review once; order state remains `REVIEWED`

This keeps `PAID` semantically clean and makes "completed but not yet reviewed" visible without inventing a vague waiting state.

## Review Data Model

Create a dedicated `review` table instead of storing review fields on `order` or `house`.

Recommended columns:

- `id`
- `order_id`
- `order_no`
- `house_id`
- `user_id`
- `score`
- `content`
- `edit_count`
- `create_time`
- `update_time`

Recommended constraints:

- unique index on `order_no`
- index on `house_id`
- index on `user_id`
- `score` must be an integer from `1` to `5`
- `content` must be non-empty
- `edit_count` defaults to `0`

## Review Data Ownership

When a review is created, persist these foreign-reference values from the order:

- `order_id`
- `order_no`
- `house_id`
- `user_id`

This avoids forcing house review queries to re-derive review ownership by joining through the order table every time.

The review table should not store a nickname snapshot in the first release. Reviewer display name can be resolved from the `user` table when returning house review data. If the current nickname is missing, the response should fall back to a generic label such as `User`.

## API Design

Keep the first-release API surface small.

### 1. Complete Order

- `POST /order/{orderNo}/complete`

Behavior:

- only the current renter can complete the order
- only orders in `PAID` state are eligible
- successful completion changes the order to `COMPLETED`

### 2. Create Review

- `POST /review`

Behavior:

- only the current renter can create the review
- only orders in `COMPLETED` state are eligible
- create one `review` row
- change the order state from `COMPLETED` to `REVIEWED`

### 3. Edit Review

- `PUT /review/{id}`

Behavior:

- only the review author can edit
- only reviews with `edit_count = 0` are editable
- update `score`, `content`, and `update_time`
- increment `edit_count` to `1`
- do not change the order state

### 4. House Review Query

- `GET /house/{houseId}/reviews`

Behavior:

- return average score
- return review count
- return the latest 5 review items by default
- support page parameters for future expansion

### 5. Order List Review Actions

Do not add a separate per-order review-status endpoint in the first release if the same information can be embedded into the order list response.

The renter order list should directly expose enough fields for the frontend to determine:

- can complete order
- can create review
- can edit review
- associated review id when one exists

This avoids one extra request per order row.

## Frontend Interaction Design

### My Orders

The renter order page should show action buttons by order state:

- `PAID` -> show `Complete Order`
- `COMPLETED` -> show `Write Review`
- `REVIEWED` with editable review -> show `Edit Review`
- all other states -> no review-related action

The first release should prefer a dedicated review page over a modal. A separate page keeps creation and edit state simpler and avoids squeezing validation into the current order list layout.

### House Detail

The house detail page should show:

- average score
- review count
- latest 5 reviews

It should not show a "write review" entry point in the first release.

## Transaction Rules

### Complete Order

Order completion should use a state-conditional update:

- update succeeds only when the order belongs to the current user and is still `PAID`

This prevents repeated clicks from incorrectly mutating orders in other states.

### Create Review

Review creation and order-state transition must be in one transaction:

1. verify order ownership and `COMPLETED` state
2. insert the review row
3. change order state from `COMPLETED` to `REVIEWED`

If any part fails, the whole transaction should roll back.

### Edit Review

Review editing should also use a guarded update:

- only update when `id` matches, `user_id` matches, and `edit_count = 0`

This prevents double-submit paths from consuming more than one edit.

## Validation and Error Handling

The first release should handle common business errors explicitly.

### Complete Order Validation

- order must exist
- order must belong to the current user
- order must be in `PAID`

### Create Review Validation

- order must exist
- order must belong to the current user
- order must be in `COMPLETED`
- review must not already exist for the order
- `score` must be `1-5`
- `content` must be non-empty

### Edit Review Validation

- review must exist
- review must belong to the current user
- `edit_count` must be `0`
- `score` must be `1-5`
- `content` must be non-empty

### Refund Validation

The existing refund application path must reject:

- `COMPLETED` orders
- `REVIEWED` orders

### House Review Query Behavior

If the house has no reviews:

- return an empty review list
- return average score as `0`
- return review count as `0`

This is not an error condition.

## Idempotency and Concurrency

Common first-release concurrency protection should be enough:

- unique `review.order_no` index prevents duplicate review creation for the same order
- complete-order update uses the current expected order state
- create-review transaction updates order state conditionally from `COMPLETED` to `REVIEWED`
- edit-review update checks `edit_count = 0`
- frontend action buttons should enter loading state to reduce repeat clicks

If the database unique constraint rejects a duplicate review insert, the service should convert that into a readable business error instead of exposing a raw SQL exception.

## Query Shape for House Reviews

The house review response should contain:

- summary block
  - `averageScore`
  - `reviewCount`
- review item list
  - `reviewId`
  - `orderNo`
  - `score`
  - `content`
  - `reviewerName`
  - `createTime`
  - `updateTime`
  - `edited` flag derived from `edit_count > 0`

For the first release, sort by newest review first.

## Query Shape for My Orders

The renter order response should expose enough fields for the frontend to render clear actions without extra per-row calls.

Recommended derived fields:

- `canComplete`
- `canReview`
- `canEditReview`
- `reviewId`
- `hasReview`

These can be built in the order list service by joining or post-processing against the review table.

## Implementation Shape

Recommended backend additions:

- `Review` entity
- `ReviewMapper`
- `IReviewService`
- `ReviewServiceImpl`
- `ReviewController`
- SQL schema file for `review`
- order completion method added to `IOrderService` and `OrderServiceImpl`

Recommended frontend additions:

- review API module or extension under `frontend/src/api`
- review page under `frontend/src/views`
- order list action wiring in `MineOrderView.vue`
- house detail review block in `HouseDetailView.vue`

## Testing Strategy

Add or update tests for:

- renter can complete a paid order
- completing a non-paid order is rejected
- renter can create a review only for a completed order
- creating a review moves order state to `REVIEWED`
- duplicate review creation for one order is rejected
- renter can edit a review exactly once
- second edit attempt is rejected
- completed and reviewed orders cannot request refund
- house review query returns correct average score and count
- house review query returns empty-state data correctly

## Rollout Order

1. add new order states `COMPLETED` and `REVIEWED`
2. add review table schema
3. add backend order completion API
4. add backend review create and edit APIs
5. add house review list and summary query
6. extend renter order list data with review action flags
7. add frontend order actions and review page
8. add frontend house detail review block
9. update tests for the new review lifecycle

## Recommendation

Implement the review module as a dedicated order-bound review system with a small state extension on `order`.

This is the best first-release fit for the current codebase because:

- the project already treats renter actions as order-centered
- review eligibility is naturally tied to order ownership and lifecycle
- one-review-per-order maps cleanly to a unique `order_no` constraint
- the approach avoids overengineering while leaving room for future review expansion

The key rule set is:

- paid order can be completed
- completed order can be reviewed
- reviewed order can be edited once
- house detail only displays review data
