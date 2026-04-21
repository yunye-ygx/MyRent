package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.PaymentRefundReasonCode;
import cn.yy.myrent.common.PaymentRefundSourceType;
import cn.yy.myrent.common.PaymentStatus;
import cn.yy.myrent.dto.PaymentRefundApplyCommand;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.PaymentMapper;
import cn.yy.myrent.mapper.PaymentRefundMapper;
import cn.yy.myrent.service.IHouseCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRefundServiceImplTest {

    @Mock
    private PaymentRefundMapper paymentRefundMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private IHouseCommandService houseCommandService;

    @InjectMocks
    private PaymentRefundServiceImpl paymentRefundService;

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
}
