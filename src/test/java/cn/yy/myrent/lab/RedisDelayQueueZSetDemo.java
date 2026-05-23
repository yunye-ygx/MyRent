package cn.yy.myrent.lab;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Run manually from the IDE or terminal to see a Redis ZSet delay queue in action.
 *
 * System properties:
 * - lab.redis.host
 * - lab.redis.port
 * - lab.redis.password
 * - lab.redis.queueKey
 */
public class RedisDelayQueueZSetDemo {

    public static void main(String[] args) throws Exception {
        String host = System.getProperty("lab.redis.host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("lab.redis.port", "6379"));
        String password = System.getProperty("lab.redis.password", "");
        String queueKey = System.getProperty("lab.redis.queueKey", "lab:delay:order");

        LettuceConnectionFactory factory = new LettuceConnectionFactory(host, port);
        if (!password.isBlank()) {
            factory.setPassword(password);
        }
        factory.afterPropertiesSet();

        StringRedisTemplate redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();

        RedisDelayQueueZSetLab lab = new RedisDelayQueueZSetLab(redisTemplate, queueKey);
        redisTemplate.delete(queueKey);

        List<RedisDelayQueueZSetLab.DelayJob> jobs = List.of(
                lab.enqueue("order-1001", Duration.ofSeconds(2)),
                lab.enqueue("order-1002", Duration.ofSeconds(4)),
                lab.enqueue("order-1003", Duration.ofSeconds(6))
        );

        System.out.println("queueKey=" + queueKey);
        System.out.println("enqueued jobs:");
        jobs.forEach(job -> System.out.println("  " + job));
        System.out.println("polling...");

        AtomicInteger processed = new AtomicInteger();
        long deadline = Instant.now().plusSeconds(20).toEpochMilli();
        while (Instant.now().toEpochMilli() < deadline && processed.get() < jobs.size()) {
            RedisDelayQueueZSetLab.DelayJob job = lab.pollAndClaimFirstDue(Instant.now().toEpochMilli());
            if (job == null) {
                Thread.sleep(200);
                continue;
            }

            System.out.println("execute order timeout job => " + job);
            processed.incrementAndGet();
        }

        System.out.println("processed=" + processed.get());
        factory.destroy();
    }
}
