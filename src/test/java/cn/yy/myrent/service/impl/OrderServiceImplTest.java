package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Review;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.service.IReviewService;
import cn.yy.myrent.vo.MyOrderItemVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private IReviewService reviewService;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void orderStatusShouldExposePaidCompletedAndReviewedStates() throws Exception {
        assertDoesNotThrow(() -> OrderStatus.class.getField("PAID"));
        assertDoesNotThrow(() -> OrderStatus.class.getField("COMPLETED"));
        assertDoesNotThrow(() -> OrderStatus.class.getField("REVIEWED"));
        assertEquals(1, OrderStatus.PAID);
        assertEquals(5, OrderStatus.COMPLETED);
        assertEquals(6, OrderStatus.REVIEWED);
    }

    @Test
    void completeOrderShouldMovePaidOrderToCompleted() {
        Order order = new Order();
        order.setOrderNo("ORDER-COMPLETE-1");
        order.setUserId(1001L);
        order.setStatus(OrderStatus.PAID);

        try (MockedStatic<UserContext> mockedUserContext = Mockito.mockStatic(UserContext.class)) {
            mockedUserContext.when(UserContext::requireCurrentUserId).thenReturn(1001L);
            when(orderMapper.selectOrderNo("ORDER-COMPLETE-1")).thenReturn(order);
            when(orderMapper.markCompletedIfPaid(eq("ORDER-COMPLETE-1"), eq(1001L), eq(OrderStatus.PAID), eq(OrderStatus.COMPLETED), any()))
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

    @Test
    void pageMineOrdersShouldExposeReviewActionFlags() {
        Order paidOrder = new Order();
        paidOrder.setId(1L);
        paidOrder.setOrderNo("ORDER-MINE-PAID");
        paidOrder.setUserId(1001L);
        paidOrder.setHouseId(201L);
        paidOrder.setAmount(1200);
        paidOrder.setStatus(OrderStatus.PAID);

        Order completedOrder = new Order();
        completedOrder.setId(2L);
        completedOrder.setOrderNo("ORDER-MINE-COMPLETED");
        completedOrder.setUserId(1001L);
        completedOrder.setHouseId(202L);
        completedOrder.setAmount(1300);
        completedOrder.setStatus(OrderStatus.COMPLETED);

        Order reviewedOrder = new Order();
        reviewedOrder.setId(3L);
        reviewedOrder.setOrderNo("ORDER-MINE-REVIEWED");
        reviewedOrder.setUserId(1001L);
        reviewedOrder.setHouseId(203L);
        reviewedOrder.setAmount(1400);
        reviewedOrder.setStatus(OrderStatus.REVIEWED);

        Review review = new Review();
        review.setId(31L);
        review.setOrderNo("ORDER-MINE-REVIEWED");
        review.setEditCount(0);

        Page<Order> page = new Page<>(1, 10, 3);
        page.setRecords(List.of(paidOrder, completedOrder, reviewedOrder));

        when(orderMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(reviewService.mapByOrderNos(List.of("ORDER-MINE-PAID", "ORDER-MINE-COMPLETED", "ORDER-MINE-REVIEWED")))
                .thenReturn(Map.of("ORDER-MINE-REVIEWED", review));

        Page<MyOrderItemVO> result = orderService.pageMineOrders(1001L, 1, 10);

        assertEquals(3, result.getRecords().size());

        MyOrderItemVO paidItem = result.getRecords().get(0);
        assertTrue(paidItem.getCanComplete());
        assertFalse(paidItem.getCanReview());
        assertFalse(paidItem.getCanEditReview());

        MyOrderItemVO completedItem = result.getRecords().get(1);
        assertFalse(completedItem.getCanComplete());
        assertTrue(completedItem.getCanReview());
        assertFalse(completedItem.getCanEditReview());

        MyOrderItemVO reviewedItem = result.getRecords().get(2);
        assertFalse(reviewedItem.getCanComplete());
        assertFalse(reviewedItem.getCanReview());
        assertTrue(reviewedItem.getCanEditReview());
        assertTrue(reviewedItem.getHasReview());
        assertNotNull(reviewedItem.getReviewId());
    }
}
