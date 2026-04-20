package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.PaymentRefund;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRefundMapper extends BaseMapper<PaymentRefund> {

    PaymentRefund selectByRequestNo(@Param("requestNo") String requestNo);

    List<PaymentRefund> selectByUserIdAndOrderNos(@Param("userId") Long userId,
                                                  @Param("orderNos") List<String> orderNos);

    List<PaymentRefund> selectPendingForProcess(@Param("now") LocalDateTime now,
                                                @Param("limit") Integer limit);
}
