package cn.yy.myrent.service.ai.chat;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiChatService {
    void chat(Long userId, String message, Long sessionId, SseEmitter emitter);
}
