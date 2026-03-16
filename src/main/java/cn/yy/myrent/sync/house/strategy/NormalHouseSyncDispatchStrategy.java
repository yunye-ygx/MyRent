package cn.yy.myrent.sync.house.strategy;

import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.sync.house.HouseSyncConstants;
import cn.yy.myrent.sync.house.model.HouseNormalRetryMessage;
import cn.yy.myrent.sync.house.model.HouseSyncContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class NormalHouseSyncDispatchStrategy implements HouseSyncDispatchStrategy {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void dispatch(HouseSyncContext context) {
        String messageId = UUID.randomUUID().toString().replace("-", "");
        String messageBody = buildMinimalMessage(context, messageId);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.HOUSE_SYNC_EXCHANGE,
                    RabbitMQConfig.HOUSE_SYNC_ROUTING_KEY,
                    messageBody);
            log.info("普通房源同步事件发送MQ成功，houseId={}, eventType={}, messageId={}, reason={}",
                    context.getHouseId(),
                    context.getEventType(),
                    messageId,
                    context.getReason());
        } catch (Exception e) {
            try {
                pushRetryMessage(messageBody);
                log.warn("普通房源同步事件发送MQ失败，已进入Redis补偿，houseId={}, eventType={}, messageId={}",
                        context.getHouseId(),
                        context.getEventType(),
                        messageId,
                        e);
            } catch (Exception retryException) {
                log.error("普通房源同步事件发送MQ失败且写入Redis补偿也失败，houseId={}, eventType={}, messageId={}",
                        context.getHouseId(),
                        context.getEventType(),
                        messageId,
                        retryException);
            }
        }
    }

    private String buildMinimalMessage(HouseSyncContext context, String messageId) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", messageId);
        message.put("houseId", context.getHouseId());
        message.put("eventType", context.getEventType());
        return toJson(message);
    }

    private void pushRetryMessage(String messageBody) {
        HouseNormalRetryMessage retryMessage = new HouseNormalRetryMessage();
        retryMessage.setMessageBody(messageBody);
        retryMessage.setRetryCount(0);
        retryMessage.setFirstFailedTime(LocalDateTime.now());
        retryMessage.setLastRetryTime(LocalDateTime.now());
        String retryJson = toJson(retryMessage);
        stringRedisTemplate.opsForList().rightPush(HouseSyncConstants.NORMAL_COMPENSATE_REDIS_LIST_KEY, retryJson);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("房源同步消息序列化失败", e);
        }
    }
}
