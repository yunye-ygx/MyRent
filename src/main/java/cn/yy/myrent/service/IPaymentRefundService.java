package cn.yy.myrent.service;

import cn.yy.myrent.dto.PaymentRefundApplyCommand;
import cn.yy.myrent.entity.PaymentRefund;
import cn.yy.myrent.vo.PaymentRefundApplyVO;
import cn.yy.myrent.vo.PaymentRefundOrderStatusVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IPaymentRefundService extends IService<PaymentRefund> {

    PaymentRefund applyRefund(PaymentRefundApplyCommand command);

    List<PaymentRefundOrderStatusVO> listLatestRefundStatusForOrders(Long userId, List<String> orderNos);

    void processPendingRefunds();

    PaymentRefundApplyVO toApplyVO(PaymentRefund refund);
}
