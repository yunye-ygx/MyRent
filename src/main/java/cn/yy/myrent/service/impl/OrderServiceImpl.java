package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.Constant;
import cn.yy.myrent.common.GenerateOrder;
import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.dto.LockHouseReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.IOrderService;
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

    private DefaultRedisScript<Long> lockHouseScript;

    public OrderServiceImpl() {
        lockHouseScript = new DefaultRedisScript<>();
        lockHouseScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/Stock.lua")));
        lockHouseScript.setResultType(Long.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(LockHouseReqDTO lockHouse) {

        // 先走 Redis + Lua 原子判定/锁定
        Long locked = stringRedisTemplate.execute(
                lockHouseScript,
                Collections.singletonList(lockHouse.getHouseId().toString())

        );
        if (locked == null || locked != 1L) {
            throw new RuntimeException("房源已下架");
        }

        // 用数据库的乐观锁解决超卖问题
        boolean isUpdated = houseService.update()
                .eq("id", lockHouse.getHouseId())
                .eq("status", 1)
                .set("status", 2)
                .setSql("`version` = `version` + 1")
                .update();
        if (!isUpdated) {
            throw new RuntimeException("房源已下架");
        }

        House house = houseService.getById(lockHouse.getHouseId());

        Order order = new Order();
        order.setOrderNo(GenerateOrder.generateOrderNo(Constant.ORDER_NO_PREFIX));
        order.setUserId(1L);
        order.setHouseId(house.getId());
        order.setAmount(house.getDepositAmount());
        order.setStatus(0); // 0-待支付
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));

        orderMapper.insert(order);

        String orderNo = order.getOrderNo();

        // 事务提交后再发 MQ，避免 DB 回滚但消息已发出的不一致问题
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.ORDER_EXCHANGE,
                        RabbitMQConfig.ORDER_ROUTING_KEY,
                        orderNo,
                        message -> {
                            message.getMessageProperties().setExpiration("10000");
                            return message;
                        });
                log.info("订单 [{}] 已入 MQ，TTL=30min，等待支付", orderNo);
            }
        });
    }
}
