package cn.yy.myrent.task;

import cn.yy.myrent.mapper.PaymentMapper;
import cn.yy.myrent.service.IPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class PaymentCompensationTask {

    private final PaymentMapper paymentMapper;
    private final IPaymentService paymentService;

    public PaymentCompensationTask(PaymentMapper paymentMapper,
                                   IPaymentService paymentService) {
        this.paymentMapper = paymentMapper;
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelay = 30000)
    public void repairSuspiciousPayments() {
        List<String> orderNos = paymentMapper.selectSuspiciousOrderNosForRepair();
        for (String orderNo : orderNos) {
            try {
                paymentService.repairSuccessfulPaymentsForOrder(orderNo);
            } catch (Exception e) {
                log.warn("repair suspicious order failed, orderNo={}", orderNo, e);
            }
        }
    }
}
