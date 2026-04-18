package cn.yy.myrent.service;

import cn.yy.myrent.dto.MockPaymentCallbackReqDTO;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.vo.MockCheckoutVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IPaymentService extends IService<Payment> {

    MockCheckoutVO getMockCheckout(String paymentNo);

    void handleMockCallback(MockPaymentCallbackReqDTO req);
}
