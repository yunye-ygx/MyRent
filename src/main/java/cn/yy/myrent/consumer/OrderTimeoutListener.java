package cn.yy.myrent.consumer;

import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.service.IOrderService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class OrderTimeoutListener {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private HouseMapper houseMapper;
    @Autowired
    private IOrderService orderService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @RabbitListener(queues = RabbitMQConfig.ORDER_DL_QUEUE, ackMode = "MANUAL")
    @Transactional(rollbackFor = Exception.class)
    public void orderTimeout(String orderNo, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        log.info("接收到超时订单消息，orderNo={}, deliveryTag={}", orderNo, deliveryTag);

        if (orderNo == null || orderNo.trim().isEmpty()) {
            log.warn("超时订单消息缺少orderNo，直接ack，deliveryTag={}", deliveryTag);
            channel.basicAck(deliveryTag, false);
            return;
        }

        String idempotentKey = "ordertime:key:" + orderNo;
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", 1, TimeUnit.DAYS);
        if (!Boolean.TRUE.equals(flag)) {
            log.info("超时订单消息重复消费，直接ack，orderNo={}, key={}", orderNo, idempotentKey);
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
                    log.info("订单状态更新为超时关闭失败(可能并发已处理)，orderNo={}", orderNo);
                }

                House house = houseMapper.selectById(order.getHouseId());
                if (updated && house != null && house.getStatus() == 2) {
                    house.setStatus(1);
                    houseMapper.updateById(house);
                    log.info("订单超时未支付，已自动关单并释放房源，orderNo={}, houseId={}", orderNo, house.getId());
                    stringRedisTemplate.opsForValue().set("house:lock:" + house.getId().toString(), "1");
                } else if (updated) {
                    log.warn("订单已关闭但房源释放条件不满足，orderNo={}, houseId={}, houseStatus={}",
                            orderNo,
                            house == null ? null : house.getId(),
                            house == null ? null : house.getStatus());
                }
            } else {
                log.info("订单无需超时关单，orderNo={}, orderStatus={}", orderNo, order == null ? null : order.getStatus());
            }
            channel.basicAck(deliveryTag, false);
            log.info("超时订单消息处理完成并ack，orderNo={}, deliveryTag={}", orderNo, deliveryTag);
        } catch (Exception e) {
            stringRedisTemplate.delete(idempotentKey);
            log.error("超时订单消息处理异常，执行nack，orderNo={}, deliveryTag={}", orderNo, deliveryTag, e);
            // 出现异常时拒绝消息，避免反复重试；可按需改为重回队列
            channel.basicNack(deliveryTag, false, false);
            throw e;
        }

    }
}
