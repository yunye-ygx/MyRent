package cn.yy.myrent.service;

import cn.yy.myrent.vo.PublisherFollowStatusVO;

public interface IPublisherFollowService {

    PublisherFollowStatusVO follow(Long publisherUserId, Long userId);

    PublisherFollowStatusVO unfollow(Long publisherUserId, Long userId);

    PublisherFollowStatusVO getStatus(Long publisherUserId, Long userId);
}
