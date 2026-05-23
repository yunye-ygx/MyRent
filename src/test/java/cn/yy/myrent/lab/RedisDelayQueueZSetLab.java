package cn.yy.myrent.lab;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Minimal Redis ZSet delay-queue lab.
 *
 * This is intentionally isolated from the production order flow so the queue
 * mechanics are easier to read and experiment with.
 */
public class RedisDelayQueueZSetLab {

    private final StringRedisTemplate redisTemplate;
    private final String queueKey;

    public RedisDelayQueueZSetLab(StringRedisTemplate redisTemplate) {
        this(redisTemplate, "lab:delay:order");
    }

    public RedisDelayQueueZSetLab(StringRedisTemplate redisTemplate, String queueKey) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.queueKey = Objects.requireNonNull(queueKey, "queueKey");
    }

    public DelayJob enqueue(String orderNo, Duration delay) {
        long executeAtMillis = Instant.now().plus(delay).toEpochMilli();
        DelayJob job = new DelayJob(UUID.randomUUID().toString().replace("-", ""), orderNo, executeAtMillis);
        redisTemplate.opsForZSet().add(queueKey, encode(job), executeAtMillis);
        return job;
    }

    public List<DelayJob> pollDueJobs(long nowMillis, int limit) {
        return redisTemplate.opsForZSet()
                .rangeByScore(queueKey, 0, nowMillis, 0, limit)
                .stream()
                .map(this::decode)
                .toList();
    }

    public boolean claim(DelayJob job) {
        if (job == null) {
            return false;
        }
        Long removed = redisTemplate.opsForZSet().remove(queueKey, encode(job));
        return removed != null && removed > 0;
    }

    public DelayJob pollAndClaimFirstDue(long nowMillis) {
        List<DelayJob> dueJobs = pollDueJobs(nowMillis, 1);
        if (dueJobs.isEmpty()) {
            return null;
        }
        DelayJob job = dueJobs.get(0);
        return claim(job) ? job : null;
    }

    String encode(DelayJob job) {
        return job.messageId + "|" + job.orderNo + "|" + job.executeAtMillis;
    }

    DelayJob decode(String raw) {
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid delay job payload: " + raw);
        }
        return new DelayJob(parts[0], parts[1], Long.parseLong(parts[2]));
    }

    @Getter
    @ToString
    @RequiredArgsConstructor
    public static class DelayJob {
        private final String messageId;
        private final String orderNo;
        private final long executeAtMillis;
    }
}
