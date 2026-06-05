package cn.yy.myrent.service.ai.chat;

import cn.yy.myrent.entity.AiChatMessage;
import cn.yy.myrent.entity.AiChatSession;
import java.util.List;

public interface AiChatHistoryService {

    AiChatSession getOrCreateSession(Long userId);

    List<AiChatSession> listSessions(Long userId);

    List<AiChatMessage> loadMessages(Long sessionId, int limit);

    void saveMessage(AiChatMessage message);

    void saveMessages(List<AiChatMessage> messages);
}
