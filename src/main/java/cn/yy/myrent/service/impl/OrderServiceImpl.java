package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.Constant;
import cn.yy.myrent.common.GenerateOrder;
import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.dto.LockHouseReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.OrderTimeout;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.IOrderService;
import cn.yy.myrent.service.IOrderTimeoutService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

/**
 * 定金订单表 服务实现类
 */
@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    @Autowired
    private IHouseService houseService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IOrderTimeoutService orderTimeoutService;

    private DefaultRedisScript<Long> lockHouseScript;

    public OrderServiceImpl() {
        lockHouseScript = new DefaultRedisScript<>();
        lockHouseScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/Stock.lua")));
        lockHouseScript.setResultType(Long.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(LockHouseReqDTO lockHouse) {

        log.info("下单请求开始，houseId={}, userId={}", lockHouse.getHouseId(), 1L);
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
        boolean isUpdated = houseService.update()
                .eq("id", lockHouse.getHouseId())
                .eq("status", 1)
                .set("status", 2)
                .setSql("`version` = `version` + 1")
                .update();
        if (!isUpdated) {
            log.warn("DB 乐观锁更新失败，houseId={} 可能已被抢", lockHouse.getHouseId());
            throw new RuntimeException("房源已下架");
        }
        log.info("DB 乐观锁更新成功，houseId={} 状态置为锁定", lockHouse.getHouseId());

        House house = houseService.getById(lockHouse.getHouseId());
        log.info("加载房源信息成功，houseId={}, deposit={}, status={}", house.getId(), house.getDepositAmount(), house.getStatus());

        Order order = new Order();
        order.setOrderNo(GenerateOrder.generateOrderNo(Constant.ORDER_NO_PREFIX));
        order.setUserId(1L);
        order.setHouseId(house.getId());
        order.setAmount(house.getDepositAmount());
        order.setStatus(0); // 0-待支付
        order.setExpireTime(LocalDateTime.now().plusSeconds(10));

        orderMapper.insert(order);
        log.info("订单入库成功，orderNo={}, expireAt={}", order.getOrderNo(), order.getExpireTime());

      //写入本地表
        OrderTimeout  orderTimeout = new OrderTimeout();
        orderTimeout.setBizId(order.getOrderNo());
        orderTimeout.setExpireTime(order.getExpireTime());
        orderTimeoutService.save(orderTimeout);
        log.info("本地消息记录写入成功，bizId={}, expireAt={}", orderTimeout.getBizId(), orderTimeout.getExpireTime());

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
}
