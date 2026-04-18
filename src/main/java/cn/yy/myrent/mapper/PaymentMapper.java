package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.Payment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface PaymentMapper extends BaseMapper<Payment> {

    Payment selectByPaymentNo(String paymentNo);
}
