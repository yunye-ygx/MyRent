# House Hot Redis Rebuild Script Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a temporary backend trigger and a PowerShell script that rebuild the Redis hot-house cache from current MySQL rentable houses by reusing the existing `HouseHotService.rebuildHotRanking()` logic.

**Architecture:** Expose a narrow temporary HTTP endpoint in the existing house controller, wire it to `HouseHotService`, and allow local script execution without requiring an auth token. Keep all ranking and Redis write logic inside `HouseHotService`; the script only invokes the endpoint and reports the result.

**Tech Stack:** Spring Boot Web MVC, MockMvc, PowerShell, existing Redis/MySQL-backed hot-house service

---

### Task 1: Add a controller test for the rebuild trigger

**Files:**
- Create: `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`
- Test: `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`

- [ ] **Step 1: Write the failing test**

```java
@WebMvcTest(HouseController.class)
class HouseControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IHouseService houseService;

    @MockBean
    private IHouseCommandService houseCommandService;

    @MockBean
    private HouseEsSyncService houseEsSyncService;

    @MockBean
    private HouseHotService houseHotService;

    @Test
    void rebuildHotCacheShouldInvokeHotService() throws Exception {
        mockMvc.perform(post("/house/hot/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Redis 热门房源缓存重建完成"));

        verify(houseHotService).rebuildHotRanking();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=HouseControllerWebMvcTest test`
Expected: FAIL because `/house/hot/rebuild` does not exist and/or `HouseController` does not inject `HouseHotService`

- [ ] **Step 3: Write minimal implementation**

```java
private final HouseHotService houseHotService;

@PostMapping("/hot/rebuild")
public Result<Void> rebuildHotCache() {
    houseHotService.rebuildHotRanking();
    return Result.success("Redis 热门房源缓存重建完成", null);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=HouseControllerWebMvcTest test`
Expected: PASS

### Task 2: Allow the temporary rebuild trigger to be called by a local script

**Files:**
- Modify: `src/main/java/cn/yy/myrent/config/WebMvcConfig.java`
- Test: `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void rebuildHotCacheShouldBeAccessibleWithoutToken() throws Exception {
    mockMvc.perform(post("/house/hot/rebuild"))
            .andExpect(status().isOk());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=HouseControllerWebMvcTest test`
Expected: FAIL with `401 Unauthorized` if the interceptor still blocks the route

- [ ] **Step 3: Write minimal implementation**

```java
.excludePathPatterns(
        "/user/login",
        "/user/register",
        "/house/hot/rebuild",
        ...
);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=HouseControllerWebMvcTest test`
Expected: PASS

### Task 3: Add the trigger script

**Files:**
- Create: `scripts/rebuild-hot-house-cache.ps1`

- [ ] **Step 1: Write the script**

```powershell
param(
    [string]$BaseUrl = "http://localhost:8084"
)

$uri = "$BaseUrl/house/hot/rebuild"

try {
    $response = Invoke-RestMethod -Method Post -Uri $uri -TimeoutSec 30
    Write-Host "Hot-house Redis cache rebuild succeeded."
    if ($null -ne $response.message) {
        Write-Host $response.message
    }
    exit 0
} catch {
    Write-Error "Hot-house Redis cache rebuild failed: $($_.Exception.Message)"
    exit 1
}
```

- [ ] **Step 2: Run a syntax check**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\rebuild-hot-house-cache.ps1 -BaseUrl http://localhost:8084`
Expected: If backend is running, script prints success; otherwise it fails with a clear network or HTTP message

### Task 4: Verify the end-to-end behavior

**Files:**
- Modify: none

- [ ] **Step 1: Run focused backend test**

Run: `mvn -Dtest=HouseControllerWebMvcTest test`
Expected: PASS

- [ ] **Step 2: Run the rebuild script against the local backend**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\rebuild-hot-house-cache.ps1`
Expected: Success message from the endpoint

- [ ] **Step 3: Confirm UI dependency can read refreshed data**

Run: Trigger `/house/hot?page=1&size=10` from frontend or API client after rebuild
Expected: Response reflects refreshed Redis ranking data instead of stale cache
