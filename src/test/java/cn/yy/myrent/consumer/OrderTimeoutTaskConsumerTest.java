package cn.yy.myrent.consumer;

import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.PaymentStatus;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IOrderService;
import cn.yy.myrent.service.IPaymentService;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private IPaymentService paymentService;

    @Mock
    private Channel channel;

    @Mock
    private UpdateChainWrapper orderUpdateChainWrapper;

    @Mock
    private UpdateChainWrapper paymentUpdateChainWrapper;

    @InjectMocks
    private OrderTimeoutTaskConsumer consumer;

    @Test
    void timeoutShouldCloseWaitingPayment() throws Exception {
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
        when(paymentUpdateChainWrapper.eq("status", PaymentStatus.WAITING)).thenReturn(paymentUpdateChainWrapper);
        when(paymentUpdateChainWrapper.update()).thenReturn(true);

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
        verify(paymentUpdateChainWrapper).eq("status", PaymentStatus.WAITING);
        verify(paymentUpdateChainWrapper).update();
    }
}
