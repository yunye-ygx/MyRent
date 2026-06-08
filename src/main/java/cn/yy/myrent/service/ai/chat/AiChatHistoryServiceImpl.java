package cn.yy.myrent.service.ai.chat;

import cn.yy.myrent.entity.AiChatMessage;
import cn.yy.myrent.entity.AiChatSession;
import cn.yy.myrent.mapper.AiChatMessageMapper;
import cn.yy.myrent.mapper.AiChatSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiChatHistoryServiceImpl implements AiChatHistoryService {

    private static final String ROLE_SUMMARY = "summary";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_TOOL = "tool";
    private static final String TOOL_SEARCH_HOUSES = "searchHouses";
    private static final String DEFAULT_SESSION_TITLE = "新会话";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;

    @Override
    public AiChatSession createSession(Long userId) {
        AiChatSession session = new AiChatSession();
        session.setUserId(userId);
        session.setTitle(DEFAULT_SESSION_TITLE);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public AiChatSession getOwnedSession(Long userId, Long sessionId) {
        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || userId == null || !userId.equals(session.getUserId())) {
            throw new IllegalStateException("会话不存在或无权访问");
        }
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
    public List<AiChatMessage> loadVisibleMessages(Long userId, Long sessionId, int limit) {
        getOwnedSession(userId, sessionId);

        return loadMessages(sessionId, limit).stream()
                .filter(message -> "user".equals(message.getRole())
                        || ("assistant".equals(message.getRole())
                        && !StringUtils.hasText(message.getToolName())
                        && StringUtils.hasText(message.getContent()))
                        || isVisibleHouseSearchResult(message))
                .collect(Collectors.toList());
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
    public void touchSession(Long sessionId, String latestUserMessage) {
        if (sessionId == null) {
            return;
        }

        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }

        session.setUpdateTime(LocalDateTime.now());
        if (!StringUtils.hasText(session.getTitle()) || DEFAULT_SESSION_TITLE.equals(session.getTitle())) {
            String titleSource = StringUtils.hasText(latestUserMessage) ? latestUserMessage.trim() : DEFAULT_SESSION_TITLE;
            session.setTitle(titleSource.length() > 24 ? titleSource.substring(0, 24) : titleSource);
        }
        sessionMapper.updateById(session);
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

    private boolean isVisibleHouseSearchResult(AiChatMessage message) {
        if (!ROLE_TOOL.equals(message.getRole())
                || !TOOL_SEARCH_HOUSES.equals(message.getToolName())
                || !StringUtils.hasText(message.getToolResult())) {
            return false;
        }

        try {
            JsonNode houses = OBJECT_MAPPER.readTree(message.getToolResult()).get("houses");
            return houses != null && houses.isArray() && !houses.isEmpty();
        } catch (Exception ex) {
            return false;
        }
    }
}
