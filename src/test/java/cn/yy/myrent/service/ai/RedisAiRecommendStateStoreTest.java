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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
    void saveShouldPersistStateAndHistoryWithTtl() {
        AiRecommendSessionState state = AiRecommendSessionState.builder()
                .userId(1001L)
                .sessionId("ai-u1001")
                .slots(AiRecommendSlots.builder()
                        .city("上海")
                        .locationName("浦东")
                        .budgetYuan(3500)
                        .budgetScope("RENT_ONLY")
                        .rentMode("WHOLE")
                        .priority("COMMUTE")
                        .preferences(List.of("近地铁"))
                        .build())
                .history(List.of(
                        AiRecommendTurn.user("预算3500"),
                        AiRecommendTurn.assistant("再告诉我想住哪里")
                ))
                .build();

        stateStore.save(state);

        verify(valueOperations).set(eq("ai:recommend:state:1001"), any(String.class), eq(Duration.ofHours(48)));
        verify(valueOperations).set(eq("ai:recommend:history:1001"), any(String.class), eq(Duration.ofHours(48)));
    }

    @Test
    void loadOrCreateShouldReadStateAndHistory() throws Exception {
        List<AiRecommendTurn> history = List.of(
                AiRecommendTurn.user("预算3500"),
                AiRecommendTurn.assistant("再告诉我想住哪里")
        );
        ObjectMapper objectMapper = new ObjectMapper();
        AiRecommendSessionState persistedState = AiRecommendSessionState.builder()
                .userId(1001L)
                .sessionId("ai-u1001")
                .slots(AiRecommendSlots.builder()
                        .city("上海")
                        .locationName("浦东")
                        .budgetYuan(3500)
                        .budgetScope("RENT_ONLY")
                        .rentMode("WHOLE")
                        .priority("COMMUTE")
                        .preferences(List.of("近地铁"))
                        .build())
                .history(history)
                .build();
        when(valueOperations.get("ai:recommend:state:1001")).thenReturn(objectMapper.writeValueAsString(persistedState));
        when(valueOperations.get("ai:recommend:history:1001")).thenReturn(objectMapper.writeValueAsString(history));

        AiRecommendSessionState state = stateStore.loadOrCreate(1001L);

        assertEquals("ai-u1001", state.getSessionId());
        assertEquals("上海", state.getSlots().getCity());
        assertEquals("浦东", state.getSlots().getLocationName());
        assertEquals(2, state.getHistory().size());
    }

    @Test
    void loadOrCreateShouldPreferEmbeddedHistoryWhenStandaloneHistoryIsStale() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiRecommendSessionState persistedState = AiRecommendSessionState.builder()
                .userId(1001L)
                .sessionId("ai-u1001")
                .slots(AiRecommendSlots.builder()
                        .city("上海")
                        .locationName("浦东")
                        .budgetYuan(3500)
                        .budgetScope("RENT_ONLY")
                        .rentMode("WHOLE")
                        .build())
                .history(List.of(AiRecommendTurn.assistant("使用 state 内的历史")))
                .build();
        when(valueOperations.get("ai:recommend:state:1001")).thenReturn(objectMapper.writeValueAsString(persistedState));
        when(valueOperations.get("ai:recommend:history:1001"))
                .thenReturn(objectMapper.writeValueAsString(List.of(AiRecommendTurn.assistant("旧 history key"))));

        AiRecommendSessionState state = stateStore.loadOrCreate(1001L);

        assertEquals(1, state.getHistory().size());
        assertEquals("使用 state 内的历史", state.getHistory().get(0).getContent());
    }

    @Test
    void loadOrCreateShouldIgnoreStandaloneHistoryForLegacySlotOnlyState() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiRecommendSlots legacySlots = AiRecommendSlots.builder()
                .city("上海")
                .locationName("浦东")
                .budgetYuan(3500)
                .budgetScope("RENT_ONLY")
                .rentMode("WHOLE")
                .build();
        when(valueOperations.get("ai:recommend:state:1001")).thenReturn(objectMapper.writeValueAsString(legacySlots));
        when(valueOperations.get("ai:recommend:history:1001"))
                .thenReturn(objectMapper.writeValueAsString(List.of(AiRecommendTurn.assistant("旧 history key"))));

        AiRecommendSessionState state = stateStore.loadOrCreate(1001L);

        assertEquals("上海", state.getSlots().getCity());
        assertTrue(state.getHistory().isEmpty());
    }

    @Test
    void loadOrCreateShouldFallbackToEmptySessionWhenRedisFails() {
        when(valueOperations.get("ai:recommend:state:1001")).thenThrow(new RuntimeException("redis down"));

        AiRecommendSessionState state = stateStore.loadOrCreate(1001L);

        assertEquals("ai-u1001", state.getSessionId());
        assertNotNull(state.getSlots());
        assertTrue(state.getHistory().isEmpty());
    }

    @Test
    void loadOrCreateShouldFallbackToEmptySessionWhenJsonIsBroken() {
        when(valueOperations.get("ai:recommend:state:1001")).thenReturn("{broken");
       when(valueOperations.get("ai:recommend:history:1001")).thenReturn("[broken");

        AiRecommendSessionState state = stateStore.loadOrCreate(1001L);

        assertEquals("ai-u1001", state.getSessionId());
        assertNotNull(state.getSlots());
        assertTrue(state.getHistory().isEmpty());
    }

    @Test
    void shouldDeclareExactlyOneAutowiredConstructorForSpringInjection() {
        long autowiredConstructorCount = Arrays.stream(RedisAiRecommendStateStore.class.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .count();

        assertEquals(1, autowiredConstructorCount);
    }
}
