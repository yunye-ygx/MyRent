package cn.yy.myrent.sync.house.strategy;

import cn.yy.myrent.entity.LocalTask;
import cn.yy.myrent.service.ILocalTaskService;
import cn.yy.myrent.sync.house.HouseSyncConstants;
import cn.yy.myrent.sync.house.model.HouseChangedEvent;
import cn.yy.myrent.sync.house.model.HouseSyncContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

@Component
@Slf4j
public class CoreHouseSyncDispatchStrategy implements HouseSyncDispatchStrategy {

    @Autowired
    private ILocalTaskService localTaskService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void dispatch(HouseSyncContext context) {
        String messageId = UUID.randomUUID().toString().replace("-", "");
        String payload = buildPayload(context, messageId);

        LocalDateTime now = LocalDateTime.now();
        LocalTask localTask = new LocalTask();
        localTask.setMessageId(messageId);
        localTask.setBizType(HouseSyncConstants.BIZ_TYPE_HOUSE);
        localTask.setBizId(String.valueOf(context.getHouseId()));
        localTask.setEventType(context.getEventType());
        localTask.setPayload(payload);
        localTask.setStatus(HouseSyncConstants.LOCAL_TASK_STATUS_PENDING);
        localTask.setExecuteTime(now);
        localTask.setRetryCount(0);
        localTask.setMaxRetryCount(HouseSyncConstants.LOCAL_TASK_MAX_RETRY_COUNT);
        localTask.setVersion(0L);
        localTask.setCreateTime(now);
        localTask.setUpdateTime(now);

        boolean saved = localTaskService.save(localTask);
        if (!saved) {
            throw new IllegalStateException("failed to persist core house sync local task");
        }

        log.info("core house sync event persisted, houseId={}, eventType={}, messageId={}, reason={}",
                context.getHouseId(),
                context.getEventType(),
                messageId,
                context.getReason());
    }

    private String buildPayload(HouseSyncContext context, String messageId) {
        if (context != null && context.getEvent() != null) {
            HouseChangedEvent event = context.getEvent();
            if (event.getEventId() == null || event.getEventId().isBlank()) {
                event.setEventId(messageId);
            }
            return toJson(event);
        }
        return toJson(Collections.singletonMap("houseId", context == null ? null : context.getHouseId()));
    }

    private String toJson(Object message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("house sync message serialization failed", e);
        }
    }
}
