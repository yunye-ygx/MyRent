# Renter Checkout Mock Payment Design

**Goal:** Build the smallest usable renter-side order and mock payment closed loop for MyRent: submit deposit order, jump to mock checkout, pay successfully or cancel the order, and auto-close unpaid orders on timeout.

**Project Positioning:** This remains a Java backend project centered on renter flows. No admin console or landlord console is introduced in this phase. Landlords continue to use the same frontend only for chat visibility, while house publishing and listing changes still happen through Knife4j/MySQL.

**Primary Flow:** `find house -> consult -> submit order -> jump to mock checkout -> pay success / cancel order / timeout close`

---

## 1. Scope

### In Scope

- Renter-side deposit order creation from house detail page
- Automatic redirect to a mock checkout page after order creation
- Internal mock payment gateway that behaves like a third-party checkout at a structural level
- Backend payment callback handling
- Minimal order and payment state machine
- Timeout close and house release linkage
- My Orders page support for "continue payment" while the order is still unpaid
- SQL schema updates for `order`, `payment`, and the combined schema file

### Out of Scope

- Real WeChat Pay / Alipay integration
- Admin backend
- Landlord-side publishing and management frontend
- Complex payment retry, duplicate callback recovery, multi-attempt payment history
- Refunds, dispute handling, partial payment, coupons
- Rich exception handling beyond the minimum state-guard needed for a usable closed loop

---

## 2. Product Semantics

This phase distinguishes only three user-visible order endings after an order is created:

1. **Payment Success**
   The deposit order is paid and the house remains locked.

2. **User Cancelled**
   The user explicitly cancels the order on the mock checkout page, and the house is released.

3. **Timeout Closed**
   The user does nothing until expiration, and the system closes the order and releases the house.

The mock checkout page is not treated as an in-page business button group. Instead, it is treated as a simulated third-party payment page:

- The business system creates the order and payment record.
- The business system returns a `mockPayUrl`.
- The frontend jumps to the mock checkout page.
- The mock checkout page submits the result back to the business system through a mock callback endpoint.

This keeps the design close to a real payment integration model without introducing external providers.

---

## 3. Architecture

### 3.1 Business System Responsibilities

The business system is responsible for:

- locking the house
- creating `order`
- creating `payment`
- returning `mockPayUrl`
- receiving mock payment callback
- updating business state
- releasing the house on cancel or timeout

### 3.2 Mock Gateway Responsibilities

The mock gateway is responsible for:

- rendering a checkout page
- showing order number, amount, and remaining time
- offering only two actions in this phase:
  - `Pay Success`
  - `Cancel Order`
- generating a mock `thirdPartyTradeNo`
- invoking the business callback endpoint

### 3.3 State Closure Principle

Frontend actions never directly mark an order as paid. Final order state must always be closed on the backend.

---

## 4. Data Model

### 4.1 Order Status

`order.status`

- `0 = UNPAID`
- `1 = PAID_LOCKED`
- `2 = CLOSED_TIMEOUT`
- `3 = USER_CANCELLED`

Meaning:

- `UNPAID`: order created, house locked, waiting for payment
- `PAID_LOCKED`: payment completed successfully
- `CLOSED_TIMEOUT`: unpaid until expiration, system closed it
- `USER_CANCELLED`: user explicitly cancelled on checkout

### 4.2 Payment Status

`payment.status`

- `0 = WAITING`
- `1 = SUCCESS`
- `3 = CANCELLED`
- `4 = CLOSED_TIMEOUT`

This phase intentionally skips `FAILED` and multiple payment attempts to keep the first version minimal and focused.

### 4.3 Order Table Shape

The `order` table should support:

- order id and business order number
- renter id and house id
- deposit amount
- order status
- payment expiration time
- payment success time
- close time
- create/update timestamps

Fields required for this phase:

- `id`
- `order_no`
- `user_id`
- `house_id`
- `amount`
- `status`
- `expire_time`
- `paid_time`
- `close_time`
- `create_time`
- `update_time`

### 4.4 Payment Table Shape

The `payment` table should support:

- business payment number
- related order number
- user id
- amount
- mock channel
- mock third-party trade number
- callback request number reserved for later idempotency work
- payment status
- expire time
- paid time
- callback time
- failure/cancel reason
- create/update timestamps

Fields required for this phase:

- `id`
- `payment_no`
- `order_no`
- `user_id`
- `pay_amount`
- `channel`
- `third_party_trade_no`
- `callback_no`
- `status`
- `expire_time`
- `paid_time`
- `callback_time`
- `fail_reason`
- `create_time`
- `update_time`

### 4.5 Minimal Relationship Rule

For the first usable closed loop:

- one `order` maps to one `payment`
- `payment.order_no` stays unique for now

This constraint can be relaxed later when adding retryable multi-attempt payment behavior.

---

## 5. Core Flow Design

### 5.1 Submit Order and Jump to Checkout

1. User clicks submit order on house detail page.
2. Backend:
   - verifies login
   - locks the house
   - creates `order` with status `UNPAID`
   - creates `payment` with status `WAITING`
   - builds `mockPayUrl`
3. Response returns:
   - `orderNo`
   - `paymentNo`
   - `expireTime`
   - `mockPayUrl`
4. Frontend immediately redirects to `mockPayUrl`.

### 5.2 Mock Checkout Pay Success

1. User clicks `Pay Success` on mock checkout page.
2. Mock gateway generates:
   - `thirdPartyTradeNo`
   - `callbackNo`
   - success status
3. Mock gateway calls business callback endpoint.
4. Backend updates:
   - `payment: WAITING -> SUCCESS`
   - `order: UNPAID -> PAID_LOCKED`
5. Frontend returns to business result page or My Orders page.

### 5.3 Mock Checkout Cancel Order

1. User clicks `Cancel Order` on mock checkout page.
2. Mock gateway calls business callback endpoint or cancel endpoint.
3. Backend updates:
   - `payment: WAITING -> CANCELLED`
   - `order: UNPAID -> USER_CANCELLED`
4. Backend releases the house lock.
5. Frontend returns to order result view.

### 5.4 Timeout Close

1. User submits order but does not pay.
2. Existing timeout mechanism triggers after expiration.
3. Backend checks whether the order is still `UNPAID`.
4. If yes:
   - `payment: WAITING -> CLOSED_TIMEOUT`
   - `order: UNPAID -> CLOSED_TIMEOUT`
   - release house

---

## 6. Minimal State Guard Rules

This phase intentionally avoids complex payment exception handling, but one minimal guard is required:

Only orders in `UNPAID` state may transition to:

- `PAID_LOCKED`
- `USER_CANCELLED`
- `CLOSED_TIMEOUT`

This means all three closure operations must update by condition:

- pay success only affects `UNPAID`
- cancel order only affects `UNPAID`
- timeout close only affects `UNPAID`

This is the only concurrency-awareness requirement for the minimum viable version.

---

## 7. API Design

### 7.1 Business System APIs

#### `POST /order/create`

Purpose:

- create order
- create payment
- return checkout jump info

Response shape:

- `orderNo`
- `paymentNo`
- `expireTime`
- `mockPayUrl`

#### `GET /order/mine`

Purpose:

- list current user orders

#### `GET /order/{orderNo}`

Purpose:

- query order detail after payment/cancel/timeout

#### `POST /order/{orderNo}/repay`

Purpose:

- continue payment from My Orders if the order is still unpaid
- in this phase, reuse the existing payment and return the same `mockPayUrl`

Response shape:

- `orderNo`
- `paymentNo`
- `expireTime`
- `mockPayUrl`

#### `POST /payment/callback/mock`

Purpose:

- business-side callback entry from mock gateway

Minimum request fields:

- `orderNo`
- `paymentNo`
- `thirdPartyTradeNo`
- `callbackNo`
- `payStatus`
- `payAmount`
- `callbackTime`

### 7.2 Mock Gateway APIs / Pages

#### `GET /mock-pay/checkout`

Purpose:

- render mock checkout page

Input:

- `paymentNo` or signed token

Display:

- order number
- amount
- remaining time
- `Pay Success`
- `Cancel Order`

#### `POST /mock-pay/submit`

Purpose:

- submit the user action from mock checkout
- forward result to business callback endpoint

---

## 8. Frontend Behavior

Frontend in this phase stays utilitarian and renter-focused.

### House Detail Page

- submit order button remains the business entry
- on success, immediately redirect to `mockPayUrl`

### Mock Checkout Page

- owned by the same repo, but role-modeled as a simulated third-party page
- minimal UI only
- no extra product modules added

### My Orders Page

- display order status
- if order is `UNPAID`, show `Continue Payment`
- if order is `PAID_LOCKED`, show paid status
- if order is `CLOSED_TIMEOUT`, show timeout closed status
- if order is `USER_CANCELLED`, show cancelled status

---

## 9. SQL Files to Update During Implementation

The following schema files must be updated during implementation:

- `sql/rent-schema/order.sql`
- `sql/rent-schema/payment.sql`
- `sql/rent-schema/rent-schema-all.sql`

Implementation note:

- `payment.sql` currently contains duplicated `CREATE TABLE` content and must be cleaned before use.
- `rent-schema-all.sql` currently still reflects the old `order` and `payment` definitions and must be synchronized with the new minimal closed-loop design.

---

## 10. Implementation Order

1. Update schema files and entity models
2. Upgrade order creation endpoint to return checkout info
3. Add mock gateway page and submission flow
4. Add mock payment callback handling
5. Link timeout close to payment status and house release
6. Add My Orders continue payment entry
7. Verify three demonstration paths:
   - submit -> pay success
   - submit -> cancel order
   - submit -> no action -> timeout close

---

## 11. Acceptance Criteria

This design is considered complete when:

- a renter can submit a deposit order from house detail
- frontend jumps directly to a mock checkout page
- pay success updates order and payment correctly
- cancel order updates order and payment correctly and releases the house
- unpaid orders are auto-closed on timeout and release the house
- My Orders page can reopen checkout for unpaid orders
- schema SQL files are synchronized with the implemented data model

---

## 12. Future Extension Points

These are explicitly deferred:

- multiple payment attempts per order
- mock payment failure path
- duplicate callback idempotency hardening
- callback signature verification
- separated landlord frontend
- real payment provider integration

Keeping them out of this phase is intentional so the first deliverable remains a stable renter-side backend project with a believable payment-style closed loop.
