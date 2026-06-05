package cn.yy.myrent.service.ai.chat;

import cn.yy.myrent.entity.AiChatMessage;
import cn.yy.myrent.entity.AiChatSession;
import cn.yy.myrent.service.ai.chat.tools.GetHouseDetailTool;
import cn.yy.myrent.service.ai.chat.tools.SearchHousesTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private static final int HISTORY_LIMIT = 20;
    private static final int MAX_TOOL_ROUNDS = 5;

    private static final String SYSTEM_PROMPT = """
            你是 Roam，一个专业的租房助手。你通过自然对话帮用户找到合适的房子。

            ## 行为准则

            1. 像一个懂行的朋友一样对话，不要像填表机器人
            2. 信息不足时，自然地追问并给建议。比如用户说"浦东"，你可以说"浦东很大，你是通勤优先还是环境优先？"
            3. 不要一口气问完所有信息，在对话中自然地逐步了解
            4. 当你判断用户想看具体房源时，调用 searchHouses 工具。搜索前最好先给用户预期管理
            5. 搜到结果后，用口语化的方式总结推荐理由，不要列清单
            6. 如果搜索结果为空，建议用户调整条件
            7. 用户问到某套房详情时，调用 getHouseDetail
            8. 你只能推荐系统中真实存在的房源，不能编造
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final AiChatHistoryService historyService;
    private final SearchHousesTool searchHousesTool;
    private final GetHouseDetailTool getHouseDetailTool;
    private final ObjectMapper objectMapper;

    @Override
    public void chat(Long userId, String userMessage, Long sessionId, SseEmitter emitter) {
        CompletableFuture.runAsync(() -> {
            try {
                // 1. Resolve session
                AiChatSession session = (sessionId != null)
                        ? resolveExistingSession(userId, sessionId)
                        : historyService.getOrCreateSession(userId);
                Long actualSessionId = session.getId();

                // 2. Save user message to DB
                AiChatMessage userMsg = new AiChatMessage();
                userMsg.setSessionId(actualSessionId);
                userMsg.setRole("user");
                userMsg.setContent(userMessage);
                userMsg.setCreateTime(LocalDateTime.now());
                historyService.saveMessage(userMsg);

                // Update session title from first message
                if ("AI 找房助手".equals(session.getTitle())) {
                    String title = userMessage.length() > 50
                            ? userMessage.substring(0, 50) + "..."
                            : userMessage;
                    session.setTitle(title);
                }

                // 3. Load history and build initial messages
                List<Message> messageAccumulator = new ArrayList<>();
                messageAccumulator.addAll(buildHistory(actualSessionId));
                messageAccumulator.add(new UserMessage(userMessage));

                // 4. Tool calling loop
                ChatClient chatClient = chatClientBuilder.build();
                int toolRounds = 0;

                while (toolRounds < MAX_TOOL_ROUNDS) {
                    Prompt prompt = new Prompt(messageAccumulator);
                    ChatResponse response = chatClient.prompt(prompt)
                            .system(SYSTEM_PROMPT)
                            .tools(searchHousesTool, getHouseDetailTool)
                            .call()
                            .chatResponse();

                    AssistantMessage assistantMsg = response.getResult().getOutput();
                    messageAccumulator.add(assistantMsg);

                    List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
                    if (toolCalls == null || toolCalls.isEmpty()) {
                        // Final text response
                        String finalText = assistantMsg.getText();
                        sendSse(emitter, "text",
                                objectMapper.writeValueAsString(new TextContent(finalText)));

                        AiChatMessage assistantDbMsg = new AiChatMessage();
                        assistantDbMsg.setSessionId(actualSessionId);
                        assistantDbMsg.setRole("assistant");
                        assistantDbMsg.setContent(finalText);
                        assistantDbMsg.setCreateTime(LocalDateTime.now());
                        historyService.saveMessage(assistantDbMsg);
                        break;
                    }

                    // Execute tool calls
                    toolRounds++;
                    for (AssistantMessage.ToolCall toolCall : toolCalls) {
                        String toolName = toolCall.name();
                        String toolArgs = toolCall.arguments();

                        sendSse(emitter, "tool_call",
                                objectMapper.writeValueAsString(new ToolCallEvent(toolName, toolArgs)));

                        String toolResultJson = executeTool(toolName, toolArgs);

                        sendSse(emitter, "tool_result",
                                objectMapper.writeValueAsString(new ToolResultEvent(toolName, toolResultJson)));

                        // Add tool response to message accumulator
                        ToolResponseMessage toolResponseMsg = ToolResponseMessage.builder()
                                .responses(List.of(new ToolResponseMessage.ToolResponse(
                                        toolCall.id(), toolName, toolResultJson)))
                                .build();
                        messageAccumulator.add(toolResponseMsg);

                        // Save tool call record to DB
                        AiChatMessage toolCallDb = new AiChatMessage();
                        toolCallDb.setSessionId(actualSessionId);
                        toolCallDb.setRole("assistant");
                        toolCallDb.setToolName(toolName);
                        toolCallDb.setToolCallId(toolCall.id());
                        toolCallDb.setToolParams(toolArgs);
                        toolCallDb.setCreateTime(LocalDateTime.now());
                        historyService.saveMessage(toolCallDb);

                        // Save tool result to DB
                        AiChatMessage toolResultDb = new AiChatMessage();
                        toolResultDb.setSessionId(actualSessionId);
                        toolResultDb.setRole("tool");
                        toolResultDb.setContent(toolResultJson);
                        toolResultDb.setToolName(toolName);
                        toolResultDb.setToolCallId(toolCall.id());
                        toolResultDb.setToolResult(toolResultJson);
                        toolResultDb.setCreateTime(LocalDateTime.now());
                        historyService.saveMessage(toolResultDb);
                    }
                }

                sendSse(emitter, "done", "{}");
                emitter.complete();

            } catch (Exception e) {
                log.error("AI chat failed for userId={}", userId, e);
                try {
                    sendSse(emitter, "error",
                            objectMapper.writeValueAsString(
                                    new ErrorEvent("抱歉，出了点问题，请稍后再试。")));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });
    }

    private AiChatSession resolveExistingSession(Long userId, Long sessionId) {
        AiChatSession session = new AiChatSession();
        session.setId(sessionId);
        session.setUserId(userId);
        return session;
    }

    private List<Message> buildHistory(Long sessionId) {
        List<AiChatMessage> dbMessages = historyService.loadMessages(sessionId, HISTORY_LIMIT);
        List<Message> messages = new ArrayList<>();
        for (AiChatMessage msg : dbMessages) {
            switch (msg.getRole()) {
                case "user" -> messages.add(new UserMessage(msg.getContent()));
                case "assistant" -> {
                    if (msg.getToolName() == null && msg.getContent() != null) {
                        messages.add(new AssistantMessage(msg.getContent()));
                    }
                }
                // "tool" role messages are skipped in history rebuild
            }
        }
        return messages;
    }

    private String executeTool(String toolName, String toolArgsJson) {
        try {
            JsonNode params = objectMapper.readTree(toolArgsJson);
            return switch (toolName) {
                case "searchHouses" -> searchHousesTool.searchHouses(
                        getTextOrNull(params, "locationName"),
                        getIntOrNull(params, "budgetYuan"),
                        getTextOrNull(params, "rentMode"),
                        getBoolOrNull(params, "nearSubway"),
                        getBoolOrNull(params, "privateBathroom"),
                        getBoolOrNull(params, "hasBalcony"),
                        getBoolOrNull(params, "civilWaterElectric"),
                        getIntOrNull(params, "limit"));
                case "getHouseDetail" -> getHouseDetailTool.getHouseDetail(
                        params.has("houseId") ? params.get("houseId").asLong() : null);
                default -> "{\"error\":\"unknown tool: " + toolName + "\"}";
            };
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            return "{\"error\":\"工具执行失败\"}";
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private Integer getIntOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : null;
    }

    private Boolean getBoolOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asBoolean() : null;
    }

    private void sendSse(SseEmitter emitter, String eventName, String data) throws java.io.IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    // DTOs for SSE events
    private record TextContent(String content) {}

    private record ToolCallEvent(String tool, String params) {}

    private record ToolResultEvent(String tool, String result) {}

    private record ErrorEvent(String message) {}
}
