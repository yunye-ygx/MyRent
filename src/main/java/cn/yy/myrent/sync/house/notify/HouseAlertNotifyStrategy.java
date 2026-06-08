package cn.yy.myrent.sync.house.notify;

import cn.yy.myrent.common.NotificationType;
import cn.yy.myrent.entity.HouseAlert;
import cn.yy.myrent.service.IHouseAlertService;
import cn.yy.myrent.sync.house.HouseSyncConstants;
import cn.yy.myrent.sync.house.model.HouseChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class HouseAlertNotifyStrategy implements HouseNotifyStrategy {

    private final IHouseAlertService houseAlertService;

    public HouseAlertNotifyStrategy(IHouseAlertService houseAlertService) {
        this.houseAlertService = houseAlertService;
    }

    @Override
    public boolean supports(HouseChangedEvent event) {
        return event != null
                && event.getHouseId() != null
                && HouseSyncConstants.EVENT_HOUSE_CREATED.equals(event.getEventType());
    }

    @Override
    public List<HouseNotifyItem> buildNotifications(HouseChangedEvent event) {
        List<HouseAlert> alerts = houseAlertService.findMatchingAlerts(event);
        if (alerts.isEmpty()) {
            log.info("no matching house alerts found, houseId={}, eventType={}", event.getHouseId(), event.getEventType());
            return Collections.emptyList();
        }
        log.info("found {} matching house alerts, houseId={}, eventType={}", alerts.size(), event.getHouseId(), event.getEventType());

        List<HouseNotifyItem> items = new ArrayList<>(alerts.size());
        for (HouseAlert alert : alerts) {
            items.add(new HouseNotifyItem(
                    alert.getUserId(),
                    NotificationType.HOUSE_ALERT_MATCHED,
                    "House alert matched",
                    safeHouseTitle(event) + " matches your house alert.",
                    "alert:" + alert.getId() + ":house:" + event.getHouseId() + ":event:" + event.getEventType(),
                    event.getHouseId(),
                    "{\"houseId\":" + event.getHouseId() + ",\"alertId\":" + alert.getId() + ",\"eventType\":\"" + event.getEventType() + "\"}"
            ));
        }
        return items;
    }

    private String safeHouseTitle(HouseChangedEvent event) {
        return event == null || event.getHouseTitle() == null || event.getHouseTitle().isBlank()
                ? "This house"
                : event.getHouseTitle();
    }
}
