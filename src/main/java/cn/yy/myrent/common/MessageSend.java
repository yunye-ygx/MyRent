package cn.yy.myrent.common;

import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.entity.LocalTask;
import cn.yy.myrent.mapper.LocalTaskMapper;
import cn.yy.myrent.sync.house.HouseSyncConstants;
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
 * 本地任务表定时扫描并发送MQ。
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

    @Autowired
    private LocalTaskMapper localTaskMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

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
                int claimed = localTaskMapper.update(null, new LambdaUpdateWrapper<LocalTask>()
                        .eq(LocalTask::getId, task.getId())
                        .in(LocalTask::getStatus, LOCAL_TASK_STATUS_PENDING, LOCAL_TASK_STATUS_RETRY)
                        .set(LocalTask::getStatus, LOCAL_TASK_STATUS_EXECUTING)
                        .set(LocalTask::getUpdateTime, LocalDateTime.now())
                        .setSql("`version` = IFNULL(`version`,0) + 1"));
                if (claimed != 1) {
                    continue;
                }

                try {
                    sendTaskToMq(task);

                    localTaskMapper.update(null, new LambdaUpdateWrapper<LocalTask>()
                            .eq(LocalTask::getId, task.getId())
                            .set(LocalTask::getStatus, LOCAL_TASK_STATUS_SUCCESS)
                            .set(LocalTask::getNextRetryTime, null)
                            .set(LocalTask::getUpdateTime, LocalDateTime.now()));

                    log.info("本地任务发送成功，messageId={}, bizType={}, eventType={}, bizId={}",
                            task.getMessageId(),
                            task.getBizType(),
                            task.getEventType(),
                            task.getBizId());
                } catch (Exception e) {
                    int retry = task.getRetryCount() == null ? 0 : task.getRetryCount();
                    retry++;
                    int maxRetry = task.getMaxRetryCount() == null ? MAX_RETRY : task.getMaxRetryCount();
                    LambdaUpdateWrapper<LocalTask> uw = new LambdaUpdateWrapper<LocalTask>()
                            .eq(LocalTask::getId, task.getId())
                            .set(LocalTask::getRetryCount, retry)
                            .set(LocalTask::getUpdateTime, LocalDateTime.now());
                    if (retry > maxRetry) {
                        uw.set(LocalTask::getStatus, LOCAL_TASK_STATUS_DEAD)
                                .set(LocalTask::getNextRetryTime, null);
                        log.error("本地任务发送多次失败标记为死信，messageId={}, bizType={}, eventType={}, bizId={}, retry={}, maxRetry={}",
                                task.getMessageId(),
                                task.getBizType(),
                                task.getEventType(),
                                task.getBizId(),
                                retry,
                                maxRetry,
                                e);
                    } else {
                        long backoffSeconds = (long) RETRY_BASE_SECONDS * retry;
                        LocalDateTime nextRetryTime = LocalDateTime.now().plusSeconds(backoffSeconds);
                        uw.set(LocalTask::getStatus, LOCAL_TASK_STATUS_RETRY)
                                .set(LocalTask::getNextRetryTime, nextRetryTime);
                        log.warn("本地任务发送失败，messageId={}, bizType={}, eventType={}, bizId={}, retry={}/{}, nextRetryTime={}",
                                task.getMessageId(),
                                task.getBizType(),
                                task.getEventType(),
                                task.getBizId(),
                                retry,
                                maxRetry,
                                nextRetryTime,
                                e);
                    }
                    localTaskMapper.update(null, uw);
                }
            }

            if (list.size() < BATCH_SIZE) {
                return;
            }
        }
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
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.HOUSE_SYNC_EXCHANGE,
                    RabbitMQConfig.HOUSE_SYNC_ROUTING_KEY,
                    task.getPayload());
            return;
        }

        throw new IllegalStateException("不支持的本地任务类型，bizType=" + task.getBizType() + ", eventType=" + task.getEventType());
    }

    private void sendOrderTimeoutTask(LocalTask task) {
        long ttlMs = Duration.between(LocalDateTime.now(), task.getExecuteTime()).toMillis();
        if (ttlMs <= 0) {
            log.warn("订单超时任务已到执行时间，立即投递，messageId={}, bizId={}, executeTime={}",
                    task.getMessageId(),
                    task.getBizId(),
                    task.getExecuteTime());
        }

        final long ttlToSend = Math.max(ttlMs, 0);
        String orderNo = task.getPayload() == null ? task.getBizId() : task.getPayload();
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_ROUTING_KEY,
                orderNo,
                message -> {
                    message.getMessageProperties().setExpiration(String.valueOf(ttlToSend));
                    return message;
                });
    }
}

