package cn.yy.myrent.task;

import cn.yy.myrent.service.IPaymentRefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentRefundTask {

    private final IPaymentRefundService paymentRefundService;

    public PaymentRefundTask(IPaymentRefundService paymentRefundService) {
        this.paymentRefundService = paymentRefundService;
    }

    @Scheduled(fixedDelay = 10000)
    public void processPendingRefunds() {
        try {
            paymentRefundService.processPendingRefunds();
        } catch (Exception e) {
            log.warn("process pending refunds failed", e);
        }
    }
}
