# Review Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a first-release renter review module with order completion, one-review-per-order creation, one-time edit support, house detail review display, and review-aware order actions.

**Architecture:** Keep the review flow order-centered. Extend the existing order lifecycle with `COMPLETED` and `REVIEWED`, add a dedicated `review` table plus service/controller layer, then expose review summary and recent reviews on the house detail page while driving all review actions from the renter order list.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, MySQL 8, Vue 3, Vue Router, Axios, Vitest, JUnit 5, Mockito, Maven

---

## File Map

- Modify: `C:\javapractice\MyRent\sql\rent-schema\order.sql`
  Responsibility: update order status comment so schema docs match the new lifecycle.
- Modify: `C:\javapractice\MyRent\sql\rent-schema\rent-schema-all.sql`
  Responsibility: keep the aggregate schema in sync with `review.sql` and new order status comments.
- Create: `C:\javapractice\MyRent\sql\rent-schema\review.sql`
  Responsibility: define the review table.
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\common\OrderStatus.java`
  Responsibility: add `PAID`, `COMPLETED`, and `REVIEWED` while preserving current stored values.
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\mapper\OrderMapper.java`
  Responsibility: add guarded state-transition methods for completion and review.
- Modify: `C:\javapractice\MyRent\src\main\resources\mapper\OrderMapper.xml`
  Responsibility: implement guarded SQL updates for completion and review transitions.
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\IOrderService.java`
  Responsibility: expose complete-order and mine-order page methods.
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\OrderServiceImpl.java`
  Responsibility: implement order completion and review-aware order paging.
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\controller\OrderController.java`
  Responsibility: add `/order/{orderNo}/complete` and return `MyOrderItemVO` for `/order/mine`.
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\entity\Review.java`
  Responsibility: map the `review` table.
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\dto\ReviewCreateReqDTO.java`
  Responsibility: validate create-review requests.
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\dto\ReviewUpdateReqDTO.java`
  Responsibility: validate update-review requests.
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\mapper\ReviewMapper.java`
  Responsibility: expose order lookup, house review list, and aggregate queries.
- Create: `C:\javapractice\MyRent\src\main\resources\mapper\ReviewMapper.xml`
  Responsibility: implement recent-review list, count, average-score, and batch order lookup SQL.
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\IReviewService.java`
  Responsibility: define create, update, detail, and house review query operations.
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\ReviewServiceImpl.java`
  Responsibility: enforce review ownership, order-state validation, and one-time edit logic.
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\controller\ReviewController.java`
  Responsibility: expose review create, update, and detail APIs.
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\controller\HouseController.java`
  Responsibility: expose `GET /house/{houseId}/reviews`.
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\vo\MyOrderItemVO.java`
  Responsibility: return order list data plus review action flags.
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\vo\HouseReviewItemVO.java`
  Responsibility: represent one house review row with reviewer nickname.
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\vo\HouseReviewPageVO.java`
  Responsibility: wrap average score, review count, and paged review items.
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\PaymentRefundServiceImpl.java`
  Responsibility: reject refunds once orders move to `COMPLETED` or `REVIEWED`.
- Create: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\OrderServiceImplTest.java`
  Responsibility: cover complete-order transitions.
- Create: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\ReviewServiceImplTest.java`
  Responsibility: cover create-review, duplicate-review, and one-time-edit rules.
- Modify: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\PaymentRefundServiceImplTest.java`
  Responsibility: verify completed and reviewed orders cannot be refunded.
- Modify: `C:\javapractice\MyRent\frontend\src\api\order.js`
  Responsibility: add `completeOrder`.
- Modify: `C:\javapractice\MyRent\frontend\src\api\house.js`
  Responsibility: add `fetchHouseReviews`.
- Create: `C:\javapractice\MyRent\frontend\src\api\review.js`
  Responsibility: add create, update, and detail review requests.
- Modify: `C:\javapractice\MyRent\frontend\src\router\index.js`
  Responsibility: register the review form route.
- Modify: `C:\javapractice\MyRent\frontend\src\views\mine\MineOrderView.vue`
  Responsibility: render complete/review/edit buttons from backend action flags.
- Create: `C:\javapractice\MyRent\frontend\src\views\mine\MineOrderReviewView.vue`
  Responsibility: render create/edit review form.
- Modify: `C:\javapractice\MyRent\frontend\src\views\HouseDetailView.vue`
  Responsibility: show review summary and latest five reviews.
- Modify: `C:\javapractice\MyRent\frontend\src\utils\format.js`
  Responsibility: centralize order status text and review score formatting.
- Modify: `C:\javapractice\MyRent\frontend\src\views\__tests__\MineOrderView.spec.js`
  Responsibility: verify complete/review/edit buttons.
- Modify: `C:\javapractice\MyRent\frontend\src\views\__tests__\HouseDetailView.spec.js`
  Responsibility: verify review summary and latest review rendering.
- Create: `C:\javapractice\MyRent\frontend\src\views\__tests__\MineOrderReviewView.spec.js`
  Responsibility: verify create and edit submission.

### Task 1: Align Schema and Order Status Constants

**Files:**
- Modify: `C:\javapractice\MyRent\sql\rent-schema\order.sql`
- Modify: `C:\javapractice\MyRent\sql\rent-schema\rent-schema-all.sql`
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\common\OrderStatus.java`
- Test: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\OrderServiceImplTest.java`

- [ ] **Step 1: Write the failing contract test for the new order states**

Create `OrderServiceImplTest.java` with this test:

```java
package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderServiceImplTest {

    @Test
    void orderStatusShouldExposePaidCompletedAndReviewedStates() throws Exception {
        assertDoesNotThrow(() -> OrderStatus.class.getField("PAID"));
        assertDoesNotThrow(() -> OrderStatus.class.getField("COMPLETED"));
        assertDoesNotThrow(() -> OrderStatus.class.getField("REVIEWED"));
        assertEquals(1, OrderStatus.PAID);
        assertEquals(5, OrderStatus.COMPLETED);
        assertEquals(6, OrderStatus.REVIEWED);
    }
}
```

- [ ] **Step 2: Run the targeted backend test to verify it fails**

Run:

```bash
mvn -Dtest=OrderServiceImplTest#orderStatusShouldExposePaidCompletedAndReviewedStates test
```

Expected: FAIL because `PAID`, `COMPLETED`, and `REVIEWED` do not exist yet.

- [ ] **Step 3: Update the order status constants without breaking existing stored values**

Update `OrderStatus.java` to:

```java
public final class OrderStatus {

    public static final int UNPAID = 0;
    public static final int PAID = 1;
    public static final int PAID_LOCKED = PAID;
    public static final int CLOSED_TIMEOUT = 2;
    public static final int USER_CANCELLED = 3;
    public static final int REFUNDED = 4;
    public static final int COMPLETED = 5;
    public static final int REVIEWED = 6;

    private OrderStatus() {
    }
}
```

Update the `status` comment in `order.sql` and `rent-schema-all.sql` to:

```sql
`status` tinyint NOT NULL DEFAULT '0' COMMENT '0未支付，1已支付，2超时关闭，3用户取消，4已退款，5已完成，6已评价',
```

- [ ] **Step 4: Run the targeted test to verify it passes**

Run:

```bash
mvn -Dtest=OrderServiceImplTest#orderStatusShouldExposePaidCompletedAndReviewedStates test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add sql/rent-schema/order.sql sql/rent-schema/rent-schema-all.sql src/main/java/cn/yy/myrent/common/OrderStatus.java src/test/java/cn/yy/myrent/service/impl/OrderServiceImplTest.java
git commit -m "feat(review): add completed and reviewed order states"
```

### Task 2: Add Complete-Order Backend Flow

**Files:**
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\mapper\OrderMapper.java`
- Modify: `C:\javapractice\MyRent\src\main\resources\mapper\OrderMapper.xml`
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\IOrderService.java`
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\OrderServiceImpl.java`
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\controller\OrderController.java`
- Test: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\OrderServiceImplTest.java`

- [ ] **Step 1: Add the failing complete-order service tests**

Append these tests to `OrderServiceImplTest.java`:

```java
    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void completeOrderShouldMovePaidOrderToCompleted() {
        Order order = new Order();
        order.setOrderNo("ORDER-COMPLETE-1");
        order.setUserId(1001L);
        order.setStatus(OrderStatus.PAID);

        try (MockedStatic<UserContext> mockedUserContext = Mockito.mockStatic(UserContext.class)) {
            mockedUserContext.when(UserContext::requireCurrentUserId).thenReturn(1001L);
            when(orderMapper.selectOrderNo("ORDER-COMPLETE-1")).thenReturn(order);
            when(orderMapper.markCompletedIfPaid("ORDER-COMPLETE-1", 1001L, OrderStatus.PAID, OrderStatus.COMPLETED, order.getUpdateTime()))
                    .thenReturn(1);

            assertDoesNotThrow(() -> orderService.completeOrder("ORDER-COMPLETE-1"));
        }
    }

    @Test
    void completeOrderShouldRejectNonPaidOrder() {
        Order order = new Order();
        order.setOrderNo("ORDER-COMPLETE-2");
        order.setUserId(1002L);
        order.setStatus(OrderStatus.UNPAID);

        try (MockedStatic<UserContext> mockedUserContext = Mockito.mockStatic(UserContext.class)) {
            mockedUserContext.when(UserContext::requireCurrentUserId).thenReturn(1002L);
            when(orderMapper.selectOrderNo("ORDER-COMPLETE-2")).thenReturn(order);

            RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.completeOrder("ORDER-COMPLETE-2"));
            assertEquals("order is not completable", ex.getMessage());
        }
    }
```

- [ ] **Step 2: Run the targeted test class to verify it fails**

Run:

```bash
mvn -Dtest=OrderServiceImplTest test
```

Expected: FAIL because `completeOrder(...)` and `markCompletedIfPaid(...)` do not exist yet.

- [ ] **Step 3: Add guarded mapper methods and service logic**

Update `OrderMapper.java`:

```java
    int markCompletedIfPaid(@Param("orderNo") String orderNo,
                            @Param("userId") Long userId,
                            @Param("expectedStatus") Integer expectedStatus,
                            @Param("targetStatus") Integer targetStatus,
                            @Param("updateTime") LocalDateTime updateTime);

    int markReviewedIfCompleted(@Param("orderNo") String orderNo,
                                @Param("userId") Long userId,
                                @Param("expectedStatus") Integer expectedStatus,
                                @Param("targetStatus") Integer targetStatus,
                                @Param("updateTime") LocalDateTime updateTime);
```

Add to `OrderMapper.xml`:

```xml
    <update id="markCompletedIfPaid">
        update `order`
        set status = #{targetStatus},
            update_time = #{updateTime}
        where order_no = #{orderNo}
          and user_id = #{userId}
          and status = #{expectedStatus}
    </update>

    <update id="markReviewedIfCompleted">
        update `order`
        set status = #{targetStatus},
            update_time = #{updateTime}
        where order_no = #{orderNo}
          and user_id = #{userId}
          and status = #{expectedStatus}
    </update>
```

Add to `IOrderService.java`:

```java
    void completeOrder(String orderNo);

    Page<MyOrderItemVO> pageMineOrders(Long userId, long current, long size);
```

Add to `OrderServiceImpl.java`:

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeOrder(String orderNo) {
        Long currentUserId = UserContext.requireCurrentUserId();
        Order order = orderMapper.selectOrderNo(orderNo);
        if (order == null || !currentUserId.equals(order.getUserId())) {
            throw new RuntimeException("order not found");
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.PAID) {
            throw new RuntimeException("order is not completable");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.markCompletedIfPaid(orderNo, currentUserId, OrderStatus.PAID, OrderStatus.COMPLETED, now);
        if (updated <= 0) {
            throw new RuntimeException("order complete failed");
        }
    }
```

Add to `OrderController.java`:

```java
    @PostMapping("/{orderNo}/complete")
    @Operation(summary = "complete a paid order")
    public ResponseEntity<Result<Void>> complete(@PathVariable String orderNo) {
        try {
            orderService.completeOrder(orderNo);
            return ResponseEntity.ok(Result.success());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(401, e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error(e.getMessage()));
        } catch (Exception e) {
            log.error("complete order failed, orderNo={}", orderNo, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("system busy, please retry later"));
        }
    }
```

- [ ] **Step 4: Run the targeted test class to verify it passes**

Run:

```bash
mvn -Dtest=OrderServiceImplTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/mapper/OrderMapper.java src/main/resources/mapper/OrderMapper.xml src/main/java/cn/yy/myrent/service/IOrderService.java src/main/java/cn/yy/myrent/service/impl/OrderServiceImpl.java src/main/java/cn/yy/myrent/controller/OrderController.java src/test/java/cn/yy/myrent/service/impl/OrderServiceImplTest.java
git commit -m "feat(review): add complete order backend flow"
```

### Task 3: Build the Review Domain and Backend APIs

**Files:**
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\entity\Review.java`
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\dto\ReviewCreateReqDTO.java`
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\dto\ReviewUpdateReqDTO.java`
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\mapper\ReviewMapper.java`
- Create: `C:\javapractice\MyRent\src\main\resources\mapper\ReviewMapper.xml`
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\IReviewService.java`
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\ReviewServiceImpl.java`
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\controller\ReviewController.java`
- Test: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\ReviewServiceImplTest.java`

- [ ] **Step 1: Write the failing review service tests**

Create `ReviewServiceImplTest.java` with:

```java
package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.ReviewCreateReqDTO;
import cn.yy.myrent.dto.ReviewUpdateReqDTO;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Review;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.ReviewMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Test
    void createReviewShouldMoveCompletedOrderToReviewed() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("ORDER-REVIEW-1");
        order.setUserId(2001L);
        order.setHouseId(301L);
        order.setStatus(OrderStatus.COMPLETED);

        when(orderMapper.selectOrderNo("ORDER-REVIEW-1")).thenReturn(order);
        when(reviewMapper.selectByOrderNo("ORDER-REVIEW-1")).thenReturn(null);
        when(reviewMapper.insert(any(Review.class))).thenReturn(1);
        when(orderMapper.markReviewedIfCompleted(any(), any(), any(), any(), any())).thenReturn(1);

        ReviewCreateReqDTO req = new ReviewCreateReqDTO();
        req.setOrderNo("ORDER-REVIEW-1");
        req.setScore(5);
        req.setContent("房源整体不错，第一版评价链路验证通过。");

        try (MockedStatic<UserContext> mockedUserContext = Mockito.mockStatic(UserContext.class)) {
            mockedUserContext.when(UserContext::requireCurrentUserId).thenReturn(2001L);
            Review review = reviewService.createReview(req);
            assertEquals("ORDER-REVIEW-1", review.getOrderNo());
            assertEquals(0, review.getEditCount());
        }
    }

    @Test
    void createReviewShouldRejectDuplicateOrderReview() {
        Order order = new Order();
        order.setOrderNo("ORDER-REVIEW-2");
        order.setUserId(2002L);
        order.setStatus(OrderStatus.COMPLETED);

        Review existing = new Review();
        existing.setId(9L);
        existing.setOrderNo("ORDER-REVIEW-2");

        when(orderMapper.selectOrderNo("ORDER-REVIEW-2")).thenReturn(order);
        when(reviewMapper.selectByOrderNo("ORDER-REVIEW-2")).thenReturn(existing);

        ReviewCreateReqDTO req = new ReviewCreateReqDTO();
        req.setOrderNo("ORDER-REVIEW-2");
        req.setScore(4);
        req.setContent("重复评价应当被拦截。");

        try (MockedStatic<UserContext> mockedUserContext = Mockito.mockStatic(UserContext.class)) {
            mockedUserContext.when(UserContext::requireCurrentUserId).thenReturn(2002L);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> reviewService.createReview(req));
            assertEquals("order already reviewed", ex.getMessage());
        }
    }

    @Test
    void updateReviewShouldAllowOnlyOneEdit() {
        Review review = new Review();
        review.setId(10L);
        review.setUserId(2003L);
        review.setEditCount(0);
        review.setContent("初始内容");

        when(reviewMapper.selectById(10L)).thenReturn(review);
        when(reviewMapper.updateById(any(Review.class))).thenReturn(1);

        ReviewUpdateReqDTO req = new ReviewUpdateReqDTO();
        req.setScore(3);
        req.setContent("修改后的内容");

        try (MockedStatic<UserContext> mockedUserContext = Mockito.mockStatic(UserContext.class)) {
            mockedUserContext.when(UserContext::requireCurrentUserId).thenReturn(2003L);
            Review updated = reviewService.updateReview(10L, req);
            assertEquals(1, updated.getEditCount());
            assertEquals("修改后的内容", updated.getContent());
        }
    }
}
```

- [ ] **Step 2: Run the targeted test class to verify it fails**

Run:

```bash
mvn -Dtest=ReviewServiceImplTest test
```

Expected: FAIL because the review entity, mapper, service, and controller do not exist yet.

- [ ] **Step 3: Add the review persistence and service layer**

Create `Review.java`:

```java
@Data
@Accessors(chain = true)
@TableName("review")
public class Review implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private String orderNo;
    private Long houseId;
    private Long userId;
    private Integer score;
    private String content;
    private Integer editCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

Create DTOs:

```java
public class ReviewCreateReqDTO {

    @NotBlank
    private String orderNo;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer score;

    @NotBlank
    private String content;
}
```

```java
public class ReviewUpdateReqDTO {

    @NotNull
    @Min(1)
    @Max(5)
    private Integer score;

    @NotBlank
    private String content;
}
```

Create `ReviewMapper.java`:

```java
public interface ReviewMapper extends BaseMapper<Review> {

    Review selectByOrderNo(@Param("orderNo") String orderNo);

    List<Review> selectByOrderNos(@Param("orderNos") List<String> orderNos);
}
```

Create `IReviewService.java`:

```java
public interface IReviewService extends IService<Review> {

    Review createReview(ReviewCreateReqDTO req);

    Review updateReview(Long reviewId, ReviewUpdateReqDTO req);

    Review getOwnedReviewDetail(Long reviewId);

    HouseReviewPageVO pageHouseReviews(Long houseId, long current, long size);

    Map<String, Review> mapByOrderNos(List<String> orderNos);
}
```

Create `ReviewServiceImpl.java`:

```java
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements IReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Review createReview(ReviewCreateReqDTO req) {
        Long currentUserId = UserContext.requireCurrentUserId();
        Order order = orderMapper.selectOrderNo(req.getOrderNo());
        if (order == null || !currentUserId.equals(order.getUserId())) {
            throw new RuntimeException("order not found");
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.COMPLETED) {
            throw new RuntimeException("order is not reviewable");
        }
        if (reviewMapper.selectByOrderNo(order.getOrderNo()) != null) {
            throw new RuntimeException("order already reviewed");
        }

        LocalDateTime now = LocalDateTime.now();
        Review review = new Review()
                .setOrderId(order.getId())
                .setOrderNo(order.getOrderNo())
                .setHouseId(order.getHouseId())
                .setUserId(currentUserId)
                .setScore(req.getScore())
                .setContent(req.getContent().trim())
                .setEditCount(0)
                .setCreateTime(now)
                .setUpdateTime(now);
        reviewMapper.insert(review);

        int updated = orderMapper.markReviewedIfCompleted(order.getOrderNo(), currentUserId, OrderStatus.COMPLETED, OrderStatus.REVIEWED, now);
        if (updated <= 0) {
            throw new RuntimeException("order review state update failed");
        }
        return review;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Review updateReview(Long reviewId, ReviewUpdateReqDTO req) {
        Long currentUserId = UserContext.requireCurrentUserId();
        Review review = reviewMapper.selectById(reviewId);
        if (review == null || !currentUserId.equals(review.getUserId())) {
            throw new RuntimeException("review not found");
        }
        if (review.getEditCount() == null || review.getEditCount() > 0) {
            throw new RuntimeException("review edit chance already used");
        }

        review.setScore(req.getScore());
        review.setContent(req.getContent().trim());
        review.setEditCount(1);
        review.setUpdateTime(LocalDateTime.now());
        reviewMapper.updateById(review);
        return review;
    }
}
```

Create `ReviewController.java`:

```java
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    private final IReviewService reviewService;

    @PostMapping
    public Result<Long> create(@Valid @RequestBody ReviewCreateReqDTO req) {
        return Result.success(reviewService.createReview(req).getId());
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ReviewUpdateReqDTO req) {
        reviewService.updateReview(id, req);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<Review> detail(@PathVariable Long id) {
        return Result.success(reviewService.getOwnedReviewDetail(id));
    }
}
```

- [ ] **Step 4: Run the targeted test class to verify it passes**

Run:

```bash
mvn -Dtest=ReviewServiceImplTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/entity/Review.java src/main/java/cn/yy/myrent/dto/ReviewCreateReqDTO.java src/main/java/cn/yy/myrent/dto/ReviewUpdateReqDTO.java src/main/java/cn/yy/myrent/mapper/ReviewMapper.java src/main/resources/mapper/ReviewMapper.xml src/main/java/cn/yy/myrent/service/IReviewService.java src/main/java/cn/yy/myrent/service/impl/ReviewServiceImpl.java src/main/java/cn/yy/myrent/controller/ReviewController.java src/test/java/cn/yy/myrent/service/impl/ReviewServiceImplTest.java
git commit -m "feat(review): add review create and update backend"
```

### Task 4: Expose House Reviews, Review-Aware Order Paging, and Refund Guards

**Files:**
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\vo\HouseReviewItemVO.java`
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\vo\HouseReviewPageVO.java`
- Create: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\vo\MyOrderItemVO.java`
- Modify: `C:\javapractice\MyRent\src\main\resources\mapper\ReviewMapper.xml`
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\ReviewServiceImpl.java`
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\OrderServiceImpl.java`
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\controller\OrderController.java`
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\controller\HouseController.java`
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\PaymentRefundServiceImpl.java`
- Modify: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\PaymentRefundServiceImplTest.java`

- [ ] **Step 1: Add the failing refund-guard tests**

Append to `PaymentRefundServiceImplTest.java`:

```java
    @Test
    void userApplyRefundShouldRejectCompletedOrder() {
        Order order = new Order();
        order.setOrderNo("ORDER-COMPLETE-REFUND");
        order.setStatus(OrderStatus.COMPLETED);
        order.setSuccessPaymentNo("PAY-COMPLETE-REFUND");
        order.setUserId(3001L);

        Payment payment = new Payment();
        payment.setPaymentNo("PAY-COMPLETE-REFUND");
        payment.setOrderNo("ORDER-COMPLETE-REFUND");
        payment.setUserId(3001L);
        payment.setStatus(PaymentStatus.PAID);

        when(orderMapper.selectOrderNo("ORDER-COMPLETE-REFUND")).thenReturn(order);
        when(paymentMapper.selectByPaymentNo("PAY-COMPLETE-REFUND")).thenReturn(payment);

        PaymentRefundApplyCommand command = new PaymentRefundApplyCommand();
        command.setOrderNo("ORDER-COMPLETE-REFUND");
        command.setSourceType(PaymentRefundSourceType.USER_APPLY);
        command.setReasonCode(PaymentRefundReasonCode.USER_APPLY);
        command.setUserId(3001L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> paymentRefundService.applyRefund(command));
        assertEquals("order is not refundable", ex.getMessage());
    }

    @Test
    void userApplyRefundShouldRejectReviewedOrder() {
        Order order = new Order();
        order.setOrderNo("ORDER-REVIEWED-REFUND");
        order.setStatus(OrderStatus.REVIEWED);
        order.setSuccessPaymentNo("PAY-REVIEWED-REFUND");
        order.setUserId(3002L);

        Payment payment = new Payment();
        payment.setPaymentNo("PAY-REVIEWED-REFUND");
        payment.setOrderNo("ORDER-REVIEWED-REFUND");
        payment.setUserId(3002L);
        payment.setStatus(PaymentStatus.PAID);

        when(orderMapper.selectOrderNo("ORDER-REVIEWED-REFUND")).thenReturn(order);
        when(paymentMapper.selectByPaymentNo("PAY-REVIEWED-REFUND")).thenReturn(payment);

        PaymentRefundApplyCommand command = new PaymentRefundApplyCommand();
        command.setOrderNo("ORDER-REVIEWED-REFUND");
        command.setSourceType(PaymentRefundSourceType.USER_APPLY);
        command.setReasonCode(PaymentRefundReasonCode.USER_APPLY);
        command.setUserId(3002L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> paymentRefundService.applyRefund(command));
        assertEquals("order is not refundable", ex.getMessage());
    }
```

- [ ] **Step 2: Run the targeted refund test to verify it fails**

Run:

```bash
mvn -Dtest=PaymentRefundServiceImplTest#userApplyRefundShouldRejectCompletedOrder,PaymentRefundServiceImplTest#userApplyRefundShouldRejectReviewedOrder test
```

Expected: FAIL because completed and reviewed states are not yet treated as blocked refund states.

- [ ] **Step 3: Add house review queries and order-list VOs**

Create `HouseReviewItemVO.java`:

```java
@Data
public class HouseReviewItemVO {
    private Long reviewId;
    private String orderNo;
    private Integer score;
    private String content;
    private String reviewerName;
    private Boolean edited;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

Create `HouseReviewPageVO.java`:

```java
@Data
public class HouseReviewPageVO {
    private Double averageScore;
    private Long reviewCount;
    private List<HouseReviewItemVO> records;
}
```

Create `MyOrderItemVO.java`:

```java
@Data
public class MyOrderItemVO {
    private Long id;
    private String orderNo;
    private Long houseId;
    private Integer amount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
    private LocalDateTime paidTime;
    private Long reviewId;
    private Boolean hasReview;
    private Boolean canComplete;
    private Boolean canReview;
    private Boolean canEditReview;
}
```

Update `ReviewMapper.xml` with:

```xml
    <select id="selectByOrderNo" resultType="cn.yy.myrent.entity.Review">
        select * from review where order_no = #{orderNo}
    </select>

    <select id="selectByOrderNos" resultType="cn.yy.myrent.entity.Review">
        select * from review
        where order_no in
        <foreach collection="orderNos" item="orderNo" open="(" separator="," close=")">
            #{orderNo}
        </foreach>
    </select>

    <select id="selectLatestByHouseId" resultType="cn.yy.myrent.vo.HouseReviewItemVO">
        select r.id as reviewId,
               r.order_no as orderNo,
               r.score,
               r.content,
               coalesce(u.name, '用户') as reviewerName,
               case when r.edit_count > 0 then true else false end as edited,
               r.create_time as createTime,
               r.update_time as updateTime
        from review r
        left join user u on u.id = r.user_id
        where r.house_id = #{houseId}
        order by r.create_time desc, r.id desc
        limit #{offset}, #{size}
    </select>

    <select id="countByHouseId" resultType="java.lang.Long">
        select count(1) from review where house_id = #{houseId}
    </select>

    <select id="avgScoreByHouseId" resultType="java.lang.Double">
        select coalesce(avg(score), 0) from review where house_id = #{houseId}
    </select>
```

Extend `ReviewMapper.java` to match these methods, then add to `ReviewServiceImpl.java`:

```java
    @Override
    public HouseReviewPageVO pageHouseReviews(Long houseId, long current, long size) {
        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), 20L);
        long offset = (safeCurrent - 1L) * safeSize;

        HouseReviewPageVO result = new HouseReviewPageVO();
        result.setAverageScore(Optional.ofNullable(reviewMapper.avgScoreByHouseId(houseId)).orElse(0D));
        result.setReviewCount(Optional.ofNullable(reviewMapper.countByHouseId(houseId)).orElse(0L));
        result.setRecords(reviewMapper.selectLatestByHouseId(houseId, offset, safeSize));
        return result;
    }

    @Override
    public Map<String, Review> mapByOrderNos(List<String> orderNos) {
        if (CollectionUtils.isEmpty(orderNos)) {
            return Map.of();
        }
        return reviewMapper.selectByOrderNos(orderNos).stream()
                .collect(Collectors.toMap(Review::getOrderNo, Function.identity(), (left, right) -> left));
    }

    @Override
    public Review getOwnedReviewDetail(Long reviewId) {
        Long currentUserId = UserContext.requireCurrentUserId();
        Review review = reviewMapper.selectById(reviewId);
        if (review == null || !currentUserId.equals(review.getUserId())) {
            throw new RuntimeException("review not found");
        }
        return review;
    }
```

Update `OrderServiceImpl.java` to add:

```java
    @Autowired
    private IReviewService reviewService;

    @Override
    public Page<MyOrderItemVO> pageMineOrders(Long userId, long current, long size) {
        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), 100L);
        Page<Order> page = this.lambdaQuery()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getId)
                .page(new Page<>(safeCurrent, safeSize));

        List<String> orderNos = page.getRecords().stream().map(Order::getOrderNo).toList();
        Map<String, Review> reviewMap = reviewService.mapByOrderNos(orderNos);

        List<MyOrderItemVO> records = page.getRecords().stream().map(order -> {
            Review review = reviewMap.get(order.getOrderNo());
            MyOrderItemVO item = new MyOrderItemVO();
            item.setId(order.getId());
            item.setOrderNo(order.getOrderNo());
            item.setHouseId(order.getHouseId());
            item.setAmount(order.getAmount());
            item.setStatus(order.getStatus());
            item.setCreateTime(order.getCreateTime());
            item.setExpireTime(order.getExpireTime());
            item.setPaidTime(order.getPaidTime());
            item.setReviewId(review == null ? null : review.getId());
            item.setHasReview(review != null);
            item.setCanComplete(order.getStatus() != null && order.getStatus() == OrderStatus.PAID);
            item.setCanReview(order.getStatus() != null && order.getStatus() == OrderStatus.COMPLETED);
            item.setCanEditReview(order.getStatus() != null
                    && order.getStatus() == OrderStatus.REVIEWED
                    && review != null
                    && review.getEditCount() != null
                    && review.getEditCount() == 0);
            return item;
        }).toList();

        Page<MyOrderItemVO> result = new Page<>(safeCurrent, safeSize, page.getTotal());
        result.setRecords(records);
        return result;
    }
```

Update `OrderController.java` mine endpoint:

```java
    public Result<Page<MyOrderItemVO>> mine(...) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        return Result.success(orderService.pageMineOrders(userId, current, size));
    }
```

Update `HouseController.java`:

```java
    private final IReviewService reviewService;

    @GetMapping("/{houseId}/reviews")
    @Operation(summary = "查询房源评价列表")
    public Result<HouseReviewPageVO> reviews(@PathVariable Long houseId,
                                             @RequestParam(value = "current", defaultValue = "1") Long current,
                                             @RequestParam(value = "size", defaultValue = "5") Long size) {
        return Result.success(reviewService.pageHouseReviews(houseId, current, size));
    }
```

Update `PaymentRefundServiceImpl.java` inside `validateRefundRequest(...)`:

```java
        if (command.getSourceType() == PaymentRefundSourceType.USER_APPLY) {
            if (order.getStatus() != OrderStatus.PAID
                    || payment.getStatus() == null
                    || payment.getStatus() != PaymentStatus.PAID
                    || !payment.getPaymentNo().equals(order.getSuccessPaymentNo())) {
                throw new RuntimeException("order is not refundable");
            }
        }
```

- [ ] **Step 4: Run the targeted backend tests to verify they pass**

Run:

```bash
mvn -Dtest=PaymentRefundServiceImplTest#userApplyRefundShouldRejectCompletedOrder,PaymentRefundServiceImplTest#userApplyRefundShouldRejectReviewedOrder,ReviewServiceImplTest,OrderServiceImplTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/vo/HouseReviewItemVO.java src/main/java/cn/yy/myrent/vo/HouseReviewPageVO.java src/main/java/cn/yy/myrent/vo/MyOrderItemVO.java src/main/resources/mapper/ReviewMapper.xml src/main/java/cn/yy/myrent/service/impl/ReviewServiceImpl.java src/main/java/cn/yy/myrent/service/impl/OrderServiceImpl.java src/main/java/cn/yy/myrent/controller/OrderController.java src/main/java/cn/yy/myrent/controller/HouseController.java src/main/java/cn/yy/myrent/service/impl/PaymentRefundServiceImpl.java src/test/java/cn/yy/myrent/service/impl/PaymentRefundServiceImplTest.java
git commit -m "feat(review): add review queries and order action flags"
```

### Task 5: Wire Frontend Order Actions and Review Form

**Files:**
- Modify: `C:\javapractice\MyRent\frontend\src\api\order.js`
- Modify: `C:\javapractice\MyRent\frontend\src\router\index.js`
- Create: `C:\javapractice\MyRent\frontend\src\api\review.js`
- Create: `C:\javapractice\MyRent\frontend\src\views\mine\MineOrderReviewView.vue`
- Modify: `C:\javapractice\MyRent\frontend\src\views\mine\MineOrderView.vue`
- Modify: `C:\javapractice\MyRent\frontend\src\utils\format.js`
- Modify: `C:\javapractice\MyRent\frontend\src\views\__tests__\MineOrderView.spec.js`
- Create: `C:\javapractice\MyRent\frontend\src\views\__tests__\MineOrderReviewView.spec.js`

- [ ] **Step 1: Add the failing frontend tests**

Update `MineOrderView.spec.js` mocked order record to:

```js
    records: [{
      id: 1,
      orderNo: 'ORDER-1001',
      houseId: 101,
      amount: 100000,
      status: 1,
      createTime: '2026-04-18T20:00:00',
      expireTime: '2026-04-18T20:00:30',
      canComplete: true,
      canReview: false,
      canEditReview: false,
      reviewId: null,
      hasReview: false
    }],
```

Add mocks:

```js
import { completeOrder } from '@/api/order'

vi.mock('@/api/order', () => ({
  fetchMyOrderPage: vi.fn(async () => ({ ... })),
  repayOrder: vi.fn(),
  completeOrder: vi.fn(async () => undefined)
}))
```

Add this test:

```js
  it('shows complete order button for paid orders', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/mine/orders', component: MineOrderView }]
    })
    router.push('/mine/orders')
    await router.isReady()

    const wrapper = mount(MineOrderView, { global: { plugins: [router] } })
    await new Promise((resolve) => setTimeout(resolve, 0))

    const button = wrapper.findAll('button').find((item) => item.text().includes('完成订单'))
    expect(button.exists()).toBe(true)
    await button.trigger('click')
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(completeOrder).toHaveBeenCalledWith('ORDER-1001')
  })
```

Create `MineOrderReviewView.spec.js`:

```js
import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import MineOrderReviewView from '@/views/mine/MineOrderReviewView.vue'
import { createReview, fetchReviewById, updateReview } from '@/api/review'

vi.mock('@/api/review', () => ({
  createReview: vi.fn(async () => 11),
  updateReview: vi.fn(async () => undefined),
  fetchReviewById: vi.fn(async () => ({
    id: 11,
    orderNo: 'ORDER-1001',
    score: 4,
    content: '已存在的评价内容'
  }))
}))

describe('MineOrderReviewView', () => {
  it('submits create review payload', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/mine/orders/:orderNo/review', component: MineOrderReviewView }]
    })
    router.push('/mine/orders/ORDER-1001/review')
    await router.isReady()

    const wrapper = mount(MineOrderReviewView, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.find('textarea').setValue('房间干净，交通方便。')
    await wrapper.find('select').setValue('5')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(createReview).toHaveBeenCalledWith({
      orderNo: 'ORDER-1001',
      score: 5,
      content: '房间干净，交通方便。'
    })
  })
})
```

- [ ] **Step 2: Run the targeted frontend tests to verify they fail**

Run:

```bash
cd frontend
npm run test:run -- src/views/__tests__/MineOrderView.spec.js src/views/__tests__/MineOrderReviewView.spec.js
```

Expected: FAIL because `completeOrder`, `review.js`, and `MineOrderReviewView.vue` do not exist yet.

- [ ] **Step 3: Add the review API module, router entry, and review form page**

Update `frontend/src/api/order.js`:

```js
export function completeOrder(orderNo) {
  return http.post(`/order/${orderNo}/complete`)
}
```

Create `frontend/src/api/review.js`:

```js
import http from './http'

export function createReview(payload) {
  return http.post('/review', payload)
}

export function updateReview(reviewId, payload) {
  return http.put(`/review/${reviewId}`, payload)
}

export function fetchReviewById(reviewId) {
  return http.get(`/review/${reviewId}`)
}
```

Update `frontend/src/router/index.js`:

```js
      {
        path: 'mine/orders/:orderNo/review',
        name: 'mine-order-review',
        component: () => import('@/views/mine/MineOrderReviewView.vue')
      },
```

Create `frontend/src/views/mine/MineOrderReviewView.vue`:

```vue
<template>
  <div class="page mine-sub-page">
    <section class="card">
      <h2 class="section-title">{{ isEdit ? '修改评价' : '发表评价' }}</h2>
      <form class="review-form" @submit.prevent="submit">
        <label>
          <span>评分</span>
          <select v-model.number="form.score">
            <option :value="1">1 星</option>
            <option :value="2">2 星</option>
            <option :value="3">3 星</option>
            <option :value="4">4 星</option>
            <option :value="5">5 星</option>
          </select>
        </label>
        <label>
          <span>评价内容</span>
          <textarea v-model.trim="form.content" rows="6" maxlength="500" />
        </label>
        <div class="actions">
          <button type="submit" class="primary-btn" :disabled="submitting">
            {{ submitting ? '提交中...' : '提交评价' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createReview, fetchReviewById, updateReview } from '@/api/review'
import { formatRequestError } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const submitting = ref(false)
const form = reactive({ score: 5, content: '' })
const reviewId = computed(() => route.query.reviewId || '')
const isEdit = computed(() => Boolean(reviewId.value))

async function loadReview() {
  if (!isEdit.value) {
    return
  }
  const review = await fetchReviewById(reviewId.value)
  form.score = Number(review?.score || 5)
  form.content = review?.content || ''
}

async function submit() {
  if (!form.content.trim()) {
    window.alert('评价内容不能为空')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateReview(reviewId.value, { score: form.score, content: form.content })
    } else {
      await createReview({ orderNo: route.params.orderNo, score: form.score, content: form.content })
    }
    router.replace('/mine/orders')
  } catch (error) {
    window.alert(formatRequestError(error, '评价提交失败'))
  } finally {
    submitting.value = false
  }
}

onMounted(loadReview)
</script>
```

Update `frontend/src/utils/format.js` with:

```js
export function getOrderStatusText(status) {
  if (status === 0) return '待支付'
  if (status === 1) return '已支付'
  if (status === 2) return '超时关闭'
  if (status === 3) return '已取消'
  if (status === 4) return '已退款'
  if (status === 5) return '已完成'
  if (status === 6) return '已评价'
  return '未知状态'
}
```

- [ ] **Step 4: Update the order list page to use the new action flags**

Replace the local `getOrderStatusText` in `MineOrderView.vue` with the shared formatter import:

```js
import { formatDateTime, formatPrice, getOrderStatusText, formatRequestError } from '@/utils/format'
import { completeOrder, fetchMyOrderPage, repayOrder } from '@/api/order'
```

Update action buttons:

```vue
        <button
          v-if="order.canComplete"
          class="ghost-btn"
          :disabled="completingOrderNo === order.orderNo"
          @click="submitComplete(order)"
        >
          完成订单
        </button>
        <button
          v-if="order.canReview"
          class="primary-btn"
          @click="goReview(order)"
        >
          去评价
        </button>
        <button
          v-if="order.canEditReview"
          class="ghost-btn"
          @click="goEditReview(order)"
        >
          修改评价
        </button>
```

Add methods:

```js
const completingOrderNo = ref('')

function goReview(order) {
  router.push(`/mine/orders/${order.orderNo}/review`)
}

function goEditReview(order) {
  router.push({
    path: `/mine/orders/${order.orderNo}/review`,
    query: { reviewId: String(order.reviewId || '') }
  })
}

async function submitComplete(order) {
  if (!order?.orderNo || completingOrderNo.value) {
    return
  }
  completingOrderNo.value = order.orderNo
  try {
    await completeOrder(order.orderNo)
    await loadOrders(true)
  } catch (error) {
    error.value = formatRequestError(error, '完成订单失败')
  } finally {
    completingOrderNo.value = ''
  }
}
```

- [ ] **Step 5: Run the targeted frontend tests to verify they pass**

Run:

```bash
cd frontend
npm run test:run -- src/views/__tests__/MineOrderView.spec.js src/views/__tests__/MineOrderReviewView.spec.js
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/order.js frontend/src/api/review.js frontend/src/router/index.js frontend/src/views/mine/MineOrderView.vue frontend/src/views/mine/MineOrderReviewView.vue frontend/src/utils/format.js frontend/src/views/__tests__/MineOrderView.spec.js frontend/src/views/__tests__/MineOrderReviewView.spec.js
git commit -m "feat(review): add review form and order actions"
```

### Task 6: Show Review Summary and Latest Reviews on House Detail

**Files:**
- Modify: `C:\javapractice\MyRent\frontend\src\api\house.js`
- Modify: `C:\javapractice\MyRent\frontend\src\views\HouseDetailView.vue`
- Modify: `C:\javapractice\MyRent\frontend\src\views\__tests__\HouseDetailView.spec.js`

- [ ] **Step 1: Add the failing house detail test**

Update the `@/api/house` mock in `HouseDetailView.spec.js`:

```js
  fetchHouseReviews: vi.fn().mockResolvedValue({
    averageScore: 4.5,
    reviewCount: 2,
    records: [
      {
        reviewId: 11,
        orderNo: 'ORDER-1001',
        score: 5,
        content: '房间采光不错。',
        reviewerName: '测试用户A',
        edited: false,
        createTime: '2026-04-21T10:00:00',
        updateTime: '2026-04-21T10:00:00'
      }
    ]
  }),
```

Add assertions:

```js
    expect(wrapper.text()).toContain('4.5')
    expect(wrapper.text()).toContain('2 条评价')
    expect(wrapper.text()).toContain('房间采光不错。')
```

- [ ] **Step 2: Run the targeted frontend test to verify it fails**

Run:

```bash
cd frontend
npm run test:run -- src/views/__tests__/HouseDetailView.spec.js
```

Expected: FAIL because house review loading and rendering are not implemented yet.

- [ ] **Step 3: Add the review API and render block in the detail page**

Update `frontend/src/api/house.js`:

```js
export function fetchHouseReviews(id, params = {}) {
  return http.get(`/house/${id}/reviews`, { params })
}
```

Update `HouseDetailView.vue` imports:

```js
import {
  favoriteHouse,
  fetchHouseById,
  fetchHouseFavoriteStatus,
  fetchHouseReviews,
  unfavoriteHouse
} from '@/api/house'
```

Add state and loader:

```js
const reviewSummary = ref({
  averageScore: 0,
  reviewCount: 0,
  records: []
})

async function loadReviews() {
  if (!route.params.id) {
    reviewSummary.value = { averageScore: 0, reviewCount: 0, records: [] }
    return
  }
  try {
    reviewSummary.value = await fetchHouseReviews(route.params.id, { current: 1, size: 5 })
  } catch {
    reviewSummary.value = { averageScore: 0, reviewCount: 0, records: [] }
  }
}
```

Call it from `loadHouse()`:

```js
    await Promise.all([
      loadPublisher(),
      loadFavoriteStatus(),
      loadReviews()
    ])
```

Add the template block under the notes section:

```vue
        <section class="reviews app-surface">
          <p class="eyebrow">Reviews</p>
          <div class="review-meta">
            <strong>{{ Number(reviewSummary.averageScore || 0).toFixed(1) }}</strong>
            <span>{{ reviewSummary.reviewCount || 0 }} 条评价</span>
          </div>
          <ul v-if="reviewSummary.records?.length" class="review-list">
            <li v-for="item in reviewSummary.records" :key="item.reviewId" class="review-item">
              <div class="review-head">
                <span>{{ item.reviewerName }}</span>
                <span>{{ item.score }} 星</span>
              </div>
              <p>{{ item.content }}</p>
            </li>
          </ul>
          <p v-else class="copy">当前还没有评价，后续完成订单的租客可以在“我的订单”里提交评价。</p>
        </section>
```

- [ ] **Step 4: Run the targeted frontend test to verify it passes**

Run:

```bash
cd frontend
npm run test:run -- src/views/__tests__/HouseDetailView.spec.js
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/house.js frontend/src/views/HouseDetailView.vue frontend/src/views/__tests__/HouseDetailView.spec.js
git commit -m "feat(review): show house review summary and list"
```

### Task 7: Run the Review Module Verification Suite

**Files:**
- Test: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\OrderServiceImplTest.java`
- Test: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\ReviewServiceImplTest.java`
- Test: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\PaymentRefundServiceImplTest.java`
- Test: `C:\javapractice\MyRent\frontend\src\views\__tests__\MineOrderView.spec.js`
- Test: `C:\javapractice\MyRent\frontend\src\views\__tests__\MineOrderReviewView.spec.js`
- Test: `C:\javapractice\MyRent\frontend\src\views\__tests__\HouseDetailView.spec.js`

- [ ] **Step 1: Run the focused backend review suite**

Run:

```bash
mvn -Dtest=OrderServiceImplTest,ReviewServiceImplTest,PaymentRefundServiceImplTest test
```

Expected: PASS with the order completion, review rules, and refund guards all green.

- [ ] **Step 2: Run the focused frontend review suite**

Run:

```bash
cd frontend
npm run test:run -- src/views/__tests__/MineOrderView.spec.js src/views/__tests__/MineOrderReviewView.spec.js src/views/__tests__/HouseDetailView.spec.js
```

Expected: PASS.

- [ ] **Step 3: Build the frontend once to catch route or import regressions**

Run:

```bash
cd frontend
npm run build
```

Expected: Vite build completes successfully.

- [ ] **Step 4: Run the backend package step once for compile verification**

Run:

```bash
mvn -DskipTests package
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit the verification checkpoint**

```bash
git add src/main/java src/main/resources/mapper src/test/java frontend/src sql/rent-schema
git commit -m "test(review): verify review module flow"
```

## Self-Review

- Spec coverage: The plan covers order states, complete-order flow, create/update review rules, house detail display, order list action flags, and refund rejection for completed or reviewed orders.
- Placeholder scan: No placeholder markers or vague “handle later” steps remain.
- Type consistency: `OrderStatus.PAID`, `OrderStatus.COMPLETED`, `OrderStatus.REVIEWED`, `markCompletedIfPaid`, `markReviewedIfCompleted`, `MyOrderItemVO`, and `HouseReviewPageVO` are used consistently across backend and frontend tasks.
