# Refund Compensation Design

Date: 2026-04-20

## Background

The current refund flow creates refund applications and processes them asynchronously, but the scheduled refund task only updates `payment_refund.status` to `SUCCESS` after a mock refund succeeds. It does not apply source-specific business compensation to the related order, payment, or house records.

This creates inconsistent business state after a successful refund. For example, a user-initiated refund can succeed while:

- the order remains `PAID_LOCKED`
- the winning payment remains `PAID`
- the house remains locked and unavailable

The system already has a synchronized house status update path through `houseCommandService.updateHouseStatusWithSync(...)`, which dispatches the existing house DB/ES sync event when the house status changes. The refund design should reuse that path instead of introducing direct house table updates.

## Problem Statement

The refund task currently treats all refund source types the same after third-party success. That is not correct because different refund sources imply different business outcomes:

- `USER_APPLY` means the paid and locked order should be closed by refund and the house should become available again.
- `DUPLICATE_PAID` means only the extra payment should be refunded; the winning paid order must remain effective and the house must stay locked.
- `LATE_SUCCESS_UNRECOVERABLE` means the late payment is refunded because the order could not be recovered; the house should not be touched by refund success.

Without type-specific compensation, the system can end up with correct fund flow but incorrect business state.

## Goals

- Close the refund success loop for order, payment, and house state.
- Apply different post-refund compensation rules by refund source type.
- Keep house DB/ES sync consistent by routing house status changes through the existing command service.
- Preserve idempotency so scheduled retries or repeated task execution do not corrupt state.
- Keep the implementation compatible with the current single-service transactional style.

## Non-Goals

- Redesign the refund application API contract.
- Introduce a new distributed event bus for refund completion.
- Implement partial refunds.
- Redesign all admin/manual compensation semantics in this change.

## Current Relevant State Model

### Order

- `UNPAID = 0`
- `PAID_LOCKED = 1`
- `CLOSED_TIMEOUT = 2`
- `USER_CANCELLED = 3`

### Payment

- `PENDING = 0`
- `PAYING = 1`
- `PAID = 2`
- `USER_CANCELLED = 3`
- `CLOSED_TIMEOUT = 4`
- `DUPLICATE_PAID = 5`

### Refund

- `PENDING = 0`
- `PROCESSING = 1`
- `SUCCESS = 2`
- `RETRY = 3`
- `FAILED = 4`
- `MANUAL_REVIEW = 5`
- `CANCELLED = 6`

## Proposed State Additions

Add explicit refunded terminal states:

- `OrderStatus.REFUNDED`
- `PaymentStatus.REFUNDED`

These states make refund completion visible in the main business records instead of leaving them in paid states after money has already been returned.

## Recommended Design

### Core Principle

Keep refund application creation unified, but split refund-success compensation by `sourceType`.

The system should distinguish between:

1. refund request creation
2. third-party refund execution
3. local business compensation after refund success

The current issue is at step 3, not step 1.

### Refund Processing Responsibilities

`applyRefund(...)`

- validates refund request eligibility
- creates an idempotent refund application record
- does not release houses or alter final order/payment states

`processSingleRefund(refund)`

- performs the third-party refund attempt
- updates retry-related refund task state on failure
- marks the refund record itself as successful on success
- invokes post-success business compensation

`handleRefundSuccess(refund)`

- dispatches to a source-specific compensation handler
- is responsible for updating order/payment/house state consistently

### Source-Specific Compensation

#### 1. `USER_APPLY`

Business meaning:

- the user is actively giving up a paid and currently locked order

Expected final state:

- refund record: `SUCCESS`
- order: `PAID_LOCKED -> REFUNDED`
- payment: `PAID -> REFUNDED`
- house: `LOCKED(2) -> AVAILABLE(1)`

House release must call:

- `houseCommandService.updateHouseStatusWithSync(houseId, 2, 1, "refund-user-apply-release")`

This preserves existing DB/ES house synchronization.

#### 2. `DUPLICATE_PAID`

Business meaning:

- only the extra payment should be refunded
- the winning paid order remains valid

Expected final state:

- refund record: `SUCCESS`
- duplicate payment: `DUPLICATE_PAID -> REFUNDED`
- order: unchanged
- house: unchanged

#### 3. `LATE_SUCCESS_UNRECOVERABLE`

Business meaning:

- a late successful payment arrived after timeout close
- the order could not be recovered, so the payment must be returned

Expected final state:

- refund record: `SUCCESS`
- affected payment: `REFUNDED`
- order: remains `CLOSED_TIMEOUT`
- house: unchanged

#### 4. `ADMIN_MANUAL` and `OTHER_COMPENSATION`

This change should not invent final business rules for these types. For now:

- keep a clear extension point in the dispatcher
- either no-op with explicit logging, or route to `MANUAL_REVIEW` until product rules are defined

The implementation should make these types impossible to ignore silently.

## Transaction Strategy

For successful refund completion, the local updates should happen in one transaction:

1. update `payment_refund`
2. update related `payment`
3. update related `order` if required
4. update related `house` through `houseCommandService` if required

If any local compensation step fails, the local transaction should roll back so the database does not record a partially applied post-refund state.

## Idempotency Rules

Refund success handling must be safe to run more than once.

Required safeguards:

- `USER_APPLY` order update should only apply when the order is still `PAID_LOCKED`
- `USER_APPLY` payment update should only apply when the payment is still `PAID`
- house release should only apply when the house is still locked
- duplicate paid refund compensation should only target the duplicate payment record
- if a handler detects that the desired final state has already been reached, it should treat that as success rather than failure

This is necessary because scheduled refund tasks can retry, and operational replay must not break consistency.

## Failure Handling

Two failure categories should be treated differently:

### Third-Party Refund Failure

- continue using existing refund retry logic
- update refund record to `RETRY` or `MANUAL_REVIEW`

### Local Compensation Failure After Third-Party Refund Success

This is the most dangerous case because money may already be returned while local business state is stale.

Recommended short-term handling:

- fail the local transaction
- keep the refund task eligible for retry by not finalizing the entire local success path
- log the exact compensation phase and source type

Recommended medium-term direction:

- separate "refund channel success" from "business compensation completed" if retries become ambiguous in practice

This design does not require the medium-term split immediately, but it should not block it.

## House Sync Behavior

If refund compensation changes house status through `houseCommandService.updateHouseStatusWithSync(...)`, the existing house sync pipeline will be triggered automatically because that method dispatches the core house sync event after a successful status update.

Therefore:

- refund compensation must not update the house table directly
- house command service is the only allowed path for refund-driven house release

## Implementation Shape

Recommended method structure:

- `processPendingRefunds()`
- `processSingleRefund(PaymentRefund refund)`
- `markRefundSuccess(PaymentRefund refund, LocalDateTime now)`
- `handleRefundSuccess(PaymentRefund refund)`
- `handleUserApplyRefundSuccess(PaymentRefund refund, LocalDateTime now)`
- `handleDuplicatePaidRefundSuccess(PaymentRefund refund, LocalDateTime now)`
- `handleLateSuccessRefundSuccess(PaymentRefund refund, LocalDateTime now)`

This keeps third-party refund execution separate from business-state compensation and avoids one large conditional block.

## Testing Strategy

Add or update tests for:

- user refund success updates refund, order, payment, and house status
- duplicate paid refund success updates only the duplicate payment and refund record
- late success unrecoverable refund success does not release the house
- repeated refund success handling is idempotent
- house release path uses `houseCommandService.updateHouseStatusWithSync(...)`
- unsupported source types are explicit and observable

## Rollout Order

1. add `OrderStatus.REFUNDED`
2. add `PaymentStatus.REFUNDED`
3. add refund-success dispatcher
4. implement `USER_APPLY` compensation
5. implement `DUPLICATE_PAID` compensation
6. implement `LATE_SUCCESS_UNRECOVERABLE` compensation
7. add tests for all three paths

## Recommendation

Implement the refund hardening in the current service layer rather than introducing a new event-driven architecture now.

This approach is the best fit for the existing codebase because:

- current flows already rely on transactional service methods
- house sync is already encapsulated behind `houseCommandService`
- source-specific handlers provide enough structure without adding system-wide complexity

The key design rule is simple:

- unified refund application
- source-specific refund-success compensation

That is the smallest change that closes the current consistency gap without overengineering the system.
