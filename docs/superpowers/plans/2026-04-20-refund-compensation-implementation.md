# Refund Compensation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make refund processing apply source-specific business compensation so successful refunds leave `payment_refund`, `order`, `payment`, and `house` in consistent final states.

**Architecture:** Keep refund application creation unchanged, but refactor refund success handling inside `PaymentRefundServiceImpl` into a dispatcher with source-specific handlers. Route house state changes through `IHouseCommandService.updateHouseStatusWithSync(...)` so the existing house DB/ES sync pipeline continues to work.

**Tech Stack:** Java, Spring Boot, MyBatis-Plus, JUnit 5, Mockito, Maven

---

## File Map

- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\common\OrderStatus.java`
  Responsibility: add `REFUNDED` terminal order state.
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\common\PaymentStatus.java`
  Responsibility: add `REFUNDED` terminal payment state.
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\PaymentRefundServiceImpl.java`
  Responsibility: inject `IHouseCommandService`, refactor refund success flow, add source-specific compensation handlers.
- Modify: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\PaymentRefundServiceImplTest.java`
  Responsibility: add failing and passing tests for `USER_APPLY`, `DUPLICATE_PAID`, and `LATE_SUCCESS_UNRECOVERABLE` compensation rules.

### Task 1: Add Refunded Terminal States

**Files:**
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\common\OrderStatus.java`
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\common\PaymentStatus.java`
- Test: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\PaymentRefundServiceImplTest.java`

- [ ] **Step 1: Write the failing contract test**

Add this test to `PaymentRefundServiceImplTest`:

```java
    @Test
    void refundedTerminalStatesShouldExist() {
        assertDoesNotThrow(() -> OrderStatus.class.getField("REFUNDED"));
        assertDoesNotThrow(() -> PaymentStatus.class.getField("REFUNDED"));
    }
```

- [ ] **Step 2: Run the targeted test to verify it fails**

Run:

```bash
mvn -Dtest=PaymentRefundServiceImplTest#refundedTerminalStatesShouldExist test
```

Expected: FAIL with `NoSuchFieldException` for `REFUNDED`.

- [ ] **Step 3: Add the new status constants**

Update `OrderStatus.java`:

```java
public final class OrderStatus {

    public static final int UNPAID = 0;
    public static final int PAID_LOCKED = 1;
    public static final int CLOSED_TIMEOUT = 2;
    public static final int USER_CANCELLED = 3;
    public static final int REFUNDED = 4;

    private OrderStatus() {
    }
}
```

Update `PaymentStatus.java`:

```java
public final class PaymentStatus {

    public static final int PENDING = 0;
    public static final int PAYING = 1;
    public static final int PAID = 2;
    public static final int USER_CANCELLED = 3;
    public static final int CLOSED_TIMEOUT = 4;
    public static final int DUPLICATE_PAID = 5;
    public static final int REFUNDED = 6;

    private PaymentStatus() {
    }
}
```

- [ ] **Step 4: Run the targeted test to verify it passes**

Run:

```bash
mvn -Dtest=PaymentRefundServiceImplTest#refundedTerminalStatesShouldExist test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/common/OrderStatus.java src/main/java/cn/yy/myrent/common/PaymentStatus.java src/test/java/cn/yy/myrent/service/impl/PaymentRefundServiceImplTest.java
git commit -m "feat(refund): add refunded terminal statuses"
```

### Task 2: Add Refund Success Dispatcher Skeleton

**Files:**
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\PaymentRefundServiceImpl.java`
- Test: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\PaymentRefundServiceImplTest.java`

- [ ] **Step 1: Write the failing test for source dispatch entry**

Add a new mock field:

```java
    @Mock
    private IHouseCommandService houseCommandService;
```

Add this test:

```java
    @Test
    void processPendingRefundsShouldKeepRefundRetryWhenSourceTypeIsUnsupported() {
        PaymentRefund refund = new PaymentRefund();
        refund.setId(10L);
        refund.setRefundNo("REF-UNSUPPORTED");
        refund.setStatus(PaymentRefundStatus.PENDING);
        refund.setChannel("MOCK");
        refund.setPaymentNo("PAY-UNSUPPORTED");
        refund.setOrderNo("ORDER-UNSUPPORTED");
        refund.setSourceType(PaymentRefundSourceType.ADMIN_MANUAL);

        when(paymentRefundMapper.selectPendingForProcess(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(refund));
        when(paymentRefundMapper.updateById(any(PaymentRefund.class))).thenReturn(1);

        paymentRefundService.processPendingRefunds();

        ArgumentCaptor<PaymentRefund> captor = ArgumentCaptor.forClass(PaymentRefund.class);
        verify(paymentRefundMapper, atLeastOnce()).updateById(captor.capture());
        PaymentRefund last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(PaymentRefundStatus.RETRY, last.getStatus());
        assertNotNull(last.getFailReason());
    }
```

- [ ] **Step 2: Run the targeted test to verify it fails**

Run:

```bash
mvn -Dtest=PaymentRefundServiceImplTest#processPendingRefundsShouldKeepRefundRetryWhenSourceTypeIsUnsupported test
```

Expected: FAIL because the current implementation marks the refund `SUCCESS`.

- [ ] **Step 3: Refactor `PaymentRefundServiceImpl` to add a success dispatcher**

Update constructor injection and fields:

```java
    private final IHouseCommandService houseCommandService;

    public PaymentRefundServiceImpl(PaymentRefundMapper paymentRefundMapper,
                                    OrderMapper orderMapper,
                                    PaymentMapper paymentMapper,
                                    IHouseCommandService houseCommandService) {
        this.paymentRefundMapper = paymentRefundMapper;
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.houseCommandService = houseCommandService;
    }
```

Split the success branch in `processSingleRefund(...)`:

```java
    private void processSingleRefund(PaymentRefund refund) {
        if (refund == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            if (!"MOCK".equalsIgnoreCase(refund.getChannel())) {
                throw new IllegalStateException("unsupported refund channel: " + refund.getChannel());
            }

            markRefundSuccess(refund, now);
            handleRefundSuccess(refund, now);
        } catch (Exception e) {
            handleRefundFailure(refund, now, e);
        }
    }
```

Add these helper methods:

```java
    private void markRefundSuccess(PaymentRefund refund, LocalDateTime now) {
        refund.setStatus(PaymentRefundStatus.SUCCESS);
        refund.setThirdPartyRefundNo(GenerateOrder.generateOrderNo("MOCKRF"));
        refund.setSuccessTime(now);
        refund.setNextRetryTime(null);
        refund.setFailReason(null);
        refund.setUpdateTime(now);
        paymentRefundMapper.updateById(refund);
    }

    private void handleRefundSuccess(PaymentRefund refund, LocalDateTime now) {
        if (refund.getSourceType() == PaymentRefundSourceType.USER_APPLY) {
            handleUserApplyRefundSuccess(refund, now);
            return;
        }
        if (refund.getSourceType() == PaymentRefundSourceType.DUPLICATE_PAID) {
            handleDuplicatePaidRefundSuccess(refund, now);
            return;
        }
        if (refund.getSourceType() == PaymentRefundSourceType.LATE_SUCCESS_UNRECOVERABLE) {
            handleLateSuccessRefundSuccess(refund, now);
            return;
        }
        throw new IllegalStateException("unsupported refund source type: " + refund.getSourceType());
    }

    private void handleRefundFailure(PaymentRefund refund, LocalDateTime now, Exception e) {
        int retryCount = refund.getRetryCount() == null ? 0 : refund.getRetryCount();
        retryCount++;
        refund.setRetryCount(retryCount);
        refund.setFailReason(e.getMessage());
        refund.setUpdateTime(now);
        if (retryCount >= (refund.getMaxRetryCount() == null ? 10 : refund.getMaxRetryCount())) {
            refund.setStatus(PaymentRefundStatus.MANUAL_REVIEW);
            refund.setCloseTime(now);
            refund.setNextRetryTime(null);
        } else {
            refund.setStatus(PaymentRefundStatus.RETRY);
            refund.setNextRetryTime(now.plusSeconds(10L * retryCount));
        }
        paymentRefundMapper.updateById(refund);
    }
```

Add temporary skeleton handlers:

```java
    private void handleUserApplyRefundSuccess(PaymentRefund refund, LocalDateTime now) {
    }

    private void handleDuplicatePaidRefundSuccess(PaymentRefund refund, LocalDateTime now) {
    }

    private void handleLateSuccessRefundSuccess(PaymentRefund refund, LocalDateTime now) {
    }
```

- [ ] **Step 4: Run the targeted test to verify it passes**

Run:

```bash
mvn -Dtest=PaymentRefundServiceImplTest#processPendingRefundsShouldKeepRefundRetryWhenSourceTypeIsUnsupported test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/impl/PaymentRefundServiceImpl.java src/test/java/cn/yy/myrent/service/impl/PaymentRefundServiceImplTest.java
git commit -m "refactor(refund): add refund success dispatcher skeleton"
```

### Task 3: Implement `USER_APPLY` Refund Compensation

**Files:**
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\PaymentRefundServiceImpl.java`
- Test: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\PaymentRefundServiceImplTest.java`

- [ ] **Step 1: Write the failing test for user refund success compensation**

Add this test:

```java
    @Test
    void processPendingRefundsShouldRefundUserApplyOrderAndReleaseHouse() {
        PaymentRefund refund = new PaymentRefund();
        refund.setId(1L);
        refund.setRefundNo("REF-USER");
        refund.setStatus(PaymentRefundStatus.PENDING);
        refund.setChannel("MOCK");
        refund.setPaymentNo("PAY-USER");
        refund.setOrderNo("ORDER-USER");
        refund.setSourceType(PaymentRefundSourceType.USER_APPLY);

        Order order = new Order();
        order.setOrderNo("ORDER-USER");
        order.setHouseId(99L);
        order.setStatus(OrderStatus.PAID_LOCKED);

        Payment payment = new Payment();
        payment.setPaymentNo("PAY-USER");
        payment.setOrderNo("ORDER-USER");
        payment.setStatus(PaymentStatus.PAID);

        when(paymentRefundMapper.selectPendingForProcess(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(refund));
        when(orderMapper.selectOrderNo("ORDER-USER")).thenReturn(order);
        when(paymentMapper.selectByPaymentNo("PAY-USER")).thenReturn(payment);
        when(paymentRefundMapper.updateById(any(PaymentRefund.class))).thenReturn(1);
        when(houseCommandService.updateHouseStatusWithSync(99L, 2, 1, "refund-user-apply-release"))
                .thenReturn(true);

        paymentRefundService.processPendingRefunds();

        assertEquals(OrderStatus.REFUNDED, order.getStatus());
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(houseCommandService).updateHouseStatusWithSync(99L, 2, 1, "refund-user-apply-release");
        verify(orderMapper).updateById(order);
        verify(paymentMapper).updateById(payment);
    }
```

- [ ] **Step 2: Run the targeted test to verify it fails**

Run:

```bash
mvn -Dtest=PaymentRefundServiceImplTest#processPendingRefundsShouldRefundUserApplyOrderAndReleaseHouse test
```

Expected: FAIL because the current handler skeleton does not update the order, payment, or house.

- [ ] **Step 3: Implement the `USER_APPLY` handler**

Add this method body:

```java
    private void handleUserApplyRefundSuccess(PaymentRefund refund, LocalDateTime now) {
        Order order = orderMapper.selectOrderNo(refund.getOrderNo());
        Payment payment = paymentMapper.selectByPaymentNo(refund.getPaymentNo());
        if (order == null || payment == null) {
            throw new IllegalStateException("refund compensation target not found");
        }

        if (order.getStatus() == OrderStatus.REFUNDED
                && payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }

        if (order.getStatus() != OrderStatus.PAID_LOCKED) {
            throw new IllegalStateException("user apply refund order status invalid: " + order.getStatus());
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException("user apply refund payment status invalid: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setUpdateTime(now);
        paymentMapper.updateById(payment);

        order.setStatus(OrderStatus.REFUNDED);
        order.setCloseTime(now);
        order.setUpdateTime(now);
        orderMapper.updateById(order);

        boolean released = houseCommandService.updateHouseStatusWithSync(
                order.getHouseId(),
                2,
                1,
                "refund-user-apply-release");
        if (!released) {
            throw new IllegalStateException("refund user apply release house failed");
        }
    }
```

- [ ] **Step 4: Add an idempotency test for already-refunded user refunds**

Add this test:

```java
    @Test
    void processPendingRefundsShouldTreatUserApplyRefundCompensationAsIdempotent() {
        PaymentRefund refund = new PaymentRefund();
        refund.setId(2L);
        refund.setRefundNo("REF-USER-IDEMPOTENT");
        refund.setStatus(PaymentRefundStatus.PENDING);
        refund.setChannel("MOCK");
        refund.setPaymentNo("PAY-USER-IDEMPOTENT");
        refund.setOrderNo("ORDER-USER-IDEMPOTENT");
        refund.setSourceType(PaymentRefundSourceType.USER_APPLY);

        Order order = new Order();
        order.setOrderNo("ORDER-USER-IDEMPOTENT");
        order.setStatus(OrderStatus.REFUNDED);

        Payment payment = new Payment();
        payment.setPaymentNo("PAY-USER-IDEMPOTENT");
        payment.setStatus(PaymentStatus.REFUNDED);

        when(paymentRefundMapper.selectPendingForProcess(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(refund));
        when(orderMapper.selectOrderNo("ORDER-USER-IDEMPOTENT")).thenReturn(order);
        when(paymentMapper.selectByPaymentNo("PAY-USER-IDEMPOTENT")).thenReturn(payment);
        when(paymentRefundMapper.updateById(any(PaymentRefund.class))).thenReturn(1);

        paymentRefundService.processPendingRefunds();

        verify(orderMapper, never()).updateById(order);
        verify(paymentMapper, never()).updateById(payment);
        verify(houseCommandService, never()).updateHouseStatusWithSync(anyLong(), anyInt(), anyInt(), anyString());
    }
```

- [ ] **Step 5: Run the two targeted tests to verify they pass**

Run:

```bash
mvn -Dtest=PaymentRefundServiceImplTest#processPendingRefundsShouldRefundUserApplyOrderAndReleaseHouse,PaymentRefundServiceImplTest#processPendingRefundsShouldTreatUserApplyRefundCompensationAsIdempotent test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/impl/PaymentRefundServiceImpl.java src/test/java/cn/yy/myrent/service/impl/PaymentRefundServiceImplTest.java
git commit -m "feat(refund): compensate user refund success"
```

### Task 4: Implement `DUPLICATE_PAID` Refund Compensation

**Files:**
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\PaymentRefundServiceImpl.java`
- Test: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\PaymentRefundServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Add this test:

```java
    @Test
    void processPendingRefundsShouldRefundDuplicatePaymentWithoutReleasingHouse() {
        PaymentRefund refund = new PaymentRefund();
        refund.setId(3L);
        refund.setRefundNo("REF-DUP");
        refund.setStatus(PaymentRefundStatus.PENDING);
        refund.setChannel("MOCK");
        refund.setPaymentNo("PAY-DUP");
        refund.setOrderNo("ORDER-DUP");
        refund.setSourceType(PaymentRefundSourceType.DUPLICATE_PAID);

        Payment payment = new Payment();
        payment.setPaymentNo("PAY-DUP");
        payment.setOrderNo("ORDER-DUP");
        payment.setStatus(PaymentStatus.DUPLICATE_PAID);

        when(paymentRefundMapper.selectPendingForProcess(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(refund));
        when(paymentMapper.selectByPaymentNo("PAY-DUP")).thenReturn(payment);
        when(paymentRefundMapper.updateById(any(PaymentRefund.class))).thenReturn(1);

        paymentRefundService.processPendingRefunds();

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(paymentMapper).updateById(payment);
        verify(orderMapper, never()).updateById(any(Order.class));
        verify(houseCommandService, never()).updateHouseStatusWithSync(anyLong(), anyInt(), anyInt(), anyString());
    }
```

- [ ] **Step 2: Run the targeted test to verify it fails**

Run:

```bash
mvn -Dtest=PaymentRefundServiceImplTest#processPendingRefundsShouldRefundDuplicatePaymentWithoutReleasingHouse test
```

Expected: FAIL because the current duplicate handler skeleton is empty.

- [ ] **Step 3: Implement the duplicate payment handler**

Add this method body:

```java
    private void handleDuplicatePaidRefundSuccess(PaymentRefund refund, LocalDateTime now) {
        Payment payment = paymentMapper.selectByPaymentNo(refund.getPaymentNo());
        if (payment == null) {
            throw new IllegalStateException("duplicate paid refund payment not found");
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.DUPLICATE_PAID) {
            throw new IllegalStateException("duplicate paid refund payment status invalid: " + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setUpdateTime(now);
        paymentMapper.updateById(payment);
    }
```

- [ ] **Step 4: Run the targeted test to verify it passes**

Run:

```bash
mvn -Dtest=PaymentRefundServiceImplTest#processPendingRefundsShouldRefundDuplicatePaymentWithoutReleasingHouse test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/impl/PaymentRefundServiceImpl.java src/test/java/cn/yy/myrent/service/impl/PaymentRefundServiceImplTest.java
git commit -m "feat(refund): compensate duplicate paid refunds"
```

### Task 5: Implement `LATE_SUCCESS_UNRECOVERABLE` Refund Compensation

**Files:**
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\PaymentRefundServiceImpl.java`
- Test: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\PaymentRefundServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Add this test:

```java
    @Test
    void processPendingRefundsShouldRefundLateSuccessPaymentWithoutChangingHouse() {
        PaymentRefund refund = new PaymentRefund();
        refund.setId(4L);
        refund.setRefundNo("REF-LATE");
        refund.setStatus(PaymentRefundStatus.PENDING);
        refund.setChannel("MOCK");
        refund.setPaymentNo("PAY-LATE");
        refund.setOrderNo("ORDER-LATE");
        refund.setSourceType(PaymentRefundSourceType.LATE_SUCCESS_UNRECOVERABLE);

        Order order = new Order();
        order.setOrderNo("ORDER-LATE");
        order.setStatus(OrderStatus.CLOSED_TIMEOUT);

        Payment payment = new Payment();
        payment.setPaymentNo("PAY-LATE");
        payment.setOrderNo("ORDER-LATE");
        payment.setStatus(PaymentStatus.PAID);

        when(paymentRefundMapper.selectPendingForProcess(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(refund));
        when(orderMapper.selectOrderNo("ORDER-LATE")).thenReturn(order);
        when(paymentMapper.selectByPaymentNo("PAY-LATE")).thenReturn(payment);
        when(paymentRefundMapper.updateById(any(PaymentRefund.class))).thenReturn(1);

        paymentRefundService.processPendingRefunds();

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(OrderStatus.CLOSED_TIMEOUT, order.getStatus());
        verify(paymentMapper).updateById(payment);
        verify(orderMapper, never()).updateById(any(Order.class));
        verify(houseCommandService, never()).updateHouseStatusWithSync(anyLong(), anyInt(), anyInt(), anyString());
    }
```

- [ ] **Step 2: Run the targeted test to verify it fails**

Run:

```bash
mvn -Dtest=PaymentRefundServiceImplTest#processPendingRefundsShouldRefundLateSuccessPaymentWithoutChangingHouse test
```

Expected: FAIL because the current late-success handler skeleton is empty.

- [ ] **Step 3: Implement the late-success handler**

Add this method body:

```java
    private void handleLateSuccessRefundSuccess(PaymentRefund refund, LocalDateTime now) {
        Order order = orderMapper.selectOrderNo(refund.getOrderNo());
        Payment payment = paymentMapper.selectByPaymentNo(refund.getPaymentNo());
        if (order == null || payment == null) {
            throw new IllegalStateException("late success refund target not found");
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        if (order.getStatus() != OrderStatus.CLOSED_TIMEOUT) {
            throw new IllegalStateException("late success refund order status invalid: " + order.getStatus());
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setUpdateTime(now);
        paymentMapper.updateById(payment);
    }
```

- [ ] **Step 4: Run the targeted test to verify it passes**

Run:

```bash
mvn -Dtest=PaymentRefundServiceImplTest#processPendingRefundsShouldRefundLateSuccessPaymentWithoutChangingHouse test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/impl/PaymentRefundServiceImpl.java src/test/java/cn/yy/myrent/service/impl/PaymentRefundServiceImplTest.java
git commit -m "feat(refund): compensate late success refunds"
```

### Task 6: Run the Focused Refund Verification Suite

**Files:**
- Test: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\PaymentRefundServiceImplTest.java`

- [ ] **Step 1: Run the focused refund service test suite**

Run:

```bash
mvn -Dtest=PaymentRefundServiceImplTest test
```

Expected: PASS with all refund service tests green.

- [ ] **Step 2: Run the adjacent payment repair suite**

Run:

```bash
mvn -Dtest=PaymentServiceImplTest test
```

Expected: PASS, proving refund hardening did not break payment anomaly repair flows that create refund requests.

- [ ] **Step 3: Commit the verification checkpoint**

```bash
git add src/test/java/cn/yy/myrent/service/impl/PaymentRefundServiceImplTest.java src/main/java/cn/yy/myrent/service/impl/PaymentRefundServiceImpl.java src/main/java/cn/yy/myrent/common/OrderStatus.java src/main/java/cn/yy/myrent/common/PaymentStatus.java
git commit -m "test(refund): verify refund compensation flows"
```

## Self-Review

- Spec coverage: The plan covers new refunded states, source-specific refund success compensation, idempotency, house sync through `IHouseCommandService`, and focused regression testing.
- Placeholder scan: No `TODO`, `TBD`, or undefined "handle later" steps remain in the plan.
- Type consistency: `OrderStatus.REFUNDED`, `PaymentStatus.REFUNDED`, `handleRefundSuccess(...)`, and the three source-specific handler names are consistent across all tasks.
