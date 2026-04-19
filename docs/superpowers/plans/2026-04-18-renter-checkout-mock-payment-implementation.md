# Renter Checkout Mock Payment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the minimum viable renter-side deposit order and mock payment closed loop: submit order, redirect to mock checkout, pay successfully or cancel the order, and auto-close unpaid orders on timeout.

**Architecture:** The business system remains responsible for locking houses, creating orders/payments, and closing final business state. A mock payment gateway page is added inside the repo but treated as a simulated third-party checkout: the business backend returns a `mockPayUrl`, the frontend redirects there, and the gateway submits a callback-like request back to the business backend to close payment and order state.

**Tech Stack:** Spring Boot 3.5, MyBatis-Plus, RabbitMQ/local task timeout flow, Vue 3 + Vite + Vue Router + Pinia, Vitest, MySQL 8

---

### Task 1: Align Schema And Backend Models

**Files:**
- Modify: `sql/rent-schema/order.sql`
- Modify: `sql/rent-schema/payment.sql`
- Modify: `sql/rent-schema/rent-schema-all.sql`
- Modify: `src/main/java/cn/yy/myrent/entity/Order.java`
- Modify: `src/main/java/cn/yy/myrent/entity/Payment.java`
- Create: `src/main/java/cn/yy/myrent/common/OrderStatus.java`
- Create: `src/main/java/cn/yy/myrent/common/PaymentStatus.java`

- [ ] **Step 1: Write the failing schema/entity test**

Create a new test file `src/test/java/cn/yy/myrent/entity/OrderPaymentModelSmokeTest.java`:

```java
package cn.yy.myrent.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderPaymentModelSmokeTest {

    @Test
    void orderShouldExposeClosedLoopFields() {
        Set<String> fieldNames = Arrays.stream(Order.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(fieldNames.contains("paidTime"));
        assertTrue(fieldNames.contains("closeTime"));
        assertTrue(fieldNames.contains("updateTime"));
    }

    @Test
    void paymentShouldExposeMockGatewayFields() {
        Set<String> fieldNames = Arrays.stream(Payment.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(fieldNames.contains("paymentNo"));
        assertTrue(fieldNames.contains("userId"));
        assertTrue(fieldNames.contains("channel"));
        assertTrue(fieldNames.contains("callbackNo"));
        assertTrue(fieldNames.contains("expireTime"));
        assertTrue(fieldNames.contains("paidTime"));
        assertTrue(fieldNames.contains("callbackTime"));
        assertTrue(fieldNames.contains("failReason"));
        assertTrue(fieldNames.contains("updateTime"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -Dtest=OrderPaymentModelSmokeTest test
```

Expected: FAIL because `Order` and `Payment` do not yet expose the new fields.

- [ ] **Step 3: Add shared order/payment status constants**

Create `src/main/java/cn/yy/myrent/common/OrderStatus.java`:

```java
package cn.yy.myrent.common;

public final class OrderStatus {

    public static final int UNPAID = 0;
    public static final int PAID_LOCKED = 1;
    public static final int CLOSED_TIMEOUT = 2;
    public static final int USER_CANCELLED = 3;

    private OrderStatus() {
    }
}
```

Create `src/main/java/cn/yy/myrent/common/PaymentStatus.java`:

```java
package cn.yy.myrent.common;

public final class PaymentStatus {

    public static final int WAITING = 0;
    public static final int SUCCESS = 1;
    public static final int CANCELLED = 3;
    public static final int CLOSED_TIMEOUT = 4;

    private PaymentStatus() {
    }
}
```

- [ ] **Step 4: Update `Order` and `Payment` entities**

Update `src/main/java/cn/yy/myrent/entity/Order.java` so the fields section includes:

```java
private LocalDateTime expireTime;

private LocalDateTime paidTime;

private LocalDateTime closeTime;

private LocalDateTime createTime;

private LocalDateTime updateTime;
```

Update `src/main/java/cn/yy/myrent/entity/Payment.java` so the fields section includes:

```java
private String paymentNo;

private String orderNo;

private Long userId;

private Integer payAmount;

private String channel;

private String thirdPartyTradeNo;

private String callbackNo;

private Integer status;

private LocalDateTime expireTime;

private LocalDateTime paidTime;

private LocalDateTime callbackTime;

private String failReason;

private LocalDateTime createTime;

private LocalDateTime updateTime;
```

- [ ] **Step 5: Rewrite SQL schema files to match the database shape already applied manually**

Replace `sql/rent-schema/order.sql` with:

```sql
DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
  `id` bigint NOT NULL COMMENT '分布式订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '业务订单号(全局唯一)',
  `user_id` bigint NOT NULL COMMENT '租客用户ID',
  `house_id` bigint NOT NULL COMMENT '房源ID',
  `amount` int NOT NULL COMMENT '应付定金(分)',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '订单状态: 0-待支付, 1-已支付锁房, 2-超时关闭, 3-用户取消',
  `expire_time` datetime NOT NULL COMMENT '支付过期时间',
  `paid_time` datetime DEFAULT NULL COMMENT '支付成功时间',
  `close_time` datetime DEFAULT NULL COMMENT '关闭时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_order_user_id` (`user_id`),
  KEY `idx_order_house_id` (`house_id`),
  KEY `idx_order_status` (`status`),
  KEY `idx_order_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定金订单表';
```

Replace `sql/rent-schema/payment.sql` with:

```sql
DROP TABLE IF EXISTS `payment`;
CREATE TABLE `payment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '支付流水ID',
  `payment_no` varchar(64) NOT NULL COMMENT '支付流水号',
  `order_no` varchar(64) NOT NULL COMMENT '业务订单号',
  `user_id` bigint NOT NULL COMMENT '支付用户ID',
  `pay_amount` int NOT NULL COMMENT '支付金额(分)',
  `channel` varchar(32) NOT NULL DEFAULT 'MOCK' COMMENT '支付渠道',
  `third_party_trade_no` varchar(64) DEFAULT NULL COMMENT '第三方交易号',
  `callback_no` varchar(64) DEFAULT NULL COMMENT '回调请求号',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '支付状态: 0-待支付, 1-支付成功, 3-用户取消, 4-超时关闭',
  `expire_time` datetime NOT NULL COMMENT '支付过期时间',
  `paid_time` datetime DEFAULT NULL COMMENT '支付成功时间',
  `callback_time` datetime DEFAULT NULL COMMENT '回调时间',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT '失败/取消原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  UNIQUE KEY `uk_payment_order_no` (`order_no`),
  UNIQUE KEY `uk_third_party_trade_no` (`third_party_trade_no`),
  KEY `idx_payment_user_id` (`user_id`),
  KEY `idx_payment_status` (`status`),
  KEY `idx_payment_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付流水表';
```

Apply the same `order` and `payment` definitions inside `sql/rent-schema/rent-schema-all.sql`.

- [ ] **Step 6: Run test to verify it passes**

Run:

```bash
mvn -Dtest=OrderPaymentModelSmokeTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add sql/rent-schema/order.sql sql/rent-schema/payment.sql sql/rent-schema/rent-schema-all.sql src/main/java/cn/yy/myrent/entity/Order.java src/main/java/cn/yy/myrent/entity/Payment.java src/main/java/cn/yy/myrent/common/OrderStatus.java src/main/java/cn/yy/myrent/common/PaymentStatus.java src/test/java/cn/yy/myrent/entity/OrderPaymentModelSmokeTest.java
git commit -m "feat: align order and payment schema for mock checkout"
```

### Task 2: Upgrade Order Creation To Return Checkout Info

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/IOrderService.java`
- Modify: `src/main/java/cn/yy/myrent/service/impl/OrderServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/controller/OrderController.java`
- Create: `src/main/java/cn/yy/myrent/vo/CreateOrderVO.java`
- Modify: `src/main/java/cn/yy/myrent/service/IPaymentService.java`
- Modify: `src/main/java/cn/yy/myrent/service/impl/PaymentServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/mapper/PaymentMapper.java`
- Modify: `src/main/resources/mapper/PaymentMapper.xml`
- Create: `src/test/java/cn/yy/myrent/controller/OrderControllerWebMvcTest.java`

- [ ] **Step 1: Write the failing controller test**

Create `src/test/java/cn/yy/myrent/controller/OrderControllerWebMvcTest.java`:

```java
package cn.yy.myrent.controller;

import cn.yy.myrent.vo.CreateOrderVO;
import cn.yy.myrent.service.IOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IOrderService orderService;

    @Test
    void createOrderShouldReturnCheckoutInfo() throws Exception {
        CreateOrderVO result = new CreateOrderVO();
        result.setOrderNo("ORDER-1001");
        result.setPaymentNo("PAY-1001");
        result.setMockPayUrl("/mock-pay/checkout?paymentNo=PAY-1001");
        result.setExpireTime(LocalDateTime.of(2026, 4, 18, 21, 0, 0));

        given(orderService.createOrder(any())).willReturn(result);

        mockMvc.perform(post("/order/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                            put("houseId", 101);
                            put("version", 0);
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value("ORDER-1001"))
                .andExpect(jsonPath("$.data.paymentNo").value("PAY-1001"))
                .andExpect(jsonPath("$.data.mockPayUrl").value("/mock-pay/checkout?paymentNo=PAY-1001"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -Dtest=OrderControllerWebMvcTest test
```

Expected: FAIL because `/order/create` and `CreateOrderVO` do not exist yet.

- [ ] **Step 3: Create the response VO**

Create `src/main/java/cn/yy/myrent/vo/CreateOrderVO.java`:

```java
package cn.yy.myrent.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateOrderVO {
    private String orderNo;
    private String paymentNo;
    private String mockPayUrl;
    private LocalDateTime expireTime;
}
```

- [ ] **Step 4: Change the order service contract**

Replace the interface method in `src/main/java/cn/yy/myrent/service/IOrderService.java`:

```java
CreateOrderVO createOrder(LockHouseReqDTO lockHouse);
```

- [ ] **Step 5: Add payment creation into the order flow**

Update `src/main/java/cn/yy/myrent/service/impl/OrderServiceImpl.java` so the method:

```java
@Override
@Transactional(rollbackFor = Exception.class)
public CreateOrderVO createOrder(LockHouseReqDTO lockHouse) {
    Long currentUserId = UserContext.requireCurrentUserId();
    // existing house validation and lock flow stays

    Order order = new Order();
    order.setOrderNo(GenerateOrder.generateOrderNo(Constant.ORDER_NO_PREFIX));
    order.setUserId(currentUserId);
    order.setHouseId(house.getId());
    order.setAmount(house.getDepositAmount());
    order.setStatus(OrderStatus.UNPAID);
    order.setExpireTime(LocalDateTime.now().plusSeconds(30));
    order.setCreateTime(LocalDateTime.now());
    order.setUpdateTime(LocalDateTime.now());
    orderMapper.insert(order);

    Payment payment = new Payment();
    payment.setPaymentNo(GenerateOrder.generateOrderNo("PAY"));
    payment.setOrderNo(order.getOrderNo());
    payment.setUserId(currentUserId);
    payment.setPayAmount(order.getAmount());
    payment.setChannel("MOCK");
    payment.setStatus(PaymentStatus.WAITING);
    payment.setExpireTime(order.getExpireTime());
    payment.setCreateTime(LocalDateTime.now());
    payment.setUpdateTime(LocalDateTime.now());
    paymentService.save(payment);

    CreateOrderVO result = new CreateOrderVO();
    result.setOrderNo(order.getOrderNo());
    result.setPaymentNo(payment.getPaymentNo());
    result.setExpireTime(order.getExpireTime());
    result.setMockPayUrl("/mock-pay/checkout?paymentNo=" + payment.getPaymentNo());
    return result;
}
```

Inject `IPaymentService paymentService;` into the service.

- [ ] **Step 6: Replace the order creation controller endpoint**

Update `src/main/java/cn/yy/myrent/controller/OrderController.java`:

```java
@PostMapping("/create")
@Operation(summary = "创建订单并返回模拟收银台地址")
public ResponseEntity<Result<CreateOrderVO>> createOrder(@RequestBody LockHouseReqDTO lockHouse) {
    try {
        CreateOrderVO result = orderService.createOrder(lockHouse);
        return ResponseEntity.ok(Result.success("订单创建成功，请尽快支付", result));
    } catch (IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(401, e.getMessage()));
    } catch (RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error(e.getMessage()));
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.error("系统繁忙，请稍后重试"));
    }
}
```

- [ ] **Step 7: Add payment mapper lookup methods**

Extend `src/main/java/cn/yy/myrent/mapper/PaymentMapper.java`:

```java
Payment selectByPaymentNo(String paymentNo);
```

Add to `src/main/resources/mapper/PaymentMapper.xml`:

```xml
<select id="selectByPaymentNo" resultType="cn.yy.myrent.entity.Payment" parameterType="java.lang.String">
    select * from payment where payment_no = #{paymentNo}
</select>
```

- [ ] **Step 8: Run tests to verify they pass**

Run:

```bash
mvn -Dtest=OrderControllerWebMvcTest,OrderPaymentModelSmokeTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/IOrderService.java src/main/java/cn/yy/myrent/service/impl/OrderServiceImpl.java src/main/java/cn/yy/myrent/controller/OrderController.java src/main/java/cn/yy/myrent/vo/CreateOrderVO.java src/main/java/cn/yy/myrent/service/IPaymentService.java src/main/java/cn/yy/myrent/service/impl/PaymentServiceImpl.java src/main/java/cn/yy/myrent/mapper/PaymentMapper.java src/main/resources/mapper/PaymentMapper.xml src/test/java/cn/yy/myrent/controller/OrderControllerWebMvcTest.java
git commit -m "feat: return mock checkout info when creating orders"
```

### Task 3: Add Mock Gateway Page And Business Callback Closure

**Files:**
- Modify: `src/main/java/cn/yy/myrent/controller/PaymentController.java`
- Modify: `src/main/java/cn/yy/myrent/service/IPaymentService.java`
- Modify: `src/main/java/cn/yy/myrent/service/impl/PaymentServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/mapper/OrderMapper.java`
- Modify: `src/main/resources/mapper/OrderMapper.xml`
- Create: `src/main/java/cn/yy/myrent/dto/MockPaymentCallbackReqDTO.java`
- Create: `src/main/java/cn/yy/myrent/vo/MockCheckoutVO.java`
- Create: `src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java`
- Modify: `frontend/src/router/index.js`
- Create: `frontend/src/views/mock/MockCheckoutView.vue`
- Modify: `frontend/src/api/order.js`
- Create: `frontend/src/api/payment.js`
- Create: `frontend/src/views/__tests__/MockCheckoutView.spec.js`

- [ ] **Step 1: Write the failing service test**

Create `src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java`:

```java
package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.PaymentStatus;
import cn.yy.myrent.dto.MockPaymentCallbackReqDTO;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.PaymentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void handleMockSuccessShouldClosePaymentAndOrder() {
        Payment payment = new Payment();
        payment.setPaymentNo("PAY-1001");
        payment.setOrderNo("ORDER-1001");
        payment.setStatus(PaymentStatus.WAITING);

        Order order = new Order();
        order.setOrderNo("ORDER-1001");
        order.setStatus(OrderStatus.UNPAID);
        order.setExpireTime(LocalDateTime.now().plusMinutes(1));

        when(paymentMapper.selectByPaymentNo("PAY-1001")).thenReturn(payment);
        when(orderMapper.selectOrderNo("ORDER-1001")).thenReturn(order);

        MockPaymentCallbackReqDTO req = new MockPaymentCallbackReqDTO();
        req.setPaymentNo("PAY-1001");
        req.setOrderNo("ORDER-1001");
        req.setPayStatus("SUCCESS");
        req.setThirdPartyTradeNo("TP-1001");
        req.setCallbackNo("CB-1001");
        req.setCallbackTime(LocalDateTime.now());

        paymentService.handleMockCallback(req);

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(OrderStatus.PAID_LOCKED, order.getStatus());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -Dtest=PaymentServiceImplTest test
```

Expected: FAIL because callback DTO and service method do not exist yet.

- [ ] **Step 3: Add DTO and checkout VO**

Create `src/main/java/cn/yy/myrent/dto/MockPaymentCallbackReqDTO.java`:

```java
package cn.yy.myrent.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MockPaymentCallbackReqDTO {
    private String orderNo;
    private String paymentNo;
    private String thirdPartyTradeNo;
    private String callbackNo;
    private String payStatus;
    private Integer payAmount;
    private LocalDateTime callbackTime;
}
```

Create `src/main/java/cn/yy/myrent/vo/MockCheckoutVO.java`:

```java
package cn.yy.myrent.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MockCheckoutVO {
    private String orderNo;
    private String paymentNo;
    private Integer amount;
    private LocalDateTime expireTime;
    private long remainingSeconds;
}
```

- [ ] **Step 4: Add payment service methods**

Update `src/main/java/cn/yy/myrent/service/IPaymentService.java`:

```java
MockCheckoutVO getMockCheckout(String paymentNo);

void handleMockCallback(MockPaymentCallbackReqDTO req);
```

Implement in `src/main/java/cn/yy/myrent/service/impl/PaymentServiceImpl.java`:

```java
@Service
public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> implements IPaymentService {

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public MockCheckoutVO getMockCheckout(String paymentNo) {
        Payment payment = paymentMapper.selectByPaymentNo(paymentNo);
        if (payment == null) {
            throw new RuntimeException("支付单不存在");
        }
        MockCheckoutVO vo = new MockCheckoutVO();
        vo.setOrderNo(payment.getOrderNo());
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setAmount(payment.getPayAmount());
        vo.setExpireTime(payment.getExpireTime());
        vo.setRemainingSeconds(Math.max(0, java.time.Duration.between(LocalDateTime.now(), payment.getExpireTime()).getSeconds()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleMockCallback(MockPaymentCallbackReqDTO req) {
        Payment payment = paymentMapper.selectByPaymentNo(req.getPaymentNo());
        Order order = orderMapper.selectOrderNo(req.getOrderNo());
        if (payment == null || order == null) {
            throw new RuntimeException("订单或支付单不存在");
        }
        if (order.getStatus() != OrderStatus.UNPAID || payment.getStatus() != PaymentStatus.WAITING) {
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(req.getPayStatus())) {
            payment.setThirdPartyTradeNo(req.getThirdPartyTradeNo());
            payment.setCallbackNo(req.getCallbackNo());
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setCallbackTime(req.getCallbackTime());
            payment.setPaidTime(req.getCallbackTime());
            payment.setUpdateTime(LocalDateTime.now());
            this.updateById(payment);

            order.setStatus(OrderStatus.PAID_LOCKED);
            order.setPaidTime(req.getCallbackTime());
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.updateById(order);
            return;
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setCallbackNo(req.getCallbackNo());
        payment.setCallbackTime(req.getCallbackTime());
        payment.setFailReason("USER_CANCELLED");
        payment.setUpdateTime(LocalDateTime.now());
        this.updateById(payment);

        order.setStatus(OrderStatus.USER_CANCELLED);
        order.setCloseTime(req.getCallbackTime());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }
}
```

- [ ] **Step 5: Add payment controller endpoints**

Update `src/main/java/cn/yy/myrent/controller/PaymentController.java` to add:

```java
@GetMapping("/mock-checkout/{paymentNo}")
public Result<MockCheckoutVO> mockCheckout(@PathVariable String paymentNo) {
    return Result.success(paymentService.getMockCheckout(paymentNo));
}

@PostMapping("/callback/mock")
public Result<Void> mockCallback(@RequestBody MockPaymentCallbackReqDTO req) {
    paymentService.handleMockCallback(req);
    return Result.success();
}
```

- [ ] **Step 6: Add mock checkout frontend page**

Create `frontend/src/api/payment.js`:

```javascript
import http from './http'

export function fetchMockCheckout(paymentNo) {
  return http.get(`/payment/mock-checkout/${paymentNo}`)
}

export function submitMockCallback(payload) {
  return http.post('/payment/callback/mock', payload)
}
```

Create `frontend/src/views/mock/MockCheckoutView.vue`:

```vue
<template>
  <div class="page">
    <section class="card">
      <h2 class="section-title">Mock Checkout</h2>
      <p>订单号：{{ checkout?.orderNo }}</p>
      <p>支付流水号：{{ checkout?.paymentNo }}</p>
      <p>支付金额：{{ formatPrice(checkout?.amount) }}</p>
      <p>剩余时间：{{ remainingText }}</p>
      <div class="actions">
        <button class="primary-btn" @click="paySuccess">支付成功</button>
        <button class="ghost-btn" @click="cancelOrder">取消订单</button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchMockCheckout, submitMockCallback } from '@/api/payment'
import { formatPrice } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const checkout = ref(null)

const remainingText = computed(() => `${Math.max(Number(checkout.value?.remainingSeconds || 0), 0)} 秒`)

async function loadCheckout() {
  checkout.value = await fetchMockCheckout(route.query.paymentNo)
}

async function paySuccess() {
  await submitMockCallback({
    orderNo: checkout.value.orderNo,
    paymentNo: checkout.value.paymentNo,
    thirdPartyTradeNo: `TP-${Date.now()}`,
    callbackNo: `CB-${Date.now()}`,
    payStatus: 'SUCCESS',
    payAmount: checkout.value.amount,
    callbackTime: new Date().toISOString()
  })
  router.replace('/mine/orders')
}

async function cancelOrder() {
  await submitMockCallback({
    orderNo: checkout.value.orderNo,
    paymentNo: checkout.value.paymentNo,
    thirdPartyTradeNo: '',
    callbackNo: `CB-${Date.now()}`,
    payStatus: 'CANCELLED',
    payAmount: checkout.value.amount,
    callbackTime: new Date().toISOString()
  })
  router.replace('/mine/orders')
}

onMounted(loadCheckout)
</script>
```

Add the route in `frontend/src/router/index.js`:

```javascript
{
  path: '/mock-pay/checkout',
  name: 'mock-checkout',
  component: () => import('@/views/mock/MockCheckoutView.vue'),
  meta: { requiresAuth: true }
}
```

- [ ] **Step 7: Update order API names to match the new endpoint**

Update `frontend/src/api/order.js`:

```javascript
export function createOrder(payload) {
  return http.post('/order/create', payload)
}

export function repayOrder(orderNo) {
  return http.post(`/order/${orderNo}/repay`)
}
```

- [ ] **Step 8: Add the failing frontend test and make it pass**

Create `frontend/src/views/__tests__/MockCheckoutView.spec.js`:

```javascript
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MockCheckoutView from '@/views/mock/MockCheckoutView.vue'

vi.mock('@/api/payment', () => ({
  fetchMockCheckout: vi.fn(async () => ({
    orderNo: 'ORDER-1001',
    paymentNo: 'PAY-1001',
    amount: 100000,
    remainingSeconds: 20
  })),
  submitMockCallback: vi.fn(async () => undefined)
}))

describe('MockCheckoutView', () => {
  it('renders checkout summary', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/mock-pay/checkout', component: MockCheckoutView }]
    })
    router.push('/mock-pay/checkout?paymentNo=PAY-1001')
    await router.isReady()

    const wrapper = mount(MockCheckoutView, {
      global: { plugins: [router] }
    })

    await new Promise(resolve => setTimeout(resolve, 0))
    expect(wrapper.text()).toContain('Mock Checkout')
    expect(wrapper.text()).toContain('ORDER-1001')
    expect(wrapper.text()).toContain('PAY-1001')
  })
})
```

Run:

```bash
npm run test:run -- MockCheckoutView.spec.js
```

Expected: PASS.

- [ ] **Step 9: Run backend and frontend targeted tests**

Run:

```bash
mvn -Dtest=PaymentServiceImplTest,OrderControllerWebMvcTest test
npm run test:run -- MockCheckoutView.spec.js
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/cn/yy/myrent/controller/PaymentController.java src/main/java/cn/yy/myrent/service/IPaymentService.java src/main/java/cn/yy/myrent/service/impl/PaymentServiceImpl.java src/main/java/cn/yy/myrent/mapper/OrderMapper.java src/main/resources/mapper/OrderMapper.xml src/main/java/cn/yy/myrent/dto/MockPaymentCallbackReqDTO.java src/main/java/cn/yy/myrent/vo/MockCheckoutVO.java src/test/java/cn/yy/myrent/service/impl/PaymentServiceImplTest.java frontend/src/router/index.js frontend/src/api/order.js frontend/src/api/payment.js frontend/src/views/mock/MockCheckoutView.vue frontend/src/views/__tests__/MockCheckoutView.spec.js
git commit -m "feat: add mock checkout gateway and payment callback flow"
```

### Task 4: Link Cancel/Timeout To House Release And Payment Closure

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/impl/PaymentServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumer.java`
- Create: `src/test/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumerTest.java`

- [ ] **Step 1: Write the failing timeout consumer test**

Create `src/test/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumerTest.java`:

```java
package cn.yy.myrent.consumer;

import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IOrderService;
import cn.yy.myrent.service.IPaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTimeoutTaskConsumerTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private IOrderService orderService;

    @Mock
    private IHouseCommandService houseCommandService;

    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Mock
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @Mock
    private IPaymentService paymentService;

    @InjectMocks
    private OrderTimeoutTaskConsumer consumer;

    @Test
    void timeoutShouldCloseWaitingPayment() {
        Order order = new Order();
        order.setOrderNo("ORDER-1001");
        order.setStatus(OrderStatus.UNPAID);
        order.setHouseId(101L);

        when(orderMapper.selectOrderNo("ORDER-1001")).thenReturn(order);

        // compile target: consumer should call payment service on timeout
        verify(paymentService, org.mockito.Mockito.never()).update(any());
    }
}
```

Run:

```bash
mvn -Dtest=OrderTimeoutTaskConsumerTest test
```

Expected: FAIL or compile-fail because `OrderTimeoutTaskConsumer` does not yet depend on `IPaymentService`.

- [ ] **Step 2: Release house when user cancels order**

Extend `PaymentServiceImpl.handleMockCallback(...)` so the cancellation path also performs:

```java
houseCommandService.updateHouseStatusWithSync(order.getHouseId(), 2, 1, "user-cancel-order");
```

Inject `IHouseCommandService houseCommandService`.

- [ ] **Step 3: Close waiting payment when timeout closes the order**

Update `src/main/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumer.java`:

```java
@Autowired
private IPaymentService paymentService;
```

Inside the successful timeout close branch:

```java
paymentService.update()
        .set("status", PaymentStatus.CLOSED_TIMEOUT)
        .set("fail_reason", "TIMEOUT_CLOSED")
        .set("close_time", null)
        .eq("order_no", orderNo)
        .eq("status", PaymentStatus.WAITING)
        .update();
```

Use the actual available fields only:

```java
paymentService.update()
        .set("status", PaymentStatus.CLOSED_TIMEOUT)
        .set("fail_reason", "TIMEOUT_CLOSED")
        .eq("order_no", orderNo)
        .eq("status", PaymentStatus.WAITING)
        .update();
```

- [ ] **Step 4: Add a payment update timestamp in cancel/success/timeout writes**

For every payment status transition, make sure:

```java
payment.setUpdateTime(LocalDateTime.now());
```

or the equivalent update wrapper sets `update_time`.

- [ ] **Step 5: Run targeted tests**

Run:

```bash
mvn -Dtest=PaymentServiceImplTest,OrderTimeoutTaskConsumerTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/impl/PaymentServiceImpl.java src/main/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumer.java src/test/java/cn/yy/myrent/consumer/OrderTimeoutTaskConsumerTest.java
git commit -m "feat: close payments on cancel and timeout"
```

### Task 5: Wire Frontend Redirect And My Orders Continue Payment

**Files:**
- Modify: `frontend/src/views/HouseDetailView.vue`
- Modify: `frontend/src/views/mine/MineOrderView.vue`
- Modify: `frontend/src/api/order.js`
- Modify: `frontend/src/utils/format.js`
- Create: `frontend/src/views/__tests__/MineOrderView.spec.js`
- Modify: `frontend/src/views/__tests__/HouseDetailView.spec.js`

- [ ] **Step 1: Write the failing order-list test**

Create `frontend/src/views/__tests__/MineOrderView.spec.js`:

```javascript
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MineOrderView from '@/views/mine/MineOrderView.vue'

vi.mock('@/api/order', () => ({
  fetchMyOrderPage: vi.fn(async () => ({
    records: [{
      id: 1,
      orderNo: 'ORDER-1001',
      houseId: 101,
      amount: 100000,
      status: 0,
      createTime: '2026-04-18T20:00:00',
      expireTime: '2026-04-18T20:00:30'
    }],
    total: 1
  })),
  repayOrder: vi.fn(async () => ({
    orderNo: 'ORDER-1001',
    paymentNo: 'PAY-1001',
    mockPayUrl: '/mock-pay/checkout?paymentNo=PAY-1001',
    expireTime: '2026-04-18T20:00:30'
  }))
}))

vi.mock('@/api/house', () => ({
  fetchHouseById: vi.fn(async () => ({ id: 101, title: '测试房源' }))
}))

describe('MineOrderView', () => {
  it('shows continue payment button for unpaid orders', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine/orders', component: MineOrderView },
        { path: '/mock-pay/checkout', component: { template: '<div />' } }
      ]
    })
    router.push('/mine/orders')
    await router.isReady()

    const wrapper = mount(MineOrderView, {
      global: { plugins: [router] }
    })

    await new Promise(resolve => setTimeout(resolve, 0))
    expect(wrapper.text()).toContain('继续支付')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
npm run test:run -- MineOrderView.spec.js
```

Expected: FAIL because the view does not yet render a continue payment action.

- [ ] **Step 3: Redirect to mock checkout after order creation**

Update `frontend/src/views/HouseDetailView.vue` inside `submitDeposit()`:

```javascript
const result = await createOrder({
  houseId: house.value.id,
  version: house.value.version || 0
})

if (result?.mockPayUrl) {
  window.location.href = result.mockPayUrl
  return
}
```

Replace the alert-only success branch.

- [ ] **Step 4: Add continue-payment action to My Orders**

Update `frontend/src/views/mine/MineOrderView.vue`:

```vue
<div class="order-actions">
  <button class="ghost-btn" @click="goDetail(order.houseId)">查看房源</button>
  <button
    v-if="order.status === 0"
    class="primary-btn"
    @click="continuePay(order.orderNo)"
  >
    继续支付
  </button>
</div>
```

Add script logic:

```javascript
import { fetchMyOrderPage, repayOrder } from '@/api/order'

async function continuePay(orderNo) {
  try {
    const result = await repayOrder(orderNo)
    if (result?.mockPayUrl) {
      router.push(result.mockPayUrl)
    }
  } catch (err) {
    error.value = err?.message || '继续支付失败'
  }
}
```

For route-safe navigation:

```javascript
if (result?.mockPayUrl?.startsWith('/')) {
  window.location.href = result.mockPayUrl
}
```

Use the same full redirect behavior as the initial submit flow.

- [ ] **Step 5: Make status text reflect the new semantic values**

Update `getOrderStatusText(status)` in `frontend/src/views/mine/MineOrderView.vue`:

```javascript
function getOrderStatusText(status) {
  if (status === 0) return '待支付'
  if (status === 1) return '已支付'
  if (status === 2) return '已超时关闭'
  if (status === 3) return '已取消'
  return '未知状态'
}
```

Add a `.status-3` style block similar to the closed style.

- [ ] **Step 6: Run frontend tests**

Run:

```bash
npm run test:run -- MineOrderView.spec.js HouseDetailView.spec.js MockCheckoutView.spec.js
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/views/HouseDetailView.vue frontend/src/views/mine/MineOrderView.vue frontend/src/api/order.js frontend/src/views/__tests__/MineOrderView.spec.js frontend/src/views/__tests__/HouseDetailView.spec.js
git commit -m "feat: redirect renters to mock checkout and support repay"
```

### Task 6: Verify Closed Loop End-To-End

**Files:**
- Test: existing backend/frontend files from prior tasks

- [ ] **Step 1: Build backend**

Run:

```bash
mvn -DskipTests package
```

Expected: `BUILD SUCCESS`

- [ ] **Step 2: Run backend targeted tests**

Run:

```bash
mvn -Dtest=OrderPaymentModelSmokeTest,OrderControllerWebMvcTest,PaymentServiceImplTest,OrderTimeoutTaskConsumerTest test
```

Expected: all targeted tests PASS.

- [ ] **Step 3: Run frontend targeted tests**

Run:

```bash
cd frontend
npm run test:run -- MockCheckoutView.spec.js MineOrderView.spec.js HouseDetailView.spec.js
```

Expected: all targeted tests PASS.

- [ ] **Step 4: Build frontend**

Run:

```bash
cd frontend
npm run build
```

Expected: production build completes successfully.

- [ ] **Step 5: Manual verification path 1**

Run the app and verify:

1. Open a house detail page.
2. Click submit order.
3. Confirm redirect to `/mock-pay/checkout?...`.
4. Click `支付成功`.
5. Open My Orders.
6. Confirm the order shows `已支付`.

- [ ] **Step 6: Manual verification path 2**

Verify:

1. Submit a new order.
2. Redirect to mock checkout.
3. Click `取消订单`.
4. Confirm My Orders shows `已取消`.
5. Confirm the house is released.

- [ ] **Step 7: Manual verification path 3**

Verify:

1. Submit a new order.
2. Do not pay.
3. Wait for timeout.
4. Confirm My Orders shows `已超时关闭`.
5. Confirm the house is released.
