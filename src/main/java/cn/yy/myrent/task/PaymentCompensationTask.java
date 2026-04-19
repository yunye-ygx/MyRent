package cn.yy.myrent.task;

import cn.yy.myrent.entity.MockPayTrade;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.mapper.PaymentMapper;
import cn.yy.myrent.service.IMockPayTradeService;
import cn.yy.myrent.service.IPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class PaymentCompensationTask {

    private final PaymentMapper paymentMapper;
    private final IMockPayTradeService mockPayTradeService;
    private final IPaymentService paymentService;

    public PaymentCompensationTask(PaymentMapper paymentMapper,
                                   IMockPayTradeService mockPayTradeService,
                                   IPaymentService paymentService) {
        this.paymentMapper = paymentMapper;
        this.mockPayTradeService = mockPayTradeService;
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelay = 30000)
    public void repairSuspiciousPayments() {
        List<Payment> payments = paymentMapper.selectSuspiciousPayingPayments();
        for (Payment payment : payments) {
            try {
                MockPayTrade trade = mockPayTradeService.getByPaymentNo(payment.getPaymentNo());
                if (trade == null) {
                    continue;
                }
                paymentService.repairOrderPaidFromTrade(payment.getPaymentNo(),
                        trade.getThirdPartyTradeNo(),
                        null,
                        trade.getPaidTime());
            } catch (Exception e) {
                log.warn("repair suspicious payment failed, paymentNo={}", payment.getPaymentNo(), e);
            }
        }
    }
}
