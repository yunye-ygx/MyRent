package cn.yy.myrent.service;

import cn.yy.myrent.dto.LockHouseReqDTO;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.vo.CreateOrderVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IOrderService extends IService<Order> {

    CreateOrderVO createOrder(LockHouseReqDTO lockHouse);

    CreateOrderVO repay(String orderNo);
}
