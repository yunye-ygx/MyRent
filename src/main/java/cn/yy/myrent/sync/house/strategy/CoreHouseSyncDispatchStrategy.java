package cn.yy.myrent.sync.house.strategy;

import cn.yy.myrent.entity.LocalTask;
import cn.yy.myrent.service.ILocalTaskService;
import cn.yy.myrent.sync.house.HouseSyncConstants;
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
        String payload = toJson(Collections.singletonMap("houseId", context.getHouseId()));

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
            throw new IllegalStateException("核心房源同步任务写入本地任务表失败");
        }

        log.info("核心房源同步事件已落地本地任务，houseId={}, eventType={}, messageId={}, reason={}",
                context.getHouseId(),
                context.getEventType(),
                messageId,
                context.getReason());
    }

    private String toJson(Object message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("房源同步消息序列化失败", e);
        }
    }
}
