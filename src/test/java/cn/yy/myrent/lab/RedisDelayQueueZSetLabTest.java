package cn.yy.myrent.lab;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisDelayQueueZSetLabTest {

    private StringRedisTemplate redisTemplate;
    private ZSetOperations<String, String> zSetOperations;
    private RedisDelayQueueZSetLab lab;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lab = new RedisDelayQueueZSetLab(redisTemplate, "lab:delay:order");
    }

    @Test
    void enqueueShouldStoreJobInZsetWithExecuteTimeScore() {
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);

        RedisDelayQueueZSetLab.DelayJob job = lab.enqueue("order-1001", Duration.ofSeconds(30));

        assertNotNull(job);
        verify(zSetOperations).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void pollDueJobsShouldReturnOnlyJobsWhoseScoreIsDue() {
        Set<String> values = new LinkedHashSet<>(List.of(
                "msg-1|order-1|1000",
                "msg-2|order-2|2000",
                "msg-3|order-3|3000"
        ));
        when(zSetOperations.rangeByScore("lab:delay:order", 0, 2000, 0, 2)).thenReturn(values);

        List<RedisDelayQueueZSetLab.DelayJob> dueJobs = lab.pollDueJobs(2000, 2);

        assertEquals(3, dueJobs.size());
        assertEquals("order-1", dueJobs.get(0).getOrderNo());
        assertEquals("order-2", dueJobs.get(1).getOrderNo());
    }

    @Test
    void claimShouldRemoveTheExactMessageFromZset() {
        RedisDelayQueueZSetLab.DelayJob job = new RedisDelayQueueZSetLab.DelayJob("msg-1", "order-1", 1000);
        when(zSetOperations.remove("lab:delay:order", "msg-1|order-1|1000")).thenReturn(1L);

        boolean claimed = lab.claim(job);

        assertTrue(claimed);
        verify(zSetOperations).remove("lab:delay:order", "msg-1|order-1|1000");
    }

    @Test
    void pollAndClaimFirstDueShouldReturnNullWhenNothingIsDue() {
        when(zSetOperations.rangeByScore("lab:delay:order", 0, 1000, 0, 1)).thenReturn(Set.of());

        RedisDelayQueueZSetLab.DelayJob job = lab.pollAndClaimFirstDue(1000);

        assertNull(job);
    }

    @Test
    void claimShouldReturnFalseForNullJob() {
        assertFalse(lab.claim(null));
    }
}
