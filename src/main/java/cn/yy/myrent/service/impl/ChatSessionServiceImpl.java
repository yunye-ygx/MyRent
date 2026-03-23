package cn.yy.myrent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.yy.myrent.dto.MessageDTO;
import cn.yy.myrent.entity.ChatMessage;
import cn.yy.myrent.entity.ChatSession;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.ChatMessageMapper;
import cn.yy.myrent.mapper.ChatSessionMapper;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.service.IChatSessionService;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.websocket.ChatWebSocketSessionManager;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@Slf4j
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements IChatSessionService {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatWebSocketSessionManager sessionManager;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private HouseHotService houseHotService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessage sendMessage(MessageDTO messageDTO) {
        Long senderId = messageDTO.getSenderId();
        Long receiverId = messageDTO.getReceiverId();
        Long houseId = messageDTO.getHouseId();
        String content = messageDTO.getContent();

        log.info("start send chat message, senderId={}, receiverId={}, houseId={}, contentLength={}",
                senderId, receiverId, houseId, content == null ? 0 : content.length());

        if (senderId == null || receiverId == null || houseId == null || houseId <= 0L || !StringUtils.hasText(content)) {
            log.warn("invalid send chat params, senderId={}, receiverId={}, houseId={}, hasContent={}",
                    senderId, receiverId, houseId, StringUtils.hasText(content));
            throw new RuntimeException("消息参数不能为空");
        }

        String sessionId = buildSessionId(senderId, receiverId, houseId);
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
            newSession.setHouseId(houseId);
            newSession.setLastMsgContent(content);
            newSession.setCreateTime(now);
            newSession.setUpdateTime(now);

            boolean savedSession = this.save(newSession);
            if (!savedSession) {
                log.error("create chat session failed, sessionId={}, senderId={}, receiverId={}, houseId={}",
                        sessionId, senderId, receiverId, houseId);
                throw new RuntimeException("创建会话失败");
            }
            log.info("create chat session success, sessionId={}, userId1={}, userId2={}, houseId={}",
                    sessionId, userId1, userId2, houseId);
        } else {
            boolean updatedSession = this.lambdaUpdate()
                    .set(ChatSession::getHouseId, houseId)
                    .set(ChatSession::getLastMsgContent, content)
                    .set(ChatSession::getUpdateTime, now)
                    .eq(ChatSession::getSessionId, sessionId)
                    .update();
            if (!updatedSession) {
                log.error("update chat session failed, sessionId={}, senderId={}, receiverId={}, houseId={}",
                        sessionId, senderId, receiverId, houseId);
                throw new RuntimeException("更新会话失败");
            }
            log.debug("update chat session success, sessionId={}, houseId={}", sessionId, houseId);
        }

        ChatMessage chatMessage = new ChatMessage()
                .setId(IdUtil.getSnowflakeNextId())
                .setSessionId(sessionId)
                .setSenderId(senderId)
                .setReceiverId(receiverId)
                .setMsgType(1)
                .setContent(content)
                .setStatus(0)
                .setCreateTime(now);

        int rows = chatMessageMapper.insert(chatMessage);
        if (rows != 1) {
            log.error("insert chat message failed, sessionId={}, senderId={}, receiverId={}, houseId={}, rows={}",
                    sessionId, senderId, receiverId, houseId, rows);
            throw new RuntimeException("发送消息失败");
        }
        log.info("insert chat message success, messageId={}, sessionId={}, houseId={}",
                chatMessage.getId(), sessionId, houseId);

        User sender = userMapper.selectById(senderId);
        User receiver = userMapper.selectById(receiverId);
        chatMessage.setSenderName(sender == null ? null : sender.getName());
        chatMessage.setReceiverName(receiver == null ? null : receiver.getName());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    houseHotService.recordChatInteraction(houseId, senderId, receiverId);
                } catch (Exception e) {
                    log.error("record chat interaction for hot rank failed, messageId={}, houseId={}",
                            chatMessage.getId(), houseId, e);
                }

                try {
                    sessionManager.sendToUser(receiverId, chatMessage);
                    log.info("push chat message after commit success, messageId={}, receiverId={}",
                            chatMessage.getId(), receiverId);
                } catch (Exception e) {
                    log.error("push chat message after commit failed, messageId={}, receiverId={}",
                            chatMessage.getId(), receiverId, e);
                }
            }
        });

        return chatMessage;
    }

    private String buildSessionId(Long userId1, Long userId2, Long houseId) {
        long minUserId = Math.min(userId1, userId2);
        long maxUserId = Math.max(userId1, userId2);
        return minUserId + "_" + maxUserId + "_" + houseId;
    }
}
