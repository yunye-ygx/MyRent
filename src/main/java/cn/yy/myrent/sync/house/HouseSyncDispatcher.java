package cn.yy.myrent.sync.house;

import cn.yy.myrent.sync.house.model.HouseSyncContext;
import cn.yy.myrent.sync.house.strategy.HouseSyncDispatchStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Slf4j
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
            log.info("核心字段有更新，进行核心字段同步");
            coreStrategy.dispatch(context);
            return;
        }

        log.info("非核心字段有更新，进行非核心字段同步");
        normalStrategy.dispatch(context);
    }
}

