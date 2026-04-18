package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.PaymentStatus;
import cn.yy.myrent.dto.MockPaymentCallbackReqDTO;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.PaymentMapper;
import cn.yy.myrent.service.IHouseCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private IHouseCommandService houseCommandService;

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

    @Test
    void handleMockCancelShouldReleaseHouse() {
        Payment payment = new Payment();
        payment.setPaymentNo("PAY-1002");
        payment.setOrderNo("ORDER-1002");
        payment.setStatus(PaymentStatus.WAITING);

        Order order = new Order();
        order.setOrderNo("ORDER-1002");
        order.setStatus(OrderStatus.UNPAID);
        order.setHouseId(202L);

        when(paymentMapper.selectByPaymentNo("PAY-1002")).thenReturn(payment);
        when(orderMapper.selectOrderNo("ORDER-1002")).thenReturn(order);

        MockPaymentCallbackReqDTO req = new MockPaymentCallbackReqDTO();
        req.setPaymentNo("PAY-1002");
        req.setOrderNo("ORDER-1002");
        req.setPayStatus("CANCELLED");
        req.setCallbackNo("CB-1002");
        req.setCallbackTime(LocalDateTime.now());

        paymentService.handleMockCallback(req);

        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
        assertEquals(OrderStatus.USER_CANCELLED, order.getStatus());
        org.mockito.Mockito.verify(houseCommandService)
                .updateHouseStatusWithSync(202L, 2, 1, "user-cancel-order");
    }
}
