package cn.yy.myrent.common;

import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.entity.LocalTask;
import cn.yy.myrent.mapper.LocalTaskMapper;
import cn.yy.myrent.sync.house.HouseSyncConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地任务表扫描并投递 MQ。
 */
@Component
@Slf4j
public class MessageSend {

    private static final String LOCAL_TASK_BIZ_TYPE_ORDER = "ORDER";
    private static final String LOCAL_TASK_EVENT_ORDER_TIMEOUT_RELEASE = "ORDER_TIMEOUT_RELEASE";
    private static final String LOCAL_TASK_BIZ_TYPE_HOUSE = HouseSyncConstants.BIZ_TYPE_HOUSE;

    private static final int LOCAL_TASK_STATUS_PENDING = 0;
    private static final int LOCAL_TASK_STATUS_EXECUTING = 1;
    private static final int LOCAL_TASK_STATUS_SUCCESS = 2;
    private static final int LOCAL_TASK_STATUS_RETRY = 3;
    private static final int LOCAL_TASK_STATUS_DEAD = 5;

    private static final int RETRY_BASE_SECONDS = 5;
    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY = 5;
    private static final int CLEANUP_RETAIN_DAYS = 7;
    private static final int CONFIRM_TIMEOUT_SECONDS = 60;

    @Autowired
    private LocalTaskMapper localTaskMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void initRabbitCallbacks() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData == null || correlationData.getId() == null) {
                log.warn("收到 MQ confirm 但缺少 correlationData, ack={}, cause={}", ack, cause);
                return;
            }

            if (ack) {
                markLocalTaskSuccess(correlationData.getId());
            } else {
                markLocalTaskFailure(correlationData.getId(),
                        new IllegalStateException("publisher confirm nack: " + cause));
            }
        });

        rabbitTemplate.setReturnsCallback(returned -> {
            String messageId = returned.getMessage() == null
                    ? null
                    : returned.getMessage().getMessageProperties().getMessageId();
            if (messageId == null || messageId.trim().isEmpty()) {
                log.error("收到 MQ returned 但缺少 messageId, replyCode={}, replyText={}, exchange={}, routingKey={}",
                        returned.getReplyCode(),
                        returned.getReplyText(),
                        returned.getExchange(),
                        returned.getRoutingKey());
                return;
            }

            markLocalTaskFailure(messageId,
                    new IllegalStateException("publisher returned: " + returned.getReplyText()));
        });
    }

    @Scheduled(fixedRate = 2000)
    public void sendPendingMessages() {
        while (true) {
            LocalDateTime now = LocalDateTime.now();
            List<LocalTask> list = localTaskMapper.selectList(new LambdaQueryWrapper<LocalTask>()
                    .in(LocalTask::getStatus, LOCAL_TASK_STATUS_PENDING, LOCAL_TASK_STATUS_RETRY)
                    .le(LocalTask::getExecuteTime, now)
                    .and(wrapper -> wrapper.isNull(LocalTask::getNextRetryTime)
                            .or()
                            .le(LocalTask::getNextRetryTime, now))
                    .orderByAsc(LocalTask::getExecuteTime)
                    .orderByAsc(LocalTask::getId)
                    .last("limit " + BATCH_SIZE));

            if (list == null || list.isEmpty()) {
                return;
            }

            for (LocalTask task : list) {
                dispatchTask(task);
            }

            if (list.size() < BATCH_SIZE) {
                return;
            }
        }
    }

    public void dispatchPendingTaskByMessageId(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return;
        }

        LocalTask task = localTaskMapper.selectOne(new LambdaQueryWrapper<LocalTask>()
                .eq(LocalTask::getMessageId, messageId)
                .in(LocalTask::getStatus, LOCAL_TASK_STATUS_PENDING, LOCAL_TASK_STATUS_RETRY)
                .last("limit 1"));
        if (task == null) {
            return;
        }

        dispatchTask(task);
    }

    @Scheduled(fixedDelay = 30000)
    public void recoverTimeoutExecutingTasks() {
        LocalDateTime timeoutBefore = LocalDateTime.now().minusSeconds(CONFIRM_TIMEOUT_SECONDS);
        List<LocalTask> tasks = localTaskMapper.selectList(new LambdaQueryWrapper<LocalTask>()
                .eq(LocalTask::getStatus, LOCAL_TASK_STATUS_EXECUTING)
                .lt(LocalTask::getUpdateTime, timeoutBefore)
                .last("limit " + BATCH_SIZE));

        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        for (LocalTask task : tasks) {
            markLocalTaskFailure(task.getMessageId(),
                    new IllegalStateException("publisher confirm timeout"));
        }
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupInvalidLocalTasks() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(CLEANUP_RETAIN_DAYS);
        int deleted = localTaskMapper.delete(new LambdaQueryWrapper<LocalTask>()
                .in(LocalTask::getStatus, LOCAL_TASK_STATUS_SUCCESS, LOCAL_TASK_STATUS_DEAD)
                .lt(LocalTask::getUpdateTime, cutoffTime));

        log.info("本地任务清理完成，deleted={}, retainDays={}, cutoffTime={}",
                deleted,
                CLEANUP_RETAIN_DAYS,
                cutoffTime);
    }

    private void dispatchTask(LocalTask task) {
        if (!claimTask(task.getId())) {
            return;
        }

        try {
            sendTaskToMq(task);
            log.info("本地任务已投递 MQ，等待 broker confirm，messageId={}, bizType={}, eventType={}, bizId={}",
                    task.getMessageId(),
                    task.getBizType(),
                    task.getEventType(),
                    task.getBizId());
        } catch (Exception e) {
            markLocalTaskFailure(task.getMessageId(), e);
        }
    }

    private boolean claimTask(Long taskId) {
        int claimed = localTaskMapper.update(null, new LambdaUpdateWrapper<LocalTask>()
                .eq(LocalTask::getId, taskId)
                .in(LocalTask::getStatus, LOCAL_TASK_STATUS_PENDING, LOCAL_TASK_STATUS_RETRY)
                .set(LocalTask::getStatus, LOCAL_TASK_STATUS_EXECUTING)
                .set(LocalTask::getUpdateTime, LocalDateTime.now())
                .setSql("`version` = IFNULL(`version`,0) + 1"));
        return claimed == 1;
    }

    private void sendTaskToMq(LocalTask task) {
        if (LOCAL_TASK_BIZ_TYPE_ORDER.equals(task.getBizType())
                && LOCAL_TASK_EVENT_ORDER_TIMEOUT_RELEASE.equals(task.getEventType())) {
            sendOrderTimeoutTask(task);
            return;
        }

        if (LOCAL_TASK_BIZ_TYPE_HOUSE.equals(task.getBizType())
                && (HouseSyncConstants.EVENT_HOUSE_ES_UPSERT.equals(task.getEventType())
                || HouseSyncConstants.EVENT_HOUSE_ES_DELETE.equals(task.getEventType()))) {
            sendWithConfirm(
                    task,
                    RabbitMQConfig.HOUSE_SYNC_EXCHANGE,
                    RabbitMQConfig.HOUSE_SYNC_ROUTING_KEY,
                    buildHouseSyncMessage(task),
                    null);
            return;
        }

        throw new IllegalStateException("不支持的本地任务类型，bizType=" + task.getBizType() + ", eventType=" + task.getEventType());
    }

    private void sendOrderTimeoutTask(LocalTask task) {
        LocalDateTime expireTime = resolveOrderExpireTime(task);
        long ttlMs = Duration.between(LocalDateTime.now(), expireTime).toMillis();
        if (ttlMs <= 0) {
            log.warn("订单超时任务已接近或超过过期时间，立即投递，messageId={}, bizId={}, expireTime={}",
                    task.getMessageId(),
                    task.getBizId(),
                    expireTime);
        }

        sendWithConfirm(
                task,
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_ROUTING_KEY,
                resolveOrderNo(task),
                Math.max(ttlMs, 0));
    }

    private void sendWithConfirm(LocalTask task,
                                 String exchange,
                                 String routingKey,
                                 Object payload,
                                 Long ttlMs) {
        rabbitTemplate.convertAndSend(
                exchange,
                routingKey,
                payload,
                message -> {
                    message.getMessageProperties().setMessageId(task.getMessageId());
                    message.getMessageProperties().setHeader("x-local-task-message-id", task.getMessageId());
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    if (ttlMs != null) {
                        message.getMessageProperties().setExpiration(String.valueOf(ttlMs));
                    }
                    return message;
                },
                new CorrelationData(task.getMessageId()));
    }

    private String resolveOrderNo(LocalTask task) {
        String payload = task.getPayload();
        if (payload == null || payload.trim().isEmpty()) {
            return task.getBizId();
        }

        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node.isObject()) {
                JsonNode orderNoNode = node.get("orderNo");
                if (orderNoNode != null && !orderNoNode.asText().trim().isEmpty()) {
                    return orderNoNode.asText();
                }
            }
            if (node.isTextual() && !node.asText().trim().isEmpty()) {
                return node.asText();
            }
        } catch (Exception e) {
            log.warn("订单本地任务 payload 非 JSON 格式，回退原始值，messageId={}, payload={}",
                    task.getMessageId(),
                    payload);
            return payload;
        }

        return task.getBizId();
    }

    private LocalDateTime resolveOrderExpireTime(LocalTask task) {
        String payload = task.getPayload();
        if (payload != null && !payload.trim().isEmpty()) {
            try {
                JsonNode node = objectMapper.readTree(payload);
                if (node.isObject()) {
                    JsonNode expireTimeNode = node.get("expireTime");
                    if (expireTimeNode != null && !expireTimeNode.asText().trim().isEmpty()) {
                        return LocalDateTime.parse(expireTimeNode.asText().trim());
                    }
                }
            } catch (Exception e) {
                log.warn("订单本地任务 expireTime 解析失败，回退 executeTime，messageId={}, payload={}",
                        task.getMessageId(),
                        payload);
            }
        }

        return task.getExecuteTime();
    }

    private String buildHouseSyncMessage(LocalTask task) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", task.getMessageId());
        message.put("eventType", task.getEventType());
        message.put("houseId", resolveHouseId(task));
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException("房源同步消息序列化失败", e);
        }
    }

    private Long resolveHouseId(LocalTask task) {
        String payload = task.getPayload();
        if (payload != null && !payload.trim().isEmpty()) {
            try {
                JsonNode node = objectMapper.readTree(payload);
                if (node.isObject()) {
                    JsonNode houseIdNode = node.get("houseId");
                    if (houseIdNode != null && !houseIdNode.isNull()) {
                        if (houseIdNode.canConvertToLong()) {
                            return houseIdNode.longValue();
                        }
                        String text = houseIdNode.asText();
                        if (text != null && !text.trim().isEmpty()) {
                            return Long.parseLong(text.trim());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("房源本地任务 payload 解析失败，回退 bizId，messageId={}, payload={}",
                        task.getMessageId(),
                        payload);
            }
        }

        try {
            return Long.parseLong(task.getBizId());
        } catch (Exception e) {
            throw new IllegalStateException("房源本地任务 bizId 无法解析为 houseId, bizId=" + task.getBizId(), e);
        }
    }

    private void markLocalTaskSuccess(String messageId) {
        int updated = localTaskMapper.update(null, new LambdaUpdateWrapper<LocalTask>()
                .eq(LocalTask::getMessageId, messageId)
                .eq(LocalTask::getStatus, LOCAL_TASK_STATUS_EXECUTING)
                .set(LocalTask::getStatus, LOCAL_TASK_STATUS_SUCCESS)
                .set(LocalTask::getNextRetryTime, null)
                .set(LocalTask::getUpdateTime, LocalDateTime.now()));

        if (updated == 1) {
            log.info("本地任务收到 broker confirm 并标记成功，messageId={}", messageId);
        }
    }

    private void markLocalTaskFailure(String messageId, Exception e) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return;
        }

        LocalTask task = localTaskMapper.selectOne(new LambdaQueryWrapper<LocalTask>()
                .eq(LocalTask::getMessageId, messageId)
                .last("limit 1"));
        if (task == null || task.getStatus() == null || task.getStatus() != LOCAL_TASK_STATUS_EXECUTING) {
            return;
        }

        int retry = task.getRetryCount() == null ? 0 : task.getRetryCount();
        retry++;
        int maxRetry = task.getMaxRetryCount() == null ? MAX_RETRY : task.getMaxRetryCount();
        LocalDateTime now = LocalDateTime.now();

        LambdaUpdateWrapper<LocalTask> updateWrapper = new LambdaUpdateWrapper<LocalTask>()
                .eq(LocalTask::getId, task.getId())
                .eq(LocalTask::getStatus, LOCAL_TASK_STATUS_EXECUTING)
                .set(LocalTask::getRetryCount, retry)
                .set(LocalTask::getUpdateTime, now);

        if (retry > maxRetry) {
            updateWrapper.set(LocalTask::getStatus, LOCAL_TASK_STATUS_DEAD)
                    .set(LocalTask::getNextRetryTime, null);
        } else {
            LocalDateTime nextRetryTime = now.plusSeconds((long) RETRY_BASE_SECONDS * retry);
            updateWrapper.set(LocalTask::getStatus, LOCAL_TASK_STATUS_RETRY)
                    .set(LocalTask::getNextRetryTime, nextRetryTime);
        }

        int updated = localTaskMapper.update(null, updateWrapper);
        if (updated != 1) {
            return;
        }

        if (retry > maxRetry) {
            log.error("本地任务发送失败并进入死信状态，messageId={}, bizType={}, eventType={}, bizId={}, retry={}, maxRetry={}",
                    task.getMessageId(),
                    task.getBizType(),
                    task.getEventType(),
                    task.getBizId(),
                    retry,
                    maxRetry,
                    e);
        } else {
            LocalDateTime nextRetryTime = now.plusSeconds((long) RETRY_BASE_SECONDS * retry);
            log.warn("本地任务发送失败，等待后续重试，messageId={}, bizType={}, eventType={}, bizId={}, retry={}/{}, nextRetryTime={}",
                    task.getMessageId(),
                    task.getBizType(),
                    task.getEventType(),
                    task.getBizId(),
                    retry,
                    maxRetry,
                    nextRetryTime,
                    e);
        }
    }
}
