package cn.yy.myrent.service.ai;

public record AiRecommendPromptBundle(
        String systemPrompt,
        String userContextTemplate,
        String outputFormatPrompt
) {
}
