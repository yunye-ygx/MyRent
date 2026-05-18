package cn.yy.myrent.lab;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Manual local lab for understanding the interaction between:
 * 1) search executor threads
 * 2) JDBC connection pool size
 * 3) database concurrency capacity
 *
 * Run it manually, do not wire it into CI.
 */
public class SearchPoolDbLab {

    private static final String JDBC_URL =
            System.getProperty("lab.jdbc.url", "jdbc:mysql://localhost:3306/rent?useSSL=false&serverTimezone=UTC");
    private static final String JDBC_USER = System.getProperty("lab.jdbc.user", "root");
    private static final String JDBC_PASSWORD = System.getProperty("lab.jdbc.password", "1234");

    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(30);

    public static void main(String[] args) throws Exception {
        System.out.println("=== Search thread pool / JDBC pool / DB capacity lab ===");
        System.out.println("JDBC URL: " + JDBC_URL);
        System.out.println();

        String benchmarkSql = calibrateBenchmarkSql();
        System.out.println();

        runScenario(new Scenario(
                "A. Search executor > JDBC pool: tasks wait for connections",
                8,
                2,
                8,
                "SELECT SLEEP(2)",
                250
        ));

        runScenario(new Scenario(
                "B. Search executor matches JDBC pool: wait mostly disappears",
                8,
                8,
                8,
                "SELECT SLEEP(2)",
                250
        ));

        runScenario(new Scenario(
                "C. DB-heavy query with moderate concurrency",
                4,
                4,
                4,
                benchmarkSql,
                250
        ));

        runScenario(new Scenario(
                "D. Bigger pools can increase DB pressure, not just speed",
                16,
                16,
                16,
                benchmarkSql,
                250
        ));
    }

    public static LabResult runScenarioForTest(int searchThreads,
                                                int jdbcPoolSize,
                                                int taskCount,
                                                String sql) throws Exception {
        return runScenarioInternal(new Scenario(
                "test-scenario",
                searchThreads,
                jdbcPoolSize,
                taskCount,
                sql,
                100
        ));
    }

    private static String calibrateBenchmarkSql() throws Exception {
        System.out.println("Calibrating a DB-heavy benchmark query...");
        int[] candidates = {100_000, 300_000, 700_000, 1_500_000, 3_000_000, 5_000_000};
        try (HikariDataSource dataSource = buildDataSource(2)) {
            for (int loops : candidates) {
                String sql = "SELECT BENCHMARK(" + loops + ", SHA2('abc', 256))";
                long elapsedMs = runSingleQuery(dataSource, sql);
                System.out.printf(Locale.ROOT,
                        "  candidate loops=%d -> single query took %d ms%n",
                        loops, elapsedMs);
                if (elapsedMs >= 250) {
                    System.out.println("  using benchmark SQL: " + sql);
                    return sql;
                }
            }
        }
        String fallback = "SELECT BENCHMARK(5000000, SHA2('abc', 256))";
        System.out.println("  all candidates were too light, using fallback SQL: " + fallback);
        return fallback;
    }

    private static long runSingleQuery(HikariDataSource dataSource, String sql) throws Exception {
        long start = System.nanoTime();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static void runScenario(Scenario scenario) throws Exception {
        runScenarioInternal(scenario);
        System.out.println();
    }

    private static LabResult runScenarioInternal(Scenario scenario) throws Exception {
        System.out.println("============================================================");
        System.out.println(scenario.name());
        System.out.printf(Locale.ROOT,
                "searchThreads=%d, jdbcPool=%d, taskCount=%d%n",
                scenario.searchThreads(), scenario.jdbcPoolSize(), scenario.taskCount());
        System.out.println("sql=" + scenario.sql());

        try (HikariDataSource dataSource = buildDataSource(scenario.jdbcPoolSize())) {
            ExecutorService workers = Executors.newFixedThreadPool(scenario.searchThreads(), runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("lab-search-" + thread.getId());
                return thread;
            });
            ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("lab-sampler");
                thread.setDaemon(true);
                return thread;
            });

            CountDownLatch done = new CountDownLatch(scenario.taskCount());
            HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();

            sampler.scheduleAtFixedRate(() -> printSnapshot(dataSource, poolMXBean),
                    0,
                    scenario.sampleIntervalMs(),
                    TimeUnit.MILLISECONDS);

            long suiteStart = System.nanoTime();
            List<Future<TaskResult>> futures = new ArrayList<>();
            for (int i = 0; i < scenario.taskCount(); i++) {
                int taskId = i + 1;
                futures.add(workers.submit(buildTask(taskId, dataSource, scenario.sql(), done)));
                Thread.sleep(ThreadLocalRandom.current().nextInt(10, 25));
            }

            done.await();
            long totalElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - suiteStart);
            sampler.shutdownNow();
            workers.shutdown();
            workers.awaitTermination(30, TimeUnit.SECONDS);

            List<TaskResult> results = new ArrayList<>();
            for (Future<TaskResult> future : futures) {
                results.add(future.get());
            }
            printSummary(results, totalElapsedMs);
            return buildLabResult(results, totalElapsedMs, poolMXBean);
        }
    }

    private static LabResult buildLabResult(List<TaskResult> results,
                                            long totalElapsedMs,
                                            HikariPoolMXBean poolMXBean) {
        long maxWait = results.stream()
                .mapToLong(TaskResult::waitForConnectionMs)
                .max()
                .orElse(0L);
        long avgWait = Math.round(results.stream()
                .mapToLong(TaskResult::waitForConnectionMs)
                .average()
                .orElse(0L));
        long maxQuery = results.stream()
                .mapToLong(TaskResult::queryExecutionMs)
                .max()
                .orElse(0L);
        return new LabResult(
                totalElapsedMs,
                avgWait,
                maxWait,
                maxQuery,
                poolMXBean.getThreadsAwaitingConnection()
        );
    }

    private static Callable<TaskResult> buildTask(int taskId,
                                                  HikariDataSource dataSource,
                                                  String sql,
                                                  CountDownLatch done) {
        return () -> {
            long submitNs = System.nanoTime();
            try (Connection connection = dataSource.getConnection()) {
                long acquiredNs = System.nanoTime();
                try (Statement statement = connection.createStatement();
                     ResultSet ignored = statement.executeQuery(sql)) {
                    while (ignored.next()) {
                        // Drain the result so the query fully completes.
                    }
                }
                long finishNs = System.nanoTime();
                return new TaskResult(
                        taskId,
                        elapsedMs(submitNs, acquiredNs),
                        elapsedMs(acquiredNs, finishNs),
                        elapsedMs(submitNs, finishNs)
                );
            } finally {
                done.countDown();
            }
        };
    }

    private static long elapsedMs(long startNs, long endNs) {
        return TimeUnit.NANOSECONDS.toMillis(endNs - startNs);
    }

    private static void printSnapshot(HikariDataSource dataSource, HikariPoolMXBean poolMXBean) {
        DbStatus dbStatus = queryDbStatus();
        System.out.printf(Locale.ROOT,
                "[snapshot] active=%d idle=%d waiting=%d dbThreadsConnected=%d dbThreadsRunning=%d%n",
                poolMXBean.getActiveConnections(),
                poolMXBean.getIdleConnections(),
                poolMXBean.getThreadsAwaitingConnection(),
                dbStatus.threadsConnected(),
                dbStatus.threadsRunning());
    }

    private static DbStatus queryDbStatus() {
        int connected = -1;
        int running = -1;
        try (Connection connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("SHOW STATUS LIKE 'Threads_connected'")) {
                if (rs.next()) {
                    connected = rs.getInt("Value");
                }
            }
            try (ResultSet rs = statement.executeQuery("SHOW STATUS LIKE 'Threads_running'")) {
                if (rs.next()) {
                    running = rs.getInt("Value");
                }
            }
        } catch (Exception ex) {
            return new DbStatus(connected, running);
        }
        return new DbStatus(connected, running);
    }

    private static void printSummary(List<TaskResult> results, long totalElapsedMs) {
        List<Long> connectionWaits = results.stream()
                .map(TaskResult::waitForConnectionMs)
                .sorted()
                .toList();
        List<Long> queryTimes = results.stream()
                .map(TaskResult::queryExecutionMs)
                .sorted()
                .toList();

        long avgWait = Math.round(connectionWaits.stream().mapToLong(Long::longValue).average().orElse(0));
        long maxWait = connectionWaits.stream().mapToLong(Long::longValue).max().orElse(0);
        long p95Wait = percentile(connectionWaits, 95);

        long avgQuery = Math.round(queryTimes.stream().mapToLong(Long::longValue).average().orElse(0));
        long maxQuery = queryTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long p95Query = percentile(queryTimes, 95);

        long avgEndToEnd = Math.round(results.stream().mapToLong(TaskResult::endToEndMs).average().orElse(0));

        System.out.println("--- Summary ---");
        System.out.printf(Locale.ROOT, "total wall clock: %d ms%n", totalElapsedMs);
        System.out.printf(Locale.ROOT, "connection wait avg=%d ms, p95=%d ms, max=%d ms%n", avgWait, p95Wait, maxWait);
        System.out.printf(Locale.ROOT, "query exec     avg=%d ms, p95=%d ms, max=%d ms%n", avgQuery, p95Query, maxQuery);
        System.out.printf(Locale.ROOT, "task end-to-end avg=%d ms%n", avgEndToEnd);

        TaskResult slowestTask = results.stream()
                .max(Comparator.comparingLong(TaskResult::endToEndMs))
                .orElseThrow();
        System.out.printf(Locale.ROOT,
                "slowest task #%d -> wait=%d ms, query=%d ms, total=%d ms%n",
                slowestTask.taskId(),
                slowestTask.waitForConnectionMs(),
                slowestTask.queryExecutionMs(),
                slowestTask.endToEndMs());
    }

    private static long percentile(List<Long> values, int percentile) {
        if (values.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil((percentile / 100.0d) * values.size()) - 1;
        index = Math.max(0, Math.min(index, values.size() - 1));
        return values.get(index);
    }

    private static HikariDataSource buildDataSource(int maxPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(JDBC_URL);
        config.setUsername(JDBC_USER);
        config.setPassword(JDBC_PASSWORD);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(Math.min(2, maxPoolSize));
        config.setConnectionTimeout(CONNECTION_TIMEOUT.toMillis());
        config.setIdleTimeout(IDLE_TIMEOUT.toMillis());
        config.setPoolName("lab-hikari-" + maxPoolSize);
        return new HikariDataSource(config);
    }

    private record Scenario(String name,
                            int searchThreads,
                            int jdbcPoolSize,
                            int taskCount,
                            String sql,
                            long sampleIntervalMs) {
    }

    private record TaskResult(int taskId,
                              long waitForConnectionMs,
                              long queryExecutionMs,
                              long endToEndMs) {
    }

    private record DbStatus(int threadsConnected, int threadsRunning) {
    }

    public record LabResult(long totalElapsedMs,
                            long avgConnectionWaitMs,
                            long maxConnectionWaitMs,
                            long maxQueryExecutionMs,
                            int waitingConnections) {
    }
}
