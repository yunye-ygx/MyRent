package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.PublisherFollow;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.PublisherFollowMapper;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.vo.PublisherFollowStatusVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublisherFollowServiceImplTest {

    @Mock
    private PublisherFollowMapper publisherFollowMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private PublisherFollowServiceImpl publisherFollowService;

    @Test
    void followShouldCreateActiveRelation() {
        when(userMapper.selectById(9L)).thenReturn(new User().setId(9L).setName("Publisher"));
        when(publisherFollowMapper.selectOne(any())).thenReturn(null);
        when(publisherFollowMapper.insert(any(PublisherFollow.class))).thenReturn(1);

        PublisherFollowStatusVO result = publisherFollowService.follow(9L, 1001L);

        assertTrue(result.getFollowing());
    }

    @Test
    void unfollowShouldReturnInactiveState() {
        PublisherFollow relation = new PublisherFollow()
                .setId(7L)
                .setUserId(1001L)
                .setPublisherUserId(9L)
                .setStatus(1);
        when(userMapper.selectById(9L)).thenReturn(new User().setId(9L).setName("Publisher"));
        when(publisherFollowMapper.selectOne(any())).thenReturn(relation);
        when(publisherFollowMapper.updateById(any(PublisherFollow.class))).thenReturn(1);

        PublisherFollowStatusVO result = publisherFollowService.unfollow(9L, 1001L);

        assertFalse(result.getFollowing());
    }
}
