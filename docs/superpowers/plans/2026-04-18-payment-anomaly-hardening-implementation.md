# Payment Anomaly Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement complete duplicate-callback, duplicate-payment, and lost-callback handling for the current mock payment flow by upgrading to one-order-many-payments and adding a mock third-party truth source.

**Architecture:** Keep `order` as final business truth, upgrade `payment` into a payment-attempt ledger, and add `mock_pay_trade` as the mock provider truth table. Reuse one idempotent payment-success close path from callback, timeout-before-close, and scheduled compensation so all three entry points produce the same final state.

**Tech Stack:** Spring Boot, MyBatis-Plus, MyBatis XML mappers, MySQL schema SQL files, RabbitMQ timeout consumer, JUnit 5, Mockito

---

## File Structure

### Existing Files To Modify

- `sql/rent-schema/order.sql`
  - add `success_payment_no`
- `sql/rent-schema/payment.sql`
  - remove unique constraint on `order_no`
  - add `DUPLICATE_PAID` status note
- `sql/rent-schema/rent-schema-all.sql`
  - synchronize merged schema
- `src/main/java/cn/yy/myrent/common/PaymentStatus.java`
  - add `DUPLICATE_PAID`
- `src/main/java/cn/yy/myrent/entity/Order.java`
  - add `successPaymentNo`
- `src/main/java/cn/yy/myrent/entity/Payment.java`
  - keep one-order-many-payment compatible fields
- `src/main/java/cn/yy/myrent/mapper/OrderMapper.java`
  - add conditional update / lookup methods used by payment win logic
- `src/main/java/cn/yy/myrent/mapper/PaymentMapper.java`
  - add latest-active-payment and order-payment lookup methods
- `src/main/resources/mapper/OrderMapper.xml`
  - add SQL for conditional paid update and order lookup helpers
- `src/main/resources/mapper/PaymentMapper.xml`
  - add SQL for latest active payment and related payment queries
- `src/main/java/cn/yy/myrent/service/IOrderService.java`
  - add repay API contract
- `src/main/java/cn/yy/myrent/service/impl/OrderServiceImpl.java`
  - create first payment + first mock trade
  - add repay flow
- `src/main/java/cn/yy/myrent/controller/OrderController.java`
  - expose repay endpoint
- `src/main/java/cn/yy/myrent/service/IPaymentService.java`
  - add reusable close/query/repair contracts
- `src/main/java/cn/yy/myrent/service/impl/PaymentServiceImpl.java`
  - harden callback idempotency
  - resolve duplicate payment competition
  - expose reusable repair/query methods
- `src/main/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumer.java`
  - query mock truth before timeout close
- `src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java`
  - extend payment service tests
- `src/test/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumerTest.java`
  - extend timeout-before-close tests

### New Files To Create

- `sql/rent-schema/mock_pay_trade.sql`
  - schema for mock provider truth
- `src/main/java/cn/yy/myrent/common/MockPayTradeStatus.java`
  - constants for mock trade state
- `src/main/java/cn/yy/myrent/common/MockPayCallbackStatus.java`
  - constants for callback confirmation state
- `src/main/java/cn/yy/myrent/entity/MockPayTrade.java`
  - entity for mock provider truth table
- `src/main/java/cn/yy/myrent/mapper/MockPayTradeMapper.java`
  - mapper contract
- `src/main/resources/mapper/MockPayTradeMapper.xml`
  - mapper SQL
- `src/main/java/cn/yy/myrent/service/IMockPayTradeService.java`
  - service contract
- `src/main/java/cn/yy/myrent/service/impl/MockPayTradeServiceImpl.java`
  - service implementation
- `src/main/java/cn/yy/myrent/task/PaymentCompensationTask.java`
  - scheduled suspicious-payment scan

### Existing Tests To Reuse For Patterns

- `src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java`
- `src/test/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumerTest.java`
- `src/test/java/cn/yy/myrent/controller/OrderControllerWebMvcTest.java`

---

### Task 1: Align Schema And Status Constants

**Files:**
- Create: `sql/rent-schema/mock_pay_trade.sql`
- Modify: `sql/rent-schema/order.sql`
- Modify: `sql/rent-schema/payment.sql`
- Modify: `sql/rent-schema/rent-schema-all.sql`
- Create: `src/main/java/cn/yy/myrent/common/MockPayTradeStatus.java`
- Create: `src/main/java/cn/yy/myrent/common/MockPayCallbackStatus.java`
- Modify: `src/main/java/cn/yy/myrent/common/PaymentStatus.java`
- Modify: `src/main/java/cn/yy/myrent/entity/Order.java`
- Create: `src/main/java/cn/yy/myrent/entity/MockPayTrade.java`

- [ ] **Step 1: Write the failing model/schema smoke test**

Add to `src/test/java/cn/yy/myrent/entity/OrderPaymentModelSmokeTest.java`:

```java
@Test
void orderAndMockTradeModelShouldExposeNewFields() throws Exception {
    assertNotNull(Order.class.getDeclaredField("successPaymentNo"));
    assertNotNull(MockPayTrade.class.getDeclaredField("paymentNo"));
    assertNotNull(MockPayTrade.class.getDeclaredField("callbackStatus"));
    assertEquals(5, PaymentStatus.DUPLICATE_PAID);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -Dtest=OrderPaymentModelSmokeTest test
```

Expected:

- FAIL because `successPaymentNo`, `MockPayTrade`, or `DUPLICATE_PAID` do not exist yet

- [ ] **Step 3: Add the minimal schema and model changes**

Apply these code changes:

`src/main/java/cn/yy/myrent/common/PaymentStatus.java`

```java
public final class PaymentStatus {

    public static final int PENDING = 0;
    public static final int PAYING = 1;
    public static final int PAID = 2;
    public static final int USER_CANCELLED = 3;
    public static final int CLOSED_TIMEOUT = 4;
    public static final int DUPLICATE_PAID = 5;

    private PaymentStatus() {
    }
}
```

`src/main/java/cn/yy/myrent/common/MockPayTradeStatus.java`

```java
package cn.yy.myrent.common;

public final class MockPayTradeStatus {

    public static final int CREATED = 0;
    public static final int PAYING = 1;
    public static final int SUCCESS = 2;
    public static final int USER_CANCELLED = 3;
    public static final int CLOSED_TIMEOUT = 4;

    private MockPayTradeStatus() {
    }
}
```

`src/main/java/cn/yy/myrent/common/MockPayCallbackStatus.java`

```java
package cn.yy.myrent.common;

public final class MockPayCallbackStatus {

    public static final int NOT_CONFIRMED = 0;
    public static final int CONFIRMED = 1;
    public static final int FAILED = 2;

    private MockPayCallbackStatus() {
    }
}
```

`src/main/java/cn/yy/myrent/entity/Order.java`

```java
private String successPaymentNo;
```

`src/main/java/cn/yy/myrent/entity/MockPayTrade.java`

```java
package cn.yy.myrent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("mock_pay_trade")
public class MockPayTrade implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String paymentNo;
    private String orderNo;
    private String thirdPartyTradeNo;
    private Integer status;
    private Integer amount;
    private LocalDateTime paidTime;
    private Integer callbackStatus;
    private LocalDateTime lastCallbackTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

`sql/rent-schema/order.sql`

```sql
  `paid_time` datetime DEFAULT NULL COMMENT 'payment success time',
  `success_payment_no` varchar(64) DEFAULT NULL COMMENT 'final successful payment no',
  `close_time` datetime DEFAULT NULL COMMENT 'order close time',
```

`sql/rent-schema/payment.sql`

```sql
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0 pending, 1 paying, 2 paid, 3 user cancelled, 4 timeout closed, 5 duplicate paid',
```

and remove:

```sql
  UNIQUE KEY `uk_payment_order_no` (`order_no`),
```

`sql/rent-schema/mock_pay_trade.sql`

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

Also sync the same column definitions into `sql/rent-schema/rent-schema-all.sql`.

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn -Dtest=OrderPaymentModelSmokeTest test
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```powershell
git add sql/rent-schema/order.sql sql/rent-schema/payment.sql sql/rent-schema/mock_pay_trade.sql sql/rent-schema/rent-schema-all.sql src/main/java/cn/yy/myrent/common/PaymentStatus.java src/main/java/cn/yy/myrent/common/MockPayTradeStatus.java src/main/java/cn/yy/myrent/common/MockPayCallbackStatus.java src/main/java/cn/yy/myrent/entity/Order.java src/main/java/cn/yy/myrent/entity/MockPayTrade.java src/test/java/cn/yy/myrent/entity/OrderPaymentModelSmokeTest.java
git commit -m "feat: add payment anomaly schema primitives"
```

### Task 2: Add Mock Trade Mapper And Order/Payment Query Primitives

**Files:**
- Create: `src/main/java/cn/yy/myrent/mapper/MockPayTradeMapper.java`
- Create: `src/main/resources/mapper/MockPayTradeMapper.xml`
- Create: `src/main/java/cn/yy/myrent/service/IMockPayTradeService.java`
- Create: `src/main/java/cn/yy/myrent/service/impl/MockPayTradeServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/mapper/PaymentMapper.java`
- Modify: `src/main/resources/mapper/PaymentMapper.xml`
- Modify: `src/main/java/cn/yy/myrent/mapper/OrderMapper.java`
- Modify: `src/main/resources/mapper/OrderMapper.xml`

- [ ] **Step 1: Write the failing mapper/service test**

Add to `src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java`:

```java
@Test
void mapperContractsShouldExposeLatestPaymentAndMockTradeLookup() {
    assertDoesNotThrow(() -> PaymentMapper.class.getMethod("selectLatestActiveByOrderNo", String.class));
    assertDoesNotThrow(() -> PaymentMapper.class.getMethod("selectByOrderNo", String.class));
    assertDoesNotThrow(() -> OrderMapper.class.getMethod("markPaidIfUnpaid",
            String.class, java.time.LocalDateTime.class, String.class, java.time.LocalDateTime.class));
    assertDoesNotThrow(() -> MockPayTradeMapper.class.getMethod("selectByPaymentNo", String.class));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -Dtest=PaymentServiceImplTest test
```

Expected:

- FAIL because the mapper methods and mock trade mapper do not exist

- [ ] **Step 3: Add minimal mapper and service contracts**

`src/main/java/cn/yy/myrent/mapper/MockPayTradeMapper.java`

```java
package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.MockPayTrade;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface MockPayTradeMapper extends BaseMapper<MockPayTrade> {

    MockPayTrade selectByPaymentNo(String paymentNo);
}
```

`src/main/resources/mapper/MockPayTradeMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="cn.yy.myrent.mapper.MockPayTradeMapper">

    <select id="selectByPaymentNo" resultType="cn.yy.myrent.entity.MockPayTrade" parameterType="java.lang.String">
        select * from mock_pay_trade where payment_no = #{paymentNo}
    </select>
</mapper>
```

`src/main/java/cn/yy/myrent/service/IMockPayTradeService.java`

```java
package cn.yy.myrent.service;

import cn.yy.myrent.entity.MockPayTrade;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IMockPayTradeService extends IService<MockPayTrade> {

    MockPayTrade getByPaymentNo(String paymentNo);
}
```

`src/main/java/cn/yy/myrent/service/impl/MockPayTradeServiceImpl.java`

```java
package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.MockPayTrade;
import cn.yy.myrent.mapper.MockPayTradeMapper;
import cn.yy.myrent.service.IMockPayTradeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class MockPayTradeServiceImpl extends ServiceImpl<MockPayTradeMapper, MockPayTrade>
        implements IMockPayTradeService {

    @Override
    public MockPayTrade getByPaymentNo(String paymentNo) {
        return baseMapper.selectByPaymentNo(paymentNo);
    }
}
```

`src/main/java/cn/yy/myrent/mapper/PaymentMapper.java`

```java
import java.util.List;

public interface PaymentMapper extends BaseMapper<Payment> {

    Payment selectByPaymentNo(String paymentNo);

    Payment selectLatestActiveByOrderNo(String orderNo);

    List<Payment> selectByOrderNo(String orderNo);
}
```

`src/main/resources/mapper/PaymentMapper.xml`

```xml
<select id="selectLatestActiveByOrderNo" resultType="cn.yy.myrent.entity.Payment" parameterType="java.lang.String">
    select * from payment
    where order_no = #{orderNo}
      and status in (0, 1)
    order by id desc
    limit 1
</select>

<select id="selectByOrderNo" resultType="cn.yy.myrent.entity.Payment" parameterType="java.lang.String">
    select * from payment
    where order_no = #{orderNo}
    order by id desc
</select>
```

`src/main/java/cn/yy/myrent/mapper/OrderMapper.java`

```java
import org.apache.ibatis.annotations.Param;

public interface OrderMapper extends BaseMapper<Order> {

    Order selectOrderNo(String orderNo);

    int markPaidIfUnpaid(@Param("orderNo") String orderNo,
                         @Param("paidTime") java.time.LocalDateTime paidTime,
                         @Param("successPaymentNo") String successPaymentNo,
                         @Param("updateTime") java.time.LocalDateTime updateTime);
}
```

`src/main/resources/mapper/OrderMapper.xml`

```xml
<update id="markPaidIfUnpaid">
    update `order`
    set status = 1,
        paid_time = #{paidTime},
        success_payment_no = #{successPaymentNo},
        update_time = #{updateTime}
    where order_no = #{orderNo}
      and status = 0
</update>
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn -Dtest=PaymentServiceImplTest test
```

Expected:

- PASS for the new method existence check

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/cn/yy/myrent/mapper/MockPayTradeMapper.java src/main/resources/mapper/MockPayTradeMapper.xml src/main/java/cn/yy/myrent/service/IMockPayTradeService.java src/main/java/cn/yy/myrent/service/impl/MockPayTradeServiceImpl.java src/main/java/cn/yy/myrent/mapper/PaymentMapper.java src/main/resources/mapper/PaymentMapper.xml src/main/java/cn/yy/myrent/mapper/OrderMapper.java src/main/resources/mapper/OrderMapper.xml src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java
git commit -m "feat: add payment anomaly query primitives"
```

### Task 3: Create Mock Trade On Order Creation And Add Repay Flow

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/IOrderService.java`
- Modify: `src/main/java/cn/yy/myrent/service/impl/OrderServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/controller/OrderController.java`
- Modify: `src/main/java/cn/yy/myrent/vo/CreateOrderVO.java`
- Test: `src/test/java/cn/yy/myrent/controller/OrderControllerWebMvcTest.java`

- [ ] **Step 1: Write the failing repay and create-order test**

Add tests to `src/test/java/cn/yy/myrent/controller/OrderControllerWebMvcTest.java`:

```java
@Test
void createOrderShouldReturnPaymentAttemptInfo() throws Exception {
    CreateOrderVO vo = new CreateOrderVO();
    vo.setOrderNo("ORDER-1");
    vo.setPaymentNo("PAY-1");
    vo.setMockPayUrl("/mock-pay/checkout?paymentNo=PAY-1");

    when(orderService.createOrder(any())).thenReturn(vo);

    mockMvc.perform(post("/order/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"houseId\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paymentNo").value("PAY-1"));
}

@Test
void repayShouldReturnNewPaymentAttemptInfo() throws Exception {
    CreateOrderVO vo = new CreateOrderVO();
    vo.setOrderNo("ORDER-1");
    vo.setPaymentNo("PAY-2");
    vo.setMockPayUrl("/mock-pay/checkout?paymentNo=PAY-2");

    when(orderService.repay("ORDER-1")).thenReturn(vo);

    mockMvc.perform(post("/order/ORDER-1/repay"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paymentNo").value("PAY-2"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -Dtest=OrderControllerWebMvcTest test
```

Expected:

- FAIL because `repay` endpoint and service contract do not exist

- [ ] **Step 3: Implement create-order mock trade creation and repay**

`src/main/java/cn/yy/myrent/service/IOrderService.java`

```java
CreateOrderVO createOrder(LockHouseReqDTO lockHouse);

CreateOrderVO repay(String orderNo);
```

In `src/main/java/cn/yy/myrent/service/impl/OrderServiceImpl.java`, inject `IMockPayTradeService` and add helper:

```java
private Payment buildPayment(String orderNo, Long userId, Integer amount, LocalDateTime expireTime, LocalDateTime now) {
    Payment payment = new Payment();
    payment.setPaymentNo(GenerateOrder.generateOrderNo("PAY"));
    payment.setOrderNo(orderNo);
    payment.setUserId(userId);
    payment.setPayAmount(amount);
    payment.setChannel("MOCK");
    payment.setStatus(PaymentStatus.PENDING);
    payment.setExpireTime(expireTime);
    payment.setCreateTime(now);
    payment.setUpdateTime(now);
    return payment;
}

private MockPayTrade buildMockTrade(Payment payment, LocalDateTime now) {
    MockPayTrade trade = new MockPayTrade();
    trade.setPaymentNo(payment.getPaymentNo());
    trade.setOrderNo(payment.getOrderNo());
    trade.setStatus(MockPayTradeStatus.CREATED);
    trade.setAmount(payment.getPayAmount());
    trade.setCallbackStatus(MockPayCallbackStatus.NOT_CONFIRMED);
    trade.setCreateTime(now);
    trade.setUpdateTime(now);
    return trade;
}
```

Use the helper inside `createOrder(...)` after order insert:

```java
Payment payment = buildPayment(order.getOrderNo(), currentUserId, order.getAmount(), order.getExpireTime(), now);
paymentService.save(payment);
mockPayTradeService.save(buildMockTrade(payment, now));
```

Add `repay(...)`:

```java
@Override
@Transactional(rollbackFor = Exception.class)
public CreateOrderVO repay(String orderNo) {
    Long currentUserId = UserContext.requireCurrentUserId();
    Order order = orderMapper.selectOrderNo(orderNo);
    if (order == null) {
        throw new RuntimeException("order not found");
    }
    if (!currentUserId.equals(order.getUserId())) {
        throw new RuntimeException("cannot pay other user's order");
    }
    if (order.getStatus() != OrderStatus.UNPAID) {
        throw new RuntimeException("order is not payable");
    }

    LocalDateTime now = LocalDateTime.now();
    Payment payment = buildPayment(orderNo, currentUserId, order.getAmount(), order.getExpireTime(), now);
    paymentService.save(payment);
    mockPayTradeService.save(buildMockTrade(payment, now));

    CreateOrderVO result = new CreateOrderVO();
    result.setOrderNo(orderNo);
    result.setPaymentNo(payment.getPaymentNo());
    result.setExpireTime(order.getExpireTime());
    result.setMockPayUrl("/mock-pay/checkout?paymentNo=" + payment.getPaymentNo());
    return result;
}
```

`src/main/java/cn/yy/myrent/controller/OrderController.java`

```java
@PostMapping("/{orderNo}/repay")
@Operation(summary = "create a new payment attempt for unpaid order")
public ResponseEntity<Result<CreateOrderVO>> repay(@PathVariable String orderNo) {
    try {
        return ResponseEntity.ok(Result.success(orderService.repay(orderNo)));
    } catch (IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(401, e.getMessage()));
    } catch (RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error(e.getMessage()));
    } catch (Exception e) {
        log.error("repay failed, orderNo={}", orderNo, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("system busy, please retry later"));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn -Dtest=OrderControllerWebMvcTest test
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/cn/yy/myrent/service/IOrderService.java src/main/java/cn/yy/myrent/service/impl/OrderServiceImpl.java src/main/java/cn/yy/myrent/controller/OrderController.java src/test/java/cn/yy/myrent/controller/OrderControllerWebMvcTest.java
git commit -m "feat: support repay payment attempts"
```

### Task 4: Harden Callback Handling For Duplicate Callback And Duplicate Payment

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/IPaymentService.java`
- Modify: `src/main/java/cn/yy/myrent/service/impl/PaymentServiceImpl.java`
- Modify: `src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java`

- [ ] **Step 1: Write the failing payment service tests**

Add these tests to `src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java`:

```java
@Test
void duplicateCallbackShouldReturnWithoutChangingPaidPaymentAgain() {
    Payment payment = new Payment();
    payment.setPaymentNo("PAY-1");
    payment.setOrderNo("ORDER-1");
    payment.setStatus(PaymentStatus.PAID);

    Order order = new Order();
    order.setOrderNo("ORDER-1");
    order.setStatus(OrderStatus.PAID_LOCKED);
    order.setSuccessPaymentNo("PAY-1");

    when(paymentMapper.selectByPaymentNo("PAY-1")).thenReturn(payment);
    when(orderMapper.selectOrderNo("ORDER-1")).thenReturn(order);

    MockPaymentCallbackReqDTO req = new MockPaymentCallbackReqDTO();
    req.setPaymentNo("PAY-1");
    req.setOrderNo("ORDER-1");
    req.setPayStatus("SUCCESS");

    paymentService.handleMockCallback(req);

    verify(paymentMapper, never()).updateById(any());
}

@Test
void secondSuccessfulPaymentShouldBecomeDuplicatePaid() {
    Payment payment = new Payment();
    payment.setPaymentNo("PAY-2");
    payment.setOrderNo("ORDER-2");
    payment.setStatus(PaymentStatus.PAYING);

    Order order = new Order();
    order.setOrderNo("ORDER-2");
    order.setStatus(OrderStatus.PAID_LOCKED);
    order.setSuccessPaymentNo("PAY-1");

    when(paymentMapper.selectByPaymentNo("PAY-2")).thenReturn(payment);
    when(orderMapper.selectOrderNo("ORDER-2")).thenReturn(order);
    when(orderMapper.markPaidIfUnpaid(eq("ORDER-2"), any(), eq("PAY-2"), any())).thenReturn(0);

    MockPaymentCallbackReqDTO req = new MockPaymentCallbackReqDTO();
    req.setPaymentNo("PAY-2");
    req.setOrderNo("ORDER-2");
    req.setPayStatus("SUCCESS");
    req.setThirdPartyTradeNo("TP-2");
    req.setCallbackNo("CB-2");
    req.setCallbackTime(LocalDateTime.now());

    paymentService.handleMockCallback(req);

    assertEquals(PaymentStatus.DUPLICATE_PAID, payment.getStatus());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -Dtest=PaymentServiceImplTest test
```

Expected:

- FAIL because callback handling does not yet distinguish duplicate callback from duplicate payment

- [ ] **Step 3: Refactor payment success close path and duplicate handling**

Update `src/main/java/cn/yy/myrent/service/IPaymentService.java`:

```java
MockCheckoutVO getMockCheckout(String paymentNo);

void handleMockCallback(MockPaymentCallbackReqDTO req);

boolean repairOrderPaidFromTrade(String paymentNo, String thirdPartyTradeNo, String callbackNo, LocalDateTime callbackTime);
```

In `src/main/java/cn/yy/myrent/service/impl/PaymentServiceImpl.java`, add reusable close logic:

```java
@Override
@Transactional(rollbackFor = Exception.class)
public boolean repairOrderPaidFromTrade(String paymentNo, String thirdPartyTradeNo, String callbackNo, LocalDateTime callbackTime) {
    Payment payment = paymentMapper.selectByPaymentNo(paymentNo);
    if (payment == null) {
        throw new RuntimeException("payment not found");
    }
    Order order = orderMapper.selectOrderNo(payment.getOrderNo());
    if (order == null) {
        throw new RuntimeException("order not found");
    }
    if (payment.getStatus() != null && payment.getStatus() == PaymentStatus.PAID) {
        return true;
    }

    LocalDateTime effectiveTime = callbackTime != null ? callbackTime : LocalDateTime.now();
    LocalDateTime now = LocalDateTime.now();
    int updated = orderMapper.markPaidIfUnpaid(order.getOrderNo(), effectiveTime, paymentNo, now);

    payment.setThirdPartyTradeNo(thirdPartyTradeNo);
    payment.setCallbackNo(callbackNo);
    payment.setCallbackTime(effectiveTime);
    payment.setPaidTime(effectiveTime);
    payment.setUpdateTime(now);

    if (updated > 0) {
        payment.setStatus(PaymentStatus.PAID);
        paymentMapper.updateById(payment);
        return true;
    }

    Order latestOrder = orderMapper.selectOrderNo(order.getOrderNo());
    if (latestOrder != null && paymentNo.equals(latestOrder.getSuccessPaymentNo())) {
        payment.setStatus(PaymentStatus.PAID);
        paymentMapper.updateById(payment);
        return true;
    }

    payment.setStatus(PaymentStatus.DUPLICATE_PAID);
    payment.setFailReason("DUPLICATE_PAID");
    paymentMapper.updateById(payment);
    return false;
}
```

Then simplify `handleMockCallback(...)`:

```java
if ("SUCCESS".equalsIgnoreCase(req.getPayStatus())) {
    repairOrderPaidFromTrade(req.getPaymentNo(),
            req.getThirdPartyTradeNo(),
            req.getCallbackNo(),
            req.getCallbackTime());
    return;
}
```

Also harden the early-return condition so cancel only applies when payment is still open:

```java
if (payment.getStatus() != null && (payment.getStatus() == PaymentStatus.PAID
        || payment.getStatus() == PaymentStatus.DUPLICATE_PAID
        || payment.getStatus() == PaymentStatus.USER_CANCELLED
        || payment.getStatus() == PaymentStatus.CLOSED_TIMEOUT)) {
    return;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn -Dtest=PaymentServiceImplTest test
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/cn/yy/myrent/service/IPaymentService.java src/main/java/cn/yy/myrent/service/impl/PaymentServiceImpl.java src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java
git commit -m "feat: harden payment callback idempotency"
```

### Task 5: Sync Mock Trade State Through Checkout And Callback

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/impl/PaymentServiceImpl.java`
- Modify: `src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java`

- [ ] **Step 1: Write the failing mock trade sync tests**

Add tests:

```java
@Test
void getMockCheckoutShouldMarkTradeAsPaying() {
    Payment payment = new Payment();
    payment.setPaymentNo("PAY-10");
    payment.setOrderNo("ORDER-10");
    payment.setPayAmount(100);
    payment.setStatus(PaymentStatus.PENDING);
    payment.setExpireTime(LocalDateTime.now().plusMinutes(1));

    MockPayTrade trade = new MockPayTrade();
    trade.setPaymentNo("PAY-10");
    trade.setStatus(MockPayTradeStatus.CREATED);

    when(paymentMapper.selectByPaymentNo("PAY-10")).thenReturn(payment);
    when(mockPayTradeService.getByPaymentNo("PAY-10")).thenReturn(trade);

    paymentService.getMockCheckout("PAY-10");

    assertEquals(MockPayTradeStatus.PAYING, trade.getStatus());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -Dtest=PaymentServiceImplTest test
```

Expected:

- FAIL because payment service does not yet update mock trade state

- [ ] **Step 3: Update checkout and callback code to sync mock trade**

In `PaymentServiceImpl`, inject `IMockPayTradeService`.

Update `getMockCheckout(...)`:

```java
MockPayTrade trade = mockPayTradeService.getByPaymentNo(paymentNo);
if (trade == null) {
    throw new RuntimeException("mock trade not found");
}
if (payment.getStatus() != null && payment.getStatus() == PaymentStatus.PENDING) {
    payment.setStatus(PaymentStatus.PAYING);
    payment.setUpdateTime(LocalDateTime.now());
    paymentMapper.updateById(payment);
}
if (trade.getStatus() != null && trade.getStatus() == MockPayTradeStatus.CREATED) {
    trade.setStatus(MockPayTradeStatus.PAYING);
    trade.setUpdateTime(LocalDateTime.now());
    mockPayTradeService.updateById(trade);
}
```

Inside `repairOrderPaidFromTrade(...)`, after `paymentMapper.updateById(payment)`, add:

```java
MockPayTrade trade = mockPayTradeService.getByPaymentNo(paymentNo);
if (trade != null) {
    trade.setThirdPartyTradeNo(thirdPartyTradeNo);
    trade.setStatus(MockPayTradeStatus.SUCCESS);
    trade.setPaidTime(effectiveTime);
    trade.setCallbackStatus(MockPayCallbackStatus.CONFIRMED);
    trade.setLastCallbackTime(now);
    trade.setUpdateTime(now);
    mockPayTradeService.updateById(trade);
}
```

Inside the cancel branch, update matching mock trade:

```java
MockPayTrade trade = mockPayTradeService.getByPaymentNo(payment.getPaymentNo());
if (trade != null) {
    trade.setStatus(MockPayTradeStatus.USER_CANCELLED);
    trade.setCallbackStatus(MockPayCallbackStatus.CONFIRMED);
    trade.setLastCallbackTime(now);
    trade.setUpdateTime(now);
    mockPayTradeService.updateById(trade);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn -Dtest=PaymentServiceImplTest test
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/cn/yy/myrent/service/impl/PaymentServiceImpl.java src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java
git commit -m "feat: sync mock trade state with checkout flow"
```

### Task 6: Query Mock Truth Before Timeout Close

**Files:**
- Modify: `src/main/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumer.java`
- Modify: `src/test/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumerTest.java`

- [ ] **Step 1: Write the failing timeout compensation tests**

Add test:

```java
@Test
void timeoutShouldRepairPaidOrderBeforeClosing() throws Exception {
    Order order = new Order();
    order.setOrderNo("ORDER-2001");
    order.setStatus(OrderStatus.UNPAID);

    Payment payment = new Payment();
    payment.setPaymentNo("PAY-2001");
    payment.setOrderNo("ORDER-2001");
    payment.setStatus(PaymentStatus.PAYING);

    MockPayTrade trade = new MockPayTrade();
    trade.setPaymentNo("PAY-2001");
    trade.setStatus(MockPayTradeStatus.SUCCESS);

    when(orderMapper.selectOrderNo("ORDER-2001")).thenReturn(order);
    when(paymentMapper.selectLatestActiveByOrderNo("ORDER-2001")).thenReturn(payment);
    when(mockPayTradeService.getByPaymentNo("PAY-2001")).thenReturn(trade);
    when(paymentService.repairOrderPaidFromTrade("PAY-2001", null, null, null)).thenReturn(true);

    MessageProperties properties = new MessageProperties();
    properties.setDeliveryTag(1L);
    Message message = new Message("ORDER-2001".getBytes(), properties);

    consumer.consumeOrderTimeoutMessage("ORDER-2001", message, channel);

    verify(paymentService).repairOrderPaidFromTrade("PAY-2001", null, null, null);
    verify(orderService, never()).update();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -Dtest=OrderTimeoutTaskConsumerTest test
```

Expected:

- FAIL because the timeout consumer directly closes unpaid order

- [ ] **Step 3: Implement timeout-before-close query**

Inject `PaymentMapper` and `IMockPayTradeService` into `OrderTimeoutTaskConsumer`.

Add helper:

```java
private boolean tryRepairPaidOrderBeforeClose(String orderNo) {
    Payment latestPayment = paymentMapper.selectLatestActiveByOrderNo(orderNo);
    if (latestPayment == null) {
        return false;
    }
    MockPayTrade trade = mockPayTradeService.getByPaymentNo(latestPayment.getPaymentNo());
    if (trade == null || trade.getStatus() == null || trade.getStatus() != MockPayTradeStatus.SUCCESS) {
        return false;
    }
    paymentService.repairOrderPaidFromTrade(latestPayment.getPaymentNo(),
            trade.getThirdPartyTradeNo(),
            null,
            trade.getPaidTime());
    return true;
}
```

Call it before order close:

```java
if (order != null && order.getStatus() == OrderStatus.UNPAID) {
    if (tryRepairPaidOrderBeforeClose(orderNo)) {
        channel.basicAck(deliveryTag, false);
        return;
    }
    // existing close logic continues here
}
```

When close really happens, also close mock trades:

```java
mockPayTradeService.lambdaUpdate()
        .set(MockPayTrade::getStatus, MockPayTradeStatus.CLOSED_TIMEOUT)
        .set(MockPayTrade::getUpdateTime, LocalDateTime.now())
        .eq(MockPayTrade::getOrderNo, orderNo)
        .in(MockPayTrade::getStatus, MockPayTradeStatus.CREATED, MockPayTradeStatus.PAYING)
        .update();
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn -Dtest=OrderTimeoutTaskConsumerTest test
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumer.java src/test/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumerTest.java
git commit -m "feat: query mock truth before timeout close"
```

### Task 7: Add Scheduled Suspicious-Payment Compensation

**Files:**
- Create: `src/main/java/cn/yy/myrent/task/PaymentCompensationTask.java`
- Modify: `src/main/java/cn/yy/myrent/service/impl/PaymentServiceImpl.java`
- Modify: `src/main/resources/mapper/PaymentMapper.xml`
- Modify: `src/main/java/cn/yy/myrent/mapper/PaymentMapper.java`
- Modify: `src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java`

- [ ] **Step 1: Write the failing suspicious-payment scan test**

Add mapper contract assertion and a service-oriented test:

```java
@Test
void mapperShouldExposeSuspiciousPaymentsQuery() {
    assertDoesNotThrow(() -> PaymentMapper.class.getMethod("selectSuspiciousPayingPayments"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -Dtest=PaymentServiceImplTest test
```

Expected:

- FAIL because suspicious payment query does not exist

- [ ] **Step 3: Add suspicious scan query and scheduled task**

Update `PaymentMapper.java`:

```java
List<Payment> selectSuspiciousPayingPayments();
```

Update `PaymentMapper.xml`:

```xml
<select id="selectSuspiciousPayingPayments" resultType="cn.yy.myrent.entity.Payment">
    select p.*
    from payment p
    join `order` o on o.order_no = p.order_no
    join mock_pay_trade t on t.payment_no = p.payment_no
    where p.status = 1
      and o.status = 0
      and t.status = 2
      and t.callback_status <> 1
    order by p.id asc
    limit 100
</select>
```

Create `src/main/java/cn/yy/myrent/task/PaymentCompensationTask.java`:

```java
package cn.yy.myrent.task;

import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.entity.MockPayTrade;
import cn.yy.myrent.mapper.PaymentMapper;
import cn.yy.myrent.service.IMockPayTradeService;
import cn.yy.myrent.service.IPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class PaymentCompensationTask {

    private final PaymentMapper paymentMapper;
    private final IMockPayTradeService mockPayTradeService;
    private final IPaymentService paymentService;

    public PaymentCompensationTask(PaymentMapper paymentMapper,
                                   IMockPayTradeService mockPayTradeService,
                                   IPaymentService paymentService) {
        this.paymentMapper = paymentMapper;
        this.mockPayTradeService = mockPayTradeService;
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelay = 30000)
    public void repairSuspiciousPayments() {
        List<Payment> payments = paymentMapper.selectSuspiciousPayingPayments();
        for (Payment payment : payments) {
            try {
                MockPayTrade trade = mockPayTradeService.getByPaymentNo(payment.getPaymentNo());
                if (trade == null) {
                    continue;
                }
                paymentService.repairOrderPaidFromTrade(payment.getPaymentNo(),
                        trade.getThirdPartyTradeNo(),
                        null,
                        trade.getPaidTime());
            } catch (Exception e) {
                log.warn("repair suspicious payment failed, paymentNo={}", payment.getPaymentNo(), e);
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```powershell
mvn -Dtest=PaymentServiceImplTest,OrderTimeoutTaskConsumerTest test
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/cn/yy/myrent/task/PaymentCompensationTask.java src/main/java/cn/yy/myrent/mapper/PaymentMapper.java src/main/resources/mapper/PaymentMapper.xml src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java
git commit -m "feat: add lost callback compensation task"
```

### Task 8: End-To-End Verification And SQL Sync Review

**Files:**
- Modify: `sql/rent-schema/rent-schema-all.sql`
- Test: `src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java`
- Test: `src/test/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumerTest.java`
- Test: `src/test/java/cn/yy/myrent/controller/OrderControllerWebMvcTest.java`

- [ ] **Step 1: Add one integrated regression test for multi-payment winner logic**

Append to `src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java`:

```java
@Test
void successfulWinnerShouldPopulateOrderSuccessPaymentNo() {
    Payment payment = new Payment();
    payment.setPaymentNo("PAY-3001");
    payment.setOrderNo("ORDER-3001");
    payment.setStatus(PaymentStatus.PAYING);

    Order order = new Order();
    order.setOrderNo("ORDER-3001");
    order.setStatus(OrderStatus.UNPAID);

    when(paymentMapper.selectByPaymentNo("PAY-3001")).thenReturn(payment);
    when(orderMapper.selectOrderNo("ORDER-3001")).thenReturn(order);
    when(orderMapper.markPaidIfUnpaid(eq("ORDER-3001"), any(), eq("PAY-3001"), any())).thenReturn(1);

    MockPaymentCallbackReqDTO req = new MockPaymentCallbackReqDTO();
    req.setPaymentNo("PAY-3001");
    req.setOrderNo("ORDER-3001");
    req.setPayStatus("SUCCESS");
    req.setCallbackTime(LocalDateTime.now());

    paymentService.handleMockCallback(req);

    verify(orderMapper).markPaidIfUnpaid(eq("ORDER-3001"), any(), eq("PAY-3001"), any());
}
```

- [ ] **Step 2: Run the focused test suite**

Run:

```powershell
mvn -Dtest=OrderControllerWebMvcTest,PaymentServiceImplTest,OrderTimeoutTaskConsumerTest,OrderPaymentModelSmokeTest test
```

Expected:

- PASS

- [ ] **Step 3: Run the broader module test suite**

Run:

```powershell
mvn test
```

Expected:

- PASS, or if unrelated tests fail, record the exact unrelated failures before proceeding

- [ ] **Step 4: Review final schema sync**

Check:

```powershell
Get-Content sql\rent-schema\order.sql
Get-Content sql\rent-schema\payment.sql
Get-Content sql\rent-schema\mock_pay_trade.sql
Get-Content sql\rent-schema\rent-schema-all.sql
```

Expected:

- `order.sql` includes `success_payment_no`
- `payment.sql` has no unique key on `order_no`
- `payment.sql` documents `DUPLICATE_PAID`
- `mock_pay_trade.sql` exists and matches entity fields
- `rent-schema-all.sql` reflects all of the above

- [ ] **Step 5: Commit**

```powershell
git add sql/rent-schema/rent-schema-all.sql src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java src/test/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumerTest.java src/test/java/cn/yy/myrent/controller/OrderControllerWebMvcTest.java
git commit -m "test: verify payment anomaly hardening flow"
```

---

## Spec Coverage Self-Review

- `one order -> many payments`: covered by Task 1 schema removal of unique constraint and Task 3 repay flow
- `order.success_payment_no`: covered by Task 1 entity/schema and Task 4 final-success logic
- `duplicate callback`: covered by Task 4
- `duplicate payment`: covered by Task 4 with `DUPLICATE_PAID`
- `mock_pay_trade`: covered by Tasks 1, 2, 3, and 5
- `query before timeout close`: covered by Task 6
- `scheduled suspicious-payment compensation`: covered by Task 7
- `SQL synchronization`: covered by Tasks 1 and 8
- `tests`: covered across Tasks 1 through 8

## Placeholder Self-Review

- no `TODO` or `TBD` markers remain
- every step names exact files
- every code step includes concrete code
- every test step includes a concrete command and expected result

## Type Consistency Self-Review

- `successPaymentNo` is used consistently across entity, mapper, and SQL
- `MockPayTrade`, `MockPayTradeStatus`, and `MockPayCallbackStatus` use stable names across all tasks
- `repairOrderPaidFromTrade(...)` is the shared payment-close method across callback, timeout, and scheduled compensation

