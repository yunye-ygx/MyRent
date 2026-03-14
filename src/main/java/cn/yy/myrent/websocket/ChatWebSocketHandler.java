package cn.yy.myrent.websocket;

import cn.yy.myrent.common.JwtTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@Component
/**
 * WebSocket 连接生命周期处理器（面向小白版说明）。
 *
 * 你可以把它理解成“连接门卫”：
 * 1) 用户连上来时，校验并记录 userId。
 * 2) 用户断开时，清理在线记录。
 * 3) 网络异常时，也做清理，避免脏连接。
 */
public class ChatWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private ChatWebSocketSessionManager sessionManager;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    /**
     * 握手成功后会进入这里。
     *
     * 前端连接示例：
     * ws://localhost:8080/ws/chat?userId=1001
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = resolveUserId(session.getUri());
        if (userId == null) {
            session.close(CloseStatus.BAD_DATA.withReason("token is required"));
            return;
        }

        sessionManager.register(userId, session);
        log.info("websocket connected, userId={}, sessionId={}", userId, session.getId());
    }

    /**
     * 连接关闭时调用（用户主动退出、页面关闭、网络断开等）。
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.unregister(session);
        log.info("websocket closed, sessionId={}, status={}", session.getId(), status);
    }

    /**
     * 连接传输异常时调用。
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessionManager.unregister(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * 从 URL query 参数中解析 userId。
     *
     * 例如：/ws/chat?userId=2 -> 2
     */
    private Long resolveUserId(URI uri) {
        if (uri == null) {
            return null;
        }

        String token = UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst("token");

        if (!StringUtils.hasText(token)) {
            return null;
        }

        try {
            return jwtTokenUtil.parseUserId(token);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
