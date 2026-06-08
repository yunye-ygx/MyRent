package cn.yy.myrent.service;

import cn.yy.myrent.entity.Notification;
import cn.yy.myrent.vo.UnreadTotalVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface INotificationService extends IService<Notification> {

    Page<Notification> pageMine(Long userId, Long current, Long size);

    UnreadTotalVO buildUnreadTotal(Long userId);

    void markRead(Long notificationId, Long userId);

    void markAllRead(Long userId);

    void createHouseNotification(Long userId,
                                 String type,
                                 String title,
                                 String content,
                                 String bizKey,
                                 Long targetId,
                                 String extraJson);
}
