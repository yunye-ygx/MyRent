package cn.yy.myrent.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
/**
 * WebSocket 在线会话管理器（面向小白版说明）。
 *
 * 这是后端的“在线用户通讯录”：
 * - register：用户上线时登记连接
 * - unregister：用户下线时移除连接
 * - sendToUser：按 userId 给在线用户推送消息
 */
public class ChatWebSocketSessionManager {

    /**
     * userId -> 该用户所有连接。
     *
     * 为什么是 Set？因为一个用户可能多个端同时在线。
     */
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> userSessionMap = new ConcurrentHashMap<>();

    /**
     * sessionId -> userId 的反向索引。
     *
     * 断开连接时，通常先拿到 session，再反查到 userId。
     */
    private final ConcurrentHashMap<String, Long> sessionUserMap = new ConcurrentHashMap<>();

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 用户建立连接后调用：登记在线。
     */
    public void register(Long userId, WebSocketSession session) {
        userSessionMap.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
        sessionUserMap.put(session.getId(), userId);
    }

    /**
     * 用户断开连接后调用：清理在线状态。
     */
    public void unregister(WebSocketSession session) {
        Long userId = sessionUserMap.remove(session.getId());
        if (userId == null) {
            return;
        }

        Set<WebSocketSession> sessions = userSessionMap.get(userId);
        if (sessions == null) {
            return;
        }

        sessions.remove(session);
        if (sessions.isEmpty()) {
            userSessionMap.remove(userId);
        }
    }

    /**
     * 向指定用户推送消息。
     *
     * @param userId 接收方ID
     * @param payload 任意对象，会被转换为 JSON 文本
     */
    public void sendToUser(Long userId, Object payload) {
        Set<WebSocketSession> sessions = userSessionMap.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String jsonText;
        try {
            jsonText = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("websocket push serialization failed, userId={}", userId, e);
            return;
        }

        TextMessage textMessage = new TextMessage(jsonText);

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                unregister(session);
                continue;
            }

            try {
                synchronized (session) {
                    session.sendMessage(textMessage);
                }
            } catch (Exception e) {
                unregister(session);
                closeQuietly(session);
                log.warn("websocket push failed, userId={}, sessionId={}", userId, session.getId(), e);
            }
        }
    }

    /**
     * 安静关闭连接：失败时不再向上抛异常。
     */
    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (IOException ignored) {
        }
    }
}

