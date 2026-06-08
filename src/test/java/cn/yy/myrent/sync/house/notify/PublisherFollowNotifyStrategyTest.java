package cn.yy.myrent.sync.house.notify;

import cn.yy.myrent.common.NotificationType;
import cn.yy.myrent.entity.PublisherFollow;
import cn.yy.myrent.mapper.PublisherFollowMapper;
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
class PublisherFollowNotifyStrategyTest {

    @Mock
    private PublisherFollowMapper publisherFollowMapper;

    @InjectMocks
    private PublisherFollowNotifyStrategy strategy;

    @Test
    void buildNotificationsShouldReturnItemsForActiveFollowersWhenHouseCreated() {
        HouseChangedEvent event = new HouseChangedEvent()
                .setEventId("evt-1")
                .setEventType(HouseSyncConstants.EVENT_HOUSE_CREATED)
                .setOccurredAt(LocalDateTime.now())
                .setHouseId(8L)
                .setPublisherUserId(9L)
                .setHouseTitle("New listing")
                .setPriceYuan(4300)
                .setCity("Nanjing")
                .setRegion("Gulou")
                .setRentType(1);

        when(publisherFollowMapper.selectList(any())).thenReturn(List.of(
                new PublisherFollow().setUserId(1002L).setPublisherUserId(9L).setStatus(1)
        ));

        List<HouseNotifyItem> items = strategy.buildNotifications(event);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).userId()).isEqualTo(1002L);
        assertThat(items.get(0).type()).isEqualTo(NotificationType.PUBLISHER_NEW_HOUSE);
        assertThat(items.get(0).title()).isEqualTo("Publisher posted a new house");
        assertThat(items.get(0).content()).isEqualTo("New listing is now available.");
        assertThat(items.get(0).bizKey()).isEqualTo("publisher:9:house:8:new");
        assertThat(items.get(0).targetId()).isEqualTo(8L);
    }
}
