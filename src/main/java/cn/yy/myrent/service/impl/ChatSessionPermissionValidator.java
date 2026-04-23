package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.House;

final class ChatSessionPermissionValidator {

    private static final int HOUSE_STATUS_AVAILABLE = 1;
    private static final int HOUSE_STATUS_LOCKED = 2;

    private ChatSessionPermissionValidator() {
    }

    static void validate(House house, Long senderId, Long receiverId, boolean sessionExists) {
        if (house == null) {
            throw new RuntimeException("房源不存在");
        }
        if (!isChatEnabledHouseStatus(house.getStatus())) {
            throw new RuntimeException("当前房源状态不允许聊天");
        }
        if (house.getPublisherUserId() == null) {
            throw new RuntimeException("房源发布者信息缺失");
        }

        Long publisherUserId = house.getPublisherUserId();
        boolean senderIsPublisher = publisherUserId.equals(senderId);
        boolean receiverIsPublisher = publisherUserId.equals(receiverId);

        if (!senderIsPublisher && !receiverIsPublisher) {
            throw new RuntimeException("只允许联系当前房源发布者");
        }

        if (!sessionExists) {
            if (!receiverIsPublisher) {
                throw new RuntimeException("只允许联系当前房源发布者");
            }
            if (senderIsPublisher) {
                throw new RuntimeException("房源发布者不能主动发起会话");
            }
        }
    }

    private static boolean isChatEnabledHouseStatus(Integer status) {
        return status != null && (status == HOUSE_STATUS_AVAILABLE || status == HOUSE_STATUS_LOCKED);
    }
}
