package cn.yy.myrent.service.ai.chat;

import cn.yy.myrent.entity.AiChatMessage;
import cn.yy.myrent.entity.AiChatSession;
import cn.yy.myrent.service.ai.chat.tools.GetHouseDetailTool;
import cn.yy.myrent.service.ai.chat.tools.SearchHousesTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private static final int SUMMARY_TRIGGER_ROUNDS = 10;
    private static final Pattern NEGATIVE_BUDGET_PATTERN = Pattern.compile("(?<!\\d)-\\s*(\\d{2,6})(?!\\d)");

    private static final String SYSTEM_PROMPT = """
            你是 Roam，一个专业但自然的租房助手。你的目标不是把所有偏好一次问完，而是优先补齐最影响找房结果的关键信息，再逐步推进到推荐。

            核心行为要求：
            1. 像懂租房的朋友一样对话，不要机械罗列，不要像表单机器人。
            2. 信息不够时先追问，但每次优先问“当前最关键、最影响搜索结果”的一个问题，不要连续追问很多次次要偏好。
            3. 不要猜测用户没说过的条件；用户没有明确表达的偏好，宁可先留空，也不要擅自补全。
            4. 只有在用户明确想找房、看推荐、筛具体房源时，才调用 searchHouses。
            5. 只有在用户明确追问某套房详情时，才调用 getHouseDetail。
            6. 工具返回 errorCode 时，先根据错误继续追问或纠正，不要假装成功。
            7. 推荐房源时，只能依据工具返回的真实结果，不要虚构房源。
            8. 解释推荐理由时，优先使用工具返回的 reasons 和 reasonCodes，把它们自然转成口语化表达。
            9. 如果工具返回 count=0，先说明当前条件下没有结果，再建议用户调整预算、区域或配套条件。
            10. 回复保持简洁，优先帮助用户推进到下一步。

            追问优先级规则：
            1. 如果用户表达了“离公司近”“通勤方便”“上班近”“靠近工作地”这类通勤导向需求，但没有给出公司位置、办公区、地铁站、商圈或地标，那么最先追问的必须是工作地点锚点。
            2. 在这种通勤导向场景下，不要先问卫浴、阳台、民水民电、装修风格这类次级偏好。
            3. 如果用户已经给了预算，但没给区域或通勤锚点，优先补位置，不要继续细问配套。
            4. 如果用户只给了大区域，比如“浦东”“闵行”，可以继续问通勤地、地铁线、商圈或地标，让位置更可搜索。
            5. 只有当位置锚点已经足够支持搜索后，才去追问整租/合租、独卫、阳台、民水民电等细项。

            语义理解要求：
            1. “不用整租”“不一定整租”“可以合租”都表示：用户不强求整租，可以接受合租；不要反着理解。
            2. “离公司近一点”“通勤方便”本质上是位置约束，不是配套偏好。
            3. 用户只说模糊偏好时，先把它归类成预算、位置、租住方式、配套四类中的一类，再决定下一问。

            少量示例：
            1. 用户说：“我想住得离公司近一点，不用整租，预算5000”
               正确做法：先问“你公司在哪个区、哪条地铁线，或者附近哪个地标？”
               错误做法：先问独卫、阳台、民水民电。
            2. 用户说：“预算3500，想在浦东租房”
               正确做法：先问“浦东范围比较大，你更想靠近哪一带，或者通勤目的地在哪？”
            3. 用户说：“想看陆家嘴附近 4000 左右的合租”
               正确做法：这时位置和预算已经基本够了，可以直接调用 searchHouses，没必要先追问次要偏好。
            """;

    private static final String SUMMARY_PROMPT = """
            你是对话上下文压缩助手。请把下面的租房对话压缩成一条可持续复用的上下文摘要，供后续聊天直接使用。

            要求：
            1. 只保留对后续找房真正有用的信息，不要写寒暄和废话。
            2. 明确区分：用户硬约束、已确认偏好、明确拒绝的条件、最近推荐结论、仍待确认的问题。
            3. 如果出现过工具搜索或推荐结果，只保留有决策价值的结论，不要原样抄长 JSON。
            4. 如果信息互相冲突，以最近一次用户明确表达为准。
            5. 输出必须简洁、结构化、稳定，方便后续模型继续理解。

            输出格式固定为：
            用户硬约束:
            已确认偏好:
            明确排斥:
            最近结论:
            待确认:
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
                AiChatSession session = (sessionId != null)
                        ? historyService.getOwnedSession(userId, sessionId)
                        : historyService.createSession(userId);
                Long actualSessionId = session.getId();
                sendSessionEvent(emitter, actualSessionId);

                List<AiChatMessage> incrementalMessages = historyService.loadMessagesSinceLatestSummary(actualSessionId);
                List<Message> historyMessages = buildHistory(incrementalMessages);
                saveUserMessage(actualSessionId, userMessage);
                historyService.touchSession(actualSessionId, userMessage);

                ValidationIssue validationIssue = validateUserMessage(userMessage);
                if (validationIssue != null) {
                    String assistantReply = validationIssue.reply();
                    saveAssistantMessage(actualSessionId, assistantReply);
                    maybeSummarizeConversation(actualSessionId, null);
                    sendTextChunk(emitter, assistantReply);
                    sendSse(emitter, "done", "{}");
                    emitter.complete();
                    return;
                }

                List<ToolExecutionRecord> toolExecutions = Collections.synchronizedList(new LinkedList<>());
                List<ToolCallSnapshot> toolCalls = new ArrayList<>();
                ToolCallback[] toolCallbacks = buildRecordingToolCallbacks(toolExecutions);

                ChatClient chatClient = chatClientBuilder.build();
                StringBuilder assistantText = new StringBuilder();

                chatClient.prompt()
                        .messages(historyMessages)
                        .user(userMessage)
                        .system(SYSTEM_PROMPT)
                        .toolContext(Map.of(
                                "rawUserMessage", userMessage,
                                "sessionId", actualSessionId
                        ))
                        .toolCallbacks(toolCallbacks)
                        .stream()
                        .chatClientResponse()
                        .doOnNext(response -> handleChatClientResponse(response, assistantText, toolCalls, emitter))
                        .blockLast();

                persistToolInteractions(actualSessionId, toolCalls, toolExecutions);
                emitSearchHouseResults(toolExecutions, emitter);

                if (assistantText.length() > 0) {
                    saveAssistantMessage(actualSessionId, assistantText.toString());
                    maybeSummarizeConversation(actualSessionId, chatClient);
                }

                sendSse(emitter, "done", "{}");
                emitter.complete();
            } catch (Exception e) {
                log.error("AI chat failed for userId={}", userId, e);
                try {
                    sendSse(emitter, "error",
                            objectMapper.writeValueAsString(new ErrorEvent("抱歉，出了一点问题，请稍后再试。")));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });
    }

    private ValidationIssue validateUserMessage(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return null;
        }
        Matcher matcher = NEGATIVE_BUDGET_PATTERN.matcher(userMessage);
        if (!matcher.find()) {
            return null;
        }
        return new ValidationIssue("INVALID_BUDGET",
                "预算金额不能为负数。你可以直接告诉我一个大于 0 的预算，比如 3500 元。");
    }

    private ToolCallback[] buildRecordingToolCallbacks(List<ToolExecutionRecord> toolExecutions) {
        ToolCallback[] callbacks = ToolCallbacks.from(searchHousesTool, getHouseDetailTool);
        ToolCallback[] wrappedCallbacks = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            wrappedCallbacks[i] = new RecordingToolCallback(callbacks[i], toolExecutions);
        }
        return wrappedCallbacks;
    }

    private void handleChatClientResponse(ChatClientResponse response,
                                          StringBuilder assistantText,
                                          List<ToolCallSnapshot> toolCalls,
                                          SseEmitter emitter) {
        if (response == null || response.chatResponse() == null) {
            return;
        }

        Generation generation = response.chatResponse().getResult();
        if (generation == null || generation.getOutput() == null) {
            return;
        }

        AssistantMessage assistantMessage = generation.getOutput();
        if (assistantMessage.hasToolCalls()) {
            collectToolCalls(assistantMessage, toolCalls);
        }

        String text = assistantMessage.getText();
        if (!StringUtils.hasText(text)) {
            return;
        }

        assistantText.append(text);
        sendTextChunk(emitter, text);
    }

    private void collectToolCalls(AssistantMessage assistantMessage, List<ToolCallSnapshot> toolCalls) {
        for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
            if (toolCall == null || !StringUtils.hasText(toolCall.name())) {
                continue;
            }
            toolCalls.add(new ToolCallSnapshot(
                    StringUtils.hasText(toolCall.id()) ? toolCall.id() : UUID.randomUUID().toString(),
                    toolCall.name(),
                    toolCall.arguments()
            ));
        }
    }

    private void persistToolInteractions(Long sessionId,
                                         List<ToolCallSnapshot> toolCalls,
                                         List<ToolExecutionRecord> toolExecutions) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }

        List<ToolExecutionRecord> executionPool = new ArrayList<>(toolExecutions == null ? List.of() : toolExecutions);
        List<AiChatMessage> messages = new ArrayList<>();

        for (ToolCallSnapshot toolCall : toolCalls) {
            ToolExecutionRecord matchedExecution = matchExecution(toolCall, executionPool);
            messages.add(buildAssistantToolCallMessage(sessionId, toolCall));
            messages.add(buildToolResultMessage(sessionId, toolCall, matchedExecution));
        }

        historyService.saveMessages(messages);
    }

    void emitSearchHouseResults(List<ToolExecutionRecord> toolExecutions, SseEmitter emitter) {
        if (toolExecutions == null || toolExecutions.isEmpty()) {
            return;
        }
        for (ToolExecutionRecord toolExecution : toolExecutions) {
            sendHousesEventIfPresent(emitter, toolExecution.toolName(), toolExecution.result());
        }
    }

    boolean sendHousesEventIfPresent(SseEmitter emitter, String toolName, String toolResult) {
        if (!"searchHouses".equals(toolName) || !StringUtils.hasText(toolResult)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(toolResult);
            JsonNode houses = root.get("houses");
            if (houses == null || !houses.isArray() || houses.isEmpty()) {
                return false;
            }

            sendSse(emitter, "houses", objectMapper.writeValueAsString(new HousesEvent(
                    root.path("location").asText(""),
                    houses
            )));
            return true;
        } catch (Exception ex) {
            log.warn("Failed to stream house recommendation cards, toolName={}", toolName, ex);
            return false;
        }
    }

    private ToolExecutionRecord matchExecution(ToolCallSnapshot toolCall, List<ToolExecutionRecord> executionPool) {
        if (executionPool == null || executionPool.isEmpty()) {
            return null;
        }

        for (int i = 0; i < executionPool.size(); i++) {
            ToolExecutionRecord candidate = executionPool.get(i);
            if (Objects.equals(candidate.toolName(), toolCall.toolName())
                    && Objects.equals(normalizeJson(candidate.arguments()), normalizeJson(toolCall.arguments()))) {
                executionPool.remove(i);
                return candidate;
            }
        }

        for (int i = 0; i < executionPool.size(); i++) {
            ToolExecutionRecord candidate = executionPool.get(i);
            if (Objects.equals(candidate.toolName(), toolCall.toolName())) {
                executionPool.remove(i);
                return candidate;
            }
        }
        return null;
    }

    private String normalizeJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json).toString();
        } catch (Exception ex) {
            return json;
        }
    }

    private void maybeSummarizeConversation(Long sessionId, ChatClient currentChatClient) {
        try {
            if (historyService.countCompletedRoundsSinceLatestSummary(sessionId) < SUMMARY_TRIGGER_ROUNDS) {
                return;
            }

            List<AiChatMessage> summarySourceMessages = historyService.loadMessagesSinceLatestSummary(sessionId);
            if (summarySourceMessages.isEmpty()) {
                return;
            }

            ChatClient chatClient = currentChatClient != null ? currentChatClient : chatClientBuilder.build();
            String summaryContent = chatClient.prompt()
                    .messages(List.of(new UserMessage(buildSummaryInput(summarySourceMessages))))
                    .system(SUMMARY_PROMPT)
                    .call()
                    .content();

            if (!StringUtils.hasText(summaryContent)) {
                return;
            }

            AiChatMessage summaryMessage = baseMessage(sessionId, "summary");
            summaryMessage.setContent(summaryContent);
            historyService.saveMessage(summaryMessage);
        } catch (Exception ex) {
            log.warn("Failed to summarize chat session {}", sessionId, ex);
        }
    }

    private AiChatMessage buildAssistantToolCallMessage(Long sessionId, ToolCallSnapshot toolCall) {
        AiChatMessage message = baseMessage(sessionId, "assistant");
        message.setToolName(toolCall.toolName());
        message.setToolCallId(toolCall.toolCallId());
        message.setToolParams(toolCall.arguments());
        return message;
    }

    private AiChatMessage buildToolResultMessage(Long sessionId,
                                                 ToolCallSnapshot toolCall,
                                                 ToolExecutionRecord executionRecord) {
        AiChatMessage message = baseMessage(sessionId, "tool");
        message.setToolName(toolCall.toolName());
        message.setToolCallId(toolCall.toolCallId());
        message.setToolParams(toolCall.arguments());
        if (executionRecord != null) {
            message.setToolResult(executionRecord.result());
        }
        return message;
    }

    private void saveUserMessage(Long sessionId, String userMessage) {
        AiChatMessage userMessageEntity = baseMessage(sessionId, "user");
        userMessageEntity.setContent(userMessage);
        historyService.saveMessage(userMessageEntity);
    }

    private void saveAssistantMessage(Long sessionId, String assistantText) {
        AiChatMessage assistantMessage = baseMessage(sessionId, "assistant");
        assistantMessage.setContent(assistantText);
        historyService.saveMessage(assistantMessage);
    }

    private AiChatMessage baseMessage(Long sessionId, String role) {
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    private AiChatSession resolveExistingSession(Long userId, Long sessionId) {
        AiChatSession session = new AiChatSession();
        session.setId(sessionId);
        session.setUserId(userId);
        return session;
    }

    private List<Message> buildHistory(List<AiChatMessage> promptSourceMessages) {
        List<Message> messages = new ArrayList<>();
        for (AiChatMessage message : promptSourceMessages) {
            appendPromptMessage(messages, message);
        }
        return messages;
    }

    private void appendPromptMessage(List<Message> promptMessages, AiChatMessage message) {
        if ("summary".equals(message.getRole()) && StringUtils.hasText(message.getContent())) {
            promptMessages.add(new SystemMessage("以下是此前对话摘要，请将其视为已经确认的上下文：\n" + message.getContent()));
            return;
        }
        if ("user".equals(message.getRole()) && StringUtils.hasText(message.getContent())) {
            promptMessages.add(new UserMessage(message.getContent()));
            return;
        }
        if ("assistant".equals(message.getRole()) && message.getToolName() == null && StringUtils.hasText(message.getContent())) {
            promptMessages.add(new AssistantMessage(message.getContent()));
            return;
        }
        if ("assistant".equals(message.getRole()) && StringUtils.hasText(message.getToolName())) {
            promptMessages.add(AssistantMessage.builder()
                    .content(StringUtils.hasText(message.getContent()) ? message.getContent() : "")
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                            StringUtils.hasText(message.getToolCallId()) ? message.getToolCallId() : UUID.randomUUID().toString(),
                            "function",
                            message.getToolName(),
                            message.getToolParams()
                    )))
                    .build());
            return;
        }
        if ("tool".equals(message.getRole()) && StringUtils.hasText(message.getToolName()) && StringUtils.hasText(message.getToolResult())) {
            promptMessages.add(ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                            StringUtils.hasText(message.getToolCallId()) ? message.getToolCallId() : UUID.randomUUID().toString(),
                            message.getToolName(),
                            message.getToolResult()
                    )))
                    .build());
        }
    }

    private String buildSummaryInput(List<AiChatMessage> summarySourceMessages) {
        StringBuilder builder = new StringBuilder();
        for (AiChatMessage message : summarySourceMessages) {
            if ("summary".equals(message.getRole()) && StringUtils.hasText(message.getContent())) {
                builder.append("已有摘要: ").append(message.getContent()).append('\n');
                continue;
            }
            if ("user".equals(message.getRole()) && StringUtils.hasText(message.getContent())) {
                builder.append("用户: ").append(message.getContent()).append('\n');
                continue;
            }
            if ("assistant".equals(message.getRole())
                    && message.getToolName() == null
                    && StringUtils.hasText(message.getContent())) {
                builder.append("助手: ").append(message.getContent()).append('\n');
                continue;
            }
            if ("assistant".equals(message.getRole()) && StringUtils.hasText(message.getToolName())) {
                builder.append("工具调用(")
                        .append(message.getToolName())
                        .append("): ")
                        .append(message.getToolParams())
                        .append('\n');
                continue;
            }
            if ("tool".equals(message.getRole()) && StringUtils.hasText(message.getToolName()) && StringUtils.hasText(message.getToolResult())) {
                builder.append("工具结果(")
                        .append(message.getToolName())
                        .append("): ")
                        .append(message.getToolResult())
                        .append('\n');
            }
        }
        return builder.toString();
    }

    private void sendTextChunk(SseEmitter emitter, String chunk) {
        try {
            sendSse(emitter, "text", objectMapper.writeValueAsString(new TextContent(chunk)));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to stream AI text chunk", e);
        }
    }

    private void sendSessionEvent(SseEmitter emitter, Long sessionId) {
        try {
            sendSse(emitter, "session", objectMapper.writeValueAsString(new SessionEvent(sessionId)));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to stream AI session event", e);
        }
    }

    private void sendSse(SseEmitter emitter, String eventName, String data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private record TextContent(String content) {
    }

    private record SessionEvent(Long sessionId) {
    }

    private record HousesEvent(String location, JsonNode houses) {
    }

    private record ErrorEvent(String message) {
    }

    private record ValidationIssue(String code, String reply) {
    }

    private record ToolCallSnapshot(String toolCallId, String toolName, String arguments) {
    }

    private record ToolExecutionRecord(String toolName, String arguments, String result) {
    }

    private static final class RecordingToolCallback implements ToolCallback {

        private final ToolCallback delegate;
        private final List<ToolExecutionRecord> toolExecutions;

        private RecordingToolCallback(ToolCallback delegate, List<ToolExecutionRecord> toolExecutions) {
            this.delegate = delegate;
            this.toolExecutions = toolExecutions;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String arguments) {
            String result = delegate.call(arguments);
            record(arguments, result);
            return result;
        }

        @Override
        public String call(String arguments, ToolContext toolContext) {
            String result = delegate.call(arguments, toolContext);
            record(arguments, result);
            return result;
        }

        private void record(String arguments, String result) {
            toolExecutions.add(new ToolExecutionRecord(
                    delegate.getToolDefinition().name(),
                    arguments,
                    result
            ));
        }
    }
}
