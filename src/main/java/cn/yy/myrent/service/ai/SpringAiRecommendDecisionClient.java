package cn.yy.myrent.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(value = "myrent.ai.recommend.enabled", havingValue = "true")
@ConditionalOnProperty(value = "spring.ai.openai.chat.enabled", havingValue = "true")
public class SpringAiRecommendDecisionClient implements AiRecommendDecisionClient {

    private static final String SYSTEM_PROMPT = """
            你是租房推荐助手的决策层，不直接返回真实房源，只负责输出结构化决策。
            目标：
            1. 根据当前槽位和最近对话，判断下一步是 ASK、ADVISE 还是 SEARCH。
            2. 当信息不足时，优先 ASK，只追问 1 到 2 个最关键的问题。
            3. 只有在 budgetYuan、rentMode、locationName 都可用时，才允许输出 SEARCH。
            4. ADVISE 只能给高层建议，不能假装已经查过真实房源。
            5. slots 字段只填写你本轮能确认的新状态；未知字段保留为空或不填。
            6. 输出必须严格符合给定 JSON 结构，不要追加解释文本。
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public SpringAiRecommendDecisionClient(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AiRecommendDecision decide(AiRecommendSessionState sessionState, String userMessage) {
        BeanOutputConverter<AiRecommendDecision> outputConverter = new BeanOutputConverter<>(AiRecommendDecision.class);
        String prompt = """
                当前会话状态：
                %s

                最近对话：
                %s

                用户本轮消息：
                %s

                请直接返回结构化 JSON：
                %s
                """.formatted(
                toJson(sessionState.getSlots()),
                formatHistory(sessionState.getHistory()),
                userMessage,
                outputConverter.getFormat()
        );

        AiRecommendDecision decision = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(prompt)
                .call()
                .entity(outputConverter);
        if (decision == null) {
            throw new IllegalStateException("spring ai decision is null");
        }
        return decision;
    }

    private String formatHistory(List<AiRecommendTurn> history) {
        if (history == null || history.isEmpty()) {
            return "[]";
        }
        return history.stream()
                .map(turn -> "%s: %s".formatted(turn.getRole(), turn.getContent()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("[]");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("serialize ai recommend context failed", ex);
        }
    }
}
