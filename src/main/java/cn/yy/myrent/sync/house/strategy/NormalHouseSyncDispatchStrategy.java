package cn.yy.myrent.sync.house.strategy;

import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.sync.house.HouseSyncConstants;
import cn.yy.myrent.sync.house.model.HouseChangedEvent;
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
        String messageBody = buildMessage(context, messageId);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.HOUSE_SYNC_EXCHANGE,
                    RabbitMQConfig.HOUSE_SYNC_ROUTING_KEY,
                    messageBody);
            log.info("normal house sync event sent, houseId={}, eventType={}, messageId={}, reason={}",
                    context.getHouseId(),
                    context.getEventType(),
                    messageId,
                    context.getReason());
        } catch (Exception e) {
            try {
                pushRetryMessage(messageBody);
                log.warn("normal house sync event send failed, pushed to redis retry, houseId={}, eventType={}, messageId={}",
                        context.getHouseId(),
                        context.getEventType(),
                        messageId,
                        e);
            } catch (Exception retryException) {
                log.error("normal house sync event send and retry enqueue both failed, houseId={}, eventType={}, messageId={}",
                        context.getHouseId(),
                        context.getEventType(),
                        messageId,
                        retryException);
            }
        }
    }

    private String buildMessage(HouseSyncContext context, String messageId) {
        if (context != null && context.getEvent() != null) {
            HouseChangedEvent event = context.getEvent();
            if (event.getEventId() == null || event.getEventId().isBlank()) {
                event.setEventId(messageId);
            }
            return toJson(event);
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("eventId", messageId);
        message.put("houseId", context == null ? null : context.getHouseId());
        message.put("eventType", context == null ? null : context.getEventType());
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
            throw new IllegalStateException("house sync message serialization failed", e);
        }
    }
}
