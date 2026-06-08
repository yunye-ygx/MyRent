package cn.yy.myrent.service.ai.chat;

import cn.yy.myrent.entity.AiChatMessage;
import cn.yy.myrent.entity.AiChatSession;
import java.util.List;

public interface AiChatHistoryService {

    AiChatSession createSession(Long userId);

    AiChatSession getOwnedSession(Long userId, Long sessionId);

    List<AiChatSession> listSessions(Long userId);

    List<AiChatMessage> loadMessages(Long sessionId, int limit);

    List<AiChatMessage> loadVisibleMessages(Long userId, Long sessionId, int limit);

    List<AiChatMessage> loadMessagesSinceLatestSummary(Long sessionId);

    int countCompletedRoundsSinceLatestSummary(Long sessionId);

    void touchSession(Long sessionId, String latestUserMessage);

    void saveMessage(AiChatMessage message);

    void saveMessages(List<AiChatMessage> messages);
}
