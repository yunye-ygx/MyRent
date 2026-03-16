package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.Constant;
import cn.yy.myrent.common.GenerateOrder;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import java.util.UUID;

/**
 * 定金订单表 服务实现类
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

    private DefaultRedisScript<Long> lockHouseScript;

    public OrderServiceImpl() {
        lockHouseScript = new DefaultRedisScript<>();
        lockHouseScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/Stock.lua")));
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
            throw new RuntimeException("不能租自己发布的房源");
        }

        log.info("下单请求开始，houseId={}, userId={}", lockHouse.getHouseId(), currentUserId);
        log.info("加载房源信息成功，houseId={}, deposit={}, status={}, publisherUserId={}",
                house.getId(), house.getDepositAmount(), house.getStatus(), house.getPublisherUserId());
        // 先走 Redis + Lua 原子判定/锁定
        Long locked = stringRedisTemplate.execute(
                lockHouseScript,
                Collections.singletonList(lockHouse.getHouseId().toString())

        );
        if (locked == null || locked != 1L) {
            log.warn("Redis 锁定失败，houseId={}, locked={}", lockHouse.getHouseId(), locked);
            throw new RuntimeException("房源已下架");
        }
        log.info("Redis 锁定成功，houseId={}", lockHouse.getHouseId());
        //注册事务回调申请
        String key="house:lock:"+lockHouse.getHouseId().toString();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if(status==TransactionSynchronization.STATUS_ROLLED_BACK){
                    stringRedisTemplate.delete(key);
                    log.info("事务回滚，已释放房源锁: {}", key);
                } else {
                    log.info("事务提交成功，保持房源锁: {}", key);
                }
            }
        });

        // 用数据库的乐观锁解决超卖问题
        boolean isUpdated = houseCommandService.updateHouseStatusWithSync(lockHouse.getHouseId(), 1, 2, "order-lock-house");
        if (!isUpdated) {
            log.warn("DB 乐观锁更新失败，houseId={} 可能已被抢", lockHouse.getHouseId());
            throw new RuntimeException("房源已下架");
        }
        log.info("DB 乐观锁更新成功，houseId={} 状态置为锁定", lockHouse.getHouseId());

        Order order = new Order();
        order.setOrderNo(GenerateOrder.generateOrderNo(Constant.ORDER_NO_PREFIX));
        order.setUserId(currentUserId);
        order.setHouseId(house.getId());
        order.setAmount(house.getDepositAmount());
        order.setStatus(0); // 0-待支付
        order.setExpireTime(LocalDateTime.now().plusSeconds(30));

        orderMapper.insert(order);
        log.info("订单入库成功，orderNo={}, expireAt={}", order.getOrderNo(), order.getExpireTime());

        // 写入本地任务表（通用本地消息/延迟任务）
        LocalDateTime now = LocalDateTime.now();
        LocalTask localTask = new LocalTask();
        localTask.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        localTask.setBizType(LOCAL_TASK_BIZ_TYPE_ORDER);
        localTask.setBizId(order.getOrderNo());
        localTask.setEventType(LOCAL_TASK_EVENT_ORDER_TIMEOUT_RELEASE);
        localTask.setPayload(buildOrderLocalTaskPayload(order.getOrderNo()));
        localTask.setStatus(LOCAL_TASK_STATUS_PENDING);
        localTask.setExecuteTime(order.getExpireTime());
        localTask.setRetryCount(0);
        localTask.setMaxRetryCount(LOCAL_TASK_MAX_RETRY_COUNT);
        localTask.setVersion(0L);
        localTask.setCreateTime(now);
        localTask.setUpdateTime(now);

        boolean taskSaved = localTaskService.save(localTask);
        if (!taskSaved) {
            throw new RuntimeException("写入本地任务失败");
        }
        log.info("本地任务写入成功，messageId={}, bizId={}, eventType={}, executeTime={}",
                localTask.getMessageId(),
                localTask.getBizId(),
                localTask.getEventType(),
                localTask.getExecuteTime());

//        // 事务提交后再发 MQ，避免 DB 回滚但消息已发出的不一致问题，但是不能解决消息持久性
//        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
//            @Override
//            public void afterCommit() {
//                rabbitTemplate.convertAndSend(
//                        RabbitMQConfig.ORDER_EXCHANGE,
//                        RabbitMQConfig.ORDER_ROUTING_KEY,
//                        orderNo,
//                        message -> {
//                            message.getMessageProperties().setExpiration("10000");
//                            return message;
//                        });
//                log.info("订单 [{}] 已入 MQ，TTL=30min，等待支付", orderNo);
//            }
//        });

    }

    private String buildOrderLocalTaskPayload(String orderNo) {
        try {
            return objectMapper.writeValueAsString(Collections.singletonMap("orderNo", orderNo));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("订单本地任务payload序列化失败", e);
        }
    }
}
