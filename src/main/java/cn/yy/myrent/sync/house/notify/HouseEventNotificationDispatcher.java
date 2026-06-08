package cn.yy.myrent.sync.house.notify;

import cn.yy.myrent.sync.house.model.HouseChangedEvent;

public interface HouseEventNotificationDispatcher {

    void dispatch(HouseChangedEvent event);
}
