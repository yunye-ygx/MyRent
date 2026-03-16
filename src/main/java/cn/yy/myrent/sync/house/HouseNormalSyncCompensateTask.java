package cn.yy.myrent.sync.house;

import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.sync.house.model.HouseNormalRetryMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Component
@Slf4j
public class HouseNormalSyncCompensateTask {

    private static final int BATCH_SIZE = 100;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    public void compensate() {
        for (int i = 0; i < BATCH_SIZE; i++) {
            String retryJson = stringRedisTemplate.opsForList().leftPop(HouseSyncConstants.NORMAL_COMPENSATE_REDIS_LIST_KEY);
            if (!StringUtils.hasText(retryJson)) {
                return;
            }
            try {
                HouseNormalRetryMessage retryMessage = objectMapper.readValue(retryJson, HouseNormalRetryMessage.class);
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.HOUSE_SYNC_EXCHANGE,
                        RabbitMQConfig.HOUSE_SYNC_ROUTING_KEY,
                        retryMessage.getMessageBody());
                log.info("普通房源同步补偿发送成功，retryCount={}", retryMessage.getRetryCount());
            } catch (Exception e) {
                handleRetry(retryJson, e);
            }
        }
    }

    private void handleRetry(String retryJson, Exception cause) {
        try {
            HouseNormalRetryMessage retryMessage = objectMapper.readValue(retryJson, HouseNormalRetryMessage.class);
            int currentRetry = retryMessage.getRetryCount() == null ? 0 : retryMessage.getRetryCount();
            int nextRetry = currentRetry + 1;
            if (nextRetry > HouseSyncConstants.NORMAL_COMPENSATE_MAX_RETRY) {
                log.error("普通房源同步补偿超过最大重试次数，retryCount={}, messageBody={}",
                        nextRetry,
                        retryMessage.getMessageBody(),
                        cause);
                return;
            }
            retryMessage.setRetryCount(nextRetry);
            retryMessage.setLastRetryTime(LocalDateTime.now());
            String json = objectMapper.writeValueAsString(retryMessage);
            stringRedisTemplate.opsForList().rightPush(HouseSyncConstants.NORMAL_COMPENSATE_REDIS_LIST_KEY, json);
            log.warn("普通房源同步补偿发送失败，已回写重试队列，retryCount={}", nextRetry, cause);
        } catch (JsonProcessingException e) {
            log.error("普通房源同步补偿消息反序列化失败，原始数据={}", retryJson, e);
        }
    }
}

