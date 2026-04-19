# Payment Anomaly Hardening Design

**Goal:** Upgrade the current mock payment closed loop so MyRent can truly handle the two payment exception classes that matter for this phase:

- duplicate processing
- lost payment callback / delayed callback

This design intentionally extends the current payment model to support `one order -> many payment attempts`, because duplicate payment handling cannot be implemented honestly on top of a one-order-one-payment model.

---

## 1. Scope

### In Scope

- upgrade `payment` from single-attempt to multi-attempt per order
- add a minimal internal mock third-party truth table
- harden callback handling against duplicate callbacks
- support repeated payment attempts from My Orders
- distinguish duplicate callback vs duplicate payment
- add active query compensation for lost callbacks
- check third-party truth before timeout close
- add a scheduled suspicious-payment compensation task
- update SQL schema files, entities, services, and tests

### Out of Scope

- real WeChat Pay / Alipay integration
- refunds
- signature verification
- partial payment
- multiple channels
- automatic refund for extra successful duplicate payments
- risk control, rate limiting, or anti-fraud features

---

## 2. Current State

The current code path is:

1. `POST /order/create`
2. backend creates one `order`
3. backend creates one `payment`
4. frontend redirects to mock checkout
5. opening checkout changes `payment` from `PENDING` to `PAYING`
6. mock callback sets:
   - `payment -> PAID` on success
   - `order -> PAID_LOCKED` on success
   - or cancel / timeout close states
7. timeout consumer directly closes `UNPAID` order and any `PENDING/PAYING` payment

Current gap summary:

- duplicate callback is not explicitly hardened
- duplicate payment is not modeled end-to-end
- lost callback compensation has no third-party truth source
- timeout close may close an order before checking third-party truth

---

## 3. Design Principles

### 3.1 Order vs Payment vs Third-Party Truth

- `order` stores the business result
- `payment` stores each payment attempt
- `mock_pay_trade` stores the mock third-party truth

### 3.2 One Payment Attempt, One Payment Record

Each new payment launch from MyRent creates a new `payment_no`.

This means:

- initial submit order creates the first payment
- repay from My Orders creates a new payment
- retry inside the same third-party checkout page is still the same payment

### 3.3 Order Has Only One Final Successful Payment

An order may have many payment attempts, but only one payment may win the final success right.

The winning payment is recorded by `order.success_payment_no`.

### 3.4 Push-Pull Combination

- push: mock third-party callback
- pull: active query against `mock_pay_trade`

Lost callback handling must not rely only on callback retry.

---

## 4. Data Model

### 4.1 Order Table

`order` remains one row per business order.

New field:

- `success_payment_no`

Purpose:

- record which payment attempt finally paid the order
- distinguish duplicate callback from duplicate payment
- keep final business truth easy to inspect

Minimal required fields:

- `id`
- `order_no`
- `user_id`
- `house_id`
- `amount`
- `status`
- `expire_time`
- `paid_time`
- `success_payment_no`
- `close_time`
- `create_time`
- `update_time`

Status remains:

- `0 = UNPAID`
- `1 = PAID_LOCKED`
- `2 = CLOSED_TIMEOUT`
- `3 = USER_CANCELLED`

### 4.2 Payment Table

`payment` becomes the payment-attempt table.

Relationship change:

- one `order` may map to many `payment`
- `payment.order_no` must no longer be unique

Minimal required fields:

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

Status:

- `0 = PENDING`
- `1 = PAYING`
- `2 = PAID`
- `3 = USER_CANCELLED`
- `4 = CLOSED_TIMEOUT`
- `5 = DUPLICATE_PAID`

Meaning:

- `PENDING`: payment record created, checkout not opened yet
- `PAYING`: checkout opened, result not yet finalized
- `PAID`: this payment itself succeeded and won the order
- `USER_CANCELLED`: user cancelled this attempt
- `CLOSED_TIMEOUT`: this attempt was closed on timeout
- `DUPLICATE_PAID`: this attempt succeeded at third-party side but lost the order race

### 4.3 Mock Third-Party Truth Table

New table: `mock_pay_trade`

Purpose:

- represent mock third-party transaction truth
- keep truth even when business callback fails
- support active query before timeout close and in compensation job

Minimal fields:

- `id`
- `payment_no`
- `order_no`
- `third_party_trade_no`
- `status`
- `amount`
- `paid_time`
- `callback_status`
- `last_callback_time`
- `create_time`
- `update_time`

Trade status:

- `0 = CREATED`
- `1 = PAYING`
- `2 = SUCCESS`
- `3 = USER_CANCELLED`
- `4 = CLOSED_TIMEOUT`

Callback status:

- `0 = NOT_SENT_OR_NOT_CONFIRMED`
- `1 = CALLBACK_CONFIRMED`
- `2 = CALLBACK_FAILED`

### 4.4 Minimal SQL for `mock_pay_trade`

```sql
CREATE TABLE `mock_pay_trade` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'mock trade id',
  `payment_no` varchar(64) NOT NULL COMMENT 'internal payment number',
  `order_no` varchar(64) NOT NULL COMMENT 'business order number',
  `third_party_trade_no` varchar(64) DEFAULT NULL COMMENT 'mock third-party trade number',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0 created, 1 paying, 2 success, 3 user cancelled, 4 timeout closed',
  `amount` int NOT NULL COMMENT 'payment amount in cents',
  `paid_time` datetime DEFAULT NULL COMMENT 'mock payment success time',
  `callback_status` tinyint NOT NULL DEFAULT '0' COMMENT '0 not sent or not confirmed, 1 callback confirmed, 2 callback failed',
  `last_callback_time` datetime DEFAULT NULL COMMENT 'last callback attempt time',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mock_trade_payment_no` (`payment_no`),
  UNIQUE KEY `uk_mock_trade_third_party_trade_no` (`third_party_trade_no`),
  KEY `idx_mock_trade_order_no` (`order_no`),
  KEY `idx_mock_trade_status` (`status`),
  KEY `idx_mock_trade_callback_status` (`callback_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='mock third-party payment trade';
```

---

## 5. Core Flow

### 5.1 Submit Order

1. User clicks submit order.
2. Backend creates:
   - one `order`
   - first `payment`
   - first `mock_pay_trade`
3. Response returns:
   - `orderNo`
   - `paymentNo`
   - `expireTime`
   - `mockPayUrl`
4. Frontend redirects to checkout.

### 5.2 Open Checkout

When checkout opens:

- `payment.status: PENDING -> PAYING`
- `mock_pay_trade.status: CREATED -> PAYING`

### 5.3 Success Inside Mock Third-Party

1. Mock side first writes truth:
   - `mock_pay_trade.status -> SUCCESS`
   - set `third_party_trade_no`
   - set `paid_time`
2. Mock side then calls business callback.
3. If business callback succeeds:
   - `mock_pay_trade.callback_status -> CALLBACK_CONFIRMED`
4. If callback fails:
   - `mock_pay_trade.callback_status -> CALLBACK_FAILED`

### 5.4 Repay From My Orders

If order is still `UNPAID`, user may click pay again from My Orders.

Backend behavior:

- do not create a new order
- create a new `payment`
- create a new `mock_pay_trade`
- return a new `paymentNo` and new `mockPayUrl`

Old unfinished payments are not proactively changed.

If an old payment later succeeds, it is treated as duplicate payment competition.

---

## 6. Duplicate Processing Design

### 6.1 Duplicate Callback

Definition:

- same `payment_no`
- same payment result callback delivered more than once

Minimal handling rule:

1. find `payment` by `payment_no`
2. if `payment.status` is already `PAID`, return success directly

This is payment-level idempotency.

### 6.2 Duplicate Payment

Definition:

- different `payment_no`
- same `order_no`
- more than one payment succeeds on the mock third-party side

Minimal handling rule:

1. current callback is a success callback for one payment
2. attempt to update order by condition:
   - `order.status = UNPAID`
3. if order update succeeds:
   - current payment wins
   - set `order.status = PAID_LOCKED`
   - set `order.paid_time`
   - set `order.success_payment_no = current payment_no`
   - set current `payment.status = PAID`
4. if order update fails:
   - query order again
   - if `order.success_payment_no != current payment_no`
   - mark current payment as `DUPLICATE_PAID`

This is order-level final success control.

### 6.3 Why Both Layers Are Needed

- `payment` layer prevents one payment from being processed many times
- `order` layer prevents many payments from finalizing one order many times

Without both layers, duplicate handling is incomplete.

---

## 7. Lost Callback Design

### 7.1 Problem Definition

Lost callback means:

- mock third-party already has final truth
- business system did not successfully receive or finish callback processing

Typical reasons:

- service downtime
- callback timeout
- callback retry exhausted
- local exception during callback processing

### 7.2 Push-Pull Combination

Push path:

- mock third-party callback retries

Pull path:

- business system actively queries `mock_pay_trade`

### 7.3 Suspicious Payment Definition

A suspicious payment is:

- `order.status = UNPAID`
- `payment.status = PAYING`
- corresponding `mock_pay_trade.status = SUCCESS`
- `mock_pay_trade.callback_status != CALLBACK_CONFIRMED`

This means:

- third-party truth already says success
- local business system still has not closed the order

### 7.4 Query Before Timeout Close

Timeout close must change from:

- directly close unpaid order

to:

- query current active payment's `mock_pay_trade` first

Decision rule:

1. if trade truth is `SUCCESS`
   - first repair local payment and order to paid
   - do not close order
2. if trade truth is not success
   - continue timeout close

This prevents paid-but-closed mistakes.

### 7.5 Scheduled Compensation

Add a scheduled job to scan suspicious payments.

Recommended minimal logic:

1. scan recent `PAYING` payments whose orders are still `UNPAID`
2. join or query corresponding `mock_pay_trade`
3. if trade truth is success but callback is not confirmed
   - execute the same local success-close logic
4. if repair succeeds
   - mark `mock_pay_trade.callback_status = CALLBACK_CONFIRMED`

This improves user experience by restoring paid state earlier, not only at timeout time.

---

## 8. State Transition Rules

### 8.1 Order

- `UNPAID -> PAID_LOCKED`
- `UNPAID -> CLOSED_TIMEOUT`
- `UNPAID -> USER_CANCELLED`

No backward transition is allowed.

### 8.2 Payment

- `PENDING -> PAYING`
- `PAYING -> PAID`
- `PAYING -> USER_CANCELLED`
- `PENDING/PAYING -> CLOSED_TIMEOUT`
- `PAYING -> DUPLICATE_PAID`

### 8.3 Mock Pay Trade

- `CREATED -> PAYING`
- `PAYING -> SUCCESS`
- `PAYING -> USER_CANCELLED`
- `CREATED/PAYING -> CLOSED_TIMEOUT`

---

## 9. Service and API Changes

### 9.1 Order Creation

`POST /order/create`

Add behavior:

- create first `mock_pay_trade`

### 9.2 Repay

Add or complete:

- `POST /order/{orderNo}/repay`

Rules:

- order must exist
- order must still be `UNPAID`
- create new `payment`
- create new `mock_pay_trade`
- return new payment info

### 9.3 Mock Checkout Query

`GET /payment/mock-checkout/{paymentNo}`

Add behavior:

- move matching `mock_pay_trade` to `PAYING` when first opened

### 9.4 Mock Callback

`POST /payment/callback/mock`

Refactor callback processing into an idempotent service method that can be called by:

- callback path
- scheduled compensation path
- timeout-before-close path

### 9.5 Active Query Service

Add a payment query service method, conceptually:

- query trade truth by `paymentNo`

Since this is an internal mock provider, the implementation queries `mock_pay_trade`.

---

## 10. Timeout Consumer Changes

Current timeout consumer directly closes unpaid order.

New required behavior:

1. load order by `orderNo`
2. if order is not `UNPAID`, ack and return
3. select the latest active payment attempt for this order
4. query corresponding `mock_pay_trade`
5. if trade truth is `SUCCESS`
   - repair local paid state
   - ack and return
6. otherwise
   - close order
   - close all unfinished payments for this order
   - close unfinished mock trades for this order
   - release house

The consumer must not close first and query later.

---

## 11. SQL and Entity Migration

### 11.1 Order

- add `success_payment_no`
- update entity class
- update schema SQL files

### 11.2 Payment

- remove unique constraint on `order_no`
- add `DUPLICATE_PAID` status in code
- update schema SQL files

### 11.3 Mock Pay Trade

- add new table SQL
- add entity, mapper, service

---

## 12. Testing

Minimum tests required:

1. `same payment callback twice`
   - second callback is ignored as idempotent

2. `two different payments for one order both succeed`
   - first winner updates order
   - second becomes `DUPLICATE_PAID`

3. `mock trade success but callback fails`
   - scheduled compensation repairs order to paid

4. `mock trade success right before timeout`
   - timeout consumer queries truth first
   - order becomes paid, not timeout closed

5. `repay creates new payment`
   - same order, different `payment_no`

---

## 13. Implementation Order

1. update schema files for `order`, `payment`, and add `mock_pay_trade`
2. update entities and status constants
3. add `mock_pay_trade` mapper/service
4. create mock trade during order creation
5. add or complete repay flow to create new payment attempt
6. refactor callback success processing into reusable idempotent service logic
7. harden duplicate callback handling
8. harden duplicate payment handling with `success_payment_no`
9. change timeout consumer to query truth before close
10. add scheduled suspicious-payment compensation task
11. update and add tests

---

## 14. Acceptance Criteria

This design is complete when:

- one order can create many payments across retries
- duplicate callback for same `payment_no` is harmless
- two successful payments for one order leave only one final winning payment
- order stores `success_payment_no`
- callback loss can be repaired from `mock_pay_trade`
- timeout close checks mock truth before closing
- suspicious successful-but-unconfirmed payments can be repaired by scheduled compensation

