package cn.yy.myrent.service;

import cn.yy.myrent.entity.MockPayTrade;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IMockPayTradeService extends IService<MockPayTrade> {

    MockPayTrade getByPaymentNo(String paymentNo);
}
