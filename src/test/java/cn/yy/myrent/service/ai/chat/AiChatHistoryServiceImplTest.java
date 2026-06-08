package cn.yy.myrent.service.ai.chat;

import cn.yy.myrent.entity.AiChatMessage;
import cn.yy.myrent.entity.AiChatSession;
import cn.yy.myrent.mapper.AiChatMessageMapper;
import cn.yy.myrent.mapper.AiChatSessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatHistoryServiceImplTest {

    @Mock
    private AiChatSessionMapper sessionMapper;

    @Mock
    private AiChatMessageMapper messageMapper;

    @InjectMocks
    private AiChatHistoryServiceImpl historyService;

    @Test
    void createSessionShouldAlwaysInsertFreshConversation() {
        when(sessionMapper.insert(any(AiChatSession.class))).thenAnswer(invocation -> {
            AiChatSession session = invocation.getArgument(0);
            session.setId(22L);
            return 1;
        });

        AiChatSession result = historyService.createSession(7L);

        ArgumentCaptor<AiChatSession> captor = ArgumentCaptor.forClass(AiChatSession.class);
        verify(sessionMapper).insert(captor.capture());
        AiChatSession saved = captor.getValue();

        assertEquals(7L, saved.getUserId());
        assertEquals("新会话", saved.getTitle());
        assertTrue(saved.getCreateTime() != null);
        assertTrue(saved.getUpdateTime() != null);
        assertEquals(22L, result.getId());
    }

    @Test
    void loadVisibleMessagesShouldKeepSearchHouseToolResultsForCardRecovery() {
        AiChatMessage user = message(11L, "user", "预算 3500，想在浦东整租");
        AiChatMessage assistant = message(11L, "assistant", "普通回复");
        AiChatMessage searchHousesTool = message(11L, "tool", null);
        searchHousesTool.setToolName("searchHouses");
        searchHousesTool.setToolResult("""
                {"count":1,"houses":[{"houseId":101,"title":"陆家嘴精装一居"}]}
                """);
        AiChatMessage detailTool = message(11L, "tool", null);
        detailTool.setToolName("getHouseDetail");
        detailTool.setToolResult("{\"houseId\":101}");
        AiChatMessage summary = message(11L, "summary", "摘要");

        when(sessionMapper.selectById(11L)).thenReturn(session(11L, 7L));
        when(messageMapper.selectList(any())).thenReturn(List.of(user, searchHousesTool, detailTool, assistant, summary));

        List<AiChatMessage> result = historyService.loadVisibleMessages(7L, 11L, 100);

        assertEquals(3, result.size());
        assertEquals("user", result.get(0).getRole());
        assertEquals("tool", result.get(1).getRole());
        assertEquals("searchHouses", result.get(1).getToolName());
        assertEquals("assistant", result.get(2).getRole());
        assertEquals("普通回复", result.get(2).getContent());
    }

    @Test
    void getOwnedSessionShouldRejectSessionFromAnotherUser() {
        when(sessionMapper.selectById(11L)).thenReturn(session(11L, 99L));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> historyService.getOwnedSession(7L, 11L)
        );

        assertEquals("会话不存在或无权访问", exception.getMessage());
    }

    @Test
    void touchSessionShouldPromoteUserMessageToTitleAndRefreshUpdateTime() {
        AiChatSession session = session(11L, 7L);
        session.setTitle("新会话");

        when(sessionMapper.selectById(11L)).thenReturn(session);

        historyService.touchSession(11L, "预算 3500，想在浦东整租，最好近地铁");

        ArgumentCaptor<AiChatSession> captor = ArgumentCaptor.forClass(AiChatSession.class);
        verify(sessionMapper).updateById(captor.capture());
        AiChatSession updated = captor.getValue();

        String expectedTitle = "预算 3500，想在浦东整租，最好近地铁";
        if (expectedTitle.length() > 24) {
            expectedTitle = expectedTitle.substring(0, 24);
        }
        assertEquals(expectedTitle, updated.getTitle());
        assertTrue(updated.getUpdateTime() != null);
    }

    private AiChatSession session(Long id, Long userId) {
        AiChatSession session = new AiChatSession();
        session.setId(id);
        session.setUserId(userId);
        session.setTitle("AI 会话");
        session.setUpdateTime(LocalDateTime.now());
        return session;
    }

    private AiChatMessage message(Long sessionId, String role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        return message;
    }
}
