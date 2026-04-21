package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

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
}
