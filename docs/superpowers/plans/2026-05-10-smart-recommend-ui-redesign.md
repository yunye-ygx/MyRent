# 智能推荐 UI 重设计实施计划（Roam 云朵版）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `/ai-recommend` 页面及其在顶部导航、移动端 tab 中的入口重做成马卡龙云朵风的专属视觉区，引入吉祥物 Roam，解决当前页面"丑"和右侧空荡的问题。

**Architecture:** 仅前端改造。新增一个 `RoamMascotIcon.vue` SVG 组件作为全站唯一的吉祥物来源，在 3 个位置（顶部导航徽章、移动 tab、英雄区）和对话气泡里复用。页面主布局从"英雄区 + 左对话+右摘要两栏"改为"英雄区 + 摘要横向状态条 + 对话卡全宽"。配色使用作用域限定的天空蓝 + 奶白 + 奶黄，不进 `tokens.css`。

**Tech Stack:** Vue 3 `<script setup>` + scoped CSS + Vitest + @vue/test-utils。

**Spec:** `docs/superpowers/specs/2026-05-10-smart-recommend-ui-redesign-design.md`

---

## File Structure

**新建：**
- `frontend/src/components/icons/RoamMascotIcon.vue` — 唯一吉祥物 SVG 组件，`size` prop 切换 tiny / mini / big

**修改：**
- `frontend/src/components/layout/AppTopNav.vue` — 徽章样式（圆形白底 + 光晕 + 虚线环），换 icon 导入
- `frontend/src/components/AppTabBar.vue` — 移动端 tab 徽章简化版，换 icon 导入
- `frontend/src/components/ai/AiChatBubble.vue` — 云朵气泡 + 插入 Roam 头像
- `frontend/src/components/ai/AiQuickPromptChips.vue` — 样式改天空蓝
- `frontend/src/components/ai/AiRequirementSummary.vue` — 重写为横向状态条（含进度环）
- `frontend/src/components/ai/AiPreviewPanel.vue` — 配色改天空蓝系
- `frontend/src/components/ai/AiRecommendationPanel.vue` — 配色改天空蓝系
- `frontend/src/views/AiRecommendView.vue` — 布局重组 + 英雄区重做 + scoped 样式
- `frontend/src/views/__tests__/AiRecommendView.spec.js` — 仅同步文案断言"当前已知条件"→"Roam 知道的"

**保留（暂不删）：**
- `frontend/src/components/icons/DogAssistantIcon.vue`（旧资源，稳定后另起任务清理）

---

## Task 1：创建 RoamMascotIcon 组件

**Files:**
- Create: `frontend/src/components/icons/RoamMascotIcon.vue`
- Test: `frontend/src/components/__tests__/RoamMascotIcon.spec.js`

- [ ] **Step 1: 写失败测试**

```js
// frontend/src/components/__tests__/RoamMascotIcon.spec.js
import { mount } from '@vue/test-utils'
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'

describe('RoamMascotIcon', () => {
  it('renders an svg with aria-hidden by default', () => {
    const wrapper = mount(RoamMascotIcon)
    const svg = wrapper.find('svg')
    expect(svg.exists()).toBe(true)
    expect(svg.attributes('aria-hidden')).toBe('true')
  })

  it('applies size="mini" class by default', () => {
    const wrapper = mount(RoamMascotIcon)
    expect(wrapper.classes()).toContain('roam-mascot-icon')
    expect(wrapper.classes()).toContain('roam-mascot-icon--mini')
  })

  it('respects size prop', () => {
    const wrapper = mount(RoamMascotIcon, { props: { size: 'big' } })
    expect(wrapper.classes()).toContain('roam-mascot-icon--big')
  })

  it('renders star decorations only in big size', () => {
    const mini = mount(RoamMascotIcon, { props: { size: 'mini' } })
    expect(mini.find('[data-deco="star"]').exists()).toBe(false)

    const big = mount(RoamMascotIcon, { props: { size: 'big' } })
    expect(big.find('[data-deco="star"]').exists()).toBe(true)
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && npx vitest run src/components/__tests__/RoamMascotIcon.spec.js`
Expected: FAIL（组件不存在）

- [ ] **Step 3: 实现组件**

```vue
<!-- frontend/src/components/icons/RoamMascotIcon.vue -->
<template>
  <svg
    class="roam-mascot-icon"
    :class="sizeClass"
    :viewBox="viewBox"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <!-- 云朵身体 -->
    <ellipse cx="32" cy="36" rx="22" ry="17" fill="#ffffff" stroke="#b8c8e0" :stroke-width="strokeWidth"/>
    <circle cx="14" cy="30" r="9" fill="#ffffff" stroke="#b8c8e0" :stroke-width="strokeWidth"/>
    <circle cx="50" cy="30" r="9" fill="#ffffff" stroke="#b8c8e0" :stroke-width="strokeWidth"/>
    <circle cx="23" cy="23" r="8" fill="#ffffff" stroke="#b8c8e0" :stroke-width="strokeWidth"/>
    <circle cx="41" cy="23" r="9" fill="#ffffff" stroke="#b8c8e0" :stroke-width="strokeWidth"/>
    <!-- 眼 -->
    <ellipse cx="25" cy="35" rx="2" ry="2.4" fill="#2d3748"/>
    <ellipse cx="39" cy="35" rx="2" ry="2.4" fill="#2d3748"/>
    <ellipse cx="25.7" cy="34" rx="0.7" ry="0.9" fill="#ffffff"/>
    <ellipse cx="39.7" cy="34" rx="0.7" ry="0.9" fill="#ffffff"/>
    <!-- 嘴 -->
    <path d="M28 40 Q32 43 36 40" stroke="#2d3748" stroke-width="1.6" stroke-linecap="round" fill="none"/>
    <!-- 腮红 -->
    <ellipse cx="18" cy="39" rx="3" ry="1.8" fill="#ffb8c8" opacity="0.7"/>
    <ellipse cx="46" cy="39" rx="3" ry="1.8" fill="#ffb8c8" opacity="0.7"/>
    <!-- 装饰（仅 big 尺寸显示） -->
    <g v-if="size === 'big'" data-deco="star">
      <path d="M56 10L57 13L60 14L57 15L56 18L55 15L52 14L55 13L56 10Z" fill="#ffd166"/>
      <circle cx="8" cy="14" r="1.2" fill="#ffd166"/>
      <circle cx="58" cy="46" r="1" fill="#ffb8c8"/>
    </g>
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  size: {
    type: String,
    default: 'mini',
    validator: (v) => ['tiny', 'mini', 'big'].includes(v)
  }
})

const sizeClass = computed(() => `roam-mascot-icon--${props.size}`)
const strokeWidth = computed(() => (props.size === 'tiny' ? 2 : 1.8))
const viewBox = computed(() => '0 0 64 64')
</script>

<style scoped>
.roam-mascot-icon {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd frontend && npx vitest run src/components/__tests__/RoamMascotIcon.spec.js`
Expected: PASS (4/4)

- [ ] **Step 5: 提交**

```bash
rtk git add frontend/src/components/icons/RoamMascotIcon.vue frontend/src/components/__tests__/RoamMascotIcon.spec.js
rtk git commit -m "feat(ai-recommend): add RoamMascotIcon component (云朵吉祥物)"
```

---

## Task 2：顶部导航徽章（AppTopNav）

**Files:**
- Modify: `frontend/src/components/layout/AppTopNav.vue`
- Test: `frontend/src/components/__tests__/AppTopNav.spec.js`（已存在，需补充一条断言）

- [ ] **Step 1: 在现有测试里补一条断言（失败）**

在 `AppTopNav.spec.js` 第一个 `it` 测试里末尾追加：

```js
    // 新断言：徽章内部应是 RoamMascotIcon，不再是 DogAssistantIcon
    expect(wrapper.find('[data-nav="/ai-recommend"]').html()).toContain('roam-mascot-icon')
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && npx vitest run src/components/__tests__/AppTopNav.spec.js`
Expected: FAIL（当前 HTML 里没有 `roam-mascot-icon` class）

- [ ] **Step 3: 修改 AppTopNav.vue 模板**

在 `<script setup>` 里把 `DogAssistantIcon` 的导入改为 `RoamMascotIcon`：

```js
// 移除
import DogAssistantIcon from '@/components/icons/DogAssistantIcon.vue'
// 改为
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'
```

在模板里把 `.featured-shell` 内部结构替换为新结构：

```vue
<span v-if="item.featured" class="featured-shell">
  <span class="featured-core">
    <RoamMascotIcon size="tiny" />
  </span>
  <span class="featured-label">{{ item.label }}</span>
</span>
```

（注意：原来 `featured-icon` 和 `featured-label` 都在 `featured-shell` 里；新版把 `featured-icon` 改成 `featured-core`，`featured-label` 位置不变。）

- [ ] **Step 4: 修改 AppTopNav.vue `<style scoped>`**

替换 `.featured-shell` 及相关规则：

```css
/* 原来的 .featured-shell / .featured-icon / .nav-link.is-featured.is-active .featured-shell 全部删除，改成： */

.nav-link.is-featured {
  padding: 0;
  background: transparent;
  transform: translateY(-10px);
}

.nav-link.is-featured:hover {
  background: transparent;
  transform: translateY(-12px);
}

.nav-link.is-featured::after {
  display: none;
}

.featured-shell {
  display: inline-grid;
  justify-items: center;
  gap: 6px;
  background: transparent;
  border: 0;
  box-shadow: none;
  padding: 0;
}

.featured-core {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #ffffff;
  box-shadow:
    0 10px 24px rgba(80, 120, 200, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  isolation: isolate;
}

.featured-core::before {
  content: '';
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(184, 216, 255, 0.5), transparent 70%);
  z-index: -1;
}

.featured-core::after {
  content: '';
  position: absolute;
  inset: -2px;
  border-radius: 50%;
  border: 2px dashed rgba(140, 180, 240, 0.55);
  animation: roam-badge-spin 18s linear infinite;
}

.featured-core :deep(.roam-mascot-icon) {
  width: 44px;
  height: 44px;
}

.featured-label {
  font-size: 13px;
  font-weight: 800;
  color: #3b4a6b;
}

.nav-link.is-featured.is-active .featured-core {
  box-shadow:
    0 12px 28px rgba(80, 120, 200, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

@keyframes roam-badge-spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .featured-core::after { animation: none; }
}
```

响应式内的 `.featured-shell { min-width: 122px }` 一并删除（新版是圆形徽章，宽度由 `.featured-core` 决定，不再需要）。

- [ ] **Step 5: 跑测试确认通过**

Run: `cd frontend && npx vitest run src/components/__tests__/AppTopNav.spec.js`
Expected: PASS (all 3 tests including new assertion)

- [ ] **Step 6: 手动视觉验收（可选但推荐）**

```bash
cd frontend && npm run dev
```

打开 http://localhost:5173 (或 vite 分配的端口)，确认顶部导航"智能推荐"徽章：
- 是圆形白色浮动徽章（不是橙色半胶囊）
- 外圈有慢转的淡蓝虚线环
- 背后有浅蓝光晕
- 里面是 Roam 的云朵小脸

- [ ] **Step 7: 提交**

```bash
rtk git add frontend/src/components/layout/AppTopNav.vue frontend/src/components/__tests__/AppTopNav.spec.js
rtk git commit -m "feat(ai-recommend): redesign top-nav featured badge to Roam cloud"
```

---

## Task 3：移动端 tab 徽章（AppTabBar）

**Files:**
- Modify: `frontend/src/components/AppTabBar.vue`
- Test: `frontend/src/components/__tests__/AppTabBar.spec.js`（已存在）

- [ ] **Step 1: 读现有测试**

```bash
cd frontend && npx vitest run src/components/__tests__/AppTabBar.spec.js
```

确认现有测试当前通过，记下它断言了什么（内部用的就是 icon 渲染 + label 渲染）。

- [ ] **Step 2: 修改 AppTabBar.vue 导入**

```js
// 移除
import DogAssistantIcon from '@/components/icons/DogAssistantIcon.vue'
// 改为
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'
```

模板里替换：
```vue
<span v-if="item.icon === 'dog'" class="icon icon-roam">
  <RoamMascotIcon size="tiny" />
</span>
```

把原 `icon-dog` 改名为 `icon-roam`（同时 CSS 选择器一起改）。

- [ ] **Step 3: 修改 AppTabBar.vue `<style scoped>`**

替换 `.tab-btn.featured.active` 和 `.icon-dog` 部分：

```css
.tab-btn.featured {
  position: relative;
}

.tab-btn.featured .icon-roam {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #ffffff;
  display: grid;
  place-items: center;
  box-shadow:
    0 6px 14px rgba(80, 120, 200, 0.22),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  margin-top: -14px;
  position: relative;
  isolation: isolate;
}

.tab-btn.featured .icon-roam::before {
  content: '';
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(184, 216, 255, 0.5), transparent 70%);
  z-index: -1;
}

.tab-btn.featured .icon-roam :deep(.roam-mascot-icon) {
  width: 30px;
  height: 30px;
}

.tab-btn.featured.active {
  background: transparent;
  color: #3b4a6b;
}

.tab-btn.featured.active .icon-roam {
  box-shadow:
    0 8px 18px rgba(80, 120, 200, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
}
```

（保留 `.icon` / `.tab-btn` 等通用规则不改。）

- [ ] **Step 4: 跑测试确认通过**

Run: `cd frontend && npx vitest run src/components/__tests__/AppTabBar.spec.js`
Expected: PASS（原测试不依赖具体 icon class 名，应该直接通过）

- [ ] **Step 5: 提交**

```bash
rtk git add frontend/src/components/AppTabBar.vue
rtk git commit -m "feat(ai-recommend): redesign mobile tab featured badge to Roam cloud"
```

---

## Task 4：需求摘要横向状态条（AiRequirementSummary 重写）

**Files:**
- Modify: `frontend/src/components/ai/AiRequirementSummary.vue`
- Test: `frontend/src/components/__tests__/AiRequirementSummary.spec.js`（新建）

- [ ] **Step 1: 写失败测试**

```js
// frontend/src/components/__tests__/AiRequirementSummary.spec.js
import { mount } from '@vue/test-utils'
import AiRequirementSummary from '@/components/ai/AiRequirementSummary.vue'

describe('AiRequirementSummary', () => {
  it('renders roam avatar and the known/missing split', () => {
    const wrapper = mount(AiRequirementSummary, {
      props: {
        slots: {
          city: '上海',
          locationName: '浦东',
          budgetYuan: 3500,
          rentMode: 'WHOLE'
        },
        missingSlots: ['priority', 'preferences']
      }
    })
    expect(wrapper.find('.summary-bar').exists()).toBe(true)
    expect(wrapper.find('.roam-mascot-icon').exists()).toBe(true)
    expect(wrapper.text()).toContain('Roam 知道的')
    expect(wrapper.text()).toContain('上海')
    expect(wrapper.text()).toContain('3500')
    expect(wrapper.text()).toContain('整租')
    expect(wrapper.findAll('.tag.done').length).toBeGreaterThanOrEqual(4)
    expect(wrapper.findAll('.tag.todo').length).toBe(2)
  })

  it('renders progress ring with computed label', () => {
    const wrapper = mount(AiRequirementSummary, {
      props: {
        slots: { city: '上海', budgetYuan: 3500, rentMode: 'WHOLE' },
        missingSlots: ['locationName', 'priority', 'preferences']
      }
    })
    // known=3 missing=3 total=6
    expect(wrapper.find('.progress-ring').exists()).toBe(true)
    expect(wrapper.find('.progress-ring').text()).toContain('3/6')
  })

  it('shows missing hint only when there are missing slots', () => {
    const empty = mount(AiRequirementSummary, {
      props: { slots: { city: '上海', locationName: '浦东', budgetYuan: 3500, rentMode: 'WHOLE', priority: 'COMMUTE', preferences: ['nearSubway'] }, missingSlots: [] }
    })
    expect(empty.find('.missing-hint').exists()).toBe(false)

    const hasMissing = mount(AiRequirementSummary, {
      props: { slots: { city: '上海' }, missingSlots: ['budgetYuan'] }
    })
    expect(hasMissing.find('.missing-hint').exists()).toBe(true)
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && npx vitest run src/components/__tests__/AiRequirementSummary.spec.js`
Expected: FAIL（旧组件是 dl/dt/dd 结构，不会匹配新 class）

- [ ] **Step 3: 重写 AiRequirementSummary.vue**

```vue
<template>
  <section class="summary-bar">
    <div class="summary-bar__avatar">
      <RoamMascotIcon size="mini" />
    </div>
    <div class="summary-bar__label">Roam 知道的</div>
    <div class="summary-bar__tags">
      <span v-for="tag in doneTags" :key="`d-${tag.key}`" class="tag done">
        <span class="k">{{ tag.key }}</span>
        <span class="v">{{ tag.value }}</span>
      </span>
      <span v-for="tag in todoTags" :key="`t-${tag.key}`" class="tag todo">
        ? {{ tag.label }}待补充
      </span>
    </div>
    <div class="progress-ring" :style="ringStyle">
      <div class="progress-ring__inner">{{ knownCount }}/{{ totalCount }}</div>
    </div>
    <p v-if="missingSlots.length" class="missing-hint">
      还差 {{ missingSlots.length }} 项信息，Roam 就可以去筛房源啦 ～
    </p>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'

const props = defineProps({
  slots: { type: Object, default: () => ({}) },
  missingSlots: { type: Array, default: () => [] }
})

const SLOT_ORDER = ['city', 'locationName', 'budgetYuan', 'rentMode', 'priority', 'preferences']
const SLOT_LABEL = {
  city: '城市',
  locationName: '区域',
  budgetYuan: '预算',
  rentMode: '方式',
  priority: '优先',
  preferences: '偏好'
}

function rentModeText(v) {
  if (v === 'WHOLE') return '整租'
  if (v === 'SHARED') return '合租'
  return v || ''
}
function priorityText(v) {
  if (v === 'PRICE') return '价格'
  if (v === 'COMMUTE') return '通勤'
  if (v === 'QUALITY') return '品质'
  return v || ''
}
function preferencesText(list) {
  if (!list?.length) return ''
  const map = { nearSubway: '近地铁', balcony: '阳台', quiet: '安静' }
  return list.map((p) => map[p] || p).join('·')
}
function budgetText(v) {
  if (!v) return ''
  return `${v}/月`
}

function readValue(key, raw) {
  switch (key) {
    case 'budgetYuan': return budgetText(raw)
    case 'rentMode': return rentModeText(raw)
    case 'priority': return priorityText(raw)
    case 'preferences': return preferencesText(raw)
    default: return raw || ''
  }
}

const doneTags = computed(() =>
  SLOT_ORDER
    .filter((k) => !props.missingSlots.includes(k))
    .map((k) => ({ key: SLOT_LABEL[k], value: readValue(k, props.slots?.[k]) }))
    .filter((t) => t.value)
)

const todoTags = computed(() =>
  props.missingSlots.map((k) => ({ key: k, label: SLOT_LABEL[k] || k }))
)

const knownCount = computed(() => doneTags.value.length)
const totalCount = computed(() => SLOT_ORDER.length)

const ringStyle = computed(() => {
  const ratio = totalCount.value === 0 ? 0 : knownCount.value / totalCount.value
  const deg = Math.round(ratio * 360)
  return {
    background: `conic-gradient(#7aa3e0 0deg ${deg}deg, #f0f5ff ${deg}deg 360deg)`
  }
})
</script>

<style scoped>
.summary-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  background: #ffffff;
  border-radius: 18px;
  border: 1px solid rgba(184, 200, 224, 0.3);
  box-shadow: 0 4px 10px rgba(100, 130, 200, 0.05);
  flex-wrap: wrap;
}

.summary-bar__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #f0f5ff;
  display: grid;
  place-items: center;
  border: 1px solid rgba(184, 200, 224, 0.4);
  flex-shrink: 0;
}
.summary-bar__avatar :deep(.roam-mascot-icon) {
  width: 22px;
  height: 22px;
}

.summary-bar__label {
  font-size: 12.5px;
  color: #5b6a8a;
  font-weight: 700;
  flex-shrink: 0;
  white-space: nowrap;
}

.summary-bar__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1;
  min-width: 200px;
}

.tag {
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.tag.done {
  background: #f0f5ff;
  color: #3b4a6b;
  border: 1px solid rgba(140, 180, 240, 0.35);
}
.tag.todo {
  background: #fff8e6;
  color: #a37920;
  border: 1.2px dashed #e8c488;
}
.tag .k { opacity: 0.65; font-weight: 600; font-size: 10px; }

.progress-ring {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.progress-ring__inner {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #ffffff;
  display: grid;
  place-items: center;
  font-size: 10.5px;
  font-weight: 800;
  color: #3b4a6b;
}

.missing-hint {
  margin: 0;
  flex-basis: 100%;
  padding: 8px 12px;
  border-radius: 12px;
  background: #fff8e6;
  color: #a37920;
  font-size: 12.5px;
  line-height: 1.5;
}

@media (max-width: 640px) {
  .summary-bar {
    gap: 10px;
    padding: 10px 12px;
  }
  .summary-bar__tags { min-width: 0; }
}
</style>
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd frontend && npx vitest run src/components/__tests__/AiRequirementSummary.spec.js`
Expected: PASS (3/3)

- [ ] **Step 5: 提交**

```bash
rtk git add frontend/src/components/ai/AiRequirementSummary.vue frontend/src/components/__tests__/AiRequirementSummary.spec.js
rtk git commit -m "feat(ai-recommend): rewrite requirement summary as horizontal status bar"
```

---

## Task 5：对话气泡（AiChatBubble）

**Files:**
- Modify: `frontend/src/components/ai/AiChatBubble.vue`
- Test: `frontend/src/components/__tests__/AiChatBubble.spec.js`（新建）

- [ ] **Step 1: 写失败测试**

```js
// frontend/src/components/__tests__/AiChatBubble.spec.js
import { mount } from '@vue/test-utils'
import AiChatBubble from '@/components/ai/AiChatBubble.vue'

describe('AiChatBubble', () => {
  it('renders assistant bubble with Roam avatar on the left', () => {
    const wrapper = mount(AiChatBubble, { props: { role: 'assistant', text: '你好' } })
    expect(wrapper.classes()).toContain('chat-row')
    expect(wrapper.classes()).toContain('is-assistant')
    expect(wrapper.find('.roam-mascot-icon').exists()).toBe(true)
    expect(wrapper.find('.bubble').exists()).toBe(true)
    expect(wrapper.text()).toContain('你好')
    expect(wrapper.text()).toContain('ROAM')
  })

  it('renders user bubble without avatar, right-aligned', () => {
    const wrapper = mount(AiChatBubble, { props: { role: 'user', text: '预算 3500' } })
    expect(wrapper.classes()).toContain('is-user')
    expect(wrapper.find('.roam-mascot-icon').exists()).toBe(false)
    expect(wrapper.text()).toContain('你')
    expect(wrapper.text()).toContain('预算 3500')
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && npx vitest run src/components/__tests__/AiChatBubble.spec.js`
Expected: FAIL（旧组件根元素是 `article.chat-bubble`，没有 `.chat-row` class）

- [ ] **Step 3: 改写 AiChatBubble.vue**

```vue
<template>
  <div class="chat-row" :class="roleClass">
    <div v-if="role === 'assistant'" class="chat-row__avatar">
      <RoamMascotIcon size="mini" />
    </div>
    <article class="bubble" :class="roleClass">
      <div class="bubble-meta">{{ roleLabel }}</div>
      <div class="bubble-body">{{ text }}</div>
    </article>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'

const props = defineProps({
  role: { type: String, default: 'assistant' },
  text: { type: String, default: '' }
})

const roleClass = computed(() => (props.role === 'user' ? 'is-user' : 'is-assistant'))
const roleLabel = computed(() => (props.role === 'user' ? '你' : 'ROAM'))
</script>

<style scoped>
.chat-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.chat-row.is-user { justify-content: flex-end; }

.chat-row__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #ffffff;
  display: grid;
  place-items: center;
  box-shadow: 0 4px 10px rgba(100, 130, 200, 0.14);
  border: 1px solid rgba(184, 200, 224, 0.4);
  flex-shrink: 0;
}
.chat-row__avatar :deep(.roam-mascot-icon) {
  width: 22px;
  height: 22px;
}

.bubble {
  display: grid;
  gap: 4px;
  max-width: min(560px, 78%);
  padding: 12px 18px;
  line-height: 1.55;
  font-size: 14px;
}

.bubble.is-assistant {
  background: #ffffff;
  color: #2d3748;
  border-radius: 26px 26px 26px 8px;
  position: relative;
  filter: drop-shadow(0 3px 10px rgba(100, 130, 200, 0.1));
}
.bubble.is-assistant::before {
  content: '';
  position: absolute;
  left: -5px;
  top: 8px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #ffffff;
}
.bubble.is-assistant::after {
  content: '';
  position: absolute;
  right: 14px;
  top: -4px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #ffffff;
}

.bubble.is-user {
  background: linear-gradient(135deg, #a8d8ff, #7db5f0);
  color: #ffffff;
  border-radius: 22px 22px 6px 22px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(125, 181, 240, 0.3);
}

.bubble-meta {
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 0.1em;
  opacity: 0.7;
}
.bubble.is-user .bubble-meta { opacity: 0.75; }

.bubble-body {
  white-space: pre-wrap;
  line-height: 1.55;
}
</style>
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd frontend && npx vitest run src/components/__tests__/AiChatBubble.spec.js`
Expected: PASS (2/2)

- [ ] **Step 5: 提交**

```bash
rtk git add frontend/src/components/ai/AiChatBubble.vue frontend/src/components/__tests__/AiChatBubble.spec.js
rtk git commit -m "feat(ai-recommend): cloud-shaped chat bubble with Roam avatar"
```

---

## Task 6：快捷提示 chips（AiQuickPromptChips）

**Files:**
- Modify: `frontend/src/components/ai/AiQuickPromptChips.vue`

（纯样式改，测试可依赖 AiRecommendView 集成测试覆盖，不单独加单测。）

- [ ] **Step 1: 修改 `<style scoped>`**

替换 `.prompt-chip` 和 `.prompt-chip:hover` 规则：

```css
.prompt-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.prompt-chip {
  border: 1px solid rgba(140, 180, 240, 0.3);
  background: #f0f5ff;
  color: #3b4a6b;
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  font-weight: 600;
  transition: transform 0.18s ease, background 0.18s ease, border-color 0.18s ease;
}

.prompt-chip:hover {
  transform: translateY(-1px);
  background: #e4edfc;
  border-color: rgba(140, 180, 240, 0.5);
}
```

（模板和 script 不变。）

- [ ] **Step 2: 跑已有测试确认没回归**

Run: `cd frontend && npx vitest run src/views/__tests__/AiRecommendView.spec.js`
Expected: PASS（若失败，说明 AiRecommendView 对 chip class 有依赖，检查日志）

- [ ] **Step 3: 提交**

```bash
rtk git add frontend/src/components/ai/AiQuickPromptChips.vue
rtk git commit -m "style(ai-recommend): restyle quick prompt chips in sky blue"
```

---

## Task 7：预览卡（AiPreviewPanel）

**Files:**
- Modify: `frontend/src/components/ai/AiPreviewPanel.vue`

（纯样式改。模板里的 `data-test="preview-select-<groupKey>"` 保留。）

- [ ] **Step 1: 替换 `<style scoped>`**

完整替换为：

```css
.preview-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(140, 180, 240, 0.25);
  border-radius: 24px;
  background: linear-gradient(135deg, #f8fbff, #eef5ff);
}

.preview-head {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 12px;
}

.preview-head h3 {
  margin: 4px 0 0;
  font-size: 20px;
  color: #2d3748;
}

.preview-eyebrow {
  margin: 0;
  color: #5b6a8a;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.preview-copy {
  margin: 8px 0 0;
  color: #5b6a8a;
  line-height: 1.6;
}

.preview-count {
  flex-shrink: 0;
  padding: 8px 12px;
  border-radius: 999px;
  background: #ffffff;
  color: #3b4a6b;
  font-size: 13px;
  font-weight: 700;
  border: 1px solid rgba(140, 180, 240, 0.35);
}

.preview-list {
  display: grid;
  gap: 12px;
}

.preview-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid rgba(184, 200, 224, 0.35);
  box-shadow: 0 4px 12px rgba(100, 130, 200, 0.06);
}

.preview-card__head {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 12px;
}

.preview-card__head h4 {
  margin: 0;
  font-size: 18px;
  color: #2d3748;
}

.preview-card__summary {
  margin: 8px 0 0;
  color: #5b6a8a;
  line-height: 1.6;
}

.preview-card__meta {
  flex-shrink: 0;
  color: #3b4a6b;
  font-size: 13px;
  font-weight: 700;
}

.preview-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preview-card__tag {
  padding: 6px 10px;
  border-radius: 999px;
  background: #f0f5ff;
  color: #3b4a6b;
  font-size: 13px;
  font-weight: 600;
}

.preview-card__cta {
  justify-self: start;
  border: 0;
  background: linear-gradient(135deg, #7aa3e0, #9bb5e8);
  color: #ffffff;
  border-radius: 999px;
  padding: 9px 18px;
  font-weight: 800;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(122, 163, 224, 0.35);
}
.preview-card__cta:disabled { opacity: 0.6; cursor: not-allowed; }

@media (max-width: 640px) {
  .preview-head,
  .preview-card__head {
    flex-direction: column;
  }
}
```

注意：原 `.primary-btn.preview-card__cta` 依赖全局 `.primary-btn`。新版在这个 scoped 样式里自己定义 CTA，不再依赖全局。

模板中的 `class="primary-btn preview-card__cta"` 保留（`primary-btn` 类在新样式下被更具体的规则覆盖），或安全起见只保留 `preview-card__cta`：

```vue
<button
  class="preview-card__cta"
  type="button"
  :disabled="loading"
  :data-test="`preview-select-${group.groupKey}`"
  @click="$emit('select-group', group)"
>
  先看这类
</button>
```

- [ ] **Step 2: 跑集成测试**

Run: `cd frontend && npx vitest run src/views/__tests__/AiRecommendView.spec.js`
Expected: PASS（尤其是 "renders preview groups and posts..." 用例）

- [ ] **Step 3: 提交**

```bash
rtk git add frontend/src/components/ai/AiPreviewPanel.vue
rtk git commit -m "style(ai-recommend): restyle preview panel in sky blue palette"
```

---

## Task 8：推荐结果卡（AiRecommendationPanel）

**Files:**
- Modify: `frontend/src/components/ai/AiRecommendationPanel.vue`

- [ ] **Step 1: 替换 `<style scoped>`**

完整替换为：

```css
.recommend-panel {
  display: grid;
  gap: 14px;
}

.recommend-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.recommend-head h3 {
  margin: 0;
  font-size: 16px;
  color: #2d3748;
}

.recommend-tip {
  margin: 6px 0 0;
  color: #5b6a8a;
  font-size: 13px;
}

.relaxed-tag {
  flex-shrink: 0;
  border-radius: 999px;
  background: #fff8e6;
  color: #a37920;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid rgba(232, 196, 136, 0.5);
}

.recommend-list {
  display: grid;
  gap: 12px;
}

.recommend-card {
  border: 1px solid rgba(184, 200, 224, 0.35);
  border-radius: 22px;
  background: #ffffff;
  padding: 16px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  box-shadow: 0 4px 12px rgba(100, 130, 200, 0.06);
}

.recommend-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 28px rgba(100, 130, 200, 0.14);
}

.recommend-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.recommend-row h4 {
  margin: 0;
  font-size: 16px;
  color: #2d3748;
}

.recommend-price {
  margin: 6px 0 0;
  color: #3b4a6b;
  font-weight: 700;
}

.recommend-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  color: #5b6a8a;
  font-size: 13px;
}

.recommend-summary {
  margin: 12px 0 0;
  color: #2d3748;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.5;
}

.recommend-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.recommend-tag {
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 600;
}

.recommend-tag--primary {
  background: #dbe8fb;
  color: #2a4a7a;
}

.recommend-tag--secondary {
  background: #eef3fa;
  color: #5b6a8a;
}

.recommend-tag--relaxation {
  background: #fff8e6;
  color: #a37920;
}

.recommend-empty {
  margin: 0;
  color: #5b6a8a;
}

@media (max-width: 768px) {
  .recommend-row { flex-direction: column; }
}
```

（模板不变。）

- [ ] **Step 2: 跑集成测试**

Run: `cd frontend && npx vitest run src/views/__tests__/AiRecommendView.spec.js`
Expected: PASS（尤其是 "renders primary, secondary and relaxation reasons..." 和 "renders ai recommendation distance details..." 用例）

- [ ] **Step 3: 提交**

```bash
rtk git add frontend/src/components/ai/AiRecommendationPanel.vue
rtk git commit -m "style(ai-recommend): restyle recommendation cards in sky blue palette"
```

---

## Task 9：主视图重组（AiRecommendView）

**Files:**
- Modify: `frontend/src/views/AiRecommendView.vue`
- Modify: `frontend/src/views/__tests__/AiRecommendView.spec.js`（仅同步文案）

- [ ] **Step 1: 更新测试里的文案断言**

在 `AiRecommendView.spec.js` 第 88-96 行那段 `it('bootstraps the ai session...'`，把：

```js
expect(wrapper.text()).toContain('当前已知条件')
```

改为：

```js
expect(wrapper.text()).toContain('Roam 知道的')
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && npx vitest run src/views/__tests__/AiRecommendView.spec.js`
Expected: FAIL（"Roam 知道的" 还未在 DOM 中出现，因为 view 没改）

- [ ] **Step 3: 改写 AiRecommendView.vue 模板**

将 `<template>` 整块替换为：

```vue
<template>
  <div class="page ai-page">
    <section class="ai-hero">
      <div class="ai-hero__sky" aria-hidden="true"></div>
      <div class="ai-hero__content">
        <div class="ai-hero__mascot">
          <RoamMascotIcon size="big" />
        </div>
        <h1 class="ai-hero__title">Hi，我是 Roam，帮你找个家</h1>
        <p class="ai-hero__sub">
          告诉我你的预算、地段、整租合租，我从真实房源里挑给你看。
        </p>
        <div class="ai-hero__actions">
          <button type="button" class="ai-hero__ghost" :disabled="loading" @click="resetSession">
            重新开始
          </button>
          <button type="button" class="ai-hero__primary" @click="focusInput">
            现在开始聊
          </button>
        </div>
      </div>
    </section>

    <AiRequirementSummary :slots="slots" :missing-slots="missingSlots" />

    <section class="ai-chat-card">
      <div class="chat-head">
        <h2 class="chat-head__title">智能推荐</h2>
        <p class="chat-head__tip">把你的要求一步步告诉它，或者直接点下面的快捷提示。</p>
      </div>

      <AiQuickPromptChips :prompts="quickPrompts" @select="sendPrompt" />

      <div class="chat-thread" data-testid="ai-thread">
        <AiChatBubble
          v-for="(message, index) in transcript"
          :key="`${message.role}-${index}`"
          :role="message.role"
          :text="message.text"
        />
        <AiPreviewPanel
          v-if="stage === 'PREVIEW' && preview?.groups?.length"
          :preview="preview"
          :loading="loading"
          @select-group="sendPreviewSelection"
        />
        <AiRecommendationPanel
          v-if="stage === 'SEARCH' && recommendation"
          :recommendation="recommendation"
          @open-house="openHouse"
        />
      </div>

      <p v-if="errorText" class="chat-error">{{ errorText }}</p>

      <form class="chat-form" @submit.prevent="submitMessage">
        <textarea
          ref="inputRef"
          v-model="draft"
          class="chat-input"
          rows="3"
          placeholder="比如：预算 3500，想在浦东整租；或者：我现在只知道想在上海租房。"
          :disabled="loading"
        />
        <div class="chat-actions">
          <span v-if="loading" class="chat-status">正在整理需求...</span>
          <button class="chat-send" type="submit" :disabled="loading || !draft.trim()">发送</button>
        </div>
      </form>
    </section>
  </div>
</template>
```

- [ ] **Step 4: 改写 `<script setup>`**

在原 `<script setup>` 顶部 import 添加 `RoamMascotIcon`，并新增一个 `inputRef` 和 `focusInput` 函数：

```js
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { chatAiRecommend, fetchAiRecommendSession, resetAiRecommendSession } from '@/api/aiRecommend'
import AiChatBubble from '@/components/ai/AiChatBubble.vue'
import AiPreviewPanel from '@/components/ai/AiPreviewPanel.vue'
import AiQuickPromptChips from '@/components/ai/AiQuickPromptChips.vue'
import AiRecommendationPanel from '@/components/ai/AiRecommendationPanel.vue'
import AiRequirementSummary from '@/components/ai/AiRequirementSummary.vue'
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'

// ... 原有 router / 所有 ref / quickPrompts / onMounted / bootstrapSession / submitMessage / sendPrompt / resetSession / sendPreviewSelection / applyResponse / openHouse 全部保留不动

const inputRef = ref(null)

async function focusInput() {
  await nextTick()
  inputRef.value?.focus()
  inputRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
```

**删除原来 `DogAssistantIcon` 的 import**（如果存在）。

- [ ] **Step 5: 替换 `<style scoped>`**

完整替换为：

```css
.ai-page {
  display: grid;
  gap: 16px;
  max-width: 960px;
  margin: 0 auto;
  width: 100%;
}

/* ========== 英雄区 ========== */
.ai-hero {
  position: relative;
  border-radius: 28px;
  overflow: hidden;
  padding: 36px 24px 28px;
  text-align: center;
}

.ai-hero__sky {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 15% 10%, rgba(255, 209, 102, 0.18), transparent 40%),
    radial-gradient(circle at 85% 20%, rgba(255, 184, 200, 0.22), transparent 45%),
    linear-gradient(180deg, #eaf4ff 0%, #f8f4ff 60%, #fff8e6 100%);
}
.ai-hero__sky::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle, rgba(255, 255, 255, 0.8) 1.5px, transparent 2px);
  background-size: 40px 40px;
  opacity: 0.4;
}

.ai-hero__content {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 10px;
  justify-items: center;
}

.ai-hero__mascot {
  width: 140px;
  height: 120px;
  animation: roam-float 3.5s ease-in-out infinite;
}

@keyframes roam-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}
@media (prefers-reduced-motion: reduce) {
  .ai-hero__mascot { animation: none; }
}

.ai-hero__title {
  margin: 2px 0 0;
  font-size: clamp(20px, 3vw, 26px);
  color: #2d3748;
  line-height: 1.2;
}

.ai-hero__sub {
  margin: 4px auto 10px;
  color: #5b6a8a;
  line-height: 1.65;
  max-width: 500px;
  font-size: 14px;
}

.ai-hero__actions {
  display: inline-flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
}

.ai-hero__ghost {
  border: 1px solid rgba(140, 180, 240, 0.35);
  background: rgba(255, 255, 255, 0.75);
  color: #3b4a6b;
  border-radius: 999px;
  padding: 8px 16px;
  font-weight: 700;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.18s ease, transform 0.18s ease;
}
.ai-hero__ghost:hover:not(:disabled) { transform: translateY(-1px); background: #ffffff; }
.ai-hero__ghost:disabled { opacity: 0.6; cursor: not-allowed; }

.ai-hero__primary {
  border: 0;
  background: linear-gradient(135deg, #7aa3e0, #9bb5e8);
  color: #ffffff;
  border-radius: 999px;
  padding: 9px 20px;
  font-weight: 800;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(122, 163, 224, 0.35);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.ai-hero__primary:hover { transform: translateY(-1px); box-shadow: 0 9px 20px rgba(122, 163, 224, 0.4); }

/* ========== 对话卡 ========== */
.ai-chat-card {
  background: #ffffff;
  border-radius: 24px;
  padding: 18px;
  border: 1px solid rgba(184, 200, 224, 0.3);
  box-shadow: 0 4px 14px rgba(100, 130, 200, 0.06);
  display: grid;
  gap: 16px;
}

.chat-head {
  display: grid;
  gap: 4px;
}
.chat-head__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #2d3748;
}
.chat-head__tip {
  margin: 0;
  color: #5b6a8a;
  font-size: 13px;
}

.chat-thread {
  min-height: 320px;
  display: grid;
  gap: 14px;
  align-content: start;
}

.chat-error {
  margin: 0;
  color: #b04f2d;
  font-size: 14px;
}

.chat-form {
  display: grid;
  gap: 12px;
}

.chat-input {
  width: 100%;
  min-height: 120px;
  resize: vertical;
  border: 1px solid rgba(184, 200, 224, 0.4);
  border-radius: 18px;
  padding: 12px 14px;
  font-size: 14px;
  background: #f8fbff;
  outline: none;
  font-family: inherit;
}
.chat-input:focus {
  border-color: #7aa3e0;
  background: #ffffff;
  box-shadow: 0 0 0 4px rgba(122, 163, 224, 0.15);
}

.chat-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.chat-status {
  color: #5b6a8a;
  font-size: 13px;
}

.chat-send {
  border: 0;
  background: linear-gradient(135deg, #7aa3e0, #9bb5e8);
  color: #ffffff;
  border-radius: 999px;
  padding: 10px 22px;
  font-weight: 800;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(122, 163, 224, 0.35);
  margin-left: auto;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.chat-send:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 9px 20px rgba(122, 163, 224, 0.4); }
.chat-send:disabled { opacity: 0.6; cursor: not-allowed; }

@media (max-width: 640px) {
  .ai-hero { padding: 28px 16px 20px; }
  .ai-hero__mascot { width: 110px; height: 95px; }
  .ai-chat-card { padding: 14px; border-radius: 20px; }
}
</style>
```

**注意**：新样式里的 `.chat-send` 替换了原模板中的 `class="primary-btn"`。模板中的发送按钮 class 已在 Step 3 中改为 `chat-send`。同理 `.chat-error` 替换原 `.error-text`、`.ai-hero__ghost` 替换原 `.ghost-btn hero-reset`。

- [ ] **Step 6: 跑完整测试**

Run: `cd frontend && npx vitest run src/views/__tests__/AiRecommendView.spec.js`
Expected: PASS (all 7 tests)

测试里对 `.chat-input` 和 `.chat-form` 的依赖保留；对 `.primary-btn` 的隐式依赖（按钮点击触发提交）不变，因为 form 的 `@submit.prevent` 仍有效——"预算3500，我更在意通勤..."用例仍能通过按回车或提交 form 生效。如用例里有 `wrapper.get('.primary-btn')` 的选择器，替换为 `.chat-send`；如未使用则无需改。

**检查 `AiRecommendView.spec.js` 是否有 `.primary-btn` 选择器：**
```bash
rtk grep ".primary-btn" frontend/src/views/__tests__/AiRecommendView.spec.js
```
如有，将其改为 `.chat-send`；如无，跳过。

- [ ] **Step 7: 手动视觉验收**

```bash
cd frontend && npm run dev
```

打开 http://localhost:5173/ai-recommend，用眼睛确认：
- 英雄区 Roam 居中、轻微上下浮动
- 横向状态条里 Roam 头 + "Roam 知道的" + 胶囊 + 进度环
- 快捷 chips 浅蓝
- 对话气泡白云形助手泡（带左下小尖角 + 小圆凸起）、天空蓝渐变用户泡
- 发送按钮渐变蓝

- [ ] **Step 8: 提交**

```bash
rtk git add frontend/src/views/AiRecommendView.vue frontend/src/views/__tests__/AiRecommendView.spec.js
rtk git commit -m "feat(ai-recommend): restructure page layout with Roam hero and full-width chat"
```

---

## Task 10：残留引用清理 + 全回归

**Files:**
- Grep: 全项目检查 `DogAssistantIcon` 残留
- Test: 跑整套前端测试

- [ ] **Step 1: 检查是否还有 `DogAssistantIcon` 导入**

```bash
rtk grep "DogAssistantIcon" frontend/src
```

Expected: 只在 `frontend/src/components/icons/DogAssistantIcon.vue` 自己出现（文件保留但无引用），其他文件都已换成 `RoamMascotIcon`。

如果有残留 import，定位并替换。

- [ ] **Step 2: 跑完整前端测试套**

```bash
cd frontend && npx vitest run
```

Expected: 所有套件 PASS。如有失败，逐个修复（通常是测试里选择器引用了已改名的 class）。

- [ ] **Step 3: 跑 build 验证无编译错误**

```bash
cd frontend && npm run build
```

Expected: 成功输出，没有 import 错误或 CSS 编译失败。

- [ ] **Step 4: 提交（如有修复）**

```bash
rtk git add -A
rtk git commit -m "chore(ai-recommend): wire up regression test fixes and cleanup"
```

（如果无改动就跳过此步。）

---

## 验收清单（手动）

全部任务完成后，在浏览器打开 `http://localhost:5173` 并逐项验收：

- [ ] **顶部导航**："智能推荐"徽章是圆形白底、带慢转虚线环和浅蓝光晕，不再是橙黄半胶囊
- [ ] **英雄区**：Roam 云朵居中悬浮、"Hi，我是 Roam" 自我介绍、双按钮（重新开始 / 现在开始聊）
- [ ] **需求摘要条**：在英雄区下方横向一条，Roam 头像 + 胶囊 + 右侧进度环
- [ ] **快捷 chips**：浅蓝胶囊，hover 微上浮
- [ ] **对话气泡**：助手白云泡（带左下尖 + 小圆凸起），每条助手消息前有 Roam 头像；用户气泡天空蓝渐变
- [ ] **预览卡**：浅蓝玻璃背景 + 白色内卡 + 渐变蓝 CTA
- [ ] **推荐结果卡**：白底 + 浅蓝描边 + 深蓝价格 + 蓝系三级理由 tag
- [ ] **输入框**：聚焦蓝色外光晕
- [ ] **发送按钮**：渐变蓝胶囊
- [ ] **移动端底部 tab**："智能推荐" tab 是圆形白底悬浮 + 浅光晕
- [ ] **其他页面未受影响**：首页/找房/消息/我的依然是原米色风

---

## 风险 & 边界

1. **测试中 `.primary-btn` 选择器**：原 AiRecommendView 用了全局 `.primary-btn` class，已改名为 `.chat-send`。若测试用例里有该选择器依赖，Task 9 Step 6 会发现并修掉。
2. **`:deep(...)` 穿透作用域**：在 `AppTopNav`、`AppTabBar`、`AiRequirementSummary`、`AiChatBubble` 里用 `:deep(.roam-mascot-icon)` 控制 icon 尺寸。Vue 3 + scoped CSS 原生支持。
3. **动画性能**：`float` 和 `spin` 都只用 transform，浏览器合成层加速。`prefers-reduced-motion` 已禁用。
4. **旧 `DogAssistantIcon.vue`**：保留文件但所有引用已切走。下个冲刺另起任务物理删除。
