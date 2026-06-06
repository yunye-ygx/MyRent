package cn.yy.myrent.service.ai.chat;

import cn.yy.myrent.entity.AiChatMessage;
import cn.yy.myrent.entity.AiChatSession;
import cn.yy.myrent.mapper.AiChatMessageMapper;
import cn.yy.myrent.mapper.AiChatSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiChatHistoryServiceImpl implements AiChatHistoryService {

    private static final String ROLE_SUMMARY = "summary";
    private static final String ROLE_ASSISTANT = "assistant";

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;

    @Override
    public AiChatSession getOrCreateSession(Long userId) {
        AiChatSession session = sessionMapper.selectOne(
                new LambdaQueryWrapper<AiChatSession>()
                        .eq(AiChatSession::getUserId, userId)
                        .orderByDesc(AiChatSession::getUpdateTime)
                        .last("LIMIT 1")
        );
        if (session != null) {
            return session;
        }
        session = new AiChatSession();
        session.setUserId(userId);
        session.setTitle("AI 找房助手");
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public List<AiChatSession> listSessions(Long userId) {
        return sessionMapper.selectList(
                new LambdaQueryWrapper<AiChatSession>()
                        .eq(AiChatSession::getUserId, userId)
                        .orderByDesc(AiChatSession::getUpdateTime)
        );
    }

    @Override
    public List<AiChatMessage> loadMessages(Long sessionId, int limit) {
        List<AiChatMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .orderByAsc(AiChatMessage::getId)
        );
        if (messages.size() <= limit) {
            return messages;
        }
        return new ArrayList<>(messages.subList(messages.size() - limit, messages.size()));
    }

    @Override
    public List<AiChatMessage> loadMessagesSinceLatestSummary(Long sessionId) {
        AiChatMessage latestSummary = findLatestSummaryMessage(sessionId);
        LambdaQueryWrapper<AiChatMessage> queryWrapper = new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId);
        if (latestSummary != null && latestSummary.getId() != null) {
            queryWrapper.ge(AiChatMessage::getId, latestSummary.getId());
        }
        queryWrapper.orderByAsc(AiChatMessage::getId);
        return messageMapper.selectList(queryWrapper);
    }

    @Override
    public int countCompletedRoundsSinceLatestSummary(Long sessionId) {
        AiChatMessage latestSummary = findLatestSummaryMessage(sessionId);
        LambdaQueryWrapper<AiChatMessage> queryWrapper = new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .eq(AiChatMessage::getRole, ROLE_ASSISTANT)
                .isNull(AiChatMessage::getToolName)
                .isNotNull(AiChatMessage::getContent);
        if (latestSummary != null && latestSummary.getId() != null) {
            queryWrapper.gt(AiChatMessage::getId, latestSummary.getId());
        }
        Long count = messageMapper.selectCount(queryWrapper);
        return count == null ? 0 : count.intValue();
    }

    @Override
    public void saveMessage(AiChatMessage message) {
        messageMapper.insert(message);
    }

    @Override
    public void saveMessages(List<AiChatMessage> messages) {
        for (AiChatMessage message : messages) {
            messageMapper.insert(message);
        }
    }

    private AiChatMessage findLatestSummaryMessage(Long sessionId) {
        return messageMapper.selectOne(
                new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .eq(AiChatMessage::getRole, ROLE_SUMMARY)
                        .orderByDesc(AiChatMessage::getId)
                        .last("LIMIT 1")
        );
    }
}
