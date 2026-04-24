package cn.yy.myrent.service.impl;

import cn.yy.myrent.dto.MessageDTO;
import cn.yy.myrent.entity.ChatMessage;
import cn.yy.myrent.entity.ChatSession;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.ChatMessageMapper;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.websocket.ChatWebSocketSessionManager;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceImplTest {

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private ChatWebSocketSessionManager sessionManager;

    @Mock
    private UserMapper userMapper;

    @Mock
    private HouseMapper houseMapper;

    @Mock
    private HouseHotService houseHotService;

    @InjectMocks
    private ChatSessionServiceImpl chatSessionService;

    @Test
    void sendMessageShouldRecoverWhenFirstSessionInsertHitsDuplicateKey() {
        MessageDTO messageDTO = new MessageDTO();
        messageDTO.setSenderId(1001L);
        messageDTO.setReceiverId(2002L);
        messageDTO.setHouseId(7L);
        messageDTO.setContent("hello");

        House house = new House().setId(7L).setPublisherUserId(2002L).setStatus(1);
        User sender = new User().setId(1001L).setName("sender");
        User receiver = new User().setId(2002L).setName("receiver");
        ChatSession existingSession = new ChatSession()
                .setSessionId("1001_2002_7")
                .setUserId1(1001L)
                .setUserId2(2002L)
                .setHouseId(7L);

        @SuppressWarnings("unchecked")
        LambdaQueryChainWrapper<ChatSession> queryChain =
                Mockito.mock(LambdaQueryChainWrapper.class, Answers.RETURNS_SELF);
        when(queryChain.eq(any(), any())).thenReturn(queryChain);
        when(queryChain.one()).thenReturn(null, existingSession);

        @SuppressWarnings("unchecked")
        LambdaUpdateChainWrapper<ChatSession> updateChain =
                Mockito.mock(LambdaUpdateChainWrapper.class, Answers.RETURNS_SELF);
        when(updateChain.set(any(), any())).thenReturn(updateChain);
        when(updateChain.eq(any(), any())).thenReturn(updateChain);
        when(updateChain.update()).thenReturn(true);

        ChatSessionServiceImpl serviceSpy = Mockito.spy(chatSessionService);
        doReturn(queryChain).when(serviceSpy).lambdaQuery();
        doReturn(updateChain).when(serviceSpy).lambdaUpdate();
        doThrow(new DuplicateKeyException("duplicate chat_session.session_id"))
                .when(serviceSpy).save(any(ChatSession.class));

        when(houseMapper.selectById(7L)).thenReturn(house);
        when(userMapper.selectById(2002L)).thenReturn(receiver);
        when(userMapper.selectById(1001L)).thenReturn(sender);
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            ChatMessage result = serviceSpy.sendMessage(messageDTO);

            assertNotNull(result.getId());
            assertEquals("1001_2002_7", result.getSessionId());
            assertEquals(1001L, result.getSenderId());
            assertEquals(2002L, result.getReceiverId());
            assertEquals("hello", result.getContent());
            assertEquals("sender", result.getSenderName());
            assertEquals("receiver", result.getReceiverName());

            verify(serviceSpy).save(any(ChatSession.class));
            verify(queryChain, times(2)).one();
            verify(updateChain).update();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
