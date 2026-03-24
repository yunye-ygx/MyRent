package cn.yy.myrent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.yy.myrent.dto.MessageDTO;
import cn.yy.myrent.entity.ChatMessage;
import cn.yy.myrent.entity.ChatSession;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.ChatMessageMapper;
import cn.yy.myrent.mapper.ChatSessionMapper;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.service.IChatSessionService;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.vo.ChatSessionSummaryVO;
import cn.yy.myrent.websocket.ChatWebSocketSessionManager;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;

@Service
@Slf4j
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements IChatSessionService {

    private static final int HOUSE_STATUS_AVAILABLE = 1;
    private static final int HOUSE_STATUS_LOCKED = 2;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatWebSocketSessionManager sessionManager;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private HouseMapper houseMapper;

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

        validateSendMessageRequest(senderId, receiverId, houseId, content);

        House house = houseMapper.selectById(houseId);
        validateHouseAndReceiver(senderId, receiverId, house);

        User receiver = userMapper.selectById(receiverId);
        if (receiver == null) {
            throw new RuntimeException("接收方不存在");
        }

        String sessionId = buildSessionId(senderId, receiverId, houseId);
        LocalDateTime now = LocalDateTime.now();

        ChatSession chatSession = this.lambdaQuery()
                .eq(ChatSession::getSessionId, sessionId)
                .one();
        validateExistingSession(chatSession, senderId, receiverId, houseId);

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
        chatMessage.setSenderName(sender == null ? null : sender.getName());
        chatMessage.setReceiverName(receiver.getName());

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

    @Override
    public Page<ChatSessionSummaryVO> querySessionSummaries(Long userId, Long current, Long size) {
        long safeCurrent = Math.max(current == null ? 1L : current, 1L);
        long safeSize = Math.min(Math.max(size == null ? 10L : size, 1L), 100L);
        long offset = (safeCurrent - 1) * safeSize;

        long total = this.baseMapper.countSessionSummaries(userId);
        Page<ChatSessionSummaryVO> page = new Page<>(safeCurrent, safeSize, total);
        if (total <= 0L) {
            page.setRecords(Collections.emptyList());
            return page;
        }

        page.setRecords(this.baseMapper.selectSessionSummaries(userId, offset, safeSize));
        return page;
    }

    private void validateSendMessageRequest(Long senderId, Long receiverId, Long houseId, String content) {
        if (senderId == null || receiverId == null || houseId == null || houseId <= 0L || !StringUtils.hasText(content)) {
            log.warn("invalid send chat params, senderId={}, receiverId={}, houseId={}, hasContent={}",
                    senderId, receiverId, houseId, StringUtils.hasText(content));
            throw new RuntimeException("消息参数不能为空");
        }
        if (senderId.equals(receiverId)) {
            throw new RuntimeException("不能给自己发送消息");
        }
    }

    private void validateHouseAndReceiver(Long senderId, Long receiverId, House house) {
        if (house == null) {
            throw new RuntimeException("房源不存在");
        }
        if (!isChatEnabledHouseStatus(house.getStatus())) {
            throw new RuntimeException("当前房源状态不允许聊天");
        }
        if (house.getPublisherUserId() == null) {
            throw new RuntimeException("房源发布者信息缺失");
        }
        if (!receiverId.equals(house.getPublisherUserId())) {
            throw new RuntimeException("只允许联系当前房源发布者");
        }
        if (senderId.equals(house.getPublisherUserId())) {
            throw new RuntimeException("房源发布者不能主动发起会话");
        }
    }

    private void validateExistingSession(ChatSession chatSession, Long senderId, Long receiverId, Long houseId) {
        if (chatSession == null) {
            return;
        }

        long expectedUserId1 = Math.min(senderId, receiverId);
        long expectedUserId2 = Math.max(senderId, receiverId);
        boolean invalid = !Long.valueOf(expectedUserId1).equals(chatSession.getUserId1())
                || !Long.valueOf(expectedUserId2).equals(chatSession.getUserId2())
                || !houseId.equals(chatSession.getHouseId());
        if (invalid) {
            throw new RuntimeException("会话数据异常，无法发送消息");
        }
    }

    private boolean isChatEnabledHouseStatus(Integer status) {
        return status != null && (status == HOUSE_STATUS_AVAILABLE || status == HOUSE_STATUS_LOCKED);
    }

    private String buildSessionId(Long userId1, Long userId2, Long houseId) {
        long minUserId = Math.min(userId1, userId2);
        long maxUserId = Math.max(userId1, userId2);
        return minUserId + "_" + maxUserId + "_" + houseId;
    }
}
