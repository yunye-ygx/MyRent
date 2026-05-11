# MyRent 管理后台设计文档

**日期：** 2026-05-11  
**状态：** 待实现

---

## 1. 背景与目标

目前所有平台管理操作（发布房源、查看用户、处理订单）都通过 Knife4j 手动调接口完成，没有可用的管理前端页面。本次目标是构建一个完整的管理后台，替代 Knife4j 的手动操作，覆盖房源管理、用户管理、订单管理和数据概览。

管理员与普通用户共用同一套账号体系，通过 `role` 字段区分身份。不单独设立商家端，管理员承担房源发布职责。

---

## 2. 数据库变更

### 2.1 User 表新增 role 字段

```sql
ALTER TABLE user ADD COLUMN role TINYINT NOT NULL DEFAULT 0 COMMENT '0=普通用户 1=管理员';
```

### 2.2 House 表新增 auditStatus 字段

```sql
-- 新增字段，默认 0（待审核）
ALTER TABLE house ADD COLUMN audit_status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待审核 1=已通过 2=已拒绝';
-- 存量数据视为已通过
UPDATE house SET audit_status = 1;
```

现有 `status` 字段保持不变（1=可租，2=已锁定）。新发布的房源默认 `audit_status=0`（待审核），审核通过后变为 1。

### 2.3 User 表新增 banned 字段

```sql
ALTER TABLE user ADD COLUMN banned TINYINT NOT NULL DEFAULT 0 COMMENT '0=正常 1=已封禁';
```

登录接口校验 `banned=0`，被封禁用户返回 403 并提示账号已封禁。

---

## 3. 后端设计

### 3.1 认证与鉴权

- `JwtTokenUtil.generateToken(userId, phone, role)` — 生成 token 时携带 role
- `JwtTokenUtil.getRole(token)` — 解析 role
- 新增 `AdminInterceptor`，拦截 `/api/admin/**`，校验 role=1，否则返回 403
- `WebMvcConfig` 注册 `AdminInterceptor`，排除 `/user/login`、`/user/register`

### 3.2 新增接口

#### 数据概览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/dashboard` | 返回总用户数、总房源数、今日订单数、今日收入（分） |

#### 用户管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/users` | 分页查询用户列表，支持 `keyword`（手机号/姓名）、`page`、`size` |
| PUT | `/api/admin/users/{id}/ban` | 封禁用户（role 不变，新增 `banned` 字段） |
| PUT | `/api/admin/users/{id}/unban` | 解封用户 |

> `banned` 字段已在第 2.3 节定义，登录时校验 banned=0。

#### 房源管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/houses` | 分页查询所有房源，支持 `auditStatus`、`city`、`keyword`、`page`、`size` |
| POST | `/api/admin/houses` | 发布新房源（复用现有 House 字段） |
| PUT | `/api/admin/houses/{id}` | 编辑房源 |
| DELETE | `/api/admin/houses/{id}` | 删除房源 |
| PUT | `/api/admin/houses/{id}/approve` | 审核通过（audit_status=1） |
| PUT | `/api/admin/houses/{id}/reject` | 审核拒绝（audit_status=2），body 携带拒绝原因 |

#### 订单管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/orders` | 分页查询所有订单，支持 `status`、`keyword`（订单号/用户手机）、`page`、`size` |
| GET | `/api/admin/orders/{id}` | 订单详情（含关联用户、房源、支付信息） |

#### 支付管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/payments` | 分页查询支付记录，支持 `status`、`keyword`、`page`、`size` |

### 3.3 新增文件

```
src/main/java/cn/yy/myrent/
├── controller/
│   └── AdminController.java        # 所有 /api/admin/** 接口
├── config/
│   └── AdminInterceptor.java       # role=1 鉴权拦截器
└── vo/
    └── AdminDashboardVO.java       # 概览数据 VO
```

`AdminController` 按模块分方法，不拆成多个 Controller（规模不大，保持简单）。

---

## 4. 前端设计

### 4.1 技术选型

- Element Plus（按需引入，仅 admin 路由下加载）
- 现有 Vue 3 + Vite + Pinia + Axios 栈不变

### 4.2 新增文件结构

```
frontend/src/
├── api/
│   └── admin.js                    # 所有 /api/admin/** 请求封装
├── views/admin/
│   ├── AdminLayout.vue             # 侧边栏 + 顶栏布局容器
│   ├── DashboardView.vue           # 数据概览（4 个统计卡片 + 待审核房源快捷列表）
│   ├── UsersView.vue               # 用户管理（表格 + 搜索 + 封禁/解封操作）
│   ├── HousesView.vue              # 房源列表（表格 + 状态筛选 + 审核/编辑/删除操作）
│   ├── HouseFormView.vue           # 发布/编辑房源（表单，复用 House 所有字段）
│   ├── OrdersView.vue              # 订单管理（表格 + 状态筛选 + 详情弹窗）
│   └── PaymentsView.vue            # 支付记录（表格 + 状态筛选）
```

### 4.3 路由设计

```js
// router/index.js 新增
{
  path: '/admin',
  component: AdminLayout,
  meta: { requiresAdmin: true },
  children: [
    { path: '', redirect: '/admin/dashboard' },
    { path: 'dashboard', component: DashboardView },
    { path: 'users', component: UsersView },
    { path: 'houses', component: HousesView },
    { path: 'houses/new', component: HouseFormView },
    { path: 'houses/:id/edit', component: HouseFormView },
    { path: 'orders', component: OrdersView },
    { path: 'payments', component: PaymentsView },
  ]
}
```

路由守卫：检查 token 中 role=1，否则跳转 `/home`。

### 4.4 布局

`AdminLayout.vue` 包含：
- 左侧深色侧边栏（200px），含 Logo、分组导航菜单、当前用户名
- 顶部白色 header（面包屑 + 当前时间）
- 右侧内容区（`<router-view />`）

### 4.5 各页面核心交互

**DashboardView** — 4 个统计卡片（总用户、总房源、今日订单、今日收入）+ 待审核房源快捷表格（直接通过/拒绝）

**UsersView** — `el-table` 展示用户列表，支持手机号/姓名搜索，操作列含封禁/解封按钮，封禁时弹 `el-confirm` 确认

**HousesView** — `el-table` 展示房源列表，顶部 `el-select` 筛选审核状态，操作列含审核通过、审核拒绝（弹输入框填原因）、编辑、删除

**HouseFormView** — `el-form` 包含 House 所有字段（标题、城市、区域、价格、押金、经纬度、租型、配套设施等），新建和编辑复用同一个页面

**OrdersView** — `el-table` 展示订单，支持状态筛选，点击行展开详情弹窗（含用户信息、房源信息、支付信息）

**PaymentsView** — `el-table` 展示支付记录，支持状态筛选

---

## 5. 鉴权流程

```
用户登录 → 后端返回 JWT（含 role）
→ 前端存入 localStorage
→ 访问 /admin/* → 路由守卫解析 token 中 role
  → role=1：放行
  → role≠1：跳转 /home
→ 每个 API 请求携带 token
→ AdminInterceptor 校验 role=1
  → 通过：执行业务逻辑
  → 失败：返回 403
```

---

## 6. 不在本次范围内

- 商家端（房东独立管理页面）
- 退款手动处理（支付记录只读查看）
- 评论管理
- 系统配置页面
- 数据图表（折线图、柱状图等可视化）
