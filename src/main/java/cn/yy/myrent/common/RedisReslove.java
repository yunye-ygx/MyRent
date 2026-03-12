package cn.yy.myrent.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 熔断/防击穿工具：热点 key 逻辑过期 + 异步续约。
 * 调用方：优先命中缓存；过期返回旧值并异步刷新，避免大量请求打穿数据库。
 */
@Component
public class RedisReslove {

    private static final Logger log = LoggerFactory.getLogger(RedisReslove.class);
    private static final String LOCK_PREFIX = "lock:logical-expire:";
    private static final long LOCK_TTL_SECONDS = 30;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService refreshPool = Executors.newFixedThreadPool(4);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 逻辑过期查询：
     * 1) 缓存命中且未过期：直接返回。
     * 2) 命中但已过期：返回旧值，同时异步刷新（单飞刷新）。
     * 3) 未命中：回源 DB，写入逻辑过期缓存。
     *
     * @param key        缓存 key
     * @param type       数据类型
     * @param dbFallback DB 获取函数
     * @param ttl        逻辑过期时长
     * @param unit       逻辑过期时间单位
     */
    public <T> T queryWithLogicalExpire(String key,
                                        Class<T> type,
                                        Supplier<T> dbFallback,
                                        long ttl,
                                        TimeUnit unit) {
        String cacheJson = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(cacheJson)) {
            RedisData redisData = deserialize(cacheJson, RedisData.class);
            if (redisData == null || redisData.getData() == null) {
                return null;
            }
            T data = objectMapper.convertValue(redisData.getData(), type);
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                // 未过期
                return data;
            }
            // 过期：返回旧值并尝试单飞刷新
            String lockKey = LOCK_PREFIX + key;
            if (tryLock(lockKey)) {
                refreshPool.submit(() -> {
                    try {
                        T fresh = dbFallback.get();
                        if (fresh != null) {
                            setWithLogicalExpire(key, fresh, ttl, unit);
                            log.info("Logical expire refresh success, key={}", key);
                        } else {
                            // 空值也缓存一小段时间，避免穿透
                            setWithLogicalExpire(key, null, 60, TimeUnit.SECONDS);
                            log.info("Logical expire refresh got null, key={}, cached short ttl", key);
                        }
                    } catch (Exception e) {
                        log.error("Logical expire refresh error, key={}", key, e);
                    } finally {
                        unlock(lockKey);
                    }
                });
            }
            return data;
        }

        // 未命中，回源
        T dbData = dbFallback.get();
        if (dbData == null) {
            // 缓存空值短期，防穿透
            setWithLogicalExpire(key, null, 60, TimeUnit.SECONDS);
            return null;
        }
        setWithLogicalExpire(key, dbData, ttl, unit);
        return dbData;
    }

    /**
     * 写入逻辑过期数据。
     */
    public void setWithLogicalExpire(String key, Object value, long ttl, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(ttl)));
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(redisData));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Serialize redis data error", e);
        }
    }

    private boolean tryLock(String lockKey) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    private void unlock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }

    private <T> T deserialize(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("Deserialize redis json error", e);
            return null;
        }
    }

    /**
     * 封装逻辑过期数据。
     */
    public static class RedisData {
        private Object data;
        private LocalDateTime expireTime;

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }

        public LocalDateTime getExpireTime() {
            return expireTime;
        }

        public void setExpireTime(LocalDateTime expireTime) {
            this.expireTime = expireTime;
        }
    }
}
