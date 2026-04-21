package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.Order;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

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

    int markPaidIfUnpaid(@Param("orderNo") String orderNo,
                         @Param("paidTime") LocalDateTime paidTime,
                         @Param("successPaymentNo") String successPaymentNo,
                         @Param("updateTime") LocalDateTime updateTime);

    int recoverPaidFromClosedTimeout(@Param("orderNo") String orderNo,
                                     @Param("paidTime") LocalDateTime paidTime,
                                     @Param("successPaymentNo") String successPaymentNo,
                                     @Param("updateTime") LocalDateTime updateTime);

    int markCompletedIfPaid(@Param("orderNo") String orderNo,
                            @Param("userId") Long userId,
                            @Param("expectedStatus") Integer expectedStatus,
                            @Param("targetStatus") Integer targetStatus,
                            @Param("updateTime") LocalDateTime updateTime);

    int markReviewedIfCompleted(@Param("orderNo") String orderNo,
                                @Param("userId") Long userId,
                                @Param("expectedStatus") Integer expectedStatus,
                                @Param("targetStatus") Integer targetStatus,
                                @Param("updateTime") LocalDateTime updateTime);
}
