package cn.yy.myrent.consumer;

import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.service.IOrderService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Component
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
        boolean flag=stringRedisTemplate.opsForValue().setIfAbsent("ordertime:key:",orderNo);
        if(flag){
            try {
                Order order = orderMapper.selectOrderNo(orderNo);
                if (order != null && order.getStatus() == 0) {
                    boolean updated = orderService.update()
                            .set("status", 2)
                            .eq("order_no", orderNo)
                            .eq("status", 0)
                            .update();

                    House house = houseMapper.selectById(order.getHouseId());
                    if (updated && house != null && house.getStatus() == 2) {
                        house.setStatus(1);
                        houseMapper.updateById(house);
                        System.out.println("订单[" + orderNo + "]超时未支付，已自动关单并释放房源[" + house.getId() + "]");
                        stringRedisTemplate.opsForValue().set("house:lock:" + house.getId().toString(), "1");
                    }
                } else {
                    System.out.println("订单[" + orderNo + "]已支付，无需处理延时关单任务");
                }
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            } catch (Exception e) {
                // 出现异常时拒绝消息，避免反复重试；可按需改为重回队列
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
                throw e;
            }
        }

    }
}
