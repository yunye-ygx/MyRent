package cn.yy.myrent.consumer;

import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IOrderService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.io.IOException;
import java.util.Map;

@Component
@Slf4j
public class OrderTimeoutTaskConsumer {

    private static final String RETRY_COUNT_HEADER = "x-order-retry-count";
    private static final int MAX_RETRY = 5;
    private static final long RETRY_BASE_MILLIS = 5000L;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private IOrderService orderService;

    @Autowired
    private IHouseCommandService houseCommandService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMQConfig.ORDER_DL_QUEUE, ackMode = "MANUAL")
    @Transactional(rollbackFor = Exception.class)
    public void consumeOrderTimeoutMessage(String orderNo, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        int currentRetry = extractRetryCount(message);
        log.info("接收到超时订单消息，orderNo={}, deliveryTag={}, retry={}", orderNo, deliveryTag, currentRetry);

        if (orderNo == null || orderNo.trim().isEmpty()) {
            log.warn("超时订单消息缺少 orderNo，直接 ack，deliveryTag={}", deliveryTag);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            Order order = orderMapper.selectOrderNo(orderNo);
            if (order != null && order.getStatus() == 0) {
                boolean updated = orderService.update()
                        .set("status", 2)
                        .eq("order_no", orderNo)
                        .eq("status", 0)
                        .update();

                if (!updated) {
                    log.info("订单状态更新为超时关闭失败，可能已被并发处理，orderNo={}", orderNo);
                }

                if (updated) {
                    boolean houseReleased = houseCommandService.updateHouseStatusWithSync(
                            order.getHouseId(),
                            2,
                            1,
                            "order-timeout-release");
                    if (houseReleased) {
                        log.info("订单超时未支付，已自动关单并释放房源，orderNo={}, houseId={}",
                                orderNo,
                                order.getHouseId());
                        stringRedisTemplate.opsForValue().set("house:lock:" + order.getHouseId(), "1");
                    } else {
                        log.warn("订单已关闭但房源释放条件不满足，orderNo={}, houseId={}",
                                orderNo,
                                order.getHouseId());
                    }
                }
            } else {
                log.info("订单无需超时关单，orderNo={}, orderStatus={}", orderNo, order == null ? null : order.getStatus());
            }

            channel.basicAck(deliveryTag, false);
            log.info("超时订单消息处理完成并 ack，orderNo={}, deliveryTag={}", orderNo, deliveryTag);
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            handleConsumeFailure(orderNo, message, channel, deliveryTag, currentRetry, e);
        }
    }

    private void handleConsumeFailure(String orderNo,
                                      Message originalMessage,
                                      Channel channel,
                                      long deliveryTag,
                                      int currentRetry,
                                      Exception consumeException) throws IOException {
        int nextRetry = currentRetry + 1;
        try {
            if (nextRetry > MAX_RETRY) {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.ORDER_FAIL_EXCHANGE,
                        RabbitMQConfig.ORDER_FAIL_ROUTING_KEY,
                        orderNo,
                        message -> {
                            copyHeaders(originalMessage, message);
                            message.getMessageProperties().setHeader(RETRY_COUNT_HEADER, nextRetry);
                            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                            return message;
                        });
                channel.basicAck(deliveryTag, false);
                log.error("超时订单消息处理失败且超过最大重试次数，已转入失败队列，orderNo={}, retry={}",
                        orderNo,
                        nextRetry,
                        consumeException);
                return;
            }

            long delayMillis = RETRY_BASE_MILLIS * nextRetry;
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ORDER_RETRY_EXCHANGE,
                    RabbitMQConfig.ORDER_RETRY_ROUTING_KEY,
                    orderNo,
                    message -> {
                        copyHeaders(originalMessage, message);
                        message.getMessageProperties().setHeader(RETRY_COUNT_HEADER, nextRetry);
                        message.getMessageProperties().setExpiration(String.valueOf(delayMillis));
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    });
            channel.basicAck(deliveryTag, false);
            log.warn("超时订单消息处理失败，已投递到重试队列，orderNo={}, retry={}, delayMs={}",
                    orderNo,
                    nextRetry,
                    delayMillis,
                    consumeException);
        } catch (Exception retryPublishException) {
            log.error("超时订单消息补偿投递失败，回退为 requeue 原消息，orderNo={}, retry={}",
                    orderNo,
                    nextRetry,
                    retryPublishException);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private int extractRetryCount(Message message) {
        if (message == null || message.getMessageProperties() == null) {
            return 0;
        }

        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        if (headers == null || headers.isEmpty()) {
            return 0;
        }

        Object value = headers.get(RETRY_COUNT_HEADER);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private void copyHeaders(Message source, Message target) {
        if (source == null || source.getMessageProperties() == null) {
            return;
        }

        Map<String, Object> headers = source.getMessageProperties().getHeaders();
        if (headers == null || headers.isEmpty()) {
            return;
        }

        target.getMessageProperties().getHeaders().putAll(headers);
    }
}
