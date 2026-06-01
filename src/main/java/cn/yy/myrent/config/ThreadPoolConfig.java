package cn.yy.myrent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class ThreadPoolConfig {

    /**
     * 用于双路召回的 IO 密集型线程池。
     *
     * 核心线程数公式：CPU核心数 / (1 - 阻塞系数)
     * ES 查询阻塞系数约 0.8，8核机器 → 8 / (1 - 0.8) = 40
     * 这里取 Runtime 动态计算，部署到不同机器自动适配。
     */

    @Bean(name = "recallExecutor")
    public Executor recallExecutor() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        log.info("本机 CPU 核心数: {}", cpuCores);
        int corePoolSize = cpuCores * 5;   // 阻塞系数 0.8 → 除以 (1-0.8) = 乘以 5
        int maxPoolSize = corePoolSize * 2;

        return new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("recall-" + t.getId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
