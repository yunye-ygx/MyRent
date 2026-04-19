package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.MockPayCallbackStatus;
import cn.yy.myrent.common.MockPayTradeStatus;
import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.PaymentStatus;
import cn.yy.myrent.dto.MockPaymentCallbackReqDTO;
import cn.yy.myrent.entity.MockPayTrade;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.PaymentMapper;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IMockPayTradeService;
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

    @Autowired
    private IHouseCommandService houseCommandService;

    @Autowired
    private IMockPayTradeService mockPayTradeService;

    @Override
    public MockCheckoutVO getMockCheckout(String paymentNo) {
        Payment payment = paymentMapper.selectByPaymentNo(paymentNo);
        if (payment == null) {
            throw new RuntimeException("payment not found");
        }
        MockPayTrade trade = mockPayTradeService.getByPaymentNo(paymentNo);
        if (trade == null) {
            throw new RuntimeException("mock trade not found");
        }
        if (payment.getStatus() != null && payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.PAYING);
            payment.setUpdateTime(LocalDateTime.now());
            paymentMapper.updateById(payment);
        }
        if (trade.getStatus() != null && trade.getStatus() == MockPayTradeStatus.CREATED) {
            trade.setStatus(MockPayTradeStatus.PAYING);
            trade.setUpdateTime(LocalDateTime.now());
            mockPayTradeService.updateById(trade);
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
        if (payment.getStatus() != null && (payment.getStatus() == PaymentStatus.PAID
                || payment.getStatus() == PaymentStatus.DUPLICATE_PAID
                || payment.getStatus() == PaymentStatus.USER_CANCELLED
                || payment.getStatus() == PaymentStatus.CLOSED_TIMEOUT)) {
            return;
        }

        LocalDateTime callbackTime = req.getCallbackTime() != null ? req.getCallbackTime() : LocalDateTime.now();
        LocalDateTime now = LocalDateTime.now();

        if ("SUCCESS".equalsIgnoreCase(req.getPayStatus())) {
            repairOrderPaidFromTrade(req.getPaymentNo(),
                    req.getThirdPartyTradeNo(),
                    req.getCallbackNo(),
                    req.getCallbackTime());
            return;
        }

        if (order.getStatus() != OrderStatus.UNPAID || payment.getStatus() != PaymentStatus.PAYING) {
            return;
        }

        payment.setStatus(PaymentStatus.USER_CANCELLED);
        payment.setCallbackNo(req.getCallbackNo());
        payment.setCallbackTime(callbackTime);
        payment.setFailReason("USER_CANCELLED");
        payment.setUpdateTime(now);
        paymentMapper.updateById(payment);

        order.setStatus(OrderStatus.USER_CANCELLED);
        order.setCloseTime(callbackTime);
        order.setUpdateTime(now);
        orderMapper.updateById(order);

        MockPayTrade trade = mockPayTradeService.getByPaymentNo(payment.getPaymentNo());
        if (trade != null) {
            trade.setStatus(MockPayTradeStatus.USER_CANCELLED);
            trade.setCallbackStatus(MockPayCallbackStatus.CONFIRMED);
            trade.setLastCallbackTime(now);
            trade.setUpdateTime(now);
            mockPayTradeService.updateById(trade);
        }

        houseCommandService.updateHouseStatusWithSync(order.getHouseId(), 2, 1, "user-cancel-order");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean repairOrderPaidFromTrade(String paymentNo,
                                            String thirdPartyTradeNo,
                                            String callbackNo,
                                            LocalDateTime callbackTime) {
        Payment payment = paymentMapper.selectByPaymentNo(paymentNo);
        if (payment == null) {
            throw new RuntimeException("payment not found");
        }
        Order order = orderMapper.selectOrderNo(payment.getOrderNo());
        if (order == null) {
            throw new RuntimeException("order not found");
        }
        if (payment.getStatus() != null && payment.getStatus() == PaymentStatus.PAID) {
            return true;
        }

        LocalDateTime effectiveTime = callbackTime != null ? callbackTime : LocalDateTime.now();
        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.markPaidIfUnpaid(order.getOrderNo(), effectiveTime, paymentNo, now);

        payment.setThirdPartyTradeNo(thirdPartyTradeNo);
        payment.setCallbackNo(callbackNo);
        payment.setCallbackTime(effectiveTime);
        payment.setPaidTime(effectiveTime);
        payment.setUpdateTime(now);

        if (updated > 0) {
            payment.setStatus(PaymentStatus.PAID);
            paymentMapper.updateById(payment);
            syncTradePaid(paymentNo, thirdPartyTradeNo, effectiveTime, now);
            return true;
        }

        Order latestOrder = orderMapper.selectOrderNo(order.getOrderNo());
        if (latestOrder != null && paymentNo.equals(latestOrder.getSuccessPaymentNo())) {
            payment.setStatus(PaymentStatus.PAID);
            paymentMapper.updateById(payment);
            syncTradePaid(paymentNo, thirdPartyTradeNo, effectiveTime, now);
            return true;
        }

        payment.setStatus(PaymentStatus.DUPLICATE_PAID);
        payment.setFailReason("DUPLICATE_PAID");
        paymentMapper.updateById(payment);
        syncTradePaid(paymentNo, thirdPartyTradeNo, effectiveTime, now);
        return false;
    }

    private void syncTradePaid(String paymentNo,
                               String thirdPartyTradeNo,
                               LocalDateTime effectiveTime,
                               LocalDateTime now) {
        MockPayTrade trade = mockPayTradeService.getByPaymentNo(paymentNo);
        if (trade != null) {
            trade.setThirdPartyTradeNo(thirdPartyTradeNo);
            trade.setStatus(MockPayTradeStatus.SUCCESS);
            trade.setPaidTime(effectiveTime);
            trade.setCallbackStatus(MockPayCallbackStatus.CONFIRMED);
            trade.setLastCallbackTime(now);
            trade.setUpdateTime(now);
            mockPayTradeService.updateById(trade);
        }
    }
}
