package cn.yy.myrent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.yy.myrent.dto.MessageDTO;
import cn.yy.myrent.entity.ChatMessage;
import cn.yy.myrent.entity.ChatSession;
import cn.yy.myrent.mapper.ChatMessageMapper;
import cn.yy.myrent.mapper.ChatSessionMapper;
import cn.yy.myrent.service.IChatSessionService;
import cn.yy.myrent.websocket.ChatWebSocketSessionManager;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * <p>
 * 聊天会话列表 服务实现类
 * </p>
 *
 * @author yy
 * @since 2026-03-12
 */
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements IChatSessionService {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatWebSocketSessionManager sessionManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendMessage(MessageDTO messageDTO) {
        Long senderId = messageDTO.getSenderId();
        Long receiverId = messageDTO.getReceiverId();
        String content = messageDTO.getContent();

        if (senderId == null || receiverId == null || !StringUtils.hasText(content)) {
            throw new RuntimeException("消息参数不能为空");
        }

        String sessionId = buildSessionId(senderId, receiverId);
        LocalDateTime now = LocalDateTime.now();

        ChatSession chatSession = this.lambdaQuery()
                .eq(ChatSession::getSessionId, sessionId)
                .one();

        if (chatSession == null) {
            long userId1 = Math.min(senderId, receiverId);
            long userId2 = Math.max(senderId, receiverId);

            ChatSession newSession = new ChatSession();
            newSession.setSessionId(sessionId);
            newSession.setUserId1(userId1);
            newSession.setUserId2(userId2);
            newSession.setLastMsgContent(content);
            newSession.setCreateTime(now);
            newSession.setUpdateTime(now);

            boolean savedSession = this.save(newSession);
            if (!savedSession) {
                throw new RuntimeException("创建会话失败");
            }
        } else {
            boolean updatedSession = this.lambdaUpdate()
                    .set(ChatSession::getLastMsgContent, content)
                    .set(ChatSession::getUpdateTime, now)
                    .eq(ChatSession::getSessionId, sessionId)
                    .update();
            if (!updatedSession) {
                throw new RuntimeException("更新会话失败");
            }
        }

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId(IdUtil.getSnowflakeNextId());
        chatMessage.setSessionId(sessionId);
        chatMessage.setSenderId(senderId);
        chatMessage.setReceiverId(receiverId);
        chatMessage.setMsgType(1);
        chatMessage.setContent(content);
        chatMessage.setStatus(0);
        chatMessage.setCreateTime(now);

        int rows = chatMessageMapper.insert(chatMessage);
        if (rows != 1) {
            throw new RuntimeException("发送消息失败");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessionManager.sendToUser(receiverId, chatMessage);
            }
        });
    }

    private String buildSessionId(Long userId1, Long userId2) {
        long minUserId = Math.min(userId1, userId2);
        long maxUserId = Math.max(userId1, userId2);
        return minUserId + "_" + maxUserId;
    }
}

