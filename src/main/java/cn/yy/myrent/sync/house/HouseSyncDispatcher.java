package cn.yy.myrent.sync.house;

import cn.yy.myrent.sync.house.model.HouseSyncContext;
import cn.yy.myrent.sync.house.strategy.HouseSyncDispatchStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class HouseSyncDispatcher {

    @Autowired
    @Qualifier("coreHouseSyncDispatchStrategy")
    private HouseSyncDispatchStrategy coreStrategy;

    @Autowired
    @Qualifier("normalHouseSyncDispatchStrategy")
    private HouseSyncDispatchStrategy normalStrategy;

    public void dispatch(HouseSyncContext context) {
        if (context == null || context.getHouseId() == null) {
            return;
        }
        if (context.isCoreEvent()) {
            coreStrategy.dispatch(context);
            return;
        }
        normalStrategy.dispatch(context);
    }
}

