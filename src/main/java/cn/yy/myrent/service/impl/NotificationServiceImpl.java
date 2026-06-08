package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.NotificationType;
import cn.yy.myrent.entity.Notification;
import cn.yy.myrent.mapper.NotificationMapper;
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

    private final NotificationMapper notificationMapper;

    public NotificationServiceImpl(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
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
    public void createHouseNotification(Long userId,
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
            log.info("notification created, userId={}, type={}, bizKey={}, targetId={}", userId, type, bizKey, targetId);
        } catch (Exception e) {
            log.warn("notification insert skipped (possible duplicate), userId={}, type={}, bizKey={}", userId, type, bizKey, e);
            // rely on unique(user_id, biz_key) to keep retries idempotent
        }
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
}
