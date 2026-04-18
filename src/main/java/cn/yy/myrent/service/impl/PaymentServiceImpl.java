package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.PaymentStatus;
import cn.yy.myrent.dto.MockPaymentCallbackReqDTO;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.PaymentMapper;
import cn.yy.myrent.service.IPaymentService;
import cn.yy.myrent.vo.MockCheckoutVO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> implements IPaymentService {

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public MockCheckoutVO getMockCheckout(String paymentNo) {
        Payment payment = paymentMapper.selectByPaymentNo(paymentNo);
        if (payment == null) {
            throw new RuntimeException("payment not found");
        }

        MockCheckoutVO vo = new MockCheckoutVO();
        vo.setOrderNo(payment.getOrderNo());
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setAmount(payment.getPayAmount());
        vo.setExpireTime(payment.getExpireTime());
        vo.setRemainingSeconds(Math.max(0, Duration.between(LocalDateTime.now(), payment.getExpireTime()).getSeconds()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleMockCallback(MockPaymentCallbackReqDTO req) {
        Payment payment = paymentMapper.selectByPaymentNo(req.getPaymentNo());
        Order order = orderMapper.selectOrderNo(req.getOrderNo());
        if (payment == null || order == null) {
            throw new RuntimeException("order or payment not found");
        }
        if (order.getStatus() != OrderStatus.UNPAID || payment.getStatus() != PaymentStatus.WAITING) {
            return;
        }

        LocalDateTime callbackTime = req.getCallbackTime() != null ? req.getCallbackTime() : LocalDateTime.now();
        LocalDateTime now = LocalDateTime.now();

        if ("SUCCESS".equalsIgnoreCase(req.getPayStatus())) {
            payment.setThirdPartyTradeNo(req.getThirdPartyTradeNo());
            payment.setCallbackNo(req.getCallbackNo());
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setCallbackTime(callbackTime);
            payment.setPaidTime(callbackTime);
            payment.setUpdateTime(now);
            paymentMapper.updateById(payment);

            order.setStatus(OrderStatus.PAID_LOCKED);
            order.setPaidTime(callbackTime);
            order.setUpdateTime(now);
            orderMapper.updateById(order);
            return;
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setCallbackNo(req.getCallbackNo());
        payment.setCallbackTime(callbackTime);
        payment.setFailReason("USER_CANCELLED");
        payment.setUpdateTime(now);
        paymentMapper.updateById(payment);

        order.setStatus(OrderStatus.USER_CANCELLED);
        order.setCloseTime(callbackTime);
        order.setUpdateTime(now);
        orderMapper.updateById(order);
    }
}
