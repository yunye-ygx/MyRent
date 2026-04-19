package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.Payment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

public interface PaymentMapper extends BaseMapper<Payment> {

    Payment selectByPaymentNo(String paymentNo);

    Payment selectLatestActiveByOrderNo(String orderNo);

    List<Payment> selectByOrderNo(String orderNo);

    List<Payment> selectSuspiciousPayingPayments();
}
