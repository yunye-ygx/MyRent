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

    private static final int STORED_HISTORY_LIMIT = 30;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    @Autowired
    public RedisAiRecommendStateStore(StringRedisTemplate stringRedisTemplate,
                                      ObjectMapper objectMapper,
                                      @Value("${myrent.ai.recommend.state-ttl-hours:48}") long ttlHours) {
        this(stringRedisTemplate, objectMapper, Duration.ofHours(ttlHours));
    }

    RedisAiRecommendStateStore(StringRedisTemplate stringRedisTemplate,
                               ObjectMapper objectMapper,
                               Duration ttl) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public AiRecommendSessionState loadOrCreate(Long userId) {
        try {
            String slotsJson = stringRedisTemplate.opsForValue().get(slotsKey(userId));
            String historyJson = stringRedisTemplate.opsForValue().get(historyKey(userId));
            String summaryText = stringRedisTemplate.opsForValue().get(summaryKey(userId));
            String stageText = stringRedisTemplate.opsForValue().get(stageKey(userId));
            if (!StringUtils.hasText(slotsJson)
                    && !StringUtils.hasText(historyJson)
                    && !StringUtils.hasText(summaryText)
                    && !StringUtils.hasText(stageText)) {
                return loadLegacyOrEmpty(userId);
            }

            AiRecommendSessionState state = AiRecommendSessionState.empty(userId);
            state.setSlots(readSlots(slotsJson, state.getSlots()));
            state.setHistory(trimHistory(readHistory(historyJson)));
            state.setSummary(StringUtils.hasText(summaryText) ? summaryText : "");
            state.setStage(StringUtils.hasText(stageText) ? stageText : AiRecommendStage.ASK.name());
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
            AiRecommendSlots slotsToSave = state.getSlots() == null
                    ? AiRecommendSlots.builder().preferences(new ArrayList<>()).build()
                    : state.getSlots();
            String summaryToSave = state.getSummary() == null ? "" : state.getSummary();
            String stageToSave = StringUtils.hasText(state.getStage()) ? state.getStage() : AiRecommendStage.ASK.name();

            stringRedisTemplate.opsForValue().set(slotsKey(userId), objectMapper.writeValueAsString(slotsToSave), ttl);
            stringRedisTemplate.opsForValue().set(historyKey(userId), objectMapper.writeValueAsString(trimmedHistory), ttl);
            stringRedisTemplate.opsForValue().set(summaryKey(userId), summaryToSave, ttl);
            stringRedisTemplate.opsForValue().set(stageKey(userId), stageToSave, ttl);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to save ai recommend state", ex);
        }
    }

    @Override
    public void reset(Long userId) {
        stringRedisTemplate.delete(slotsKey(userId));
        stringRedisTemplate.delete(historyKey(userId));
        stringRedisTemplate.delete(summaryKey(userId));
        stringRedisTemplate.delete(stageKey(userId));
        stringRedisTemplate.delete(stateKey(userId));
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

    private AiRecommendSlots readSlots(String slotsJson, AiRecommendSlots fallbackSlots) throws Exception {
        if (!StringUtils.hasText(slotsJson)) {
            return fallbackSlots;
        }
        AiRecommendSlots slots = objectMapper.readValue(slotsJson, AiRecommendSlots.class);
        return slots == null ? fallbackSlots : slots;
    }

    private AiRecommendSessionState loadLegacyOrEmpty(Long userId) throws Exception {
        String legacyStateJson = stringRedisTemplate.opsForValue().get(stateKey(userId));
        if (!StringUtils.hasText(legacyStateJson)) {
            return AiRecommendSessionState.empty(userId);
        }
        try {
            AiRecommendSessionState state = objectMapper.readValue(legacyStateJson, AiRecommendSessionState.class);
            if (state.getUserId() == null) {
                state.setUserId(userId);
            }
            if (!StringUtils.hasText(state.getSessionId())) {
                state.setSessionId(AiRecommendSessionState.buildSessionId(userId));
            }
            if (state.getSummary() == null) {
                state.setSummary("");
            }
            if (!StringUtils.hasText(state.getStage())) {
                state.setStage(AiRecommendStage.ASK.name());
            }
            if (state.getSlots() == null) {
                state.setSlots(AiRecommendSlots.builder().preferences(new ArrayList<>()).build());
            }
            if (state.getHistory() == null) {
                state.setHistory(new ArrayList<>());
            }
            state.setHistory(trimHistory(state.getHistory()));
            return state;
        } catch (Exception ex) {
            AiRecommendSlots slots = objectMapper.readValue(legacyStateJson, AiRecommendSlots.class);
            AiRecommendSessionState state = AiRecommendSessionState.empty(userId);
            state.setSlots(slots == null ? state.getSlots() : slots);
            return state;
        }
    }

    private List<AiRecommendTurn> readHistory(String historyJson) throws Exception {
        if (!StringUtils.hasText(historyJson)) {
            return new ArrayList<>();
        }
        List<AiRecommendTurn> history = objectMapper.readValue(historyJson, new TypeReference<List<AiRecommendTurn>>() {
        });
        return history == null ? new ArrayList<>() : history;
    }

    private List<AiRecommendTurn> trimHistory(List<AiRecommendTurn> history) {
        List<AiRecommendTurn> safeHistory = history == null ? new ArrayList<>() : history;
        int limit = STORED_HISTORY_LIMIT;
        if (safeHistory.size() <= limit) {
            return new ArrayList<>(safeHistory);
        }
        return new ArrayList<>(safeHistory.subList(safeHistory.size() - limit, safeHistory.size()));
    }

    private String slotsKey(Long userId) {
        return "ai:recommend:slots:" + userId;
    }

    private String stateKey(Long userId) {
        return "ai:recommend:state:" + userId;
    }

    private String historyKey(Long userId) {
        return "ai:recommend:history:" + userId;
    }

    private String summaryKey(Long userId) {
        return "ai:recommend:summary:" + userId;
    }

    private String stageKey(Long userId) {
        return "ai:recommend:stage:" + userId;
    }
}
