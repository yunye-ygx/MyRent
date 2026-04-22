package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.NotificationType;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.HouseFavorite;
import cn.yy.myrent.entity.Notification;
import cn.yy.myrent.entity.PublisherFollow;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
import cn.yy.myrent.mapper.NotificationMapper;
import cn.yy.myrent.mapper.PublisherFollowMapper;
import cn.yy.myrent.vo.UnreadTotalVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private HouseFavoriteMapper houseFavoriteMapper;

    @Mock
    private PublisherFollowMapper publisherFollowMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void notificationTypeShouldExposeInboxEventConstants() {
        assertEquals("HOUSE_OFFLINE", NotificationType.HOUSE_OFFLINE);
        assertEquals("HOUSE_RENTED", NotificationType.HOUSE_RENTED);
        assertEquals("HOUSE_DELETED", NotificationType.HOUSE_DELETED);
        assertEquals("HOUSE_PRICE_CHANGED", NotificationType.HOUSE_PRICE_CHANGED);
        assertEquals("PUBLISHER_NEW_HOUSE", NotificationType.PUBLISHER_NEW_HOUSE);
    }

    @Test
    void buildUnreadTotalShouldCountUnreadRows() {
        when(notificationMapper.selectCount(any())).thenReturn(4L);

        UnreadTotalVO result = notificationService.buildUnreadTotal(1001L);

        assertEquals(4L, result.getTotal());
    }

    @Test
    void markAllReadShouldUpdateUnreadRowsForCurrentUser() {
        when(notificationMapper.update(any(), any())).thenReturn(2);

        assertDoesNotThrow(() -> notificationService.markAllRead(1001L));
    }

    @Test
    void notifyHouseUpdatedShouldInsertPriceChangeNotificationsForActiveFavorites() {
        House oldHouse = new House().setId(7L).setTitle("Tianhe One Bed").setPrice(5200).setStatus(1);
        House newHouse = new House().setId(7L).setTitle("Tianhe One Bed").setPrice(5000).setStatus(1);

        HouseFavorite favorite = new HouseFavorite().setUserId(1001L).setHouseId(7L).setStatus(1);
        when(houseFavoriteMapper.selectList(any())).thenReturn(java.util.List.of(favorite));
        notificationService.notifyHouseUpdated(oldHouse, newHouse);

        verify(notificationMapper).insert((Notification) argThat((Notification item) ->
                item.getUserId().equals(1001L)
                        && item.getType().equals(NotificationType.HOUSE_PRICE_CHANGED)
                        && item.getBizKey().equals("house:7:price:5200->5000")));
    }

    @Test
    void notifyHouseUpdatedShouldInsertRentedNotificationsWhenStatusBecomesLocked() {
        House oldHouse = new House().setId(7L).setTitle("Tianhe One Bed").setStatus(1);
        House newHouse = new House().setId(7L).setTitle("Tianhe One Bed").setStatus(2).setVersion(3);

        HouseFavorite favorite = new HouseFavorite().setUserId(1001L).setHouseId(7L).setStatus(1);
        when(houseFavoriteMapper.selectList(any())).thenReturn(java.util.List.of(favorite));
        notificationService.notifyHouseUpdated(oldHouse, newHouse);

        verify(notificationMapper).insert((Notification) argThat((Notification item) ->
                item.getUserId().equals(1001L)
                        && item.getType().equals(NotificationType.HOUSE_RENTED)
                        && item.getBizKey().equals("house:7:type:HOUSE_RENTED:version:3")));
    }

    @Test
    void notifyHouseDeletedShouldInsertDeletedNotificationsForActiveFavorites() {
        House oldHouse = new House().setId(7L).setTitle("Tianhe One Bed").setStatus(1);

        HouseFavorite favorite = new HouseFavorite().setUserId(1001L).setHouseId(7L).setStatus(1);
        when(houseFavoriteMapper.selectList(any())).thenReturn(java.util.List.of(favorite));
        notificationService.notifyHouseDeleted(oldHouse);

        verify(notificationMapper).insert((Notification) argThat((Notification item) ->
                item.getUserId().equals(1001L)
                        && item.getType().equals(NotificationType.HOUSE_DELETED)
                        && item.getBizKey().equals("house:7:type:HOUSE_DELETED:version:delete")));
    }

    @Test
    void notifyHouseCreatedShouldInsertNewHouseNotificationsForFollowers() {
        House house = new House().setId(8L).setPublisherUserId(9L).setTitle("New listing").setPrice(4300).setStatus(1);

        PublisherFollow follow = new PublisherFollow().setUserId(1002L).setPublisherUserId(9L).setStatus(1);
        when(publisherFollowMapper.selectList(any())).thenReturn(java.util.List.of(follow));
        notificationService.notifyHouseCreated(house);

        verify(notificationMapper).insert((Notification) argThat((Notification item) ->
                item.getUserId().equals(1002L)
                        && item.getType().equals(NotificationType.PUBLISHER_NEW_HOUSE)
                        && item.getBizKey().equals("publisher:9:house:8:new")));
    }
}
