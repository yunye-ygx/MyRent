package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.service.IPublisherFollowService;
import cn.yy.myrent.vo.PublisherFollowStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/publisher-follow")
@RequiredArgsConstructor
public class PublisherFollowController {

    private final IPublisherFollowService publisherFollowService;

    @PostMapping("/{publisherUserId}")
    public Result<PublisherFollowStatusVO> follow(@PathVariable Long publisherUserId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        return Result.success(publisherFollowService.follow(publisherUserId, userId));
    }

    @DeleteMapping("/{publisherUserId}")
    public Result<PublisherFollowStatusVO> unfollow(@PathVariable Long publisherUserId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        return Result.success(publisherFollowService.unfollow(publisherUserId, userId));
    }

    @GetMapping("/{publisherUserId}/status")
    public Result<PublisherFollowStatusVO> status(@PathVariable Long publisherUserId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        return Result.success(publisherFollowService.getStatus(publisherUserId, userId));
    }
}
