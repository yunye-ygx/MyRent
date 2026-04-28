package cn.yy.myrent.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(value = "myrent.ai.recommend.enabled", havingValue = "true")
public class RedisAiRecommendStateStore implements AiRecommendStateStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final int historyLimit;

    @Autowired
    public RedisAiRecommendStateStore(StringRedisTemplate stringRedisTemplate,
                                      ObjectMapper objectMapper,
                                      @Value("${myrent.ai.recommend.state-ttl-hours:48}") long ttlHours,
                                      @Value("${myrent.ai.recommend.history-limit:10}") int historyLimit) {
        this(stringRedisTemplate, objectMapper, Duration.ofHours(ttlHours), historyLimit);
    }

    RedisAiRecommendStateStore(StringRedisTemplate stringRedisTemplate,
                               ObjectMapper objectMapper,
                               Duration ttl) {
        this(stringRedisTemplate, objectMapper, ttl, 10);
    }

    RedisAiRecommendStateStore(StringRedisTemplate stringRedisTemplate,
                               ObjectMapper objectMapper,
                               Duration ttl,
                               int historyLimit) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
        this.historyLimit = historyLimit;
    }

    @Override
    public AiRecommendSessionState loadOrCreate(Long userId) {
        try {
            String stateJson = stringRedisTemplate.opsForValue().get(stateKey(userId));
            String historyJson = stringRedisTemplate.opsForValue().get(historyKey(userId));
            LoadedState loadedState = readState(userId, stateJson);
            if (loadedState == null || loadedState.state() == null) {
                return AiRecommendSessionState.empty(userId);
            }
            AiRecommendSessionState state = loadedState.state();
            if (loadedState.canUseStandaloneHistory()
                    && (state.getHistory() == null || state.getHistory().isEmpty())
                    && StringUtils.hasText(historyJson)) {
                state.setHistory(readHistory(historyJson));
            }
            state.setHistory(trimHistory(state.getHistory()));
            return state;
        } catch (Exception ex) {
            return AiRecommendSessionState.empty(userId);
        }
    }

    @Override
    public void save(AiRecommendSessionState state) {
        Long userId = resolveUserId(state);
        try {
            List<AiRecommendTurn> trimmedHistory = trimHistory(state.getHistory());
            AiRecommendSessionState stateToSave = AiRecommendSessionState.builder()
                    .userId(userId)
                    .sessionId(StringUtils.hasText(state.getSessionId())
                            ? state.getSessionId()
                            : AiRecommendSessionState.buildSessionId(userId))
                    .slots(state.getSlots() == null
                            ? AiRecommendSlots.builder().preferences(new ArrayList<>()).build()
                            : state.getSlots())
                    .history(trimmedHistory)
                    .build();
            stringRedisTemplate.opsForValue().set(stateKey(userId), objectMapper.writeValueAsString(stateToSave), ttl);
            stringRedisTemplate.opsForValue().set(historyKey(userId), objectMapper.writeValueAsString(trimmedHistory), ttl);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to save ai recommend state", ex);
        }
    }

    @Override
    public void reset(Long userId) {
        stringRedisTemplate.delete(stateKey(userId));
        stringRedisTemplate.delete(historyKey(userId));
    }

    private Long resolveUserId(AiRecommendSessionState state) {
        if (state.getUserId() != null) {
            return state.getUserId();
        }
        String sessionId = state.getSessionId();
        if (StringUtils.hasText(sessionId) && sessionId.startsWith("ai-u")) {
            return Long.parseLong(sessionId.substring(4));
        }
        throw new IllegalArgumentException("userId is required");
    }

    private LoadedState readState(Long userId, String stateJson) throws Exception {
        if (!StringUtils.hasText(stateJson)) {
            return null;
        }
        try {
            AiRecommendSessionState state = objectMapper.readValue(stateJson, AiRecommendSessionState.class);
            if (state.getUserId() == null) {
                state.setUserId(userId);
            }
            if (!StringUtils.hasText(state.getSessionId())) {
                state.setSessionId(AiRecommendSessionState.buildSessionId(userId));
            }
            if (state.getSlots() == null) {
                state.setSlots(AiRecommendSlots.builder().preferences(new ArrayList<>()).build());
            }
            if (state.getHistory() == null) {
                state.setHistory(new ArrayList<>());
            }
            return new LoadedState(state, true);
        } catch (Exception ex) {
            AiRecommendSlots slots = objectMapper.readValue(stateJson, AiRecommendSlots.class);
            AiRecommendSessionState state = AiRecommendSessionState.empty(userId);
            state.setSlots(slots == null ? state.getSlots() : slots);
            return new LoadedState(state, false);
        }
    }

    private List<AiRecommendTurn> readHistory(String historyJson) throws Exception {
        List<AiRecommendTurn> history = objectMapper.readValue(historyJson, new TypeReference<List<AiRecommendTurn>>() {
        });
        return history == null ? new ArrayList<>() : history;
    }

    private List<AiRecommendTurn> trimHistory(List<AiRecommendTurn> history) {
        List<AiRecommendTurn> safeHistory = history == null ? new ArrayList<>() : history;
        int limit = Math.max(historyLimit, 1);
        if (safeHistory.size() <= limit) {
            return new ArrayList<>(safeHistory);
        }
        return new ArrayList<>(safeHistory.subList(safeHistory.size() - limit, safeHistory.size()));
    }

    private String stateKey(Long userId) {
        return "ai:recommend:state:" + userId;
    }

    private String historyKey(Long userId) {
        return "ai:recommend:history:" + userId;
    }

    private record LoadedState(AiRecommendSessionState state, boolean canUseStandaloneHistory) {
    }
}
