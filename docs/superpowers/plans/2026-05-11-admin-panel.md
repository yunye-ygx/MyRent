# MyRent 管理后台实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建完整管理后台，后端新增 /api/admin/** 真实接口写入数据库，前端 Vue 页面调用这些接口，实现房源管理、用户管理、订单管理和数据概览。

**Architecture:** 后端新增 AdminInterceptor 拦截 /api/admin/** 校验 role=1；User/House 实体新增字段；AdminController 提供所有管理接口复用现有 Service 层。前端集成 Element Plus，新增 /admin/* 路由和管理页面，所有操作真实调用后端。

**Tech Stack:** Spring Boot 3 + MyBatis-Plus（后端），Vue 3 + Element Plus + Pinia + Axios（前端）

---

## 文件变更清单

**后端：**
- Modify: `src/main/java/cn/yy/myrent/entity/User.java`
- Modify: `src/main/java/cn/yy/myrent/entity/House.java`
- Modify: `src/main/java/cn/yy/myrent/common/UserContext.java`
- Modify: `src/main/java/cn/yy/myrent/common/JwtTokenUtil.java`
- Modify: `src/main/java/cn/yy/myrent/config/UserContextInterceptor.java`
- Modify: `src/main/java/cn/yy/myrent/vo/LoginVO.java`
- Modify: `src/main/java/cn/yy/myrent/controller/UserController.java`
- Create: `src/main/java/cn/yy/myrent/config/AdminInterceptor.java`
- Modify: `src/main/java/cn/yy/myrent/config/WebMvcConfig.java`
- Create: `src/main/java/cn/yy/myrent/vo/AdminDashboardVO.java`
- Create: `src/main/java/cn/yy/myrent/vo/AdminOrderVO.java`
- Create: `src/main/java/cn/yy/myrent/controller/AdminController.java`

**前端：**
- Modify: `frontend/package.json`
- Modify: `frontend/src/main.js`
- Modify: `frontend/src/stores/auth.js`
- Modify: `frontend/src/router/index.js`
- Create: `frontend/src/api/admin.js`
- Create: `frontend/src/views/admin/AdminLayout.vue`
- Create: `frontend/src/views/admin/DashboardView.vue`
- Create: `frontend/src/views/admin/UsersView.vue`
- Create: `frontend/src/views/admin/HousesView.vue`
- Create: `frontend/src/views/admin/HouseFormView.vue`
- Create: `frontend/src/views/admin/OrdersView.vue`
- Create: `frontend/src/views/admin/PaymentsView.vue`

---

### Task 1: 数据库迁移

**Files:** 直接在 MySQL 执行 SQL

- [ ] **Step 1: 执行迁移 SQL**

连接 MySQL（数据库 `rent`）执行：

```sql
ALTER TABLE user ADD COLUMN role TINYINT NOT NULL DEFAULT 0 COMMENT '0=普通用户 1=管理员';
ALTER TABLE user ADD COLUMN banned TINYINT NOT NULL DEFAULT 0 COMMENT '0=正常 1=已封禁';
ALTER TABLE house ADD COLUMN audit_status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待审核 1=已通过 2=已拒绝';
UPDATE house SET audit_status = 1;
-- 将自己的账号设为管理员（替换手机号）
UPDATE user SET role = 1 WHERE phone = '你的手机号';
```

- [ ] **Step 2: 验证**

```sql
DESCRIBE user;
SELECT id, phone, role, banned FROM user LIMIT 5;
SELECT id, title, audit_status FROM house LIMIT 5;
```

预期：user 有 role/banned 列，house 有 audit_status 列，存量 house 的 audit_status=1。

- [ ] **Step 3: Commit**

```bash
git commit --allow-empty -m "chore: DB 迁移 user.role/banned, house.audit_status"
```

---

### Task 2: 后端实体 + UserContext + JwtTokenUtil

**Files:**
- Modify: `src/main/java/cn/yy/myrent/entity/User.java`
- Modify: `src/main/java/cn/yy/myrent/entity/House.java`
- Modify: `src/main/java/cn/yy/myrent/common/UserContext.java`
- Modify: `src/main/java/cn/yy/myrent/common/JwtTokenUtil.java`
- Modify: `src/main/java/cn/yy/myrent/config/UserContextInterceptor.java`

- [ ] **Step 1: User 实体追加字段**

在 `User.java` 的 `createTime` 字段后追加：

```java
    private Integer role;
    private Integer banned;
```

- [ ] **Step 2: House 实体追加字段**

在 `House.java` 的 `createTime` 字段前追加：

```java
    @TableField("audit_status")
    private Integer auditStatus;
```

- [ ] **Step 3: 替换 UserContext.java 全文**

```java
package cn.yy.myrent.common;

public class UserContext {
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Integer> ROLE_HOLDER = new ThreadLocal<>();
    private UserContext() {}
    public static void setCurrentUserId(Long userId) { USER_ID_HOLDER.set(userId); }
    public static Long getCurrentUserId() { return USER_ID_HOLDER.get(); }
    public static Long requireCurrentUserId() {
        Long userId = USER_ID_HOLDER.get();
        if (userId == null) throw new IllegalStateException("未登录或用户上下文缺失");
        return userId;
    }
    public static void setCurrentRole(Integer role) { ROLE_HOLDER.set(role); }
    public static Integer getCurrentRole() { return ROLE_HOLDER.get(); }
    public static void clear() { USER_ID_HOLDER.remove(); ROLE_HOLDER.remove(); }
}
```

- [ ] **Step 4: JwtTokenUtil 新增 role 支持**

将现有 `generateToken(Long userId, String phone)` 方法替换为两个方法：

```java
    public String generateToken(Long userId, String phone) {
        return generateToken(userId, phone, 0);
    }

    public String generateToken(Long userId, String phone, Integer role) {
        if (userId == null) throw new IllegalArgumentException("userId不能为空");
        long now = Instant.now().getEpochSecond();
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("phone", phone);
        payload.put("role", role != null ? role : 0);
        payload.put("iat", now);
        payload.put("exp", now + jwtProperties.getExpireSeconds());
        try {
            String headerPart = URL_ENCODER.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payloadPart = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            String signingInput = headerPart + "." + payloadPart;
            String signaturePart = URL_ENCODER.encodeToString(hmacSha256(signingInput));
            return signingInput + "." + signaturePart;
        } catch (Exception e) {
            throw new RuntimeException("生成token失败", e);
        }
    }

    public Integer parseRole(String token) {
        Map<String, Object> claims = parseAndVerify(token);
        Object roleObj = claims.get("role");
        if (roleObj == null) return 0;
        return ((Number) roleObj).intValue();
    }
```

- [ ] **Step 5: UserContextInterceptor 写入 role**

在 `UserContextInterceptor.java` 的 `UserContext.setCurrentUserId(userId);` 之后追加一行：

```java
            UserContext.setCurrentRole(jwtTokenUtil.parseRole(token));
```

- [ ] **Step 6: 编译**

```bash
cd C:/javapractice/MyRent && ./mvnw compile -q
```

预期：BUILD SUCCESS。

- [ ] **Step 7: Commit**

```bash
git add src/main/java/cn/yy/myrent/entity/User.java src/main/java/cn/yy/myrent/entity/House.java src/main/java/cn/yy/myrent/common/UserContext.java src/main/java/cn/yy/myrent/common/JwtTokenUtil.java src/main/java/cn/yy/myrent/config/UserContextInterceptor.java
git commit -m "feat(admin): 实体新增字段，JWT/UserContext 支持 role"
```

---

### Task 3: LoginVO + UserController + AdminInterceptor + WebMvcConfig

**Files:**
- Modify: `src/main/java/cn/yy/myrent/vo/LoginVO.java`
- Modify: `src/main/java/cn/yy/myrent/controller/UserController.java`
- Create: `src/main/java/cn/yy/myrent/config/AdminInterceptor.java`
- Modify: `src/main/java/cn/yy/myrent/config/WebMvcConfig.java`

- [ ] **Step 1: LoginVO 追加 role**

在 `LoginVO.java` 的 `name` 字段后追加：

```java
    private Integer role;
```

- [ ] **Step 2: 替换 UserController.login 方法**

```java
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "手机号+密码登录")
    public Result<LoginVO> login(@RequestBody UserPhoneReqDTO reqDTO) {
        if (reqDTO == null) return Result.error("参数不能为空");
        try {
            User user = userService.loginByPhone(reqDTO.getPhone(), reqDTO.getPassword());
            if (Integer.valueOf(1).equals(user.getBanned())) {
                return Result.error("账号已被封禁，请联系管理员");
            }
            Integer role = user.getRole() != null ? user.getRole() : 0;
            String token = jwtTokenUtil.generateToken(user.getId(), user.getPhone(), role);
            LoginVO loginVO = new LoginVO();
            loginVO.setToken(token);
            loginVO.setUserId(user.getId());
            loginVO.setPhone(user.getPhone());
            loginVO.setName(user.getName());
            loginVO.setRole(role);
            return Result.success("登录成功", loginVO);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
```

- [ ] **Step 3: 创建 AdminInterceptor.java**

```java
package cn.yy.myrent.config;

import cn.yy.myrent.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class AdminInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        Integer role = UserContext.getCurrentRole();
        if (!Integer.valueOf(1).equals(role)) {
            log.warn("管理员鉴权失败: role={}, path={}", role, request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }
}
```

- [ ] **Step 4: WebMvcConfig 注入并注册 AdminInterceptor**

在 `WebMvcConfig.java` 中追加注入：

```java
    @Autowired
    private AdminInterceptor adminInterceptor;
```

在 `addInterceptors` 方法中，现有 `registry.addInterceptor(userContextInterceptor)...` 块之后追加：

```java
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**");
```

- [ ] **Step 5: 编译**

```bash
./mvnw compile -q
```

预期：BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/yy/myrent/vo/LoginVO.java src/main/java/cn/yy/myrent/controller/UserController.java src/main/java/cn/yy/myrent/config/AdminInterceptor.java src/main/java/cn/yy/myrent/config/WebMvcConfig.java
git commit -m "feat(admin): 登录返回 role，AdminInterceptor 拦截 /api/admin/**"
```

---

### Task 4: AdminDashboardVO + AdminOrderVO + AdminController

**Files:**
- Create: `src/main/java/cn/yy/myrent/vo/AdminDashboardVO.java`
- Create: `src/main/java/cn/yy/myrent/vo/AdminOrderVO.java`
- Create: `src/main/java/cn/yy/myrent/controller/AdminController.java`

- [ ] **Step 1: 创建 AdminDashboardVO.java**

```java
package cn.yy.myrent.vo;

import lombok.Data;

@Data
public class AdminDashboardVO {
    private Long totalUsers;
    private Long totalHouses;
    private Long todayOrders;
    private Long todayRevenue; // 单位：分
}
```

- [ ] **Step 2: 创建 AdminOrderVO.java**

```java
package cn.yy.myrent.vo;

import cn.yy.myrent.entity.Order;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminOrderVO extends Order {
    private String userPhone;
    private String userName;
    private String houseTitle;
}
```

- [ ] **Step 3: 创建 AdminController.java（概览 + 用户管理）**

新建文件，内容：

```java
package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.IOrderService;
import cn.yy.myrent.service.IPaymentService;
import cn.yy.myrent.service.IUserService;
import cn.yy.myrent.vo.AdminDashboardVO;
import cn.yy.myrent.vo.AdminOrderVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private IUserService userService;
    @Autowired private IHouseService houseService;
    @Autowired private IOrderService orderService;
    @Autowired private IPaymentService paymentService;

    @GetMapping("/dashboard")
    @Operation(summary = "数据概览")
    public Result<AdminDashboardVO> dashboard() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        AdminDashboardVO vo = new AdminDashboardVO();
        vo.setTotalUsers(userService.count());
        vo.setTotalHouses(houseService.count());
        vo.setTodayOrders(orderService.lambdaQuery().ge(Order::getCreateTime, todayStart).count());
        List<Payment> paid = paymentService.lambdaQuery()
                .isNotNull(Payment::getPaidTime).ge(Payment::getPaidTime, todayStart).list();
        vo.setTodayRevenue(paid.stream().mapToLong(p -> p.getPayAmount() != null ? p.getPayAmount() : 0).sum());
        return Result.success(vo);
    }

    @GetMapping("/users")
    @Operation(summary = "分页查询用户")
    public Result<Page<User>> listUsers(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String keyword) {
        var query = userService.lambdaQuery().orderByDesc(User::getId);
        if (StringUtils.hasText(keyword))
            query.and(q -> q.like(User::getPhone, keyword).or().like(User::getName, keyword));
        Page<User> result = query.page(new Page<>(page, size));
        result.getRecords().forEach(u -> u.setPassword(null));
        return Result.success(result);
    }

    @PutMapping("/users/{id}/ban")
    @Operation(summary = "封禁用户")
    public Result<Void> banUser(@PathVariable Long id) {
        return userService.lambdaUpdate().eq(User::getId, id).set(User::getBanned, 1).update()
                ? Result.success() : Result.error("用户不存在");
    }

    @PutMapping("/users/{id}/unban")
    @Operation(summary = "解封用户")
    public Result<Void> unbanUser(@PathVariable Long id) {
        return userService.lambdaUpdate().eq(User::getId, id).set(User::getBanned, 0).update()
                ? Result.success() : Result.error("用户不存在");
    }
```

- [ ] **Step 4: 追加房源管理方法到 AdminController（接上文，在 unbanUser 后）**

```java
    @GetMapping("/houses")
    @Operation(summary = "分页查询房源")
    public Result<Page<House>> listHouses(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String keyword) {
        var query = houseService.lambdaQuery().orderByDesc(House::getId);
        if (auditStatus != null) query.eq(House::getAuditStatus, auditStatus);
        if (StringUtils.hasText(city)) query.eq(House::getCity, city);
        if (StringUtils.hasText(keyword)) query.like(House::getTitle, keyword);
        return Result.success(query.page(new Page<>(page, size)));
    }

    @PostMapping("/houses")
    @Operation(summary = "发布房源")
    public Result<Long> createHouse(@RequestBody House house) {
        house.setId(null);
        house.setStatus(1);
        house.setVersion(0);
        house.setAuditStatus(1);
        house.setCreateTime(LocalDateTime.now());
        houseService.save(house);
        return Result.success("发布成功", house.getId());
    }

    @PutMapping("/houses/{id}")
    @Operation(summary = "编辑房源")
    public Result<Void> updateHouse(@PathVariable Long id, @RequestBody House house) {
        house.setId(id);
        house.setStatus(null);
        house.setVersion(null);
        return houseService.updateById(house) ? Result.success() : Result.error("房源不存在");
    }

    @DeleteMapping("/houses/{id}")
    @Operation(summary = "删除房源")
    public Result<Void> deleteHouse(@PathVariable Long id) {
        return houseService.removeById(id) ? Result.success() : Result.error("房源不存在");
    }

    @PutMapping("/houses/{id}/approve")
    @Operation(summary = "审核通过")
    public Result<Void> approveHouse(@PathVariable Long id) {
        return houseService.lambdaUpdate().eq(House::getId, id).set(House::getAuditStatus, 1).update()
                ? Result.success() : Result.error("房源不存在");
    }

    @PutMapping("/houses/{id}/reject")
    @Operation(summary = "审核拒绝")
    public Result<Void> rejectHouse(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return houseService.lambdaUpdate().eq(House::getId, id).set(House::getAuditStatus, 2).update()
                ? Result.success() : Result.error("房源不存在");
    }
```

- [ ] **Step 5: 追加订单 + 支付方法到 AdminController（接上文，在 rejectHouse 后，加上类的结束括号）**

```java
    @GetMapping("/orders")
    @Operation(summary = "分页查询订单")
    public Result<Page<AdminOrderVO>> listOrders(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Integer status) {
        var query = orderService.lambdaQuery().orderByDesc(Order::getCreateTime);
        if (status != null) query.eq(Order::getStatus, status);
        Page<Order> orderPage = query.page(new Page<>(page, size));
        Page<AdminOrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        voPage.setRecords(orderPage.getRecords().stream().map(order -> {
            AdminOrderVO vo = new AdminOrderVO();
            vo.setId(order.getId()); vo.setOrderNo(order.getOrderNo());
            vo.setUserId(order.getUserId()); vo.setHouseId(order.getHouseId());
            vo.setAmount(order.getAmount()); vo.setStatus(order.getStatus());
            vo.setExpireTime(order.getExpireTime()); vo.setPaidTime(order.getPaidTime());
            vo.setCreateTime(order.getCreateTime());
            User user = userService.getById(order.getUserId());
            if (user != null) { vo.setUserPhone(user.getPhone()); vo.setUserName(user.getName()); }
            House house = houseService.getById(order.getHouseId());
            if (house != null) vo.setHouseTitle(house.getTitle());
            return vo;
        }).toList());
        return Result.success(voPage);
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "订单详情")
    public Result<AdminOrderVO> getOrder(@PathVariable Long id) {
        Order order = orderService.getById(id);
        if (order == null) return Result.error("订单不存在");
        AdminOrderVO vo = new AdminOrderVO();
        vo.setId(order.getId()); vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId()); vo.setHouseId(order.getHouseId());
        vo.setAmount(order.getAmount()); vo.setStatus(order.getStatus());
        vo.setExpireTime(order.getExpireTime()); vo.setPaidTime(order.getPaidTime());
        vo.setCreateTime(order.getCreateTime());
        User user = userService.getById(order.getUserId());
        if (user != null) { vo.setUserPhone(user.getPhone()); vo.setUserName(user.getName()); }
        House house = houseService.getById(order.getHouseId());
        if (house != null) vo.setHouseTitle(house.getTitle());
        return Result.success(vo);
    }

    @GetMapping("/payments")
    @Operation(summary = "分页查询支付记录")
    public Result<Page<Payment>> listPayments(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Integer status) {
        var query = paymentService.lambdaQuery().orderByDesc(Payment::getCreateTime);
        if (status != null) query.eq(Payment::getStatus, status);
        return Result.success(query.page(new Page<>(page, size)));
    }
}
```

- [ ] **Step 6: 编译**

```bash
./mvnw compile -q
```

预期：BUILD SUCCESS。

- [ ] **Step 7: 启动后端，验证接口可访问**

```bash
./mvnw spring-boot:run
```

用 Knife4j（http://localhost:8084/doc.html）或 curl 测试：
1. 用管理员账号登录 `POST /user/login`，拿到 token（role=1）
2. `GET /api/admin/dashboard` 带 Bearer token → 返回统计数据
3. 用普通用户 token 访问 `GET /api/admin/dashboard` → 返回 403

- [ ] **Step 8: Commit**

```bash
git add src/main/java/cn/yy/myrent/vo/AdminDashboardVO.java src/main/java/cn/yy/myrent/vo/AdminOrderVO.java src/main/java/cn/yy/myrent/controller/AdminController.java
git commit -m "feat(admin): AdminController 全部接口（概览/用户/房源/订单/支付）"
```

---

### Task 5: 前端安装 Element Plus + 更新 main.js

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/src/main.js`

- [ ] **Step 1: 安装 Element Plus**

```bash
cd frontend
npm install element-plus@2.9.7
```

- [ ] **Step 2: 读取 frontend/src/main.js 当前内容**

读取文件，确认现有 `app.use(...)` 调用顺序。

- [ ] **Step 3: 在 main.js 中注册 Element Plus**

在现有 `import` 语句末尾追加：

```js
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
```

在 `app.use(router)` 之后追加：

```js
app.use(ElementPlus, { locale: zhCn })
```

- [ ] **Step 4: 验证前端能启动**

```bash
npm run dev
```

预期：Vite 启动成功，无报错。

- [ ] **Step 5: Commit**

```bash
cd ..
git add frontend/package.json frontend/package-lock.json frontend/src/main.js
git commit -m "feat(admin): 安装并注册 Element Plus"
```

---

### Task 6: 前端 auth store + router 更新

**Files:**
- Modify: `frontend/src/stores/auth.js`
- Modify: `frontend/src/router/index.js`

- [ ] **Step 1: auth.js 的 login action 中保存 role**

将 `auth.js` 中 `login` action 里的 `this.profile = { ... }` 替换为：

```js
      this.profile = {
        userId: loginVO.userId,
        phone: loginVO.phone,
        name: loginVO.name,
        city: loginVO.city || this.currentCity,
        role: loginVO.role || 0
      }
```

- [ ] **Step 2: auth.js 新增 isAdmin getter**

在 `isLoggedIn` getter 之后追加：

```js
    isAdmin(state) {
      return state.profile?.role === 1
    },
```

- [ ] **Step 3: router/index.js 新增 admin 路由**

在 `routes` 数组末尾（`]` 之前）追加：

```js
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { requiresAdmin: true },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', name: 'admin-dashboard', component: () => import('@/views/admin/DashboardView.vue') },
      { path: 'users', name: 'admin-users', component: () => import('@/views/admin/UsersView.vue') },
      { path: 'houses', name: 'admin-houses', component: () => import('@/views/admin/HousesView.vue') },
      { path: 'houses/new', name: 'admin-house-new', component: () => import('@/views/admin/HouseFormView.vue') },
      { path: 'houses/:id/edit', name: 'admin-house-edit', component: () => import('@/views/admin/HouseFormView.vue') },
      { path: 'orders', name: 'admin-orders', component: () => import('@/views/admin/OrdersView.vue') },
      { path: 'payments', name: 'admin-payments', component: () => import('@/views/admin/PaymentsView.vue') }
    ]
  }
```

- [ ] **Step 4: router/index.js 更新路由守卫**

在文件顶部 import 区追加：

```js
import { getProfile } from '@/utils/storage'
```

将现有 `router.beforeEach` 替换为：

```js
router.beforeEach((to) => {
  const token = getToken()
  if (to.meta.requiresAdmin) {
    if (!token) return { path: '/login', query: { redirect: to.fullPath } }
    const profile = getProfile()
    if (!profile || profile.role !== 1) return '/home'
    return true
  }
  if (to.meta.requiresAuth && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if ((to.path === '/login' || to.path === '/register') && token) {
    return '/home'
  }
  return true
})
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/stores/auth.js frontend/src/router/index.js
git commit -m "feat(admin): auth store 存储 role，router 新增 /admin/* 路由和守卫"
```

---

### Task 7: frontend/src/api/admin.js

**Files:**
- Create: `frontend/src/api/admin.js`

- [ ] **Step 1: 创建 admin.js**

```js
import http from './http'

// 概览
export function fetchDashboard() {
  return http.get('/api/admin/dashboard')
}

// 用户管理
export function fetchAdminUsers(params = {}) {
  return http.get('/api/admin/users', { params })
}
export function banUser(id) {
  return http.put(`/api/admin/users/${id}/ban`)
}
export function unbanUser(id) {
  return http.put(`/api/admin/users/${id}/unban`)
}

// 房源管理
export function fetchAdminHouses(params = {}) {
  return http.get('/api/admin/houses', { params })
}
export function createAdminHouse(data) {
  return http.post('/api/admin/houses', data)
}
export function updateAdminHouse(id, data) {
  return http.put(`/api/admin/houses/${id}`, data)
}
export function deleteAdminHouse(id) {
  return http.delete(`/api/admin/houses/${id}`)
}
export function approveHouse(id) {
  return http.put(`/api/admin/houses/${id}/approve`)
}
export function rejectHouse(id, reason) {
  return http.put(`/api/admin/houses/${id}/reject`, { reason })
}

// 订单管理
export function fetchAdminOrders(params = {}) {
  return http.get('/api/admin/orders', { params })
}
export function fetchAdminOrderDetail(id) {
  return http.get(`/api/admin/orders/${id}`)
}

// 支付管理
export function fetchAdminPayments(params = {}) {
  return http.get('/api/admin/payments', { params })
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/api/admin.js
git commit -m "feat(admin): admin API 模块"
```

---

### Task 8: AdminLayout.vue

**Files:**
- Create: `frontend/src/views/admin/AdminLayout.vue`

- [ ] **Step 1: 创建 AdminLayout.vue**

```vue
<template>
  <el-container style="height: 100vh;">
    <el-aside width="200px" style="background:#1a1a2e; overflow:hidden;">
      <div style="padding:16px 20px; color:#fff; font-weight:bold; font-size:15px; border-bottom:1px solid #2a2a4a;">
        🏠 MyRent Admin
      </div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#1a1a2e"
        text-color="#aaa"
        active-text-color="#fff"
      >
        <el-menu-item index="/admin/dashboard">📊 数据概览</el-menu-item>
        <el-menu-item-group title="房源管理">
          <el-menu-item index="/admin/houses">📋 房源列表</el-menu-item>
          <el-menu-item index="/admin/houses/new">➕ 发布房源</el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="用户管理">
          <el-menu-item index="/admin/users">👥 用户列表</el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="订单管理">
          <el-menu-item index="/admin/orders">📦 订单列表</el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="支付管理">
          <el-menu-item index="/admin/payments">💳 支付记录</el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background:#fff; border-bottom:1px solid #e8e8e8; display:flex; align-items:center; justify-content:space-between;">
        <span style="color:#666; font-size:14px;">{{ $route.meta.title || '管理后台' }}</span>
        <el-button type="danger" size="small" text @click="handleLogout">退出登录</el-button>
      </el-header>
      <el-main style="background:#f5f7fa; padding:20px;">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/admin/AdminLayout.vue
git commit -m "feat(admin): AdminLayout 侧边栏布局"
```

---

### Task 9: DashboardView.vue

**Files:**
- Create: `frontend/src/views/admin/DashboardView.vue`

- [ ] **Step 1: 创建 DashboardView.vue**

```vue
<template>
  <div>
    <h2 style="margin:0 0 20px; font-size:18px; color:#333;">数据概览</h2>
    <el-row :gutter="16" style="margin-bottom:20px;">
      <el-col :span="6">
        <el-card shadow="never">
          <div style="color:#999; font-size:13px;">总用户数</div>
          <div style="font-size:28px; font-weight:bold; margin:8px 0;">{{ stats.totalUsers ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div style="color:#999; font-size:13px;">总房源数</div>
          <div style="font-size:28px; font-weight:bold; margin:8px 0;">{{ stats.totalHouses ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div style="color:#999; font-size:13px;">今日订单</div>
          <div style="font-size:28px; font-weight:bold; margin:8px 0;">{{ stats.todayOrders ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div style="color:#999; font-size:13px;">今日收入</div>
          <div style="font-size:28px; font-weight:bold; margin:8px 0;">¥{{ todayRevenueYuan }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { fetchDashboard } from '@/api/admin'

const stats = ref({})

const todayRevenueYuan = computed(() => {
  if (stats.value.todayRevenue == null) return '-'
  return (stats.value.todayRevenue / 100).toFixed(2)
})

onMounted(async () => {
  stats.value = await fetchDashboard()
})
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/admin/DashboardView.vue
git commit -m "feat(admin): DashboardView 数据概览页"
```

---

### Task 10: UsersView.vue

**Files:**
- Create: `frontend/src/views/admin/UsersView.vue`

- [ ] **Step 1: 创建 UsersView.vue**

```vue
<template>
  <div>
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
      <h2 style="margin:0; font-size:18px; color:#333;">用户管理</h2>
      <el-input v-model="keyword" placeholder="搜索手机号/昵称" style="width:240px;" clearable @keyup.enter="loadUsers" @clear="loadUsers">
        <template #append><el-button @click="loadUsers">搜索</el-button></template>
      </el-input>
    </div>
    <el-table :data="users" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="name" label="昵称" />
      <el-table-column prop="role" label="角色" width="90">
        <template #default="{ row }">
          <el-tag :type="row.role === 1 ? 'danger' : 'info'" size="small">
            {{ row.role === 1 ? '管理员' : '普通用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="banned" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.banned === 1 ? 'danger' : 'success'" size="small">
            {{ row.banned === 1 ? '已封禁' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="180" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.banned !== 1" type="danger" size="small" text @click="handleBan(row)">封禁</el-button>
          <el-button v-else type="success" size="small" text @click="handleUnban(row)">解封</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px; justify-content:flex-end; display:flex;"
      v-model:current-page="page" v-model:page-size="size"
      :total="total" layout="total, prev, pager, next"
      @current-change="loadUsers" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchAdminUsers, banUser, unbanUser } from '@/api/admin'

const users = ref([])
const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)

async function loadUsers() {
  loading.value = true
  try {
    const res = await fetchAdminUsers({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    users.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleBan(row) {
  await ElMessageBox.confirm(`确定封禁用户「${row.name}」吗？`, '封禁确认', { type: 'warning' })
  await banUser(row.id)
  ElMessage.success('已封禁')
  loadUsers()
}

async function handleUnban(row) {
  await unbanUser(row.id)
  ElMessage.success('已解封')
  loadUsers()
}

onMounted(loadUsers)
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/admin/UsersView.vue
git commit -m "feat(admin): UsersView 用户管理页"
```

---

### Task 11: HousesView.vue

**Files:**
- Create: `frontend/src/views/admin/HousesView.vue`

- [ ] **Step 1: 创建 HousesView.vue**

```vue
<template>
  <div>
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
      <h2 style="margin:0; font-size:18px; color:#333;">房源管理</h2>
      <div style="display:flex; gap:8px;">
        <el-select v-model="auditStatus" placeholder="审核状态" clearable style="width:120px;" @change="loadHouses">
          <el-option label="待审核" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已拒绝" :value="2" />
        </el-select>
        <el-input v-model="keyword" placeholder="搜索标题" style="width:200px;" clearable @keyup.enter="loadHouses" @clear="loadHouses" />
        <el-button type="primary" @click="$router.push('/admin/houses/new')">+ 发布房源</el-button>
      </div>
    </div>
    <el-table :data="houses" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="city" label="城市" width="80" />
      <el-table-column prop="region" label="区域" width="100" />
      <el-table-column label="价格" width="110">
        <template #default="{ row }">¥{{ (row.price / 100).toFixed(0) }}/月</template>
      </el-table-column>
      <el-table-column label="审核状态" width="100">
        <template #default="{ row }">
          <el-tag :type="auditTagType(row.auditStatus)" size="small">{{ auditLabel(row.auditStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.auditStatus === 0" type="success" size="small" text @click="handleApprove(row)">通过</el-button>
          <el-button v-if="row.auditStatus === 0" type="danger" size="small" text @click="handleReject(row)">拒绝</el-button>
          <el-button type="primary" size="small" text @click="$router.push(`/admin/houses/${row.id}/edit`)">编辑</el-button>
          <el-button type="danger" size="small" text @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px; justify-content:flex-end; display:flex;"
      v-model:current-page="page" v-model:page-size="size"
      :total="total" layout="total, prev, pager, next"
      @current-change="loadHouses" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchAdminHouses, approveHouse, rejectHouse, deleteAdminHouse } from '@/api/admin'

const houses = ref([])
const loading = ref(false)
const auditStatus = ref(null)
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)

function auditLabel(s) { return { 0: '待审核', 1: '已通过', 2: '已拒绝' }[s] ?? '-' }
function auditTagType(s) { return { 0: 'warning', 1: 'success', 2: 'danger' }[s] ?? 'info' }

async function loadHouses() {
  loading.value = true
  try {
    const res = await fetchAdminHouses({
      page: page.value, size: size.value,
      auditStatus: auditStatus.value ?? undefined,
      keyword: keyword.value || undefined
    })
    houses.value = res.records
    total.value = res.total
  } finally { loading.value = false }
}

async function handleApprove(row) {
  await approveHouse(row.id)
  ElMessage.success('已通过')
  loadHouses()
}

async function handleReject(row) {
  const { value: reason } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝房源', { confirmButtonText: '确定', cancelButtonText: '取消' })
  await rejectHouse(row.id, reason)
  ElMessage.success('已拒绝')
  loadHouses()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除房源「${row.title}」吗？`, '删除确认', { type: 'warning' })
  await deleteAdminHouse(row.id)
  ElMessage.success('已删除')
  loadHouses()
}

onMounted(loadHouses)
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/admin/HousesView.vue
git commit -m "feat(admin): HousesView 房源管理页"
```

---

### Task 12: HouseFormView.vue

**Files:**
- Create: `frontend/src/views/admin/HouseFormView.vue`

- [ ] **Step 1: 创建 HouseFormView.vue**

```vue
<template>
  <div style="max-width:720px;">
    <h2 style="margin:0 0 20px; font-size:18px; color:#333;">{{ isEdit ? '编辑房源' : '发布房源' }}</h2>
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" v-loading="loading">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" placeholder="房源标题" />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="城市" prop="city">
            <el-input v-model="form.city" placeholder="如：上海" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="区域" prop="region">
            <el-input v-model="form.region" placeholder="如：浦东新区" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="月租金(元)" prop="priceYuan">
            <el-input-number v-model="form.priceYuan" :min="1" :precision="0" style="width:100%;" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="押金(元)" prop="depositYuan">
            <el-input-number v-model="form.depositYuan" :min="0" :precision="0" style="width:100%;" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="经度" prop="longitude">
            <el-input v-model="form.longitude" placeholder="如：121.4737" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="纬度" prop="latitude">
            <el-input v-model="form.latitude" placeholder="如：31.2304" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="租型" prop="rentType">
        <el-select v-model="form.rentType" style="width:100%;">
          <el-option label="整租" :value="1" />
          <el-option label="合租" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="配套设施">
        <el-checkbox v-model="form.nearSubway" :true-value="1" :false-value="0">近地铁</el-checkbox>
        <el-checkbox v-model="form.privateBathroom" :true-value="1" :false-value="0">独卫</el-checkbox>
        <el-checkbox v-model="form.hasBalcony" :true-value="1" :false-value="0">有阳台</el-checkbox>
        <el-checkbox v-model="form.civilWaterElectric" :true-value="1" :false-value="0">民水民电</el-checkbox>
        <el-checkbox v-model="form.supportStudentDepositFree" :true-value="1" :false-value="0">学生免押</el-checkbox>
      </el-form-item>
      <el-form-item label="发布者ID" prop="publisherUserId">
        <el-input-number v-model="form.publisherUserId" :min="1" style="width:100%;" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">{{ isEdit ? '保存修改' : '发布房源' }}</el-button>
        <el-button @click="$router.push('/admin/houses')">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createAdminHouse, updateAdminHouse } from '@/api/admin'
import { fetchHouseById } from '@/api/house'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const formRef = ref()
const loading = ref(false)
const submitting = ref(false)
const isEdit = computed(() => !!route.params.id)

const form = ref({
  title: '', city: '', region: '',
  priceYuan: 1000, depositYuan: 1000,
  longitude: '', latitude: '',
  rentType: 1,
  nearSubway: 0, privateBathroom: 0, hasBalcony: 0,
  civilWaterElectric: 0, supportStudentDepositFree: 0,
  publisherUserId: authStore.userId
})

const rules = {
  title: [{ required: true, message: '请输入标题' }],
  city: [{ required: true, message: '请输入城市' }],
  region: [{ required: true, message: '请输入区域' }],
  longitude: [{ required: true, message: '请输入经度' }],
  latitude: [{ required: true, message: '请输入纬度' }],
  rentType: [{ required: true, message: '请选择租型' }],
  publisherUserId: [{ required: true, message: '请输入发布者ID' }]
}

onMounted(async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const house = await fetchHouseById(route.params.id)
    Object.assign(form.value, {
      ...house,
      priceYuan: Math.round(house.price / 100),
      depositYuan: Math.round(house.depositAmount / 100)
    })
  } finally { loading.value = false }
})

async function handleSubmit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    const payload = {
      ...form.value,
      price: Math.round(form.value.priceYuan * 100),
      depositAmount: Math.round(form.value.depositYuan * 100),
      totalCost: Math.round((form.value.priceYuan + form.value.depositYuan) * 100)
    }
    if (isEdit.value) {
      await updateAdminHouse(route.params.id, payload)
      ElMessage.success('修改成功')
    } else {
      await createAdminHouse(payload)
      ElMessage.success('发布成功')
    }
    router.push('/admin/houses')
  } finally { submitting.value = false }
}
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/admin/HouseFormView.vue
git commit -m "feat(admin): HouseFormView 发布/编辑房源表单"
```

---

### Task 13: OrdersView.vue

**Files:**
- Create: `frontend/src/views/admin/OrdersView.vue`

- [ ] **Step 1: 创建 OrdersView.vue**

```vue
<template>
  <div>
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
      <h2 style="margin:0; font-size:18px; color:#333;">订单管理</h2>
      <el-select v-model="status" placeholder="订单状态" clearable style="width:140px;" @change="loadOrders">
        <el-option label="待支付" :value="1" />
        <el-option label="已支付" :value="2" />
        <el-option label="已关闭" :value="3" />
      </el-select>
    </div>
    <el-table :data="orders" border stripe v-loading="loading" @row-click="handleRowClick" style="cursor:pointer;">
      <el-table-column prop="orderNo" label="订单号" width="200" show-overflow-tooltip />
      <el-table-column prop="userPhone" label="用户手机" width="130" />
      <el-table-column prop="userName" label="用户昵称" width="100" />
      <el-table-column prop="houseTitle" label="房源" min-width="160" show-overflow-tooltip />
      <el-table-column label="金额" width="110">
        <template #default="{ row }">¥{{ row.amount ? (row.amount / 100).toFixed(2) : '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="orderTagType(row.status)" size="small">{{ orderLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
    </el-table>
    <el-pagination style="margin-top:16px; justify-content:flex-end; display:flex;"
      v-model:current-page="page" v-model:page-size="size"
      :total="total" layout="total, prev, pager, next"
      @current-change="loadOrders" />

    <el-dialog v-model="detailVisible" title="订单详情" width="500px">
      <el-descriptions :column="2" border v-if="detail">
        <el-descriptions-item label="订单号" :span="2">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="用户手机">{{ detail.userPhone }}</el-descriptions-item>
        <el-descriptions-item label="用户昵称">{{ detail.userName }}</el-descriptions-item>
        <el-descriptions-item label="房源" :span="2">{{ detail.houseTitle }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ detail.amount ? (detail.amount / 100).toFixed(2) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ orderLabel(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ detail.paidTime ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { fetchAdminOrders, fetchAdminOrderDetail } from '@/api/admin'

const orders = ref([])
const loading = ref(false)
const status = ref(null)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const detailVisible = ref(false)
const detail = ref(null)

function orderLabel(s) { return { 1: '待支付', 2: '已支付', 3: '已关闭' }[s] ?? '-' }
function orderTagType(s) { return { 1: 'warning', 2: 'success', 3: 'info' }[s] ?? 'info' }

async function loadOrders() {
  loading.value = true
  try {
    const res = await fetchAdminOrders({ page: page.value, size: size.value, status: status.value ?? undefined })
    orders.value = res.records
    total.value = res.total
  } finally { loading.value = false }
}

async function handleRowClick(row) {
  detail.value = await fetchAdminOrderDetail(row.id)
  detailVisible.value = true
}

onMounted(loadOrders)
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/admin/OrdersView.vue
git commit -m "feat(admin): OrdersView 订单管理页"
```

---

### Task 14: PaymentsView.vue + 端到端验证

**Files:**
- Create: `frontend/src/views/admin/PaymentsView.vue`

- [ ] **Step 1: 创建 PaymentsView.vue**

```vue
<template>
  <div>
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
      <h2 style="margin:0; font-size:18px; color:#333;">支付记录</h2>
      <el-select v-model="status" placeholder="支付状态" clearable style="width:140px;" @change="loadPayments">
        <el-option label="待支付" :value="1" />
        <el-option label="已支付" :value="2" />
        <el-option label="已失败" :value="3" />
      </el-select>
    </div>
    <el-table :data="payments" border stripe v-loading="loading">
      <el-table-column prop="paymentNo" label="支付单号" width="200" show-overflow-tooltip />
      <el-table-column prop="orderNo" label="订单号" width="200" show-overflow-tooltip />
      <el-table-column prop="userId" label="用户ID" width="90" />
      <el-table-column label="金额" width="110">
        <template #default="{ row }">¥{{ row.payAmount ? (row.payAmount / 100).toFixed(2) : '-' }}</template>
      </el-table-column>
      <el-table-column prop="channel" label="渠道" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="payTagType(row.status)" size="small">{{ payLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="paidTime" label="支付时间" width="180" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
    </el-table>
    <el-pagination style="margin-top:16px; justify-content:flex-end; display:flex;"
      v-model:current-page="page" v-model:page-size="size"
      :total="total" layout="total, prev, pager, next"
      @current-change="loadPayments" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { fetchAdminPayments } from '@/api/admin'

const payments = ref([])
const loading = ref(false)
const status = ref(null)
const page = ref(1)
const size = ref(10)
const total = ref(0)

function payLabel(s) { return { 1: '待支付', 2: '已支付', 3: '已失败' }[s] ?? '-' }
function payTagType(s) { return { 1: 'warning', 2: 'success', 3: 'danger' }[s] ?? 'info' }

async function loadPayments() {
  loading.value = true
  try {
    const res = await fetchAdminPayments({ page: page.value, size: size.value, status: status.value ?? undefined })
    payments.value = res.records
    total.value = res.total
  } finally { loading.value = false }
}

onMounted(loadPayments)
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/admin/PaymentsView.vue
git commit -m "feat(admin): PaymentsView 支付记录页"
```

- [ ] **Step 3: 端到端验证**

启动后端和前端：

```bash
# 终端1
cd C:/javapractice/MyRent && ./mvnw spring-boot:run

# 终端2
cd C:/javapractice/MyRent/frontend && npm run dev
```

按顺序验证：

1. 浏览器打开 `http://localhost:5173/login`，用管理员账号登录
2. 登录成功后访问 `http://localhost:5173/admin` → 自动跳转到 `/admin/dashboard`，显示统计数据
3. 访问 `/admin/users` → 显示用户列表，点击「封禁」→ 确认弹窗 → 封禁成功，状态变为「已封禁」
4. 访问 `/admin/houses` → 显示房源列表，筛选「待审核」→ 点击「通过」→ 状态变为「已通过」
5. 访问 `/admin/houses/new` → 填写表单 → 点击「发布房源」→ 成功后跳转到房源列表，新房源出现
6. 访问 `/admin/orders` → 显示订单列表，点击任意行 → 弹出详情弹窗
7. 访问 `/admin/payments` → 显示支付记录
8. 用普通用户 token 直接访问 `http://localhost:8084/api/admin/dashboard` → 返回 403

- [ ] **Step 4: 最终 Commit**

```bash
git add .
git commit -m "feat(admin): 管理后台完整实现（后端接口 + 前端页面）"
```
