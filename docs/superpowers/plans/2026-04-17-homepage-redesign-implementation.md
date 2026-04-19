# MyRent Homepage Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the MyRent homepage into a search-first rental entry page that keeps the current visual polish while removing low-value hero copy and improving conversion-oriented browsing.

**Architecture:** Keep the existing `HomeView -> HomeHero -> HomeQuickLinks -> HouseCard` composition and the existing `useHouseFeed` loading flow, but change the homepage hierarchy so the first screen is a single search card plus three high-value entry cards and a denser featured-listing area. Limit scope to homepage-facing components and shared homepage metadata so the redesign can ship without pulling in unrelated list/detail refactors.

**Tech Stack:** Vue 3 `<script setup>`, Vue Router, existing `useHouseFeed` composable, Vitest, Vue Test Utils, CSS scoped styles, existing design token layer

---

## File Structure

### Create

- `frontend/src/components/__tests__/HomeHero.spec.js`
  Purpose: cover the new single-card search hero, quick-filter chips, and emitted events.
- `frontend/src/components/__tests__/HomeQuickLinks.spec.js`
  Purpose: cover the three-card homepage entry strip and confirm the renamed user-facing copy.

### Modify

- `frontend/src/design/site.js`
  Purpose: replace magazine-style homepage copy with task-oriented quick links and optional homepage chip metadata.
- `frontend/src/components/home/HomeHero.vue`
  Purpose: remove the left narrative block, add a single search card, secondary filters, and high-value preset chips.
- `frontend/src/components/home/HomeQuickLinks.vue`
  Purpose: present exactly three high-frequency entries with stronger action language and lighter supporting copy.
- `frontend/src/components/HouseCard.vue`
  Purpose: increase decision density by surfacing area, rental type, region, and commute/subway tags.
- `frontend/src/views/HomeView.vue`
  Purpose: remove the low-value aside placeholder, reorder the homepage into search -> quick entry -> featured listings -> secondary recommendation blocks, and keep the existing data/feed/error handling.
- `frontend/src/views/__tests__/HomeView.spec.js`
  Purpose: rewrite homepage expectations around the new search-first structure instead of the old editorial hero.
- `frontend/src/components/__tests__/HouseCard.spec.js`
  Purpose: extend regression coverage to the new decision-making fields and fallback behavior.

---

### Task 1: Replace Homepage Copy Metadata

**Files:**
- Modify: `frontend/src/design/site.js`
- Test: `frontend/src/components/__tests__/HomeQuickLinks.spec.js`

- [ ] **Step 1: Write the failing quick-link test**

```js
import { mount, RouterLinkStub } from '@vue/test-utils'
import HomeQuickLinks from '@/components/home/HomeQuickLinks.vue'

describe('HomeQuickLinks', () => {
  it('renders the three high-value homepage entry actions', () => {
    const wrapper = mount(HomeQuickLinks, {
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    })

    expect(wrapper.text()).toContain('通勤找房')
    expect(wrapper.text()).toContain('地图找房')
    expect(wrapper.text()).toContain('查看全部房源')
    expect(wrapper.text()).not.toContain('打开结果页')
    expect(wrapper.findAllComponents(RouterLinkStub)).toHaveLength(3)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- src/components/__tests__/HomeQuickLinks.spec.js`

Expected: FAIL because `HomeQuickLinks.vue` still renders the old metadata from `site.js`, including the old result-page wording.

- [ ] **Step 3: Replace homepage quick-link metadata with task-first copy**

```js
// frontend/src/design/site.js
export const homeQuickLinks = [
  {
    title: '通勤找房',
    description: '按地铁站、通勤半径和上班动线进入结果页。',
    to: '/map'
  },
  {
    title: '地图找房',
    description: '按区域块和地标快速浏览正在出租的房源。',
    to: '/map'
  },
  {
    title: '查看全部房源',
    description: '直接进入完整列表，继续按预算和户型筛选。',
    to: '/houses'
  }
]
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:run -- src/components/__tests__/HomeQuickLinks.spec.js`

Expected: PASS with 1 test passed and no references to the old “打开结果页” wording.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/design/site.js frontend/src/components/__tests__/HomeQuickLinks.spec.js
git commit -m "test: lock homepage quick links to task-first copy"
```

---

### Task 2: Redesign the Hero as a Single Search Card

**Files:**
- Create: `frontend/src/components/__tests__/HomeHero.spec.js`
- Modify: `frontend/src/components/home/HomeHero.vue`

- [ ] **Step 1: Write the failing hero test**

```js
import { mount, RouterLinkStub } from '@vue/test-utils'
import HomeHero from '@/components/home/HomeHero.vue'

describe('HomeHero', () => {
  it('renders a single search card with preset chips and emits actions', async () => {
    const wrapper = mount(HomeHero, {
      props: {
        resultTip: '当前展示广州精选房源',
        isNearbyMode: false
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    })

    expect(wrapper.text()).toContain('开始找房')
    expect(wrapper.text()).toContain('近地铁')
    expect(wrapper.text()).toContain('低总价')
    expect(wrapper.text()).not.toContain('Rent with taste')

    await wrapper.find('input').setValue('天河公园')
    await wrapper.find('[data-test="search-submit"]').trigger('click')

    expect(wrapper.emitted('search')).toEqual([['天河公园']])
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- src/components/__tests__/HomeHero.spec.js`

Expected: FAIL because the current hero still has the split editorial layout and no preset chips or `data-test="search-submit"` button.

- [ ] **Step 3: Implement the new single-card hero**

```vue
<!-- frontend/src/components/home/HomeHero.vue -->
<template>
  <section class="hero app-surface">
    <div class="search-card">
      <div class="heading-row">
        <div>
          <p class="eyebrow">Search</p>
          <h1 class="title">开始找房</h1>
        </div>
        <p class="tip">{{ tipText }}</p>
      </div>

      <div class="primary-row">
        <input
          v-model.trim="keyword"
          class="input"
          placeholder="区域 / 地点 / 地铁站"
          @keyup.enter="emitSearch"
        />
        <button data-test="search-submit" class="primary-btn" @click="emitSearch">
          开始找房
        </button>
      </div>

      <div class="filter-row">
        <button class="ghost-chip" @click="$emit('preset', 'budget')">预算</button>
        <button class="ghost-chip" @click="$emit('preset', 'rentalType')">整租 / 合租</button>
        <button class="ghost-chip" @click="$emit('preset', 'commute')">通勤 / 地铁</button>
      </div>

      <div class="preset-row">
        <button class="preset-chip" @click="$emit('search', '近地铁')">近地铁</button>
        <button class="preset-chip" @click="$emit('search', '低总价')">低总价</button>
        <button class="preset-chip" @click="$emit('search', '整租优先')">整租优先</button>
        <button class="preset-chip" @click="$emit('search', '新上房源')">新上房源</button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  resultTip: {
    type: String,
    default: ''
  },
  isNearbyMode: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['search', 'preset'])
const keyword = ref('')

const tipText = computed(() => {
  if (props.resultTip) return props.resultTip
  if (props.isNearbyMode) return '已切换到附近搜索，可继续缩小地点范围。'
  return '输入地点、预算和通勤偏好，快速开始找房。'
})

function emitSearch() {
  emit('search', keyword.value)
}
</script>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:run -- src/components/__tests__/HomeHero.spec.js`

Expected: PASS with the hero rendering a single search surface and emitting `search` from the primary button.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/home/HomeHero.vue frontend/src/components/__tests__/HomeHero.spec.js
git commit -m "feat: convert homepage hero into a search-first card"
```

---

### Task 3: Increase Homepage Entry and Card Decision Density

**Files:**
- Modify: `frontend/src/components/home/HomeQuickLinks.vue`
- Modify: `frontend/src/components/HouseCard.vue`
- Modify: `frontend/src/components/__tests__/HouseCard.spec.js`

- [ ] **Step 1: Write the failing house-card test**

```js
import { mount } from '@vue/test-utils'
import HouseCard from '@/components/HouseCard.vue'

describe('HouseCard', () => {
  it('renders region, rental type, area, and commute hints for quick decisions', () => {
    const wrapper = mount(HouseCard, {
      props: {
        house: {
          id: 9,
          title: '天河创意公寓',
          price: 4800,
          depositAmount: 4800,
          status: 1,
          region: '天河区',
          rentalType: '整租',
          area: 38,
          distance: '距地铁 600m'
        }
      }
    })

    expect(wrapper.text()).toContain('天河区')
    expect(wrapper.text()).toContain('整租')
    expect(wrapper.text()).toContain('38㎡')
    expect(wrapper.text()).toContain('距地铁 600m')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- src/components/__tests__/HouseCard.spec.js`

Expected: FAIL because the current card does not render region, rental type, or area.

- [ ] **Step 3: Update the quick-link presentation and densify the house card**

```vue
<!-- frontend/src/components/home/HomeQuickLinks.vue -->
<template>
  <section class="quick-links">
    <div class="grid">
      <RouterLink v-for="item in homeQuickLinks" :key="item.to" :to="item.to" class="card-link app-surface">
        <h3 class="title">{{ item.title }}</h3>
        <p class="description">{{ item.description }}</p>
      </RouterLink>
    </div>
  </section>
</template>
```

```vue
<!-- frontend/src/components/HouseCard.vue -->
<template>
  <article class="house-card app-surface" @click="$emit('click')">
    <img class="cover" :src="cover" alt="house cover" />
    <div class="content">
      <div class="header-row">
        <div class="title-wrap">
          <h3 class="title">{{ house.title || '未命名房源' }}</h3>
          <p class="location">{{ regionText }}</p>
        </div>
        <span class="status" :class="statusClass">{{ statusText }}</span>
      </div>

      <p class="price">
        {{ formatPrice(house.price) }}
        <span class="price-unit">/ 月</span>
      </p>

      <div class="facts">
        <span class="fact">{{ rentalTypeText }}</span>
        <span class="fact">{{ areaText }}</span>
        <span v-if="distanceText" class="fact fact-accent">{{ distanceText }}</span>
      </div>

      <p class="meta">押金 {{ formatPrice(house.depositAmount) }}</p>
      <p class="meta">发布者 {{ publisherText }}</p>
      <p v-if="hotText" class="meta meta-warning">{{ hotText }}</p>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { formatPrice, getHouseStatusText } from '@/utils/format'

const props = defineProps({
  house: {
    type: Object,
    required: true
  }
})

defineEmits(['click'])

const statusText = computed(() => getHouseStatusText(props.house.status))
const publisherText = computed(() => props.house.publisherName || '未知发布者')
const distanceText = computed(() => props.house.distance || '')
const regionText = computed(() => props.house.region || props.house.city || '区域待完善')
const rentalTypeText = computed(() => props.house.rentalType || '租住方式待完善')
const areaText = computed(() => {
  if (!props.house.area) return '面积待完善'
  return `${props.house.area}㎡`
})
</script>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:run -- src/components/__tests__/HouseCard.spec.js src/components/__tests__/HomeQuickLinks.spec.js`

Expected: PASS with the quick links still rendering three entries and the house card showing denser decision fields.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/home/HomeQuickLinks.vue frontend/src/components/HouseCard.vue frontend/src/components/__tests__/HouseCard.spec.js
git commit -m "feat: make homepage entries and house cards more decision oriented"
```

---

### Task 4: Recompose `HomeView` Around Search, Entry, Listings, and Secondary Blocks

**Files:**
- Modify: `frontend/src/views/HomeView.vue`
- Modify: `frontend/src/views/__tests__/HomeView.spec.js`

- [ ] **Step 1: Write the failing homepage view test**

```js
import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

vi.mock('@/composables/useHouseFeed', () => ({
  useHouseFeed: () => ({
    houses: ref([{ id: 1, title: '天河单间', price: 3200, depositAmount: 3200, status: 1 }]),
    loading: ref(false),
    error: ref(''),
    mode: ref('hot'),
    resultTip: ref('当前展示 1 套精选房源'),
    loadNext: vi.fn(),
    activateNearby: vi.fn(),
    activateHot: vi.fn()
  })
}))

describe('HomeView', () => {
  it('renders a search-first homepage with quick entries and secondary recommendation blocks', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/houses', component: { template: '<div />' } },
        { path: '/map', component: { template: '<div />' } },
        { path: '/house/:id', component: { template: '<div />' } }
      ]
    })

    const wrapper = mount(HomeView, {
      global: {
        plugins: [router],
        stubs: {
          HouseCard: true
        }
      }
    })

    expect(wrapper.text()).toContain('开始找房')
    expect(wrapper.text()).toContain('通勤找房')
    expect(wrapper.text()).toContain('查看全部房源')
    expect(wrapper.text()).toContain('今日新上')
    expect(wrapper.text()).not.toContain('Phase 1')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- src/views/__tests__/HomeView.spec.js`

Expected: FAIL because `HomeView.vue` still renders the old story strip and the aside placeholder empty state.

- [ ] **Step 3: Recompose the homepage layout**

```vue
<!-- frontend/src/views/HomeView.vue -->
<template>
  <div class="home-view">
    <HomeHero
      :result-tip="feed.resultTip.value"
      :is-nearby-mode="feed.mode.value === 'nearby'"
      @search="handleSearch"
    />

    <HomeQuickLinks />

    <section class="content-grid">
      <div class="featured app-surface">
        <div class="section-head">
          <div>
            <p class="section-eyebrow">Featured Listings</p>
            <h2 class="section-title">先看值得点开的房源</h2>
          </div>
          <RouterLink class="section-link" to="/houses">查看全部房源</RouterLink>
        </div>

        <div v-if="feed.houses.value.length" class="listing-grid">
          <HouseCard
            v-for="house in feed.houses.value"
            :key="house.id"
            :house="house"
            @click="toDetail(house.id)"
          />
        </div>
        <LoadingState v-else-if="feed.loading.value" text="正在加载精选房源..." />
        <EmptyState
          v-else
          title="精选房源暂时不可用"
          :description="feed.error.value || '可以先从地图找房或重新输入地点开始。'"
        />
      </div>

      <aside class="aside">
        <section class="mini-panel app-surface">
          <p class="section-eyebrow">Today</p>
          <h3 class="mini-title">今日新上</h3>
          <p class="mini-copy">优先查看刚刚进入列表的房源，减少错过热门房的概率。</p>
        </section>

        <section class="mini-panel app-surface">
          <p class="section-eyebrow">Budget</p>
          <h3 class="mini-title">低总价优先</h3>
          <p class="mini-copy">从总价更友好的房源开始浏览，再决定是否换空间和位置。</p>
        </section>
      </aside>
    </section>
  </div>
</template>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:run -- src/views/__tests__/HomeView.spec.js src/components/__tests__/HomeHero.spec.js src/components/__tests__/HomeQuickLinks.spec.js src/components/__tests__/HouseCard.spec.js`

Expected: PASS with the homepage centered on search, entry cards, listings, and secondary recommendation blocks.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/HomeView.vue frontend/src/views/__tests__/HomeView.spec.js
git commit -m "feat: recompose homepage around search and conversion paths"
```

---

### Task 5: Run Focused Verification and Full Frontend Regression

**Files:**
- Modify: `frontend/src/components/home/HomeHero.vue`
- Modify: `frontend/src/components/home/HomeQuickLinks.vue`
- Modify: `frontend/src/components/HouseCard.vue`
- Modify: `frontend/src/views/HomeView.vue`
- Test: `frontend/src/components/__tests__/HomeHero.spec.js`
- Test: `frontend/src/components/__tests__/HomeQuickLinks.spec.js`
- Test: `frontend/src/components/__tests__/HouseCard.spec.js`
- Test: `frontend/src/views/__tests__/HomeView.spec.js`

- [ ] **Step 1: Run the focused homepage regression suite**

Run: `npm run test:run -- src/components/__tests__/HomeHero.spec.js src/components/__tests__/HomeQuickLinks.spec.js src/components/__tests__/HouseCard.spec.js src/views/__tests__/HomeView.spec.js`

Expected: PASS with all homepage-specific tests green.

- [ ] **Step 2: Run the full frontend test suite**

Run: `npm run test:run`

Expected: PASS with all existing view/component tests green and no new homepage regressions.

- [ ] **Step 3: Run the production build**

Run: `npm run build`

Expected: PASS with a successful Vite build and no template or import errors from the homepage refactor.

- [ ] **Step 4: Manually verify the homepage in the browser**

Run: `npm run dev`

Expected:
- Homepage first screen shows a single search card, not a split hero.
- Quick entry cards show `通勤找房` / `地图找房` / `查看全部房源`.
- Featured cards surface region, rental type, area, and commute hints.
- No large explanatory block competes with search at the top.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/home/HomeHero.vue frontend/src/components/home/HomeQuickLinks.vue frontend/src/components/HouseCard.vue frontend/src/views/HomeView.vue frontend/src/components/__tests__/HomeHero.spec.js frontend/src/components/__tests__/HomeQuickLinks.spec.js frontend/src/components/__tests__/HouseCard.spec.js frontend/src/views/__tests__/HomeView.spec.js
git commit -m "test: verify homepage redesign end to end"
```

---

## Self-Review

### Spec coverage

- Search-first hero: covered by Task 2 and Task 4.
- Removal of low-value explanatory hero block: covered by Task 2 and Task 4.
- Three high-value entry cards: covered by Task 1, Task 3, and Task 4.
- Featured listings with more decision density: covered by Task 3.
- Secondary “今日新上 / 低总价优先” blocks: covered by Task 4.
- Friendly states and no raw backend errors: preserved in Task 4 and verified in Task 5.

### Placeholder scan

- No `TODO`, `TBD`, or “implement later” placeholders remain.
- Each task includes exact file paths, concrete test code, concrete commands, and expected outcomes.

### Type consistency

- The new hero emits `search` and `preset`; `HomeView` only depends on `search`.
- Homepage quick-link copy in Task 1 matches the rendered expectations in Tasks 3 and 4.
- House card fields use `region`, `rentalType`, `area`, and `distance` consistently across implementation and tests.
