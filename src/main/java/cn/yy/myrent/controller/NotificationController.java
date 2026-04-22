package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.entity.Notification;
import cn.yy.myrent.service.INotificationService;
import cn.yy.myrent.vo.UnreadTotalVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final INotificationService notificationService;

    @GetMapping("/page")
    public Result<Page<Notification>> page(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        return Result.success(notificationService.pageMine(userId, current, size));
    }

    @GetMapping("/unread-total")
    public Result<UnreadTotalVO> unreadTotal() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        return Result.success(notificationService.buildUnreadTotal(userId));
    }

    @PostMapping("/read/{id}")
    public Result<Void> read(@PathVariable("id") Long id) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        notificationService.markRead(id, userId);
        return Result.success();
    }

    @PostMapping("/read-all")
    public Result<Void> readAll() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        notificationService.markAllRead(userId);
        return Result.success();
    }
}
