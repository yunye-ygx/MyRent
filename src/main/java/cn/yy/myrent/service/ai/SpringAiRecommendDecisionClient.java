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

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AiRecommendPromptBundle promptBundle;

    public SpringAiRecommendDecisionClient(ChatClient.Builder chatClientBuilder,
                                           ObjectMapper objectMapper,
                                           AiRecommendPromptLoader promptLoader) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.promptBundle = promptLoader.load();
    }

    @Override
    public AiRecommendDecision decide(AiRecommendSessionState sessionState, String userMessage) {
        BeanOutputConverter<AiRecommendDecision> outputConverter = new BeanOutputConverter<>(AiRecommendDecision.class);
        String prompt = promptBundle.userContextTemplate()
                .replace("${slots}", toJson(sessionState.getSlots()))
                .replace("${summary}", safeText(sessionState.getSummary()))
                .replace("${recentHistory}", formatHistory(sessionState.getHistory()))
                .replace("${userMessage}", userMessage)
                .replace("${format}", promptBundle.outputFormatPrompt() + "\n" + outputConverter.getFormat());

        AiRecommendDecision decision = chatClient.prompt()
                .system(promptBundle.systemPrompt())
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

    private String safeText(String text) {
        return text == null ? "" : text;
    }
}
