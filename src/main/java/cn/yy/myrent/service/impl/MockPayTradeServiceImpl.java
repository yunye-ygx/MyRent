package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.MockPayTrade;
import cn.yy.myrent.mapper.MockPayTradeMapper;
import cn.yy.myrent.service.IMockPayTradeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class MockPayTradeServiceImpl extends ServiceImpl<MockPayTradeMapper, MockPayTrade>
        implements IMockPayTradeService {

    @Override
    public MockPayTrade getByPaymentNo(String paymentNo) {
        return baseMapper.selectByPaymentNo(paymentNo);
    }
}
