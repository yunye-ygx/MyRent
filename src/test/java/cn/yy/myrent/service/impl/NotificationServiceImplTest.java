package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.NotificationType;
import cn.yy.myrent.entity.Notification;
import cn.yy.myrent.mapper.NotificationMapper;
import cn.yy.myrent.vo.UnreadTotalVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DuplicateKeyException;
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

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void notificationTypeShouldExposeInboxEventConstants() {
        assertEquals("HOUSE_OFFLINE", NotificationType.HOUSE_OFFLINE);
        assertEquals("HOUSE_RENTED", NotificationType.HOUSE_RENTED);
        assertEquals("HOUSE_DELETED", NotificationType.HOUSE_DELETED);
        assertEquals("HOUSE_PRICE_CHANGED", NotificationType.HOUSE_PRICE_CHANGED);
        assertEquals("PUBLISHER_NEW_HOUSE", NotificationType.PUBLISHER_NEW_HOUSE);
        assertEquals("HOUSE_ALERT_MATCHED", NotificationType.HOUSE_ALERT_MATCHED);
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
    void createHouseNotificationShouldInsertInboxRow() {
        notificationService.createHouseNotification(
                1002L,
                NotificationType.PUBLISHER_NEW_HOUSE,
                "Publisher posted a new house",
                "New listing is now available.",
                "publisher:9:house:8:new",
                8L,
                "{\"houseId\":8}"
        );

        verify(notificationMapper).insert((Notification) argThat((Notification item) ->
                item.getUserId().equals(1002L)
                        && item.getType().equals(NotificationType.PUBLISHER_NEW_HOUSE)
                        && item.getBizKey().equals("publisher:9:house:8:new")));
    }

    @Test
    void createHouseNotificationShouldIgnoreDuplicateBizKey() {
        when(notificationMapper.insert(any(Notification.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertDoesNotThrow(() -> notificationService.createHouseNotification(
                1002L,
                NotificationType.PUBLISHER_NEW_HOUSE,
                "Publisher posted a new house",
                "New listing is now available.",
                "publisher:9:house:8:new",
                8L,
                "{\"houseId\":8}"
        ));
    }
}
