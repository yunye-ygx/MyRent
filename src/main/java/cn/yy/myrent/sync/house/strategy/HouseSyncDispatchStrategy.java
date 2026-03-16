package cn.yy.myrent.sync.house.strategy;

import cn.yy.myrent.sync.house.model.HouseSyncContext;

public interface HouseSyncDispatchStrategy {

    void dispatch(HouseSyncContext context);
}

