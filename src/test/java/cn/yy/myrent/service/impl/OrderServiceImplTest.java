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
