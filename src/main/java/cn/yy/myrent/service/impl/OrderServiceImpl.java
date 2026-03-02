package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.Constant;
import cn.yy.myrent.common.GenerateOrder;
import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.dto.LockHouseReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.IOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.ietf.jgss.MessageProp;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 定金订单表 服务实现类
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {
    @Autowired
    private IHouseService houseService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private HouseMapper houseMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(LockHouseReqDTO lockHouse, Long userId) {
//        //方案1--用行锁解决超卖问题
//        House house=houseMapper.selectForUpdateById(lockHouse.getHouseId());
//        if (house == null || house.getStatus() != 1) {
//            throw new RuntimeException("房源已下架");
//        }
//        house.setStatus(2);
//        houseMapper.updateById(house);


        //方案二--用数据库的乐观锁解决超卖问题

        boolean isUpdated = houseService.update()
                .eq("id", lockHouse.getHouseId())
                .eq("status", 1)
                .set("status", 2)
                .setSql("`version` = `version` + 1")
                .update();
        if (!isUpdated) {
            throw new RuntimeException("房源已下架");
        }
        //创建订单
        House house = houseService.getById(lockHouse.getHouseId());

        Order order = new Order();
        order.setOrderNo(GenerateOrder.generateOrderNo(Constant.ORDER_NO_PREFIX));
        order.setUserId(userId);
        order.setHouseId(house.getId());
        order.setAmount(house.getDepositAmount());
        order.setStatus(0); // 0-待支付

        // 设置订单过期时间为当前时间往后推 30 分钟
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(30);
        order.setExpireTime(expireTime);

        // 插入订单表
        orderMapper.insert(order);

        //同步信息到MQ
        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_ROUTING_KEY,
                order.getOrderNo(),
                message -> {
                    message.getMessageProperties().setExpiration("1800000");
                    return message;
                });
    }
}
