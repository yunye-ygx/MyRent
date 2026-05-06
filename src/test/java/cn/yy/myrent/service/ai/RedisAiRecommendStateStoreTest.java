package cn.yy.myrent.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisAiRecommendStateStoreTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisAiRecommendStateStore stateStore;

    @BeforeEach
    void setUp() {
        stateStore = new RedisAiRecommendStateStore(stringRedisTemplate, new ObjectMapper(), Duration.ofHours(48));
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void saveShouldPersistSlotsHistoryAndSummarySeparately() {
        AiRecommendSessionState state = AiRecommendSessionState.builder()
                .userId(1001L)
                .sessionId("ai-u1001")
                .summary("confirmed city: Shanghai")
                .stage("PREVIEW")
                .slots(AiRecommendSlots.builder()
                        .city("Shanghai")
                        .locationName("Pudong")
                        .budgetYuan(3500)
                        .budgetScope("RENT_ONLY")
                        .rentMode("WHOLE")
                        .build())
                .history(List.of(AiRecommendTurn.user("hello")))
                .build();

        stateStore.save(state);

        verify(valueOperations).set(eq("ai:recommend:slots:1001"), any(String.class), eq(Duration.ofHours(48)));
        verify(valueOperations).set(eq("ai:recommend:history:1001"), any(String.class), eq(Duration.ofHours(48)));
        verify(valueOperations).set(eq("ai:recommend:summary:1001"), eq("confirmed city: Shanghai"), eq(Duration.ofHours(48)));
        verify(valueOperations).set(eq("ai:recommend:stage:1001"), eq("PREVIEW"), eq(Duration.ofHours(48)));
    }

    @Test
    void loadOrCreateShouldReadSlotsHistoryAndSummarySeparately() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiRecommendSlots slots = AiRecommendSlots.builder()
                .city("Shanghai")
                .locationName("Pudong")
                .budgetYuan(3500)
                .budgetScope("RENT_ONLY")
                .rentMode("WHOLE")
                .build();
        List<AiRecommendTurn> history = List.of(
                AiRecommendTurn.user("budget 3500"),
                AiRecommendTurn.assistant("which area")
        );
        when(valueOperations.get("ai:recommend:slots:1001")).thenReturn(objectMapper.writeValueAsString(slots));
        when(valueOperations.get("ai:recommend:history:1001")).thenReturn(objectMapper.writeValueAsString(history));
        when(valueOperations.get("ai:recommend:summary:1001")).thenReturn("confirmed city: Shanghai");
        when(valueOperations.get("ai:recommend:stage:1001")).thenReturn("REFINE");

        AiRecommendSessionState state = stateStore.loadOrCreate(1001L);

        assertEquals("ai-u1001", state.getSessionId());
        assertEquals("Shanghai", state.getSlots().getCity());
        assertEquals("Pudong", state.getSlots().getLocationName());
        assertEquals(2, state.getHistory().size());
        assertEquals("confirmed city: Shanghai", state.getSummary());
        assertEquals("REFINE", state.getStage());
    }

    @Test
    void loadOrCreateShouldTrimStoredHistoryToThirtyTurns() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<AiRecommendTurn> history = IntStream.range(0, 35)
                .mapToObj(i -> new AiRecommendTurn(i % 2 == 0 ? "user" : "assistant", "turn-" + i))
                .toList();
        when(valueOperations.get("ai:recommend:slots:1001"))
                .thenReturn(objectMapper.writeValueAsString(AiRecommendSlots.builder().city("Shanghai").build()));
        when(valueOperations.get("ai:recommend:history:1001"))
                .thenReturn(objectMapper.writeValueAsString(history));
        when(valueOperations.get("ai:recommend:summary:1001")).thenReturn("summary");
        when(valueOperations.get("ai:recommend:stage:1001")).thenReturn("PREVIEW");

        AiRecommendSessionState state = stateStore.loadOrCreate(1001L);

        assertEquals(30, state.getHistory().size());
        assertEquals("turn-5", state.getHistory().get(0).getContent());
        assertEquals("turn-34", state.getHistory().get(29).getContent());
        assertEquals("PREVIEW", state.getStage());
    }

    @Test
    void loadOrCreateShouldFallbackToLegacyStateKeyWhenSplitKeysAreMissing() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiRecommendSessionState persistedState = AiRecommendSessionState.builder()
                .userId(1001L)
                .sessionId("ai-u1001")
                .stage("SEARCH")
                .slots(AiRecommendSlots.builder()
                        .city("Shanghai")
                        .locationName("Pudong")
                        .budgetYuan(3500)
                        .budgetScope("RENT_ONLY")
                        .rentMode("WHOLE")
                        .build())
                .history(List.of(AiRecommendTurn.assistant("legacy history")))
                .build();
        when(valueOperations.get("ai:recommend:slots:1001")).thenReturn(null);
        when(valueOperations.get("ai:recommend:history:1001")).thenReturn(null);
        when(valueOperations.get("ai:recommend:summary:1001")).thenReturn(null);
        when(valueOperations.get("ai:recommend:stage:1001")).thenReturn(null);
        when(valueOperations.get("ai:recommend:state:1001"))
                .thenReturn(objectMapper.writeValueAsString(persistedState));

        AiRecommendSessionState state = stateStore.loadOrCreate(1001L);

        assertEquals("Shanghai", state.getSlots().getCity());
        assertEquals(1, state.getHistory().size());
        assertEquals("", state.getSummary());
        assertEquals("SEARCH", state.getStage());
    }

    @Test
    void loadOrCreateShouldFallbackToEmptySessionWhenRedisFails() {
        when(valueOperations.get("ai:recommend:slots:1001")).thenThrow(new RuntimeException("redis down"));

        AiRecommendSessionState state = stateStore.loadOrCreate(1001L);

        assertEquals("ai-u1001", state.getSessionId());
        assertNotNull(state.getSlots());
        assertTrue(state.getHistory().isEmpty());
        assertEquals("", state.getSummary());
        assertEquals("ASK", state.getStage());
    }

    @Test
    void resetShouldDeleteSplitKeysAndLegacyStateKey() {
        stateStore.reset(1001L);

        verify(stringRedisTemplate).delete("ai:recommend:slots:1001");
        verify(stringRedisTemplate).delete("ai:recommend:history:1001");
        verify(stringRedisTemplate).delete("ai:recommend:summary:1001");
        verify(stringRedisTemplate).delete("ai:recommend:stage:1001");
        verify(stringRedisTemplate).delete("ai:recommend:state:1001");
    }

    @Test
    void shouldDeclareExactlyOneAutowiredConstructorForSpringInjection() {
        long autowiredConstructorCount = Arrays.stream(RedisAiRecommendStateStore.class.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .count();

        assertEquals(1, autowiredConstructorCount);
    }
}
