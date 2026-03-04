package cn.yy.myrent.service;

import cn.yy.myrent.dto.LockHouseReqDTO;
import cn.yy.myrent.entity.Order;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 定金订单表 服务类
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
public interface IOrderService extends IService<Order> {

    void createOrder(LockHouseReqDTO lockHouse);
}
