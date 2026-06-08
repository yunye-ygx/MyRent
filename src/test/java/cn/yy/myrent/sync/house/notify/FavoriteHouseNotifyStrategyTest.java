package cn.yy.myrent.sync.house.notify;

import cn.yy.myrent.common.NotificationType;
import cn.yy.myrent.entity.HouseFavorite;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
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
class FavoriteHouseNotifyStrategyTest {

    @Mock
    private HouseFavoriteMapper houseFavoriteMapper;

    @InjectMocks
    private FavoriteHouseNotifyStrategy strategy;

    @Test
    void buildNotificationsShouldReturnItemsForFavoriteUsersWhenPriceChanged() {
        HouseChangedEvent event = new HouseChangedEvent()
                .setEventId("evt-3")
                .setEventType(HouseSyncConstants.EVENT_HOUSE_PRICE_CHANGED)
                .setOccurredAt(LocalDateTime.now())
                .setHouseId(7L)
                .setPublisherUserId(9L)
                .setHouseTitle("Tianhe One Bed")
                .setPriceYuan(5000)
                .setPreviousPriceYuan(5200)
                .setCity("Guangzhou")
                .setRegion("Tianhe")
                .setRentType(1);

        when(houseFavoriteMapper.selectList(any())).thenReturn(List.of(
                new HouseFavorite().setUserId(1001L).setHouseId(7L).setStatus(1)
        ));

        List<HouseNotifyItem> items = strategy.buildNotifications(event);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).userId()).isEqualTo(1001L);
        assertThat(items.get(0).type()).isEqualTo(NotificationType.HOUSE_PRICE_CHANGED);
        assertThat(items.get(0).title()).isEqualTo("Price changed");
        assertThat(items.get(0).content()).isEqualTo("The monthly price changed from 5200 to 5000.");
        assertThat(items.get(0).bizKey()).isEqualTo("house:7:price:5200->5000");
        assertThat(items.get(0).targetId()).isEqualTo(7L);
    }
}
