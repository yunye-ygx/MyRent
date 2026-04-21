package cn.yy.myrent.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.yy.myrent.common.GenerateOrder;
import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.PaymentRefundSourceType;
import cn.yy.myrent.common.PaymentRefundStatus;
import cn.yy.myrent.common.PaymentStatus;
import cn.yy.myrent.dto.PaymentRefundApplyCommand;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.entity.PaymentRefund;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.PaymentMapper;
import cn.yy.myrent.mapper.PaymentRefundMapper;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IPaymentRefundService;
import cn.yy.myrent.vo.PaymentRefundApplyVO;
import cn.yy.myrent.vo.PaymentRefundOrderStatusVO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PaymentRefundServiceImpl extends ServiceImpl<PaymentRefundMapper, PaymentRefund> implements IPaymentRefundService {

    private static final int PROCESS_BATCH_SIZE = 20;

    private final PaymentRefundMapper paymentRefundMapper;
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final IHouseCommandService houseCommandService;

    public PaymentRefundServiceImpl(PaymentRefundMapper paymentRefundMapper,
                                    OrderMapper orderMapper,
                                    PaymentMapper paymentMapper,
                                    IHouseCommandService houseCommandService) {
        this.paymentRefundMapper = paymentRefundMapper;
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.houseCommandService = houseCommandService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentRefund applyRefund(PaymentRefundApplyCommand command) {
        if (command == null || !StrUtil.isNotBlank(command.getOrderNo())
                || command.getSourceType() == null || !StrUtil.isNotBlank(command.getReasonCode())) {
            throw new RuntimeException("refund request is invalid");
        }

        Order order = orderMapper.selectOrderNo(command.getOrderNo());
        if (order == null) {
            throw new RuntimeException("order not found");
        }

        log.info("确认是哪笔payment在退款 {}", command.getPaymentNo());
        Payment payment = resolvePayment(order, command);

        validateRefundRequest(order, payment, command);

        String requestNo = resolveRequestNo(order, payment, command);
        PaymentRefund existing = paymentRefundMapper.selectByRequestNo(requestNo);
        if (existing != null) {
            log.info("退款请求已存在 {}", requestNo);
            return existing;
        }

        log.info("创建退款请求 {}", requestNo);
        LocalDateTime now = LocalDateTime.now();
        PaymentRefund refund = new PaymentRefund();
        refund.setRefundNo(GenerateOrder.generateOrderNo("REF"));
        refund.setRequestNo(requestNo);
        refund.setOrderNo(order.getOrderNo());
        refund.setPaymentNo(payment.getPaymentNo());
        refund.setUserId(payment.getUserId());
        refund.setChannel(payment.getChannel());
        refund.setRefundAmount(payment.getPayAmount());
        refund.setSourceType(command.getSourceType());
        refund.setReasonCode(command.getReasonCode());
        refund.setReasonDetail(command.getReasonDetail());
        refund.setStatus(PaymentRefundStatus.PENDING);
        refund.setThirdPartyTradeNo(payment.getThirdPartyTradeNo());
        refund.setRetryCount(0);
        refund.setMaxRetryCount(10);
        refund.setNextRetryTime(now);
        refund.setApplyTime(now);
        refund.setCreateTime(now);
        refund.setUpdateTime(now);
        paymentRefundMapper.insert(refund);
        return refund;
    }

    @Override
    public List<PaymentRefundOrderStatusVO> listLatestRefundStatusForOrders(Long userId, List<String> orderNos) {
        if (userId == null || CollectionUtils.isEmpty(orderNos)) {
            return List.of();
        }

        List<PaymentRefund> refunds = paymentRefundMapper.selectByUserIdAndOrderNos(userId, orderNos);
        if (CollectionUtils.isEmpty(refunds)) {
            return List.of();
        }

        Map<String, PaymentRefundOrderStatusVO> latest = new LinkedHashMap<>();
        for (PaymentRefund refund : refunds) {
            if (refund == null || latest.containsKey(refund.getOrderNo())) {
                log.info("此退款请求不是最新的 {}", refund.getId());
                continue;
            }
            PaymentRefundOrderStatusVO vo = new PaymentRefundOrderStatusVO();
            vo.setOrderNo(refund.getOrderNo());
            vo.setRefundNo(refund.getRefundNo());
            vo.setStatus(refund.getStatus());
            vo.setSourceType(refund.getSourceType());
            vo.setReasonCode(refund.getReasonCode());
            latest.put(refund.getOrderNo(), vo);
        }
        return new ArrayList<>(latest.values());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processPendingRefunds() {
        List<PaymentRefund> refunds = paymentRefundMapper.selectPendingForProcess(LocalDateTime.now(), PROCESS_BATCH_SIZE);

        log.info("要处理的退款请求数量 {}", refunds.size());
        for (PaymentRefund refund : refunds) {
            processSingleRefund(refund);
        }
    }

    @Override
    public PaymentRefundApplyVO toApplyVO(PaymentRefund refund) {
        if (refund == null) {
            return null;
        }
        PaymentRefundApplyVO vo = new PaymentRefundApplyVO();
        vo.setRefundNo(refund.getRefundNo());
        vo.setOrderNo(refund.getOrderNo());
        vo.setPaymentNo(refund.getPaymentNo());
        vo.setStatus(refund.getStatus());
        vo.setSourceType(refund.getSourceType());
        vo.setReasonCode(refund.getReasonCode());
        return vo;
    }

    private Payment resolvePayment(Order order, PaymentRefundApplyCommand command) {
        String paymentNo = StrUtil.isNotBlank(command.getPaymentNo()) ? command.getPaymentNo() : order.getSuccessPaymentNo();
        if (!StrUtil.isNotBlank(paymentNo)) {
            throw new RuntimeException("payment not found for refund");
        }

        Payment payment = paymentMapper.selectByPaymentNo(paymentNo);
        if (payment == null || !order.getOrderNo().equals(payment.getOrderNo())) {
            throw new RuntimeException("payment not found for refund");
        }
        return payment;
    }

    private void validateRefundRequest(Order order, Payment payment, PaymentRefundApplyCommand command) {
        if (command.getUserId() != null && !command.getUserId().equals(order.getUserId())) {
            throw new RuntimeException("cannot refund other user's order");
        }

        if (command.getSourceType() == PaymentRefundSourceType.USER_APPLY) {
            if (order.getStatus() != OrderStatus.PAID
                    || payment.getStatus() == null
                    || payment.getStatus() != PaymentStatus.PAID
                    || !payment.getPaymentNo().equals(order.getSuccessPaymentNo())) {
                throw new RuntimeException("order is not refundable");
            }
        }

        if (command.getSourceType() == PaymentRefundSourceType.LATE_SUCCESS_UNRECOVERABLE
                && order.getStatus() != OrderStatus.CLOSED_TIMEOUT) {
            throw new RuntimeException("order is not timeout closed");
        }

        if (command.getSourceType() == PaymentRefundSourceType.DUPLICATE_PAID
                && (payment.getStatus() == null || payment.getStatus() != PaymentStatus.DUPLICATE_PAID)) {
            throw new RuntimeException("payment is not duplicate paid");
        }
    }

    private String resolveRequestNo(Order order, Payment payment, PaymentRefundApplyCommand command) {
        if (StrUtil.isNotBlank(command.getRequestNo())) {
            return command.getRequestNo();
        }
        return command.getReasonCode() + ":" + order.getOrderNo() + ":" + payment.getPaymentNo();
    }

    private void processSingleRefund(PaymentRefund refund) {
        if (refund == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            if (!"MOCK".equalsIgnoreCase(refund.getChannel())) {
                throw new IllegalStateException("unsupported refund channel: " + refund.getChannel());
            }

            markRefundSuccess(refund, now);

            log.info("退款请求标记为成功 {}", refund.getId());
            handleRefundSuccess(refund, now);
        } catch (Exception e) {
            handleRefundFailure(refund, now, e);
        }
    }

    private void markRefundSuccess(PaymentRefund refund, LocalDateTime now) {
        refund.setStatus(PaymentRefundStatus.SUCCESS);
        refund.setThirdPartyRefundNo(GenerateOrder.generateOrderNo("MOCKRF"));
        refund.setSuccessTime(now);
        refund.setNextRetryTime(null);
        refund.setFailReason(null);
        refund.setUpdateTime(now);

        log.info("尝试把退款请求标记为成功 {}", refund.getId());
        paymentRefundMapper.updateById(refund);
    }

    private void handleRefundSuccess(PaymentRefund refund, LocalDateTime now) {
        if (refund.getSourceType() == PaymentRefundSourceType.USER_APPLY) {
            log.info("处理用户申请退款成功 {}", refund.getId());
            handleUserApplyRefundSuccess(refund, now);
            return;
        }
        if (refund.getSourceType() == PaymentRefundSourceType.DUPLICATE_PAID) {
            log.info("处理重复支付退款成功 {}", refund.getId());
            handleDuplicatePaidRefundSuccess(refund, now);
            return;
        }
        if (refund.getSourceType() == PaymentRefundSourceType.LATE_SUCCESS_UNRECOVERABLE) {
            log.info("处理延迟成功退款成功 {}", refund.getId());
            handleLateSuccessRefundSuccess(refund, now);
            return;
        }
        throw new IllegalStateException("unsupported refund source type: " + refund.getSourceType());
    }

    private void handleRefundFailure(PaymentRefund refund, LocalDateTime now, Exception e) {
        int retryCount = refund.getRetryCount() == null ? 0 : refund.getRetryCount();
        retryCount++;
        refund.setRetryCount(retryCount);
        refund.setFailReason(e.getMessage());
        refund.setUpdateTime(now);
        if (retryCount >= (refund.getMaxRetryCount() == null ? 10 : refund.getMaxRetryCount())) {
            refund.setStatus(PaymentRefundStatus.MANUAL_REVIEW);
            refund.setCloseTime(now);
            refund.setNextRetryTime(null);
        } else {
            refund.setStatus(PaymentRefundStatus.RETRY);
            refund.setNextRetryTime(now.plusSeconds(10L * retryCount));
        }
        paymentRefundMapper.updateById(refund);
    }

    private void handleUserApplyRefundSuccess(PaymentRefund refund, LocalDateTime now) {
        Order order = orderMapper.selectOrderNo(refund.getOrderNo());
        Payment payment = paymentMapper.selectByPaymentNo(refund.getPaymentNo());
        if (order == null || payment == null) {
            throw new IllegalStateException("refund compensation target not found");
        }

        if (order.getStatus() == OrderStatus.REFUNDED
                && payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }

        if (order.getStatus() != OrderStatus.PAID_LOCKED) {
            throw new IllegalStateException("user apply refund order status invalid: " + order.getStatus());
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException("user apply refund payment status invalid: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setUpdateTime(now);
        paymentMapper.updateById(payment);

        order.setStatus(OrderStatus.REFUNDED);
        order.setCloseTime(now);
        order.setUpdateTime(now);
        orderMapper.updateById(order);

        boolean released = houseCommandService.updateHouseStatusWithSync(
                order.getHouseId(),
                2,
                1,
                "refund-user-apply-release");
        if (!released) {
            throw new IllegalStateException("refund user apply release house failed");
        }
    }

    private void handleDuplicatePaidRefundSuccess(PaymentRefund refund, LocalDateTime now) {
        Payment payment = paymentMapper.selectByPaymentNo(refund.getPaymentNo());
        if (payment == null) {
            throw new IllegalStateException("duplicate paid refund payment not found");
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.DUPLICATE_PAID) {
            throw new IllegalStateException("duplicate paid refund payment status invalid: " + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setUpdateTime(now);
        paymentMapper.updateById(payment);
    }

    private void handleLateSuccessRefundSuccess(PaymentRefund refund, LocalDateTime now) {
        Order order = orderMapper.selectOrderNo(refund.getOrderNo());
        Payment payment = paymentMapper.selectByPaymentNo(refund.getPaymentNo());
        if (order == null || payment == null) {
            throw new IllegalStateException("late success refund target not found");
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        if (order.getStatus() != OrderStatus.CLOSED_TIMEOUT) {
            throw new IllegalStateException("late success refund order status invalid: " + order.getStatus());
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setUpdateTime(now);
        paymentMapper.updateById(payment);
    }
}
