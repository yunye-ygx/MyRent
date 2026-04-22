package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.PublisherFollow;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.PublisherFollowMapper;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.service.IPublisherFollowService;
import cn.yy.myrent.vo.PublisherFollowStatusVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PublisherFollowServiceImpl implements IPublisherFollowService {

    private static final int STATUS_ACTIVE = 1;

    private final PublisherFollowMapper publisherFollowMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublisherFollowStatusVO follow(Long publisherUserId, Long userId) {
        validatePublisher(publisherUserId, userId);
        PublisherFollow existing = findRelation(publisherUserId, userId);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            publisherFollowMapper.insert(new PublisherFollow()
                    .setUserId(userId)
                    .setPublisherUserId(publisherUserId)
                    .setStatus(STATUS_ACTIVE)
                    .setCreateTime(now));
        } else if (!Integer.valueOf(STATUS_ACTIVE).equals(existing.getStatus())) {
            existing.setStatus(STATUS_ACTIVE);
            existing.setCancelTime(null);
            publisherFollowMapper.updateById(existing);
        }
        return buildStatus(publisherUserId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublisherFollowStatusVO unfollow(Long publisherUserId, Long userId) {
        validatePublisher(publisherUserId, userId);
        PublisherFollow existing = findRelation(publisherUserId, userId);
        if (existing != null && Integer.valueOf(STATUS_ACTIVE).equals(existing.getStatus())) {
            existing.setStatus(0);
            existing.setCancelTime(LocalDateTime.now());
            publisherFollowMapper.updateById(existing);
        }
        return buildStatus(publisherUserId, false);
    }

    @Override
    public PublisherFollowStatusVO getStatus(Long publisherUserId, Long userId) {
        validatePublisher(publisherUserId, userId);
        PublisherFollow existing = findRelation(publisherUserId, userId);
        return buildStatus(
                publisherUserId,
                existing != null && Integer.valueOf(STATUS_ACTIVE).equals(existing.getStatus())
        );
    }

    private PublisherFollow findRelation(Long publisherUserId, Long userId) {
        return publisherFollowMapper.selectOne(new LambdaQueryWrapper<PublisherFollow>()
                .eq(PublisherFollow::getUserId, userId)
                .eq(PublisherFollow::getPublisherUserId, publisherUserId));
    }

    private void validatePublisher(Long publisherUserId, Long userId) {
        if (publisherUserId == null || userId == null) {
            throw new IllegalArgumentException("user id cannot be null");
        }
        if (publisherUserId.equals(userId)) {
            throw new IllegalArgumentException("cannot follow yourself");
        }
        User publisher = userMapper.selectById(publisherUserId);
        if (publisher == null) {
            throw new IllegalArgumentException("publisher not found");
        }
    }

    private PublisherFollowStatusVO buildStatus(Long publisherUserId, boolean following) {
        PublisherFollowStatusVO vo = new PublisherFollowStatusVO();
        vo.setPublisherUserId(publisherUserId);
        vo.setFollowing(following);
        return vo;
    }
}
