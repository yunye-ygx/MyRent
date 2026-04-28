package cn.yy.myrent.service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRecommendPromptLoaderTest {

    private static final String[] REQUIRED_SLOT_KEYS = {
            "\"city\"",
            "\"locationName\"",
            "\"budgetYuan\"",
            "\"budgetScope\"",
            "\"rentMode\"",
            "\"priority\"",
            "\"preferences\""
    };

    @Test
    void loadShouldReturnPromptBundleWithAllTemplates() {
        AiRecommendPromptLoader loader = new AiRecommendPromptLoader();

        AiRecommendPromptBundle bundle = loader.load();

        assertFalse(bundle.systemPrompt().isBlank());
        assertFalse(bundle.userContextTemplate().isBlank());
        assertFalse(bundle.outputFormatPrompt().isBlank());
        assertTrue(bundle.userContextTemplate().contains("${slots}"));
        assertTrue(bundle.userContextTemplate().contains("${summary}"));
        assertTrue(bundle.userContextTemplate().contains("${recentHistory}"));
        assertTrue(bundle.userContextTemplate().contains("${userMessage}"));
        assertTrue(bundle.userContextTemplate().contains("${format}"));
        assertTrue(bundle.outputFormatPrompt().contains("\"reply\""));
        assertTrue(bundle.outputFormatPrompt().contains("\"slots\""));
        for (String slotKey : REQUIRED_SLOT_KEYS) {
            assertTrue(bundle.outputFormatPrompt().contains(slotKey));
        }
    }

    @Test
    void loadShouldCacheAndReturnSameBundleInstance() {
        AiRecommendPromptLoader loader = new AiRecommendPromptLoader();

        AiRecommendPromptBundle first = loader.load();
        AiRecommendPromptBundle second = loader.load();

        assertSame(first, second);
    }

    @Test
    void constructorShouldFailWhenAResourceIsMissing() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new AiRecommendPromptLoader(
                        "prompts/ai-recommend/missing-system.txt",
                        "prompts/ai-recommend/user-context.txt",
                        "prompts/ai-recommend/output-format.txt"));

        assertTrue(ex.getMessage().contains("missing-system.txt"));
    }

    @Test
    void constructorShouldFailWhenAResourceCannotBeRead() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new AiRecommendPromptLoader(path -> {
                    if (path.endsWith("system.txt")) {
                        throw new IllegalStateException("boom");
                    }
                    return "ok";
                }));

        assertEquals("failed to load ai recommend prompt: prompts/ai-recommend/system.txt", ex.getMessage());
        assertEquals("boom", ex.getCause().getMessage());
    }
}
