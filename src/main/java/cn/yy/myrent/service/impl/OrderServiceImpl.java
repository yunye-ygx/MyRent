package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.Constant;
import cn.yy.myrent.common.GenerateOrder;
import cn.yy.myrent.common.MessageSend;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.LockHouseReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.LocalTask;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.ILocalTaskService;
import cn.yy.myrent.service.IOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 定金订单服务实现。
 */
@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private static final String LOCAL_TASK_BIZ_TYPE_ORDER = "ORDER";
    private static final String LOCAL_TASK_EVENT_ORDER_TIMEOUT_RELEASE = "ORDER_TIMEOUT_RELEASE";
    private static final int LOCAL_TASK_STATUS_PENDING = 0;
    private static final int LOCAL_TASK_MAX_RETRY_COUNT = 5;

    @Autowired
    private IHouseService houseService;
    @Autowired
    private IHouseCommandService houseCommandService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ILocalTaskService localTaskService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MessageSend messageSend;

    private final DefaultRedisScript<Long> lockHouseScript;

    public OrderServiceImpl() {
        lockHouseScript = new DefaultRedisScript<>();
        lockHouseScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("Lua/Stock.lua")));
        lockHouseScript.setResultType(Long.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(LockHouseReqDTO lockHouse) {
        Long currentUserId = UserContext.requireCurrentUserId();
        if (lockHouse == null || lockHouse.getHouseId() == null) {
            throw new RuntimeException("houseId不能为空");
        }

        House house = houseService.getById(lockHouse.getHouseId());
        if (house == null) {
            throw new RuntimeException("房源不存在");
        }
        if (currentUserId.equals(house.getPublisherUserId())) {
            throw new RuntimeException("这是你自己发布的房源，不能给自己的房源下单");
        }

        log.info("下单请求开始，houseId={}, userId={}", lockHouse.getHouseId(), currentUserId);
        log.info("加载房源信息成功，houseId={}, deposit={}, status={}, publisherUserId={}",
                house.getId(), house.getDepositAmount(), house.getStatus(), house.getPublisherUserId());

        Long locked = stringRedisTemplate.execute(
                lockHouseScript,
                Collections.singletonList(lockHouse.getHouseId().toString()));
        if (locked == null || locked != 1L) {
            log.warn("Redis 锁定失败，houseId={}, locked={}", lockHouse.getHouseId(), locked);
            throw new RuntimeException("房源已下架");
        }
        log.info("Redis 锁定成功，houseId={}", lockHouse.getHouseId());

        String redisLockKey = "house:lock:" + lockHouse.getHouseId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    stringRedisTemplate.delete(redisLockKey);
                    log.info("事务回滚，已释放房源锁: {}", redisLockKey);
                } else {
                    log.info("事务提交成功，保持房源锁: {}", redisLockKey);
                }
            }
        });

        boolean isUpdated = houseCommandService.updateHouseStatusWithSync(
                lockHouse.getHouseId(),
                1,
                2,
                "order-lock-house");
        if (!isUpdated) {
            log.warn("DB 条件更新失败，houseId={} 可能已被抢", lockHouse.getHouseId());
            throw new RuntimeException("房源已下架");
        }
        log.info("DB 条件更新成功，houseId={} 状态置为锁定", lockHouse.getHouseId());

        Order order = new Order();
        order.setOrderNo(GenerateOrder.generateOrderNo(Constant.ORDER_NO_PREFIX));
        order.setUserId(currentUserId);
        order.setHouseId(house.getId());
        order.setAmount(house.getDepositAmount());
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().plusSeconds(30));

        orderMapper.insert(order);
        log.info("订单入库成功，orderNo={}, expireAt={}", order.getOrderNo(), order.getExpireTime());

        LocalDateTime now = LocalDateTime.now();
        LocalTask localTask = new LocalTask();
        localTask.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        localTask.setBizType(LOCAL_TASK_BIZ_TYPE_ORDER);
        localTask.setBizId(order.getOrderNo());
        localTask.setEventType(LOCAL_TASK_EVENT_ORDER_TIMEOUT_RELEASE);
        localTask.setPayload(buildOrderLocalTaskPayload(order));
        localTask.setStatus(LOCAL_TASK_STATUS_PENDING);
        localTask.setExecuteTime(now);
        localTask.setRetryCount(0);
        localTask.setMaxRetryCount(LOCAL_TASK_MAX_RETRY_COUNT);
        localTask.setVersion(0L);
        localTask.setCreateTime(now);
        localTask.setUpdateTime(now);

        boolean taskSaved = localTaskService.save(localTask);
        if (!taskSaved) {
            throw new RuntimeException("写入本地任务失败");
        }
        log.info("本地任务写入成功，messageId={}, bizId={}, eventType={}, executeTime={}, expireAt={}",
                localTask.getMessageId(),
                localTask.getBizId(),
                localTask.getEventType(),
                localTask.getExecuteTime(),
                order.getExpireTime());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messageSend.dispatchPendingTaskByMessageId(localTask.getMessageId());
            }
        });
    }

    private String buildOrderLocalTaskPayload(Order order) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderNo", order.getOrderNo());
            payload.put("expireTime", order.getExpireTime().toString());
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("订单本地任务 payload 序列化失败", e);
        }
    }
}
