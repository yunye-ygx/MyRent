package cn.yy.myrent.sync.house.notify;

import cn.yy.myrent.sync.house.model.HouseChangedEvent;

import java.util.List;

public interface HouseNotifyStrategy {

    boolean supports(HouseChangedEvent event);

    List<HouseNotifyItem> buildNotifications(HouseChangedEvent event);
}
