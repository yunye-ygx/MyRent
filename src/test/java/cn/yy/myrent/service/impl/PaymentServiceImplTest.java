package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.MockPayTradeStatus;
import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.PaymentStatus;
import cn.yy.myrent.dto.MockPaymentCallbackReqDTO;
import cn.yy.myrent.entity.MockPayTrade;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.mapper.MockPayTradeMapper;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.PaymentMapper;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IMockPayTradeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Test
    void mapperContractsShouldExposeLatestPaymentAndMockTradeLookup() {
        assertDoesNotThrow(() -> PaymentMapper.class.getMethod("selectLatestActiveByOrderNo", String.class));
        assertDoesNotThrow(() -> PaymentMapper.class.getMethod("selectByOrderNo", String.class));
        assertDoesNotThrow(() -> OrderMapper.class.getMethod("markPaidIfUnpaid",
                String.class, LocalDateTime.class, String.class, LocalDateTime.class));
        assertDoesNotThrow(() -> MockPayTradeMapper.class.getMethod("selectByPaymentNo", String.class));
    }

    @Test
    void mapperShouldExposeSuspiciousPaymentsQuery() {
        assertDoesNotThrow(() -> PaymentMapper.class.getMethod("selectSuspiciousPayingPayments"));
    }

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private IHouseCommandService houseCommandService;

    @Mock
    private IMockPayTradeService mockPayTradeService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void getMockCheckoutShouldMarkPendingPaymentAsPaying() {
        Payment payment = new Payment();
        payment.setPaymentNo("PAY-0001");
        payment.setOrderNo("ORDER-0001");
        payment.setPayAmount(19900);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setExpireTime(LocalDateTime.now().plusMinutes(5));

        MockPayTrade trade = new MockPayTrade();
        trade.setPaymentNo("PAY-0001");
        trade.setStatus(MockPayTradeStatus.CREATED);

        when(paymentMapper.selectByPaymentNo("PAY-0001")).thenReturn(payment);
        when(mockPayTradeService.getByPaymentNo("PAY-0001")).thenReturn(trade);

        paymentService.getMockCheckout("PAY-0001");

        assertEquals(PaymentStatus.PAYING, payment.getStatus());
        assertEquals(MockPayTradeStatus.PAYING, trade.getStatus());
        assertNotNull(payment.getUpdateTime());
        verify(paymentMapper).updateById(payment);
    }

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

        verify(paymentMapper, never()).updateById(any(Payment.class));
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

    @Test
    void handleMockSuccessShouldClosePaymentAndOrder() {
        Payment payment = new Payment();
        payment.setPaymentNo("PAY-1001");
        payment.setOrderNo("ORDER-1001");
        payment.setStatus(PaymentStatus.PAYING);

        Order order = new Order();
        order.setOrderNo("ORDER-1001");
        order.setStatus(OrderStatus.UNPAID);
        order.setExpireTime(LocalDateTime.now().plusMinutes(1));

        when(paymentMapper.selectByPaymentNo("PAY-1001")).thenReturn(payment);
        when(orderMapper.selectOrderNo("ORDER-1001")).thenReturn(order);
        when(orderMapper.markPaidIfUnpaid(eq("ORDER-1001"), any(), eq("PAY-1001"), any())).thenReturn(1);

        MockPaymentCallbackReqDTO req = new MockPaymentCallbackReqDTO();
        req.setPaymentNo("PAY-1001");
        req.setOrderNo("ORDER-1001");
        req.setPayStatus("SUCCESS");
        req.setThirdPartyTradeNo("TP-1001");
        req.setCallbackNo("CB-1001");
        req.setCallbackTime(LocalDateTime.now());

        paymentService.handleMockCallback(req);

        assertEquals(PaymentStatus.PAID, payment.getStatus());
        verify(orderMapper).markPaidIfUnpaid(eq("ORDER-1001"), any(), eq("PAY-1001"), any());
    }

    @Test
    void handleMockCancelShouldReleaseHouse() {
        Payment payment = new Payment();
        payment.setPaymentNo("PAY-1002");
        payment.setOrderNo("ORDER-1002");
        payment.setStatus(PaymentStatus.PAYING);

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

        assertEquals(PaymentStatus.USER_CANCELLED, payment.getStatus());
        assertEquals(OrderStatus.USER_CANCELLED, order.getStatus());
        verify(houseCommandService)
                .updateHouseStatusWithSync(202L, 2, 1, "user-cancel-order");
    }
}
