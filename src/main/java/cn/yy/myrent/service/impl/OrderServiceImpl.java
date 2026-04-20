package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.Constant;
import cn.yy.myrent.common.GenerateOrder;
import cn.yy.myrent.common.MessageSend;
import cn.yy.myrent.common.MockPayCallbackStatus;
import cn.yy.myrent.common.MockPayTradeStatus;
import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.PaymentStatus;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.LockHouseReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.LocalTask;
import cn.yy.myrent.entity.MockPayTrade;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.ILocalTaskService;
import cn.yy.myrent.service.IMockPayTradeService;
import cn.yy.myrent.service.IOrderService;
import cn.yy.myrent.service.IPaymentService;
import cn.yy.myrent.vo.CreateOrderVO;
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
    @Autowired
    private IPaymentService paymentService;
    @Autowired
    private IMockPayTradeService mockPayTradeService;

    private final DefaultRedisScript<Long> lockHouseScript;

    public OrderServiceImpl() {
        lockHouseScript = new DefaultRedisScript<>();
        lockHouseScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("Lua/Stock.lua")));
        lockHouseScript.setResultType(Long.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateOrderVO createOrder(LockHouseReqDTO lockHouse) {
        Long currentUserId = UserContext.requireCurrentUserId();
        if (lockHouse == null || lockHouse.getHouseId() == null) {
            throw new RuntimeException("houseId cannot be null");
        }

        House house = houseService.getById(lockHouse.getHouseId());
        if (house == null) {
            throw new RuntimeException("house not found");
        }
        if (currentUserId.equals(house.getPublisherUserId())) {
            throw new RuntimeException("cannot order your own house");
        }

        Long locked = stringRedisTemplate.execute(
                lockHouseScript,
                Collections.singletonList(lockHouse.getHouseId().toString()));
        if (locked == null || locked != 1L) {
            throw new RuntimeException("house already unavailable");
        }

        String redisLockKey = "house:lock:" + lockHouse.getHouseId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    stringRedisTemplate.delete(redisLockKey);
                }
            }
        });

        boolean updated = houseCommandService.updateHouseStatusWithSync(
                lockHouse.getHouseId(),
                1,
                2,
                "order-lock-house");
        if (!updated) {
            throw new RuntimeException("house already unavailable");
        }

        LocalDateTime now = LocalDateTime.now();
        log.info("锁房成功，开始创建订单，houseId={}", lockHouse.getHouseId());

        Order order = new Order();
        order.setOrderNo(GenerateOrder.generateOrderNo(Constant.ORDER_NO_PREFIX));
        order.setUserId(currentUserId);
        order.setHouseId(house.getId());
        order.setAmount(house.getDepositAmount());
        order.setStatus(OrderStatus.UNPAID);
        order.setExpireTime(now.plusSeconds(30));
        order.setCreateTime(now);
        order.setUpdateTime(now);
        orderMapper.insert(order);

        log.info("创建订单成功，开始创建支付记录，orderNo={}", order.getOrderNo());
        Payment payment = buildPayment(order.getOrderNo(), currentUserId, order.getAmount(), order.getExpireTime(), now);
        paymentService.save(payment);
        log.info("创建支付记录成功，开始创建本地任务，orderNo={}", order.getOrderNo());
        mockPayTradeService.save(buildMockTrade(payment, now));

        log.info("创建本地任务成功，开始注册事务同步，orderNo={}", order.getOrderNo());
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
            throw new RuntimeException("save local task failed");
        }


        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("派发本地任务，orderNo={}", order.getOrderNo());
                messageSend.dispatchPendingTaskByMessageId(localTask.getMessageId());
            }
        });


        return buildCreateOrderVO(order.getOrderNo(), payment.getPaymentNo(), order.getExpireTime());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateOrderVO repay(String orderNo) {
        Long currentUserId = UserContext.requireCurrentUserId();
        Order order = orderMapper.selectOrderNo(orderNo);
        if (order == null) {
            throw new RuntimeException("order not found");
        }
        if (!currentUserId.equals(order.getUserId())) {
            throw new RuntimeException("cannot pay other user's order");
        }
        if (order.getStatus() != OrderStatus.UNPAID) {
            throw new RuntimeException("order is not payable");
        }

        LocalDateTime now = LocalDateTime.now();
        Payment payment = buildPayment(orderNo, currentUserId, order.getAmount(), order.getExpireTime(), now);
        paymentService.save(payment);
        mockPayTradeService.save(buildMockTrade(payment, now));
        return buildCreateOrderVO(orderNo, payment.getPaymentNo(), order.getExpireTime());
    }

    private String buildOrderLocalTaskPayload(Order order) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderNo", order.getOrderNo());
            payload.put("expireTime", order.getExpireTime().toString());
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("serialize local task payload failed", e);
        }
    }

    private Payment buildPayment(String orderNo, Long userId, Integer amount, LocalDateTime expireTime, LocalDateTime now) {
        Payment payment = new Payment();
        payment.setPaymentNo(GenerateOrder.generateOrderNo("PAY"));
        payment.setOrderNo(orderNo);
        payment.setUserId(userId);
        payment.setPayAmount(amount);
        payment.setChannel("MOCK");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setExpireTime(expireTime);
        payment.setCreateTime(now);
        payment.setUpdateTime(now);
        return payment;
    }

    private MockPayTrade buildMockTrade(Payment payment, LocalDateTime now) {
        MockPayTrade trade = new MockPayTrade();
        trade.setPaymentNo(payment.getPaymentNo());
        trade.setOrderNo(payment.getOrderNo());
        trade.setStatus(MockPayTradeStatus.CREATED);
        trade.setAmount(payment.getPayAmount());
        trade.setCallbackStatus(MockPayCallbackStatus.NOT_CONFIRMED);
        trade.setCreateTime(now);
        trade.setUpdateTime(now);
        return trade;
    }

    private CreateOrderVO buildCreateOrderVO(String orderNo, String paymentNo, LocalDateTime expireTime) {
        CreateOrderVO result = new CreateOrderVO();
        result.setOrderNo(orderNo);
        result.setPaymentNo(paymentNo);
        result.setExpireTime(expireTime);
        result.setMockPayUrl("/mock-pay/checkout?paymentNo=" + paymentNo);
        return result;
    }
}
