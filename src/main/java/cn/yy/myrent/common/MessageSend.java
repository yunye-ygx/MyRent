package cn.yy.myrent.common;

import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.entity.OrderTimeout;
import cn.yy.myrent.mapper.OrderTimeoutMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表定时扫描并发送 MQ。
 */
@Component
@Slf4j
public class MessageSend {
    @Autowired
    private OrderTimeoutMapper orderTimeoutMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY = 5;

    @Scheduled(fixedRate = 2000)
    public void sendPendingMessages() {
        while (true) {
            List<OrderTimeout> list = orderTimeoutMapper.selectList(new LambdaQueryWrapper<OrderTimeout>()
                    .eq(OrderTimeout::getSendStatus, 0) // 0=未发送
                    .orderByAsc(OrderTimeout::getId)
                    .last("limit " + BATCH_SIZE));
            if (list == null || list.isEmpty()) {
                return;
            }
            for (OrderTimeout msg : list) {
                try {
                    long ttlMs = Duration.between(LocalDateTime.now(), msg.getExpireTime()).toMillis();
                    if (ttlMs <= 0) {
                        // 已到期，立即投递并记录
                        log.warn("消息已超期，立即投递，bizId={}, expireTime={}", msg.getBizId(), msg.getExpireTime());
                    }
                    final long ttlToSend = Math.max(ttlMs, 0);
                    rabbitTemplate.convertAndSend(
                            RabbitMQConfig.ORDER_EXCHANGE,
                            RabbitMQConfig.ORDER_ROUTING_KEY,
                            msg.getBizId(),
                            message -> {
                                message.getMessageProperties().setExpiration(String.valueOf(ttlToSend));
                                return message;
                            });
                    orderTimeoutMapper.update(null, new LambdaUpdateWrapper<OrderTimeout>()
                            .eq(OrderTimeout::getId, msg.getId())
                            .set(OrderTimeout::getSendStatus, 1)); // 1=已发送

                    log.info("本地消息发送成功，bizId={}, ttlMs={}", msg.getBizId(), ttlToSend);
                } catch (Exception e) {
                    int retry = msg.getRetryCount() == null ? 0 : msg.getRetryCount();
                    retry++;
                    LambdaUpdateWrapper<OrderTimeout> uw = new LambdaUpdateWrapper<OrderTimeout>()
                            .eq(OrderTimeout::getId, msg.getId())
                            .set(OrderTimeout::getRetryCount, retry);
                    if (retry > MAX_RETRY) {
                        uw.set(OrderTimeout::getSendStatus, 2); // 2=永久失败
                        log.error("本地消息发送多次失败标记为永久失败，bizId={}, retry={}", msg.getBizId(), retry, e);
                    } else {
                        // 仍保持 sendStatus=0，等待下一轮调度重试
                        uw.set(OrderTimeout::getSendStatus, 0);
                        log.warn("本地消息发送失败，bizId={}，retry={}/{}，将在下轮重试", msg.getBizId(), retry, MAX_RETRY, e);
                    }
                    orderTimeoutMapper.update(null, uw);
                }
            }
            if (list.size() < BATCH_SIZE) {
                return; // 当前批已处理完
            }
        }
    }
}
