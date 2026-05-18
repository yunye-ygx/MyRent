package cn.yy.myrent.lab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadConnectionDbLabTest {

    @Test
    void smallerWorkerPoolShouldTakeLongerThanLargerPool() throws Exception {
        SearchPoolDbLab.LabResult singleWorker = SearchPoolDbLab.runScenarioForTest(1, 8, 8, "SELECT SLEEP(1)");
        SearchPoolDbLab.LabResult eightWorkers = SearchPoolDbLab.runScenarioForTest(8, 8, 8, "SELECT SLEEP(1)");

        assertTrue(singleWorker.totalElapsedMs() > eightWorkers.totalElapsedMs(),
                "smaller worker pool should take longer than larger pool");
    }

    @Test
    void smallerJdbcPoolShouldTakeLongerThanLargerPool() throws Exception {
        SearchPoolDbLab.LabResult smallPool = SearchPoolDbLab.runScenarioForTest(8, 2, 8, "SELECT SLEEP(1)");
        SearchPoolDbLab.LabResult largePool = SearchPoolDbLab.runScenarioForTest(8, 8, 8, "SELECT SLEEP(1)");

        assertTrue(smallPool.totalElapsedMs() > largePool.totalElapsedMs(),
                "smaller jdbc pool should take longer than larger pool");
    }

    @Test
    void smallerJdbcPoolShouldIncreaseConnectionWait() throws Exception {
        SearchPoolDbLab.LabResult smallPool = SearchPoolDbLab.runScenarioForTest(8, 2, 8, "SELECT SLEEP(1)");
        SearchPoolDbLab.LabResult largePool = SearchPoolDbLab.runScenarioForTest(8, 8, 8, "SELECT SLEEP(1)");

        assertTrue(smallPool.maxConnectionWaitMs() >= largePool.maxConnectionWaitMs(),
                "smaller jdbc pool should not wait less than larger pool");
    }
}
