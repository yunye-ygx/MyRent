package cn.yy.myrent.sync.house.model;

import lombok.Data;

@Data
public class HouseSyncContext {

    private HouseChangedEvent event;

    private boolean coreEvent;

    private String reason;

    public Long getHouseId() {
        return event == null ? null : event.getHouseId();
    }

    public String getEventType() {
        return event == null ? null : event.getEventType();
    }
}
