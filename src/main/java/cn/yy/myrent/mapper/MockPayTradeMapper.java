package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.MockPayTrade;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface MockPayTradeMapper extends BaseMapper<MockPayTrade> {

    MockPayTrade selectByPaymentNo(String paymentNo);
}
