package cn.yy.myrent.sync.house.notify;

import cn.yy.myrent.common.NotificationType;
import cn.yy.myrent.entity.HouseAlert;
import cn.yy.myrent.service.IHouseAlertService;
import cn.yy.myrent.sync.house.HouseSyncConstants;
import cn.yy.myrent.sync.house.model.HouseChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseAlertNotifyStrategyTest {

    @Mock
    private IHouseAlertService houseAlertService;

    @InjectMocks
    private HouseAlertNotifyStrategy strategy;

    @Test
    void buildNotificationsShouldReturnItemsForMatchedAlertsWhenHouseCreated() {
        HouseChangedEvent event = new HouseChangedEvent()
                .setEventId("evt-2")
                .setEventType(HouseSyncConstants.EVENT_HOUSE_CREATED)
                .setOccurredAt(LocalDateTime.now())
                .setHouseId(18L)
                .setPublisherUserId(9L)
                .setHouseTitle("Metro Studio")
                .setPriceYuan(3200)
                .setCity("Nanjing")
                .setRegion("Qinhuai")
                .setRentType(2);

        when(houseAlertService.findMatchingAlerts(event)).thenReturn(List.of(
                new HouseAlert()
                        .setId(66L)
                        .setUserId(1003L)
                        .setCity("Nanjing")
                        .setRegion("Qinhuai")
                        .setRentType(2)
                        .setMaxPrice(3500)
                        .setStatus(1)
        ));

        List<HouseNotifyItem> items = strategy.buildNotifications(event);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).userId()).isEqualTo(1003L);
        assertThat(items.get(0).type()).isEqualTo(NotificationType.HOUSE_ALERT_MATCHED);
        assertThat(items.get(0).title()).isEqualTo("House alert matched");
        assertThat(items.get(0).content()).isEqualTo("Metro Studio matches your house alert.");
        assertThat(items.get(0).bizKey()).isEqualTo("alert:66:house:18:event:HOUSE_CREATED");
        assertThat(items.get(0).targetId()).isEqualTo(18L);
    }
}
