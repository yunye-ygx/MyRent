# 智能推荐页面 UI 重设计

> 范围：仅改前端。后端、API 合约、数据结构不动。

## 背景

当前 `/ai-recommend` 页面与顶部导航「智能推荐」入口的视觉问题：

1. **导航入口**：橙黄渐变半胶囊上顶、自绘的小狗 SVG（`DogAssistantIcon.vue`）线条生硬、五官比例怪，是整个站点视觉最弱的一环。
2. **英雄区**：米色渐变长条，大标题堆砌，吉祥物无情感化呈现。
3. **对话区**：助手用浅米泡、用户用深棕泡，配色沉闷，和「AI」主题无关联。
4. **需求摘要**：六宫格信息量不足但占了 ~35% 宽度，下半截视觉空荡。
5. **受众错配**：站点目标用户是大学生 / 青年租客，当前风格偏「成熟日式文艺」，没有能让年轻人产生记忆点的元素。

## 目标

把 `/ai-recommend` 页面和它在全站唯一的入口（顶部导航徽章 + 移动端 tab bar 徽章）一起重做成**马卡龙风的 AI 吉祥物专属视觉区**，让这个功能在站点里自成一个「可爱小世界」，成为年轻用户的记忆点。

非目标：
- 不改其他页面（首页 / 找房 / 消息 / 我的）。
- 不改后端 API、数据结构、业务逻辑。
- 不改 `AiRecommendView.vue` 的状态流转和 API 调用。
- 不改现有测试用例依赖的 DOM 钩子（见"测试契约"章节）。

## 设计总览

### 吉祥物：Roam

一朵长了脸的小云，名字 **Roam**（漫游／自由地走，呼应用户想到的"云野＝自由"）。

外形关键特征：
- 云朵轮廓由 5 个圆拼成（1 个主椭圆 + 4 个副圆），描边色 `#b8c8e0`，填充色 `#ffffff`。
- 两只竖椭圆眼（纯黑 `#2d3748`，带小白高光），柔和弯嘴。
- 粉色腮红（`#ffb8c8` + 透明度 0.7）。
- 装饰：周围偶尔配一颗黄色四角星（`#ffd166`）或小圆点。

Roam 将用三个尺寸呈现：
- **英雄区大图**：约 130×110px，带上浮动画（`@keyframes float` 垂直上下 6px / 3.5 秒）。
- **头像尺寸**：28-36px，用在对话气泡前、摘要栏、导航徽章。
- **徽章尺寸**：42-44px，用在导航栏 badge 和移动端 tab。

所有尺寸共享同一套 SVG 绘制路径，通过 `width` / `height` 控制缩放。

### 配色系统（仅作用于智能推荐视觉区）

| 用途 | 值 | 来源 |
|---|---|---|
| 天空背景主色 | `#eaf4ff → #f8f4ff → #fff8e6` 渐变 | 英雄区 sky-bg |
| 云朵白 | `#ffffff` | Roam 身体 |
| 云朵描边灰蓝 | `#b8c8e0` | Roam 描边 |
| 对话助手泡背景 | `#ffffff` | 气泡 |
| 对话用户泡渐变 | `#a8d8ff → #7db5f0` | 气泡 |
| 主操作渐变 | `#7aa3e0 → #9bb5e8` | 发送按钮、CTA |
| 已知条件胶囊 | `#f0f5ff` 底 + `#3b4a6b` 字 + `#8cb4f0` 虚浅边 | 摘要 |
| 待补充胶囊 | `#fff8e6` 底 + `#b88c2a` 字 + `#e8c488` 虚线边 | 摘要 |
| 腮红/点缀粉 | `#ffb8c8` | Roam、装饰 |
| 点缀黄 | `#ffd166` | 小星星 |
| 正文色 | `#2d3748` / `#3b4a6b` / `#5b6a8a` | 不同层级 |

这套颜色**不进 `tokens.css`**，而是作用域限定在智能推荐相关组件内，避免污染全站。

### 动效

- Roam 英雄区：`float` 上下 6px，3.5s 循环，`ease-in-out`。
- 导航徽章光晕环：`spin` 慢转 360°，18s 一圈，`linear`，几乎不分神。
- 发送按钮、CTA：`hover` 微微上移 1px + 阴影加深。
- 对话气泡出现：不做额外动画（保持轻量，Vue 渲染即可）。
- 尊重 `prefers-reduced-motion`：所有动画用媒体查询禁用。

## 组件改造清单

### 1. 导航栏徽章（`AppTopNav.vue`）

替换 `.featured-shell` 橙胶囊 + 向上位移 10px 的方案。

新结构：
```
.is-featured
  .featured-shell                      (垂直 flex 容器，含 margin-top: -30px 向上凸出)
    .featured-core                     (白色圆底 64×64 + 阴影)
      ::before                           光晕 (radial-gradient, inset:-6px)
      ::after                            虚线环 (border 2px dashed, inset:-2px, spin 18s)
      <RoamMascotIcon size="tiny" />
    .featured-label                    ("智能推荐" 文本)
```

关键样式：
- `.featured-core` 圆形容器 `border-radius: 50%`, `width/height: 64px`, `background: #fff`, `box-shadow: 0 10px 24px rgba(80,120,200,0.18)`。
- `.featured-shell` 用 `margin-top: -30px` 让徽章向上凸出导航条。
- 虚线环 `.featured-core::after` + `border: 2px dashed rgba(140,180,240,0.55)` + `animation: spin 18s linear infinite`。
- 光晕 `.featured-core::before` + `radial-gradient(circle, rgba(184,216,255,0.5), transparent 70%)` + `z-index: -1`。
- 移除原 `.featured-shell` 里的橙黄渐变 / 棕边 / `translateY(-10px)` / `border-radius: 999px 999px 22px 22px`。

保留测试契约：
- `data-nav="/ai-recommend"`、`is-featured` 类、`is-active` 态、`.featured-label` 文字「智能推荐」全部保留。

### 2. 移动端 tab 徽章（`AppTabBar.vue`）

同一套设计的简化版：
- 圆形白底 + 轻阴影 + Roam 小头。
- 移除虚线环旋转（移动端节能 + 底部 tab 上的旋转会干扰）。
- 光晕保留但更淡。
- 其他 tab 保持不变。

### 3. 新组件：`RoamMascotIcon.vue`（替换 `DogAssistantIcon.vue`）

新建在 `src/components/icons/RoamMascotIcon.vue`。

API：
- `size`: `'tiny' | 'mini' | 'big'`，默认 `'mini'`。
  - `tiny` / `mini`: 简版（无星星装饰），用于徽章和对话头像。
  - `big`: 完整版（含黄星星 + 投影椭圆），用于英雄区。

内部用一个 SVG `<symbol>` 定义，模板里通过 `<use>` 复用路径，减小体积。

**不删除 `DogAssistantIcon.vue`**：先保留，等新组件上线稳定后再清理。但 `AppTopNav.vue` / `AppTabBar.vue` / `AiRecommendView.vue` 的 import 全部改成 `RoamMascotIcon`。

### 4. 英雄区（`AiRecommendView.vue` 内的 `.ai-hero`）

改用"居中 Roam + 自我介绍 + 双 CTA"布局：

```
.ai-hero
  .sky-bg                 (绝对定位背景，天空渐变 + 小白点阵)
  .ai-hero__content       (居中)
    <RoamMascotIcon size="big" />       (带 float 动画)
    <h1>Hi，我是 Roam，帮你找个家</h1>
    <p>告诉我你的预算、地段、整租合租，我从真实房源里挑给你看。</p>
    .ai-hero__actions
      <button class="ghost-btn">重新开始</button>
      <button class="primary-btn">✨ 现在开始聊</button>
```

「现在开始聊」按钮的行为：滚动到下方对话输入框并聚焦 textarea。（纯前端行为，不调 API。）

样式要点：
- `border-radius: 28px`，背景用天空渐变 + 小白点阵（圆点模拟星星 / 气泡）。
- `h1` 字号 `clamp(20px, 3vw, 26px)`。
- `<p>` 最大宽 500px，颜色 `#5b6a8a`。
- 主按钮用渐变蓝 `linear-gradient(135deg, #7aa3e0, #9bb5e8)` + 圆角 999px + 阴影。
- "重新开始"（ghost）改为白色半透明 + 蓝灰文字，和新背景协调。

### 5. 需求摘要状态条（横向）

**从侧栏移到英雄区正下方**，改成一条横向 summary-bar。

新组件：`AiRequirementBar.vue`（替代原 `AiRequirementSummary.vue`，或直接改写原组件）。

DOM 结构：
```
.summary-bar
  .summary-bar__avatar            (小 Roam 头像 32px)
  .summary-bar__label             ("Roam 知道：")
  .summary-bar__tags              (flex 横铺，已知和待补充混排)
    .tag.done                     (已知：浅蓝底 + 深蓝字 + 小 key 标签)
    .tag.todo                     (待补充：奶黄底 + 虚线边 + 问号)
  .summary-bar__progress-ring     (conic-gradient 进度环 + 内层 "4/6" 文字)
```

行为：
- 进度环由 `knownCount / totalCount` 计算，`conic-gradient(#7aa3e0 0deg Xdeg, #f0f5ff Xdeg 360deg)`。
- 「还差 N 项就能筛房源啦～」提示可作为工具提示（tooltip）或在 `missingSlots.length > 0` 时做成条下方的一行小字，保留原逻辑。
- 已知槽位（city / locationName / budgetYuan / rentMode / priority / preferences）逐一渲染为 done tag；缺失的按 `missingSlots` 渲染为 todo tag。

槽位字段映射（沿用现有逻辑）：
- `city` → "城市"
- `locationName` → "区域"  
- `budgetYuan` → "预算"（值为 `{n}/月`）
- `rentMode` → "方式"（`WHOLE`→"整租"，`SHARED`→"合租"）
- `priority` → "优先"（`PRICE`→"价格"，`COMMUTE`→"通勤"，`QUALITY`→"品质"）
- `preferences` → "偏好"（`nearSubway`→"近地铁" 等，已有 `preferencesText` 逻辑）

### 6. 对话区（`.ai-chat-card`）

取消左右两栏。对话卡独占整行。

新结构顺序（自上而下）：
1. 标题 + 提示（保留原文案）
2. 快捷提示 chips（样式升级）
3. 对话流（气泡升级）
4. preview panel（样式升级，仍用 `AiPreviewPanel.vue`）
5. recommendation panel（样式升级，仍用 `AiRecommendationPanel.vue`）
6. 输入区 + 发送按钮

#### 6a. 快捷提示 chips（`AiQuickPromptChips.vue`）

样式改动：
- 背景：`#f0f5ff`，边 `1px solid rgba(140,180,240,0.3)`。
- 文字：`#3b4a6b`。
- hover：`translateY(-1px)` + 背景 `#e4edfc`。

#### 6b. 对话气泡（`AiChatBubble.vue`）

改云朵轮廓气泡，助手气泡前出现 Roam 小头像：

```
.chat-row
  <RoamMascotIcon size="mini" v-if="role === 'assistant'" />
  .bubble[.is-assistant|.is-user]
    .bubble-meta   (ROLE 标签 - 保留)
    .bubble-body   (正文)
```

助手气泡样式：
- 白色背景，圆角 `26px 26px 26px 8px`（左下尖）。
- 用 `::before` 和 `::after` 伪元素做两个小白圆（左上凸一个、右上凸一个），制造云朵轮廓。
- 外层 `filter: drop-shadow(0 3px 10px rgba(100,130,200,0.1))` 让阴影跟着云朵形走。
- 无描边或仅超浅描边 `rgba(184,200,224,0.3)`。

用户气泡样式：
- 渐变 `linear-gradient(135deg, #a8d8ff, #7db5f0)`，文字白色。
- 圆角 `22px 22px 6px 22px`。
- 阴影 `0 4px 12px rgba(125,181,240,0.3)`。
- 粗体正文。

布局：
- `chat-row` 用 flex，`role === 'user'` 时 `justify-content: flex-end`，无头像。
- `role === 'assistant'` 时头像在左侧 `align-items: flex-end`。

保留：
- `.bubble-meta` 文字（"ROAM" / "你"）- 但把原"智能推荐"改为"ROAM"以贴吉祥物人设。
- 白空格保留（`white-space: pre-wrap`）。

### 7. 输入区

- textarea：背景 `#f8fbff`，边 `1px solid rgba(184,200,224,0.4)`，圆角 `18px`，聚焦时加 `box-shadow: 0 0 0 4px rgba(122,163,224,0.15)` 蓝色外光晕。
- 发送按钮 `.primary-btn`：在 `AiRecommendView.vue` 作用域覆盖，改为渐变蓝 `linear-gradient(135deg, #7aa3e0, #9bb5e8)`，圆角 999px，阴影 `0 6px 14px rgba(122,163,224,0.35)`。
- 全局 `.primary-btn` 不变（不要影响其他页面）。

### 8. 预览卡 / 推荐结果卡

`AiPreviewPanel.vue`：
- 外层面板背景改 `linear-gradient(135deg, #f8fbff, #eef5ff)`，边 `rgba(140,180,240,0.25)`。
- 内部 `.preview-card` 白底 + 浅蓝描边。
- tag 改 `#f0f5ff` 底 + `#3b4a6b` 字。
- CTA 按钮用渐变蓝。

`AiRecommendationPanel.vue`：
- `.recommend-card` 背景白色 + 浅蓝描边（替换原棕米渐变）。
- `.recommend-price` 用深蓝强调色。
- 三级理由 tag 保持分色但映射到新色系：
  - `--primary` → 浅蓝色底深蓝字
  - `--secondary` → 更浅灰蓝底
  - `--relaxation` → 奶黄底（保留预算放宽的语义）
- 悬停效果保留（微上移 + 加深阴影）。

## 整体页面结构（`AiRecommendView.vue` template 更新后）

```
<div class="page ai-page">
  <section class="ai-hero">...</section>             <!-- 新 -->
  <AiRequirementBar :slots :missingSlots />          <!-- 新（横向） -->
  <section class="ai-chat-card">                     <!-- 独占整行 -->
    <header>...</header>
    <AiQuickPromptChips :prompts @select />
    <div class="chat-thread">
      <AiChatBubble v-for=... />
      <AiPreviewPanel v-if=... />
      <AiRecommendationPanel v-if=... />
    </div>
    <p v-if="errorText" class="error-text">...</p>
    <form class="chat-form">
      <textarea class="chat-input" />
      <div class="chat-actions">
        <button class="primary-btn" />
      </div>
    </form>
  </section>
</div>
```

不再有 `.ai-layout` 两栏布局。

## 测试契约（必须保留的 DOM 钩子）

`AiRecommendView.spec.js` 依赖：
- `.chat-input` / `.chat-form`
- `[data-test="preview-select-<groupKey>"]`
- 文本：「智能推荐」、「当前已知条件」、「先告诉我你的预算」、距离/通勤数字、各级 reason 文本
- 现有 `chatAiRecommend({ message, interaction })` 调用合约

改造影响的文本点：
- 「当前已知条件」→ 改为「Roam 知道的」或「Roam 当前知道」。**这是测试里 `expect(wrapper.text()).toContain('当前已知条件')` 的断言 —— 需要同步更新测试文本**（仅文案层的测试修改，不算逻辑改动）。
- 其他文案断言（"智能推荐"、"先告诉我你的预算"等）保持不变。

`AppTopNav.spec.js` 依赖：
- `data-nav="/ai-recommend"`、`is-featured`、`is-active` 类名
- `.city-select`
- 文本「青年租房」、「智能推荐」

`AppTabBar.spec.js` 依赖：
- 现有选择器 + 同位置保留 Roam 图标

所有保留的钩子在组件改造时**不许删或改名**。

## 文件变更清单

新增：
- `frontend/src/components/icons/RoamMascotIcon.vue`
- （可选）`frontend/src/components/ai/AiRequirementBar.vue`（或直接改写 `AiRequirementSummary.vue`）

修改：
- `frontend/src/views/AiRecommendView.vue` — 布局大改 + scoped 样式重写
- `frontend/src/components/layout/AppTopNav.vue` — `.featured-shell` 系列样式改写、换 icon 导入
- `frontend/src/components/AppTabBar.vue` — tab 徽章样式改写、换 icon 导入
- `frontend/src/components/ai/AiChatBubble.vue` — 云朵气泡样式 + 插入 Roam 头像
- `frontend/src/components/ai/AiQuickPromptChips.vue` — 样式
- `frontend/src/components/ai/AiPreviewPanel.vue` — 配色
- `frontend/src/components/ai/AiRecommendationPanel.vue` — 配色
- `frontend/src/components/ai/AiRequirementSummary.vue` — 重写为横向状态条（或新建 Bar 后删除此文件的内容）
- `frontend/src/views/__tests__/AiRecommendView.spec.js` — 仅同步改"当前已知条件"→"Roam 知道的"这一处文案断言

保留（暂不删）：
- `frontend/src/components/icons/DogAssistantIcon.vue` — 上线稳定后独立任务清理

## 响应式

- 桌面（≥1024px）：主内容区 `max-width: 960px` 居中，对话卡 / 状态条均受此约束，避免大屏上一行过宽。
- 平板（768–1024px）：内容自适应，对话卡和状态条默认全宽。
- 手机（<768px）：状态条胶囊自动折行；进度环保留；英雄区 Roam 缩到 100px。对话气泡最大宽度调到 85%。

## 无障碍

- Roam SVG 根节点 `aria-hidden="true"`（纯装饰）。
- 徽章按钮（RouterLink）保留 `aria-label="智能推荐"`。
- 动画启用 `@media (prefers-reduced-motion: reduce)` 禁用 float / spin。
- 色彩对比度：助手气泡 `#2d3748 on #fff` > 13:1，用户气泡 `#fff on #7db5f0` > 4.5:1。
- 摘要 todo 胶囊 `#b88c2a on #fff8e6` 接近 4.5:1，可通过调深文字到 `#a37920` 确保达标。

## 风险

1. **其他页面的 `.primary-btn` / `.ghost-btn`**：用全局 class，我们只在 `AiRecommendView.vue` 作用域内覆盖，保证不污染。
2. **测试的"当前已知条件"断言**：会一起改掉，这是唯一的测试文案同步点。
3. **`DogAssistantIcon` 残留引用**：用 Grep 清点所有 import 点，一次全替，避免漏改。
4. **动画性能**：仅使用 transform / opacity 类属性，不用 box-shadow 等触发重绘的，保证 60fps。

## 开发顺序建议

1. 写 `RoamMascotIcon.vue`（基础素材）。
2. 改 `AppTopNav.vue` 徽章 → 跑 `AppTopNav.spec.js` 确认通过。
3. 改 `AppTabBar.vue` 徽章 → 跑 `AppTabBar.spec.js` 确认通过。
4. 改 `AiRecommendView.vue` 英雄区 + 布局骨架。
5. 改 `AiRequirementSummary.vue` → 横向状态条。
6. 改 `AiChatBubble.vue` + `AiQuickPromptChips.vue`。
7. 改 `AiPreviewPanel.vue` + `AiRecommendationPanel.vue` 配色。
8. 改 `AiRecommendView.spec.js` 文案断言、跑整套测试。
9. 手动过一遍页面，验收所有 hover / 动画 / 响应式断点。
