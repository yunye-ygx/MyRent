package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.ChatMessage;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.ChatMessageMapper;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.service.IChatMessageService;
import cn.yy.myrent.vo.ChatPullVO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements IChatMessageService {

    private static final int DEFAULT_PULL_LIMIT = 50;
    private static final int MAX_PULL_LIMIT = 200;

    @Autowired
    private UserMapper userMapper;

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
        fillUserNames(messages);

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
    public ChatPullVO pullHistoryMessages(Long userId, String sessionId, Long beforeMessageId, Integer limit) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            ChatPullVO empty = new ChatPullVO();
            empty.setMessages(Collections.emptyList());
            empty.setNextCursor(beforeMessageId);
            empty.setHasMore(false);
            return empty;
        }

        int safeLimit = normalizeLimit(limit);

        List<ChatMessage> messages = this.lambdaQuery()
                .eq(ChatMessage::getSessionId, sessionId)
                .and(wrapper -> wrapper.eq(ChatMessage::getSenderId, userId)
                        .or()
                        .eq(ChatMessage::getReceiverId, userId))
                .lt(beforeMessageId != null, ChatMessage::getId, beforeMessageId)
                .orderByDesc(ChatMessage::getId)
                .last("limit " + (safeLimit + 1))
                .list();

        boolean hasMore = messages.size() > safeLimit;
        if (hasMore) {
            messages = new ArrayList<>(messages.subList(0, safeLimit));
        }

        Collections.reverse(messages);
        fillUserNames(messages);

        Long nextCursor = beforeMessageId;
        if (!messages.isEmpty()) {
            nextCursor = messages.get(0).getId();
        }

        ChatPullVO response = new ChatPullVO();
        response.setMessages(messages);
        response.setNextCursor(nextCursor);
        response.setHasMore(hasMore);
        return response;
    }

    @Override
    public void fillUserNames(List<ChatMessage> messages) {
        attachUserNames(messages);
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

    private void attachUserNames(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        Set<Long> userIds = new HashSet<>();
        for (ChatMessage message : messages) {
            if (message.getSenderId() != null) {
                userIds.add(message.getSenderId());
            }
            if (message.getReceiverId() != null) {
                userIds.add(message.getReceiverId());
            }
        }
        if (userIds.isEmpty()) {
            return;
        }

        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, String> userNameMap = new HashMap<>();
        for (User user : users) {
            userNameMap.put(user.getId(), user.getName());
        }

        for (ChatMessage message : messages) {
            message.setSenderName(userNameMap.get(message.getSenderId()));
            message.setReceiverName(userNameMap.get(message.getReceiverId()));
        }
    }
}
