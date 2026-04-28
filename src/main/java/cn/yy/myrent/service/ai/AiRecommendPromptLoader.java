package cn.yy.myrent.service.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Component
public class AiRecommendPromptLoader {

    private static final String BASE_PATH = "prompts/ai-recommend/";
    private static final String SYSTEM_PROMPT_PATH = BASE_PATH + "system.txt";
    private static final String USER_CONTEXT_PATH = BASE_PATH + "user-context.txt";
    private static final String OUTPUT_FORMAT_PATH = BASE_PATH + "output-format.txt";

    private final AiRecommendPromptBundle bundle;

    public AiRecommendPromptLoader() {
        this(readClasspathResource(), SYSTEM_PROMPT_PATH, USER_CONTEXT_PATH, OUTPUT_FORMAT_PATH);
    }

    AiRecommendPromptLoader(String systemPromptPath, String userContextPath, String outputFormatPath) {
        this(readClasspathResource(), systemPromptPath, userContextPath, outputFormatPath);
    }

    AiRecommendPromptLoader(ResourceReader resourceReader) {
        this(resourceReader, SYSTEM_PROMPT_PATH, USER_CONTEXT_PATH, OUTPUT_FORMAT_PATH);
    }

    private AiRecommendPromptLoader(ResourceReader resourceReader,
                                    String systemPromptPath,
                                    String userContextPath,
                                    String outputFormatPath) {
        Objects.requireNonNull(resourceReader, "resourceReader");
        this.bundle = new AiRecommendPromptBundle(
                loadResource(resourceReader, systemPromptPath),
                loadResource(resourceReader, userContextPath),
                loadResource(resourceReader, outputFormatPath)
        );
    }

    public AiRecommendPromptBundle load() {
        return bundle;
    }

    private static ResourceReader readClasspathResource() {
        return path -> {
            ClassPathResource resource = new ClassPathResource(path);
            try (InputStream inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        };
    }

    private String loadResource(ResourceReader resourceReader, String path) {
        try {
            return resourceReader.read(path);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to load ai recommend prompt: " + path, ex);
        }
    }

    @FunctionalInterface
    interface ResourceReader {
        String read(String path) throws IOException;
    }
}
