package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.MockPayCallbackStatus;
import cn.yy.myrent.common.MockPayTradeStatus;
import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.PaymentRepairResult;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j

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
        if ("SUCCESS".equalsIgnoreCase(req.getPayStatus())) {
            reconcilePaymentSuccess(req.getPaymentNo(),
                    req.getThirdPartyTradeNo(),
                    req.getCallbackNo(),
                    req.getCallbackTime());
            return;
        }

        if (payment.getStatus() != null && (payment.getStatus() == PaymentStatus.PAID
                || payment.getStatus() == PaymentStatus.DUPLICATE_PAID
                || payment.getStatus() == PaymentStatus.USER_CANCELLED
                || payment.getStatus() == PaymentStatus.CLOSED_TIMEOUT)) {
            return;
        }

        LocalDateTime callbackTime = req.getCallbackTime() != null ? req.getCallbackTime() : LocalDateTime.now();
        LocalDateTime now = LocalDateTime.now();

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
        PaymentRepairResult result = reconcilePaymentSuccess(paymentNo, thirdPartyTradeNo, callbackNo, callbackTime);
        return result == PaymentRepairResult.PAID_WIN
                || result == PaymentRepairResult.DUPLICATE_CALLBACK
                || result == PaymentRepairResult.LATE_SUCCESS_RECOVERED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentRepairResult reconcilePaymentSuccess(String paymentNo,
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
            return PaymentRepairResult.DUPLICATE_CALLBACK;
        }
        if (payment.getStatus() != null && payment.getStatus() == PaymentStatus.DUPLICATE_PAID) {
            return PaymentRepairResult.DUPLICATE_PAID;
        }
        log.info("订单超时取消任务在支付成功回调任务之前，{}", payment.getPaymentNo());
        if (payment.getStatus() != null && payment.getStatus() == PaymentStatus.CLOSED_TIMEOUT) {
            return repairLateSuccessAfterTimeout(payment, order, thirdPartyTradeNo, callbackNo, callbackTime);
        }
        return closeOrderAsPaidNormally(payment, order, thirdPartyTradeNo, callbackNo, callbackTime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean repairSuccessfulPaymentsForOrder(String orderNo) {
        List<Payment> candidates = paymentMapper.selectCandidatePaymentsByOrderNo(orderNo);
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }

        List<Map.Entry<Payment, MockPayTrade>> successful = new ArrayList<>();
        for (Payment payment : candidates) {
            MockPayTrade trade = mockPayTradeService.getByPaymentNo(payment.getPaymentNo());
            if (trade != null && trade.getStatus() != null && trade.getStatus() == MockPayTradeStatus.SUCCESS) {
                successful.add(new AbstractMap.SimpleEntry<>(payment, trade));
            }
        }
        successful.sort((left, right) -> {
            LocalDateTime leftPaidTime = left.getValue().getPaidTime();
            LocalDateTime rightPaidTime = right.getValue().getPaidTime();
            if (leftPaidTime == null && rightPaidTime == null) {
                return comparePaymentId(left.getKey(), right.getKey());
            }
            if (leftPaidTime == null) {
                return 1;
            }
            if (rightPaidTime == null) {
                return -1;
            }

            int paidTimeCompare = leftPaidTime.compareTo(rightPaidTime);
            if (paidTimeCompare != 0) {
                return paidTimeCompare;
            }
            return comparePaymentId(left.getKey(), right.getKey());
        });

        boolean repaired = false;
        for (Map.Entry<Payment, MockPayTrade> entry : successful) {
            PaymentRepairResult result = reconcilePaymentSuccess(
                    entry.getKey().getPaymentNo(),
                    entry.getValue().getThirdPartyTradeNo(),
                    null,
                    entry.getValue().getPaidTime());
            if (result == PaymentRepairResult.PAID_WIN
                    || result == PaymentRepairResult.DUPLICATE_CALLBACK
                    || result == PaymentRepairResult.LATE_SUCCESS_RECOVERED
                    || result == PaymentRepairResult.DUPLICATE_PAID) {
                repaired = true;
            }
        }
        return repaired;
    }

    private PaymentRepairResult closeOrderAsPaidNormally(Payment payment,
                                                         Order order,
                                                         String thirdPartyTradeNo,
                                                         String callbackNo,
                                                         LocalDateTime callbackTime) {
        LocalDateTime effectiveTime = callbackTime != null ? callbackTime : LocalDateTime.now();
        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.markPaidIfUnpaid(order.getOrderNo(), effectiveTime, payment.getPaymentNo(), now);

        applyPaidCallback(payment, thirdPartyTradeNo, callbackNo, effectiveTime, now);

        if (updated > 0) {
            payment.setStatus(PaymentStatus.PAID);
            paymentMapper.updateById(payment);
            syncTradePaid(payment.getPaymentNo(), thirdPartyTradeNo, effectiveTime, now);
            return PaymentRepairResult.PAID_WIN;
        }

        Order latestOrder = orderMapper.selectOrderNo(order.getOrderNo());
        if (latestOrder != null && payment.getPaymentNo().equals(latestOrder.getSuccessPaymentNo())) {
            payment.setStatus(PaymentStatus.PAID);
            paymentMapper.updateById(payment);
            syncTradePaid(payment.getPaymentNo(), thirdPartyTradeNo, effectiveTime, now);
            return PaymentRepairResult.DUPLICATE_CALLBACK;
        }

        return markDuplicatePaid(payment, thirdPartyTradeNo, callbackNo, effectiveTime, now);
    }

    private PaymentRepairResult repairLateSuccessAfterTimeout(Payment payment,
                                                              Order order,
                                                              String thirdPartyTradeNo,
                                                              String callbackNo,
                                                              LocalDateTime callbackTime) {
        LocalDateTime effectiveTime = callbackTime != null ? callbackTime : LocalDateTime.now();
        LocalDateTime now = LocalDateTime.now();

        if (order.getStatus() == OrderStatus.UNPAID) {
            return closeOrderAsPaidNormally(payment, order, thirdPartyTradeNo, callbackNo, effectiveTime);
        }
        if (order.getStatus() == OrderStatus.PAID_LOCKED) {
            return payment.getPaymentNo().equals(order.getSuccessPaymentNo())
                    ? PaymentRepairResult.DUPLICATE_CALLBACK
                    : markDuplicatePaid(payment, thirdPartyTradeNo, callbackNo, effectiveTime, now);
        }


        log.info("订单已超时取消，尝试重新锁定房源，{}", payment.getPaymentNo());
        boolean recoverable = houseCommandService.updateHouseStatusWithSync(
                order.getHouseId(),
                1,
                2,
                "late-success-recover-order");
        if (!recoverable) {
            log.info("重新锁定房源失败，订单支付状态恢复失败，记录异常，后续人工处理{}", payment.getPaymentNo());
            syncTradePaid(payment.getPaymentNo(), thirdPartyTradeNo, effectiveTime, now);
            return PaymentRepairResult.LATE_SUCCESS_UNRECOVERABLE;
        }

        log.info("成功重新锁定房源，尝试恢复订单支付状态，{}", payment.getPaymentNo());
        int updated = orderMapper.recoverPaidFromClosedTimeout(order.getOrderNo(), effectiveTime, payment.getPaymentNo(), now);
        if (updated > 0) {
            applyPaidCallback(payment, thirdPartyTradeNo, callbackNo, effectiveTime, now);
            payment.setStatus(PaymentStatus.PAID);
            paymentMapper.updateById(payment);
            syncTradePaid(payment.getPaymentNo(), thirdPartyTradeNo, effectiveTime, now);
            return PaymentRepairResult.LATE_SUCCESS_RECOVERED;
        }

        Order latestOrder = orderMapper.selectOrderNo(order.getOrderNo());
        if (latestOrder != null && payment.getPaymentNo().equals(latestOrder.getSuccessPaymentNo())) {
            applyPaidCallback(payment, thirdPartyTradeNo, callbackNo, effectiveTime, now);
            payment.setStatus(PaymentStatus.PAID);
            paymentMapper.updateById(payment);
            syncTradePaid(payment.getPaymentNo(), thirdPartyTradeNo, effectiveTime, now);
            return PaymentRepairResult.DUPLICATE_CALLBACK;
        }

        return markDuplicatePaid(payment, thirdPartyTradeNo, callbackNo, effectiveTime, now);
    }

    private PaymentRepairResult markDuplicatePaid(Payment payment,
                                                  String thirdPartyTradeNo,
                                                  String callbackNo,
                                                  LocalDateTime effectiveTime,
                                                  LocalDateTime now) {
        applyPaidCallback(payment, thirdPartyTradeNo, callbackNo, effectiveTime, now);
        payment.setStatus(PaymentStatus.DUPLICATE_PAID);
        payment.setFailReason("DUPLICATE_PAID");
        paymentMapper.updateById(payment);
        syncTradePaid(payment.getPaymentNo(), thirdPartyTradeNo, effectiveTime, now);
        return PaymentRepairResult.DUPLICATE_PAID;
    }

    private void applyPaidCallback(Payment payment,
                                   String thirdPartyTradeNo,
                                   String callbackNo,
                                   LocalDateTime effectiveTime,
                                   LocalDateTime now) {
        payment.setThirdPartyTradeNo(thirdPartyTradeNo);
        payment.setCallbackNo(callbackNo);
        payment.setCallbackTime(effectiveTime);
        payment.setPaidTime(effectiveTime);
        payment.setUpdateTime(now);
    }

    private int comparePaymentId(Payment left, Payment right) {
        Long leftId = left.getId();
        Long rightId = right.getId();
        if (leftId == null && rightId == null) {
            return 0;
        }
        if (leftId == null) {
            return 1;
        }
        if (rightId == null) {
            return -1;
        }
        return Long.compare(leftId, rightId);
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
