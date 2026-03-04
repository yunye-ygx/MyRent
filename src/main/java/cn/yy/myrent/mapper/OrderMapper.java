package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.Order;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 定金订单表 Mapper 接口
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
public interface OrderMapper extends BaseMapper<Order> {

    Order selectOrderNo(String orderNo);
}
