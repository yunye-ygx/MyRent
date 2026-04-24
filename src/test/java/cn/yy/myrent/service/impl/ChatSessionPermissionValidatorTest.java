package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.House;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatSessionPermissionValidatorTest {

    @Test
    void shouldAllowRenterToStartConversationWithPublisher() {
        House house = new House().setId(7L).setPublisherUserId(9L).setStatus(1);

        assertDoesNotThrow(() -> ChatSessionPermissionValidator.validate(house, 1001L, 9L, false));
    }

    @Test
    void shouldRejectPublisherStartingConversationBeforeSessionExists() {
        House house = new House().setId(7L).setPublisherUserId(9L).setStatus(1);

        assertThrows(RuntimeException.class,
                () -> ChatSessionPermissionValidator.validate(house, 9L, 1001L, false));
    }

    @Test
    void shouldAllowPublisherReplyWhenSessionAlreadyExists() {
        House house = new House().setId(7L).setPublisherUserId(9L).setStatus(1);

        assertDoesNotThrow(() -> ChatSessionPermissionValidator.validate(house, 9L, 1001L, true));
    }

    @Test
    void shouldRejectExistingSessionMessageWhenPublisherIsNotInConversation() {
        House house = new House().setId(7L).setPublisherUserId(9L).setStatus(1);

        assertThrows(RuntimeException.class,
                () -> ChatSessionPermissionValidator.validate(house, 1001L, 1002L, true));
    }
}
