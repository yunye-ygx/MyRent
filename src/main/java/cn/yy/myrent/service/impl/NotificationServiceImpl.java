package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.NotificationType;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.HouseFavorite;
import cn.yy.myrent.entity.Notification;
import cn.yy.myrent.entity.PublisherFollow;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
import cn.yy.myrent.mapper.NotificationMapper;
import cn.yy.myrent.mapper.PublisherFollowMapper;
import cn.yy.myrent.service.INotificationService;
import cn.yy.myrent.vo.UnreadTotalVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements INotificationService {

    private static final int ACTIVE_STATUS = 1;
    private static final int HOUSE_STATUS_OFFLINE = 0;
    private static final int HOUSE_STATUS_RENTED = 2;

    private final NotificationMapper notificationMapper;
    private final HouseFavoriteMapper houseFavoriteMapper;
    private final PublisherFollowMapper publisherFollowMapper;

    public NotificationServiceImpl(NotificationMapper notificationMapper,
                                   HouseFavoriteMapper houseFavoriteMapper,
                                   PublisherFollowMapper publisherFollowMapper) {
        this.notificationMapper = notificationMapper;
        this.houseFavoriteMapper = houseFavoriteMapper;
        this.publisherFollowMapper = publisherFollowMapper;
    }

    @Override
    public Page<Notification> pageMine(Long userId, Long current, Long size) {
        long safeCurrent = Math.max(current == null ? 1L : current, 1L);
        long safeSize = Math.min(Math.max(size == null ? 10L : size, 1L), 50L);
        Page<Notification> page = new Page<>(safeCurrent, safeSize);
        page.setRecords(notificationMapper.selectList(visibleInboxQuery(userId)
                .orderByDesc("create_time")
                .orderByDesc("id")
                .last("LIMIT " + page.offset() + "," + safeSize)));
        page.setTotal(notificationMapper.selectCount(visibleInboxQuery(userId)));
        return page;
    }

    @Override
    public UnreadTotalVO buildUnreadTotal(Long userId) {
        Long total = notificationMapper.selectCount(new QueryWrapper<Notification>()
                .eq("user_id", userId)
                .eq("is_read", 0));
        UnreadTotalVO vo = new UnreadTotalVO();
        vo.setTotal(total == null ? 0L : total);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long notificationId, Long userId) {
        Notification notification = notificationMapper.selectOne(new QueryWrapper<Notification>()
                .select("id", "user_id", "type", "is_read")
                .eq("id", notificationId)
                .eq("user_id", userId)
                .last("LIMIT 1"));
        if (notification == null) {
            return;
        }
        if (shouldRemoveAfterRead(notification.getType())) {
            notificationMapper.delete(new QueryWrapper<Notification>()
                    .eq("id", notificationId)
                    .eq("user_id", userId));
            return;
        }
        notificationMapper.update(null, new UpdateWrapper<Notification>()
                .eq("id", notificationId)
                .eq("user_id", userId)
                .eq("is_read", 0)
                .set("is_read", 1)
                .setSql("read_time = NOW()"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new UpdateWrapper<Notification>()
                .eq("user_id", userId)
                .eq("is_read", 0)
                .set("is_read", 1)
                .setSql("read_time = NOW()"));
        notificationMapper.delete(new QueryWrapper<Notification>()
                .eq("user_id", userId)
                .eq("type", NotificationType.PUBLISHER_NEW_HOUSE));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void notifyHouseCreated(House house) {
        if (house == null || house.getId() == null || house.getPublisherUserId() == null) {
            return;
        }

        for (PublisherFollow follow : publisherFollowMapper.selectList(new QueryWrapper<PublisherFollow>()
                .eq("publisher_user_id", house.getPublisherUserId())
                .eq("status", ACTIVE_STATUS))) {
            insertInbox(
                    follow.getUserId(),
                    NotificationType.PUBLISHER_NEW_HOUSE,
                    "Publisher posted a new house",
                    safeHouseTitle(house) + " is now available.",
                    "publisher:" + house.getPublisherUserId() + ":house:" + house.getId() + ":new",
                    house.getId(),
                    "{\"houseId\":" + house.getId() + ",\"publisherUserId\":" + house.getPublisherUserId() + "}"
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void notifyHouseUpdated(House oldHouse, House newHouse) {
        if (oldHouse == null || newHouse == null || oldHouse.getId() == null) {
            return;
        }

        if (oldHouse.getPrice() != null
                && newHouse.getPrice() != null
                && !oldHouse.getPrice().equals(newHouse.getPrice())) {
            log.info("房源价格发生变化 {}", oldHouse.getId());
            fanoutToFavoriteUsers(
                    oldHouse.getId(),
                    NotificationType.HOUSE_PRICE_CHANGED,
                    "Price changed",
                    "The monthly price changed from " + oldHouse.getPrice() + " to " + newHouse.getPrice() + ".",
                    "house:" + oldHouse.getId() + ":price:" + oldHouse.getPrice() + "->" + newHouse.getPrice(),
                    newHouse.getId(),
                    "{\"houseId\":" + newHouse.getId() + ",\"oldPrice\":" + oldHouse.getPrice() + ",\"newPrice\":" + newHouse.getPrice() + "}"
            );
        }

        if (!equalsStatus(oldHouse.getStatus(), newHouse.getStatus())
                && Integer.valueOf(HOUSE_STATUS_RENTED).equals(newHouse.getStatus())) {
            log.info("房源状态发生变化 {}", oldHouse.getId());
            fanoutToFavoriteUsers(
                    oldHouse.getId(),
                    NotificationType.HOUSE_RENTED,
                    "House rented",
                    safeHouseTitle(oldHouse) + " has been rented.",
                    "house:" + oldHouse.getId() + ":type:HOUSE_RENTED:version:" + normalizeVersion(newHouse),
                    newHouse.getId(),
                    "{\"houseId\":" + newHouse.getId() + ",\"status\":" + HOUSE_STATUS_RENTED + "}"
            );
        }

        if (!equalsStatus(oldHouse.getStatus(), newHouse.getStatus())
                && Integer.valueOf(HOUSE_STATUS_OFFLINE).equals(newHouse.getStatus())) {
            log.info("房源状态发生变化 {}", oldHouse.getId());
            fanoutToFavoriteUsers(
                    oldHouse.getId(),
                    NotificationType.HOUSE_OFFLINE,
                    "House offline",
                    safeHouseTitle(oldHouse) + " is now offline.",
                    "house:" + oldHouse.getId() + ":type:HOUSE_OFFLINE:version:" + normalizeVersion(newHouse),
                    newHouse.getId(),
                    "{\"houseId\":" + newHouse.getId() + ",\"status\":" + HOUSE_STATUS_OFFLINE + "}"
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void notifyHouseDeleted(House oldHouse) {
        if (oldHouse == null || oldHouse.getId() == null) {
            return;
        }

        fanoutToFavoriteUsers(
                oldHouse.getId(),
                NotificationType.HOUSE_DELETED,
                "House deleted",
                safeHouseTitle(oldHouse) + " is no longer available.",
                "house:" + oldHouse.getId() + ":type:HOUSE_DELETED:version:delete",
                oldHouse.getId(),
                "{\"houseId\":" + oldHouse.getId() + ",\"deleted\":true}"
        );
    }

    private void fanoutToFavoriteUsers(Long houseId,
                                       String type,
                                       String title,
                                       String content,
                                       String bizKey,
                                       Long targetHouseId,
                                       String extraJson) {
        if (houseId == null || targetHouseId == null) {
            return;
        }

        for (HouseFavorite favorite : houseFavoriteMapper.selectList(new QueryWrapper<HouseFavorite>()
                .eq("house_id", houseId)
                .eq("status", ACTIVE_STATUS))) {
            insertInbox(favorite.getUserId(), type, title, content, bizKey, targetHouseId, extraJson);
        }
    }

    private void insertInbox(Long userId,
                             String type,
                             String title,
                             String content,
                             String bizKey,
                             Long targetId,
                             String extraJson) {
        if (userId == null || type == null || bizKey == null || targetId == null) {
            return;
        }

        try {
            notificationMapper.insert(new Notification()
                    .setUserId(userId)
                    .setType(type)
                    .setTitle(title)
                    .setContent(content)
                    .setBizKey(bizKey)
                    .setRedirectType("house_detail")
                    .setRedirectTargetId(targetId)
                    .setExtraJson(extraJson)
                    .setIsRead(0));
        } catch (Exception ignore) {
            // rely on unique(user_id, biz_key) to keep retries idempotent
        }
    }

    private boolean equalsStatus(Integer left, Integer right) {
        return left == null ? right == null : left.equals(right);
    }

    private QueryWrapper<Notification> visibleInboxQuery(Long userId) {
        return new QueryWrapper<Notification>()
                .eq("user_id", userId)
                .and(wrapper -> wrapper.ne("type", NotificationType.PUBLISHER_NEW_HOUSE)
                        .or(inner -> inner.eq("type", NotificationType.PUBLISHER_NEW_HOUSE)
                                .eq("is_read", 0)));
    }

    private boolean shouldRemoveAfterRead(String type) {
        return NotificationType.PUBLISHER_NEW_HOUSE.equals(type);
    }

    private int normalizeVersion(House house) {
        return house == null || house.getVersion() == null ? 0 : house.getVersion();
    }

    private String safeHouseTitle(House house) {
        return house == null || house.getTitle() == null || house.getTitle().isBlank()
                ? "This house"
                : house.getTitle();
    }
}
