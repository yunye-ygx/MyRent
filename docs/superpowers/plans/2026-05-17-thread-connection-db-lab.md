# Thread / Connection / DB Lab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a repeatable lab that lets me observe how thread pools, connection pools, and database capacity interact under load.

**Architecture:** Add a small test-only experiment layer that can run controlled concurrent work against the real application stack and a lightweight synthetic JDBC workload. Keep production code untouched. Expose only the metrics needed to compare queueing, wait time, and throughput under different pool settings.

**Tech Stack:** Java 17, Spring Boot test, JUnit 5, HikariCP, MySQL, existing MyRent test utilities.

---

### Task 1: Add a reusable load-lab test harness

**Files:**
- Create: `src/test/java/cn/yy/myrent/lab/ThreadConnectionDbLabTest.java`
- Modify: `src/test/java/cn/yy/myrent/lab/SearchPoolDbLab.java` (reuse helper methods only if needed)

- [ ] **Step 1: Write the failing test**

```java
@Test
void labHarnessShouldPrintWaitAndThroughputSnapshots() {
    LabResult result = ThreadConnectionDbLabTest.runScenario(8, 2, 8, "SELECT SLEEP(1)");
    assertTrue(result.totalElapsedMs() > 0);
    assertTrue(result.maxConnectionWaitMs() >= 0);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=ThreadConnectionDbLabTest test`
Expected: FAIL because `ThreadConnectionDbLabTest` or `runScenario` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
public final class ThreadConnectionDbLabTest {
    public static LabResult runScenario(int searchThreads, int jdbcPoolSize, int taskCount, String sql) {
        return new LabResult(1L, 0L);
    }

    public record LabResult(long totalElapsedMs, long maxConnectionWaitMs) {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=ThreadConnectionDbLabTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/cn/yy/myrent/lab/ThreadConnectionDbLabTest.java
git commit -m "test: add thread connection db lab harness"
```

### Task 2: Make the lab measure pool wait vs execution time

**Files:**
- Modify: `src/test/java/cn/yy/myrent/lab/ThreadConnectionDbLabTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void smallerJdbcPoolShouldIncreaseConnectionWait() {
    LabResult smallPool = ThreadConnectionDbLabTest.runScenario(8, 2, 8, "SELECT SLEEP(1)");
    LabResult largePool = ThreadConnectionDbLabTest.runScenario(8, 8, 8, "SELECT SLEEP(1)");
    assertTrue(smallPool.maxConnectionWaitMs() >= largePool.maxConnectionWaitMs());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=ThreadConnectionDbLabTest#smallerJdbcPoolShouldIncreaseConnectionWait test`
Expected: FAIL until the harness records real connection wait time.

- [ ] **Step 3: Write minimal implementation**

```java
// Measure:
// 1. time to obtain Connection
// 2. time spent executing SQL
// 3. total elapsed time
// Return the max/avg values in LabResult.
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=ThreadConnectionDbLabTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/cn/yy/myrent/lab/ThreadConnectionDbLabTest.java
git commit -m "test: measure jdbc wait and execution time"
```

### Task 3: Add a service-level experiment for request-thread pressure

**Files:**
- Create: `src/test/java/cn/yy/myrent/lab/RequestThreadPressureLabTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void higherRequestConcurrencyShouldIncreaseTomcatThreadOccupation() {
    RequestThreadPressureLabTest.Result result =
            RequestThreadPressureLabTest.runConcurrentRequests(20);
    assertTrue(result.completedCount() > 0);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=RequestThreadPressureLabTest test`
Expected: FAIL because the helper does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
public final class RequestThreadPressureLabTest {
    public static Result runConcurrentRequests(int concurrency) {
        return new Result(concurrency, 0L);
    }

    public record Result(int completedCount, long maxElapsedMs) {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=RequestThreadPressureLabTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/cn/yy/myrent/lab/RequestThreadPressureLabTest.java
git commit -m "test: add request thread pressure lab"
```

### Task 4: Wire the lab to real project endpoints and compare scenarios

**Files:**
- Modify: `src/test/java/cn/yy/myrent/lab/RequestThreadPressureLabTest.java`
- Modify: `src/test/java/cn/yy/myrent/lab/ThreadConnectionDbLabTest.java`
- Optional: `src/test/java/cn/yy/myrent/lab/SearchPoolDbLab.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void realSearchPathShouldShowDifferentWaitProfilesAcrossPoolSizes() {
    // compare:
    // 1) small thread pool + small jdbc pool
    // 2) larger thread pool + same jdbc pool
    // expect different wait profiles, not identical totals
    fail("not implemented yet");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=RequestThreadPressureLabTest test`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
// Reuse MockMvc or direct controller/service calls to fire concurrent requests.
// Record:
// - request completion count
// - max elapsed time
// - average latency
// - any log markers showing thread names
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=RequestThreadPressureLabTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/cn/yy/myrent/lab/RequestThreadPressureLabTest.java src/test/java/cn/yy/myrent/lab/ThreadConnectionDbLabTest.java
git commit -m "test: compare request thread and jdbc pool pressure"
```

### Task 5: Write the learning notes from the experiment results

**Files:**
- Create: `docs/notes/thread-connection-db-lab-observations.md`

- [ ] **Step 1: Write the failing test**

```text
Not a code test.
Use the lab results to fill a notes template with:
- observed thread wait
- observed JDBC wait
- observed database slowdown
- final takeaway
```

- [ ] **Step 2: Run experiment and capture output**

Run:
`mvn -Dtest=ThreadConnectionDbLabTest test`
`mvn -Dtest=RequestThreadPressureLabTest test`
Expected: collect actual timings and thread names.

- [ ] **Step 3: Write minimal implementation**

```md
## Observations
- Scenario A:
- Scenario B:
- What changed:
- What stayed constant:
- What this means:
```

- [ ] **Step 4: Run test to verify it passes**

Manual review only.

- [ ] **Step 5: Commit**

```bash
git add docs/notes/thread-connection-db-lab-observations.md
git commit -m "docs: record thread connection db lab observations"
```

## Review Checklist

- Thread pool vs connection pool are measured separately.
- Real DB work is isolated from synthetic `SLEEP` workload.
- The lab can be rerun without touching production code.
- Results are easy to compare across at least two pool configurations.
- The notes file captures the final interpretation in plain language.
