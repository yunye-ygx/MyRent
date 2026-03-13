package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.ChatMessage;
import cn.yy.myrent.mapper.ChatMessageMapper;
import cn.yy.myrent.service.IChatMessageService;
import cn.yy.myrent.vo.ChatPullVO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements IChatMessageService {

    private static final int DEFAULT_PULL_LIMIT = 50;
    private static final int MAX_PULL_LIMIT = 200;

    @Override
    public ChatPullVO pullNewMessages(Long userId, Long lastMessageId, String sessionId, Integer limit) {
        long cursor = lastMessageId == null ? 0L : Math.max(lastMessageId, 0L);
        int safeLimit = normalizeLimit(limit);

        List<ChatMessage> messages = this.lambdaQuery()
                .eq(ChatMessage::getReceiverId, userId)
                .eq(StringUtils.hasText(sessionId), ChatMessage::getSessionId, sessionId)
                .gt(ChatMessage::getId, cursor)
                .orderByAsc(ChatMessage::getId)
                .last("limit " + safeLimit)
                .list();

        long nextCursor = cursor;
        if (!messages.isEmpty()) {
            nextCursor = messages.get(messages.size() - 1).getId();
        }

        ChatPullVO response = new ChatPullVO();
        response.setMessages(messages);
        response.setNextCursor(nextCursor);
        response.setHasMore(messages.size() >= safeLimit);
        return response;
    }

    @Override
    public int markMessagesRead(Long userId, String sessionId, Long upToMessageId) {
        if (userId == null || !StringUtils.hasText(sessionId) || upToMessageId == null || upToMessageId <= 0L) {
            return 0;
        }

        LambdaUpdateWrapper<ChatMessage> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(ChatMessage::getStatus, 1)
                .eq(ChatMessage::getReceiverId, userId)
                .eq(ChatMessage::getSessionId, sessionId)
                .le(ChatMessage::getId, upToMessageId)
                .eq(ChatMessage::getStatus, 0);
        return this.baseMapper.update(null, updateWrapper);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PULL_LIMIT;
        }
        return Math.min(limit, MAX_PULL_LIMIT);
    }
}
