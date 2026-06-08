package cn.yy.myrent.service.ai.chat;

import cn.yy.myrent.entity.AiChatMessage;
import cn.yy.myrent.entity.AiChatSession;
import cn.yy.myrent.service.ai.chat.tools.GetHouseDetailTool;
import cn.yy.myrent.service.ai.chat.tools.SearchHousesTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceImplTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private AiChatHistoryService historyService;

    @Mock
    private SearchHousesTool searchHousesTool;

    @Mock
    private GetHouseDetailTool getHouseDetailTool;

    @Mock
    private SseEmitter emitter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chatShouldStreamChunksAndPersistFinalAssistantText() throws IOException {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        AiChatSession session = session(11L, 7L);
        when(historyService.createSession(7L)).thenReturn(session);
        when(historyService.loadMessagesSinceLatestSummary(11L)).thenReturn(List.of(), List.of());
        when(historyService.countCompletedRoundsSinceLatestSummary(11L)).thenReturn(0);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolContext(any())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.chatClientResponse()).thenReturn(Flux.just(
                chatClientResponse("Hello, "),
                chatClientResponse("here are the matches.")
        ));
        doNothing().when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        AiChatServiceImpl aiChatService = service();
        aiChatService.chat(7L, "Find houses near Lujiazui", null, emitter);

        verify(emitter, timeout(2000).atLeast(3)).send(any(SseEmitter.SseEventBuilder.class));

        ArgumentCaptor<AiChatMessage> messageCaptor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(historyService, timeout(2000).times(2)).saveMessage(messageCaptor.capture());
        List<AiChatMessage> savedMessages = messageCaptor.getAllValues();
        assertEquals("user", savedMessages.get(0).getRole());
        assertEquals("Find houses near Lujiazui", savedMessages.get(0).getContent());
        assertEquals("assistant", savedMessages.get(1).getRole());
        assertEquals("Hello, here are the matches.", savedMessages.get(1).getContent());
    }

    @Test
    void chatShouldUseOwnedExistingSessionAndEmitSessionEvent() throws IOException {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        AiChatSession session = session(11L, 7L);
        when(historyService.getOwnedSession(7L, 11L)).thenReturn(session);
        when(historyService.loadMessagesSinceLatestSummary(11L)).thenReturn(List.of(), List.of());
        when(historyService.countCompletedRoundsSinceLatestSummary(11L)).thenReturn(0);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolContext(any())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.chatClientResponse()).thenReturn(Flux.just(chatClientResponse("ok")));
        doNothing().when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        AiChatServiceImpl aiChatService = service();
        aiChatService.chat(7L, "Continue this conversation", 11L, emitter);

        verify(historyService, timeout(2000)).getOwnedSession(7L, 11L);
        verify(emitter, timeout(2000).atLeast(3)).send(any(SseEmitter.SseEventBuilder.class));
        verify(historyService, timeout(2000).times(2)).saveMessage(any(AiChatMessage.class));
    }

    @Test
    void chatShouldReturnErrorWhenSessionDoesNotBelongToUser() throws IOException {
        when(historyService.getOwnedSession(7L, 11L)).thenThrow(new IllegalStateException("会话不存在或无权访问"));

        AiChatServiceImpl aiChatService = service();
        aiChatService.chat(7L, "Continue this conversation", 11L, emitter);

        verify(historyService, timeout(2000)).getOwnedSession(7L, 11L);
        verify(historyService, never()).saveMessage(any(AiChatMessage.class));
        verify(chatClientBuilder, never()).build();
    }

    @Test
    void chatShouldRejectNegativeBudgetBeforeInvokingModel() {
        AiChatSession session = session(11L, 7L);
        when(historyService.createSession(7L)).thenReturn(session);
        when(historyService.loadMessagesSinceLatestSummary(11L)).thenReturn(List.of(), List.of());
        when(historyService.countCompletedRoundsSinceLatestSummary(11L)).thenReturn(0);

        AiChatServiceImpl aiChatService = service();
        aiChatService.chat(7L, "Budget -5000 near office", null, emitter);

        ArgumentCaptor<AiChatMessage> messageCaptor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(historyService, timeout(2000).times(2)).saveMessage(messageCaptor.capture());
        List<AiChatMessage> savedMessages = messageCaptor.getAllValues();
        assertEquals("user", savedMessages.get(0).getRole());
        assertEquals("assistant", savedMessages.get(1).getRole());
        assertTrue(savedMessages.get(1).getContent().contains("预算金额不能为负数"));
        verify(chatClientBuilder, never()).build();
    }

    @Test
    void chatShouldIgnoreToolCallChunksWhenStreamingAssistantText() throws IOException {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        AiChatSession session = session(11L, 7L);
        when(historyService.createSession(7L)).thenReturn(session);
        when(historyService.loadMessagesSinceLatestSummary(11L)).thenReturn(List.of(), List.of());
        when(historyService.countCompletedRoundsSinceLatestSummary(11L)).thenReturn(0);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolContext(any())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.chatClientResponse()).thenReturn(Flux.just(
                toolCallResponse(),
                chatClientResponse("Found 2 houses, and the first one fits your budget and commute better.")
        ));
        doNothing().when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        AiChatServiceImpl aiChatService = service();
        aiChatService.chat(7L, "Find a shared flat near Lujiazui around 3500", null, emitter);

        ArgumentCaptor<AiChatMessage> messageCaptor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(historyService, timeout(2000).atLeast(2)).saveMessage(messageCaptor.capture());
        List<AiChatMessage> savedMessages = messageCaptor.getAllValues();
        AiChatMessage assistantMessage = savedMessages.stream()
                .filter(message -> "assistant".equals(message.getRole()) && message.getToolName() == null)
                .reduce((first, second) -> second)
                .orElseThrow();

        assertEquals("Found 2 houses, and the first one fits your budget and commute better.", assistantMessage.getContent());
        assertNull(assistantMessage.getToolName());
    }

    @Test
    void sendHousesEventIfPresentShouldEmitSearchHouseRecommendations() throws IOException {
        doNothing().when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        AiChatServiceImpl aiChatService = service();
        boolean emitted = aiChatService.sendHousesEventIfPresent(
                emitter,
                "searchHouses",
                """
                        {"ok":true,"count":1,"location":"陆家嘴","houses":[{"houseId":101,"title":"陆家嘴精装一居","priceYuan":3500,"rentMode":"整租","highlights":["近地铁"],"reasons":["月租贴近预算"]}]}
                        """
        );

        assertTrue(emitted);
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        assertFalse(aiChatService.sendHousesEventIfPresent(emitter, "getHouseDetail", "{\"ok\":true}"));
    }

    @Test
    void chatShouldUseLatestSummaryAndIncrementalMessagesOnly() throws IOException {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        AiChatSession session = session(11L, 7L);
        when(historyService.createSession(7L)).thenReturn(session);
        when(historyService.loadMessagesSinceLatestSummary(11L)).thenReturn(List.of(
                textMessage(11L, "summary", "summary snapshot"),
                textMessage(11L, "user", "recent question"),
                textMessage(11L, "assistant", "recent answer")
        ));
        when(historyService.countCompletedRoundsSinceLatestSummary(11L)).thenReturn(0);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolContext(any())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.chatClientResponse()).thenReturn(Flux.just(chatClientResponse("Need your office area first.")));
        doNothing().when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        AiChatServiceImpl aiChatService = service();
        aiChatService.chat(7L, "I want to live closer to my office", null, emitter);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(requestSpec, timeout(2000)).messages(messagesCaptor.capture());
        List<String> historyTexts = messagesCaptor.getValue().stream()
                .map(Message::getText)
                .toList();

        assertTrue(historyTexts.stream().anyMatch(text -> text.contains("summary snapshot")));
        assertTrue(historyTexts.stream().anyMatch(text -> text.contains("recent question")));
        assertTrue(historyTexts.stream().anyMatch(text -> text.contains("recent answer")));
        assertTrue(historyTexts.stream().noneMatch(text -> text.contains("old question")));
        assertTrue(historyTexts.stream().noneMatch(text -> text.contains("old answer")));
    }

    @Test
    void chatShouldPersistSummaryAfterTenthRound() throws IOException {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec mainRequestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);
        ChatClient.ChatClientRequestSpec summaryRequestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec summaryCallSpec = mock(ChatClient.CallResponseSpec.class);

        AiChatSession session = session(11L, 7L);
        when(historyService.createSession(7L)).thenReturn(session);
        when(historyService.loadMessagesSinceLatestSummary(11L)).thenReturn(
                nineCompletedRounds(11L),
                tenCompletedRounds(11L)
        );
        when(historyService.countCompletedRoundsSinceLatestSummary(11L)).thenReturn(10);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(mainRequestSpec, summaryRequestSpec);

        when(mainRequestSpec.messages(anyList())).thenReturn(mainRequestSpec);
        when(mainRequestSpec.user(anyString())).thenReturn(mainRequestSpec);
        when(mainRequestSpec.system(anyString())).thenReturn(mainRequestSpec);
        when(mainRequestSpec.toolContext(any())).thenReturn(mainRequestSpec);
        when(mainRequestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(mainRequestSpec);
        when(mainRequestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.chatClientResponse()).thenReturn(Flux.just(chatClientResponse("Please tell me your office station.")));

        when(summaryRequestSpec.messages(anyList())).thenReturn(summaryRequestSpec);
        when(summaryRequestSpec.system(anyString())).thenReturn(summaryRequestSpec);
        when(summaryRequestSpec.call()).thenReturn(summaryCallSpec);
        when(summaryCallSpec.content()).thenReturn("summary snapshot after ten rounds");

        doNothing().when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        AiChatServiceImpl aiChatService = service();
        aiChatService.chat(7L, "My office is near Century Avenue", null, emitter);

        ArgumentCaptor<AiChatMessage> messageCaptor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(historyService, timeout(2000).times(3)).saveMessage(messageCaptor.capture());
        List<AiChatMessage> savedMessages = messageCaptor.getAllValues();
        AiChatMessage summaryMessage = savedMessages.get(2);

        assertEquals("summary", summaryMessage.getRole());
        assertEquals("summary snapshot after ten rounds", summaryMessage.getContent());
    }

    private AiChatServiceImpl service() {
        return new AiChatServiceImpl(
                chatClientBuilder,
                historyService,
                searchHousesTool,
                getHouseDetailTool,
                objectMapper
        );
    }

    private AiChatSession session(Long sessionId, Long userId) {
        AiChatSession session = new AiChatSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setTitle("AI chat");
        return session;
    }

    private AiChatMessage textMessage(Long sessionId, String role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    private List<AiChatMessage> nineCompletedRounds(Long sessionId) {
        java.util.ArrayList<AiChatMessage> messages = new java.util.ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            messages.add(textMessage(sessionId, "user", "user round " + i));
            messages.add(textMessage(sessionId, "assistant", "assistant round " + i));
        }
        return messages;
    }

    private List<AiChatMessage> tenCompletedRounds(Long sessionId) {
        java.util.ArrayList<AiChatMessage> messages = new java.util.ArrayList<>(nineCompletedRounds(sessionId));
        messages.add(textMessage(sessionId, "user", "user round 10"));
        messages.add(textMessage(sessionId, "assistant", "assistant round 10"));
        return messages;
    }

    private ChatClientResponse chatClientResponse(String content) {
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(content)
                .build();
        return ChatClientResponse.builder()
                .chatResponse(ChatResponse.builder()
                        .metadata(new ChatResponseMetadata())
                        .generations(List.of(new Generation(assistantMessage)))
                        .build())
                .build();
    }

    private ChatClientResponse toolCallResponse() {
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "searchHouses",
                        "{\"locationName\":\"Lujiazui\",\"budgetYuan\":3500}"
                )))
                .build();
        return ChatClientResponse.builder()
                .chatResponse(ChatResponse.builder()
                        .metadata(new ChatResponseMetadata())
                        .generations(List.of(new Generation(assistantMessage)))
                        .build())
                .build();
    }
}
