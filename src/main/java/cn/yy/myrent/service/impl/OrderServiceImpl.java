package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.Constant;
import cn.yy.myrent.common.GenerateOrder;
import cn.yy.myrent.common.MessageSend;
import cn.yy.myrent.common.MockPayCallbackStatus;
import cn.yy.myrent.common.MockPayTradeStatus;
import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.PaymentRefundStatus;
import cn.yy.myrent.common.PaymentStatus;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.LockHouseReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.LocalTask;
import cn.yy.myrent.entity.MockPayTrade;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.entity.PaymentRefund;
import cn.yy.myrent.entity.Review;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.PaymentRefundMapper;
import cn.yy.myrent.service.IReviewService;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.ILocalTaskService;
import cn.yy.myrent.service.IMockPayTradeService;
import cn.yy.myrent.service.IOrderService;
import cn.yy.myrent.service.IPaymentService;
import cn.yy.myrent.vo.CreateOrderVO;
import cn.yy.myrent.vo.MyOrderItemVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import java.util.List;
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
    @Autowired
    private IReviewService reviewService;
    @Autowired
    private PaymentRefundMapper paymentRefundMapper;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeOrder(String orderNo) {
        Long currentUserId = UserContext.requireCurrentUserId();
        Order order = orderMapper.selectOrderNo(orderNo);
        if (order == null || !currentUserId.equals(order.getUserId())) {
            throw new RuntimeException("order not found");
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.PAID) {
            throw new RuntimeException("order is not completable");
        }
        if (hasBlockingRefund(currentUserId, orderNo)) {
            throw new RuntimeException("order is not completable");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.markCompletedIfPaid(
                orderNo,
                currentUserId,
                OrderStatus.PAID,
                OrderStatus.COMPLETED,
                now);
        if (updated <= 0) {
            throw new RuntimeException("order complete failed");
        }
    }

    @Override
    public Page<MyOrderItemVO> pageMineOrders(Long userId, long current, long size) {
        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), 100L);

        Page<Order> page = orderMapper.selectPage(
                new Page<>(safeCurrent, safeSize),
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .orderByDesc(Order::getCreateTime)
                        .orderByDesc(Order::getId));

        List<Order> orderRecords = page == null || page.getRecords() == null ? Collections.emptyList() : page.getRecords();
        List<String> orderNos = orderRecords.stream().map(Order::getOrderNo).toList();
        Map<String, Review> reviewMap = reviewService.mapByOrderNos(orderNos);
        Map<String, PaymentRefund> latestRefundMap = mapLatestRefundByOrderNo(userId, orderNos);

        List<MyOrderItemVO> records = orderRecords.stream().map(order -> {
            Review review = reviewMap.get(order.getOrderNo());
            PaymentRefund latestRefund = latestRefundMap.get(order.getOrderNo());
            boolean refundBlocking = isRefundBlocking(latestRefund == null ? null : latestRefund.getStatus());
            MyOrderItemVO item = new MyOrderItemVO();
            item.setId(order.getId());
            item.setOrderNo(order.getOrderNo());
            item.setHouseId(order.getHouseId());
            item.setAmount(order.getAmount());
            item.setStatus(order.getStatus());
            item.setCreateTime(order.getCreateTime());
            item.setExpireTime(order.getExpireTime());
            item.setPaidTime(order.getPaidTime());
            item.setReviewId(review == null ? null : review.getId());
            item.setHasReview(review != null);
            item.setCanComplete(order.getStatus() != null
                    && order.getStatus() == OrderStatus.PAID
                    && !refundBlocking);
            item.setCanReview(order.getStatus() != null
                    && order.getStatus() == OrderStatus.COMPLETED
                    && !refundBlocking);
            item.setCanEditReview(order.getStatus() != null
                    && order.getStatus() == OrderStatus.REVIEWED
                    && !refundBlocking
                    && review != null
                    && review.getEditCount() != null
                    && review.getEditCount() == 0);
            item.setLatestRefundStatus(latestRefund == null ? null : latestRefund.getStatus());
            item.setLatestRefundNo(latestRefund == null ? null : latestRefund.getRefundNo());
            item.setLatestRefundReasonCode(latestRefund == null ? null : latestRefund.getReasonCode());
            return item;
        }).toList();

        Page<MyOrderItemVO> result = new Page<>(safeCurrent, safeSize, page == null ? 0L : page.getTotal());
        result.setRecords(records);
        return result;
    }

    private boolean hasBlockingRefund(Long userId, String orderNo) {
        return isRefundBlocking(resolveLatestRefundStatus(userId, orderNo));
    }

    private Integer resolveLatestRefundStatus(Long userId, String orderNo) {
        if (userId == null || orderNo == null || orderNo.isBlank()) {
            return null;
        }
        List<PaymentRefund> refunds = paymentRefundMapper.selectByUserIdAndOrderNos(userId, List.of(orderNo));
        if (refunds == null || refunds.isEmpty() || refunds.get(0) == null) {
            return null;
        }
        return refunds.get(0).getStatus();
    }

    private Map<String, PaymentRefund> mapLatestRefundByOrderNo(Long userId, List<String> orderNos) {
        if (userId == null || orderNos == null || orderNos.isEmpty()) {
            return Map.of();
        }
        List<PaymentRefund> refunds = paymentRefundMapper.selectByUserIdAndOrderNos(userId, orderNos);
        if (refunds == null || refunds.isEmpty()) {
            return Map.of();
        }
        Map<String, PaymentRefund> latest = new LinkedHashMap<>();
        for (PaymentRefund refund : refunds) {
            if (refund == null || refund.getOrderNo() == null || latest.containsKey(refund.getOrderNo())) {
                continue;
            }
            latest.put(refund.getOrderNo(), refund);
        }
        return latest;
    }

    private boolean isRefundBlocking(Integer refundStatus) {
        if (refundStatus == null) {
            return false;
        }
        return refundStatus == PaymentRefundStatus.PENDING
                || refundStatus == PaymentRefundStatus.PROCESSING
                || refundStatus == PaymentRefundStatus.SUCCESS
                || refundStatus == PaymentRefundStatus.RETRY
                || refundStatus == PaymentRefundStatus.MANUAL_REVIEW;
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
