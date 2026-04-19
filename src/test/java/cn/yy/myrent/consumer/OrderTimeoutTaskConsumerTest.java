package cn.yy.myrent.consumer;

import cn.yy.myrent.common.MockPayTradeStatus;
import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.PaymentStatus;
import cn.yy.myrent.entity.MockPayTrade;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.PaymentMapper;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IMockPayTradeService;
import cn.yy.myrent.service.IOrderService;
import cn.yy.myrent.service.IPaymentService;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class OrderTimeoutTaskConsumerTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private IOrderService orderService;

    @Mock
    private IHouseCommandService houseCommandService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private IPaymentService paymentService;

    @Mock
    private IMockPayTradeService mockPayTradeService;

    @Mock
    private Channel channel;

    @Mock
    private UpdateChainWrapper orderUpdateChainWrapper;

    @Mock
    private UpdateChainWrapper paymentUpdateChainWrapper;

    @Mock
    private UpdateChainWrapper mockPayTradeUpdateChainWrapper;

    @InjectMocks
    private OrderTimeoutTaskConsumer consumer;

    @Test
    void timeoutShouldCloseInProgressPayment() throws Exception {
        Order order = new Order();
        order.setOrderNo("ORDER-1001");
        order.setStatus(OrderStatus.UNPAID);
        order.setHouseId(101L);

        when(orderMapper.selectOrderNo("ORDER-1001")).thenReturn(order);
        when(orderService.update()).thenReturn(orderUpdateChainWrapper);
        when(orderUpdateChainWrapper.set("status", 2)).thenReturn(orderUpdateChainWrapper);
        when(orderUpdateChainWrapper.eq("order_no", "ORDER-1001")).thenReturn(orderUpdateChainWrapper);
        when(orderUpdateChainWrapper.eq("status", 0)).thenReturn(orderUpdateChainWrapper);
        when(orderUpdateChainWrapper.update()).thenReturn(true);

        when(paymentService.update()).thenReturn(paymentUpdateChainWrapper);
        when(paymentUpdateChainWrapper.set("status", PaymentStatus.CLOSED_TIMEOUT)).thenReturn(paymentUpdateChainWrapper);
        when(paymentUpdateChainWrapper.set("fail_reason", "TIMEOUT_CLOSED")).thenReturn(paymentUpdateChainWrapper);
        when(paymentUpdateChainWrapper.set(org.mockito.ArgumentMatchers.eq("update_time"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(paymentUpdateChainWrapper);
        when(paymentUpdateChainWrapper.eq("order_no", "ORDER-1001")).thenReturn(paymentUpdateChainWrapper);
        when(paymentUpdateChainWrapper.in("status", PaymentStatus.PENDING, PaymentStatus.PAYING)).thenReturn(paymentUpdateChainWrapper);
        when(paymentUpdateChainWrapper.update()).thenReturn(true);

        when(mockPayTradeService.update()).thenReturn(mockPayTradeUpdateChainWrapper);
        when(mockPayTradeUpdateChainWrapper.set("status", MockPayTradeStatus.CLOSED_TIMEOUT))
                .thenReturn(mockPayTradeUpdateChainWrapper);
        when(mockPayTradeUpdateChainWrapper.set(org.mockito.ArgumentMatchers.eq("update_time"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(mockPayTradeUpdateChainWrapper);
        when(mockPayTradeUpdateChainWrapper.eq("order_no", "ORDER-1001")).thenReturn(mockPayTradeUpdateChainWrapper);
        when(mockPayTradeUpdateChainWrapper.in("status", MockPayTradeStatus.CREATED, MockPayTradeStatus.PAYING))
                .thenReturn(mockPayTradeUpdateChainWrapper);
        when(mockPayTradeUpdateChainWrapper.update()).thenReturn(true);

        when(houseCommandService.updateHouseStatusWithSync(101L, 2, 1, "order-timeout-release"))
                .thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(1L);
        Message message = new Message("ORDER-1001".getBytes(), properties);

        consumer.consumeOrderTimeoutMessage("ORDER-1001", message, channel);

        verify(paymentService).update();
        verify(paymentUpdateChainWrapper).set("status", PaymentStatus.CLOSED_TIMEOUT);
        verify(paymentUpdateChainWrapper).set("fail_reason", "TIMEOUT_CLOSED");
        verify(paymentUpdateChainWrapper).set(org.mockito.ArgumentMatchers.eq("update_time"), org.mockito.ArgumentMatchers.any());
        verify(paymentUpdateChainWrapper).eq("order_no", "ORDER-1001");
        verify(paymentUpdateChainWrapper).in("status", PaymentStatus.PENDING, PaymentStatus.PAYING);
        verify(paymentUpdateChainWrapper).update();
        verify(mockPayTradeService).update();
        verify(mockPayTradeUpdateChainWrapper).set("status", MockPayTradeStatus.CLOSED_TIMEOUT);
        verify(mockPayTradeUpdateChainWrapper).set(org.mockito.ArgumentMatchers.eq("update_time"), org.mockito.ArgumentMatchers.any());
        verify(mockPayTradeUpdateChainWrapper).eq("order_no", "ORDER-1001");
        verify(mockPayTradeUpdateChainWrapper).in("status", MockPayTradeStatus.CREATED, MockPayTradeStatus.PAYING);
        verify(mockPayTradeUpdateChainWrapper).update();
    }

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

    @Test
    void timeoutShouldLogPaymentPlatformCheckBeforeClose(CapturedOutput output) throws Exception {
        Order order = new Order();
        order.setOrderNo("ORDER-3001");
        order.setStatus(OrderStatus.UNPAID);

        Payment payment = new Payment();
        payment.setPaymentNo("PAY-3001");
        payment.setOrderNo("ORDER-3001");
        payment.setStatus(PaymentStatus.PAYING);

        when(orderMapper.selectOrderNo("ORDER-3001")).thenReturn(order);
        when(paymentMapper.selectLatestActiveByOrderNo("ORDER-3001")).thenReturn(payment);
        when(mockPayTradeService.getByPaymentNo("PAY-3001")).thenReturn(null);

        when(orderService.update()).thenReturn(orderUpdateChainWrapper);
        when(orderUpdateChainWrapper.set("status", 2)).thenReturn(orderUpdateChainWrapper);
        when(orderUpdateChainWrapper.eq("order_no", "ORDER-3001")).thenReturn(orderUpdateChainWrapper);
        when(orderUpdateChainWrapper.eq("status", 0)).thenReturn(orderUpdateChainWrapper);
        when(orderUpdateChainWrapper.update()).thenReturn(false);

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(1L);
        Message message = new Message("ORDER-3001".getBytes(), properties);

        consumer.consumeOrderTimeoutMessage("ORDER-3001", message, channel);

        assertTrue(output.getOut().contains("关单前开始检查支付平台状态，orderNo=ORDER-3001"));
        assertTrue(output.getOut().contains("支付平台检查跳过：未找到 mock trade，orderNo=ORDER-3001, paymentNo=PAY-3001"));
    }
}
