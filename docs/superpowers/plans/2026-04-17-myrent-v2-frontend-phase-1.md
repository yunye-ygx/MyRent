# MyRent v2 Frontend Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the current Vue frontend into a desktop-first, editorial-but-usable rental product by establishing a design foundation first and then redesigning the homepage, house list, house detail, and secondary shell pages.

**Architecture:** Keep the existing `Vue 3 + Vite + Pinia + Vue Router` stack, add `UnoCSS` and `Vitest` as the phase-1 foundation, and split the current all-in-one home view into a real homepage plus a dedicated house-results page. Build the redesign around a shared token/theme layer, a responsive shell in `MainLayout.vue`, and focused page sections/components rather than one-off page-local CSS.

**Tech Stack:** Vue 3, Vite, Vue Router, Pinia, Axios, UnoCSS, Vitest, Vue Test Utils, jsdom

---

## File Structure

### Create

- `frontend/uno.config.js`
  Purpose: UnoCSS presets, shortcuts, and theme aliases used by every redesigned page.
- `frontend/vitest.config.js`
  Purpose: test runner config for Vue component/unit tests.
- `frontend/src/test/setup.js`
  Purpose: shared Vitest setup and DOM/test helpers.
- `frontend/src/styles/tokens.css`
  Purpose: CSS custom properties for palette, spacing, radius, shadow, and container widths.
- `frontend/src/styles/base.css`
  Purpose: global resets, typography defaults, and shared shell behavior.
- `frontend/src/design/site.js`
  Purpose: nav items, homepage quick links, brand-content cards, and repeated display metadata.
- `frontend/src/composables/useHouseFeed.js`
  Purpose: shared house-feed loading logic currently embedded in `HomeView.vue`.
- `frontend/src/components/layout/AppTopNav.vue`
  Purpose: desktop-first top navigation for the redesigned product shell.
- `frontend/src/components/layout/AppFooter.vue`
  Purpose: footer credibility block for homepage and content-heavy screens.
- `frontend/src/components/home/HomeHero.vue`
  Purpose: left-narrative/right-search homepage hero.
- `frontend/src/components/home/HomeQuickLinks.vue`
  Purpose: quick product-entry cards surfaced inside the homepage search dock.
- `frontend/src/components/home/HomeStoryStrip.vue`
  Purpose: editorial/lifestyle content strip below the hero.
- `frontend/src/components/house/HouseResultsHero.vue`
  Purpose: house list page heading, filters summary, and entry actions.
- `frontend/src/components/house/HouseDetailSummary.vue`
  Purpose: house detail hero media, metadata, and status summary.
- `frontend/src/components/house/HouseActionBar.vue`
  Purpose: sticky detail page CTA section for favorite / consult / deposit.
- `frontend/src/views/HouseListView.vue`
  Purpose: dedicated search-results/listing page separated from the new homepage.
- `frontend/src/components/__tests__/AppTopNav.spec.js`
  Purpose: shell navigation regression coverage.
- `frontend/src/components/__tests__/HouseCard.spec.js`
  Purpose: card rendering/status/fallback coverage.
- `frontend/src/composables/__tests__/useHouseFeed.spec.js`
  Purpose: pagination and hot/nearby mode coverage.
- `frontend/src/views/__tests__/HomeView.spec.js`
  Purpose: homepage structure and CTA coverage.
- `frontend/src/views/__tests__/HouseListView.spec.js`
  Purpose: list page states and query-driven rendering coverage.
- `frontend/src/views/__tests__/HouseDetailView.spec.js`
  Purpose: detail page action-state coverage.
- `frontend/src/views/__tests__/SecondaryViews.spec.js`
  Purpose: login/messages/mine shell coverage.

### Modify

- `frontend/package.json`
  Purpose: add UnoCSS/Vitest dependencies and scripts.
- `frontend/vite.config.js`
  Purpose: register UnoCSS plugin and align aliases for tests.
- `frontend/src/main.js`
  Purpose: import new theme/base styles and UnoCSS virtual stylesheet.
- `frontend/src/router/index.js`
  Purpose: route split for homepage vs. house results, plus shell navigation mapping.
- `frontend/src/layouts/MainLayout.vue`
  Purpose: replace bottom-only mobile shell with responsive desktop shell and mobile fallback.
- `frontend/src/components/AppTabBar.vue`
  Purpose: keep as mobile navigation only, visually harmonized with the new shell.
- `frontend/src/components/HouseCard.vue`
  Purpose: redesign card layout for desktop list/homepage use and fix visible mojibake copy.
- `frontend/src/components/EmptyState.vue`
  Purpose: redesign shared empty state surfaces.
- `frontend/src/components/LoadingState.vue`
  Purpose: redesign shared loading state/skeleton behavior.
- `frontend/src/views/HomeView.vue`
  Purpose: convert from current results page into the new narrative homepage.
- `frontend/src/views/HouseDetailView.vue`
  Purpose: redesign detail page layout and CTA structure.
- `frontend/src/views/MessagesView.vue`
  Purpose: harmonize top section, list shell, and empty state.
- `frontend/src/views/MineView.vue`
  Purpose: harmonize profile card, menu grid/list, and mock-module explanation.
- `frontend/src/views/auth/LoginView.vue`
  Purpose: harmonize auth shell and copy.
- `frontend/src/views/auth/RegisterView.vue`
  Purpose: harmonize auth shell and copy.
- `frontend/src/utils/format.js`
  Purpose: normalize visible copy formatting where current strings are mojibake.

## Task 1: Tooling and Theme Foundation

**Files:**
- Create: `frontend/uno.config.js`
- Create: `frontend/vitest.config.js`
- Create: `frontend/src/test/setup.js`
- Create: `frontend/src/styles/tokens.css`
- Create: `frontend/src/styles/base.css`
- Modify: `frontend/package.json`
- Modify: `frontend/vite.config.js`
- Modify: `frontend/src/main.js`

- [ ] **Step 1: Add UnoCSS and Vitest dependencies/scripts**

```json
{
  "scripts": {
    "dev": "vite --force",
    "build": "vite build",
    "preview": "vite preview",
    "test": "vitest",
    "test:run": "vitest run"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.2.1",
    "@vue/test-utils": "^2.4.6",
    "@unocss/preset-attributify": "^0.66.5",
    "@unocss/preset-icons": "^0.66.5",
    "@unocss/preset-uno": "^0.66.5",
    "jsdom": "^26.0.0",
    "unocss": "^0.66.5",
    "vite": "^6.1.0",
    "vitest": "^3.1.1"
  }
}
```

- [ ] **Step 2: Wire Vite, UnoCSS, Vitest, and the new global style entry**

```js
// frontend/vite.config.js
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'

export default defineConfig({
  plugins: [vue(), UnoCSS()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.js']
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\\/api/, '')
      },
      '/ws': {
        target: 'ws://localhost:8081',
        ws: true,
        changeOrigin: true
      }
    }
  }
})
```

```js
// frontend/uno.config.js
import { defineConfig, presetAttributify, presetIcons, presetUno } from 'unocss'

export default defineConfig({
  presets: [presetUno(), presetAttributify(), presetIcons()],
  shortcuts: {
    'app-shell': 'min-h-screen bg-[var(--color-bg)] text-[var(--color-text)]',
    'app-container': 'mx-auto w-full max-w-[var(--container-max)] px-6 lg:px-10',
    'app-surface': 'rounded-[var(--radius-xl)] bg-[var(--color-surface)] shadow-[var(--shadow-soft)]',
    'app-muted': 'text-[var(--color-text-muted)]'
  }
})
```

```js
// frontend/vitest.config.js
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.js']
  }
})
```

```js
// frontend/src/main.js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import 'virtual:uno.css'
import './styles/tokens.css'
import './styles/base.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
```

- [ ] **Step 3: Add theme tokens and base styles**

```css
/* frontend/src/styles/tokens.css */
:root {
  --color-bg: #f6f0e9;
  --color-surface: #fffdf9;
  --color-surface-strong: #f1e6d9;
  --color-surface-dark: #241b16;
  --color-text: #211a16;
  --color-text-muted: #6f6156;
  --color-border: rgba(53, 36, 25, 0.12);
  --color-accent: #34261e;
  --color-accent-contrast: #f7f1ea;
  --color-success: #446b55;
  --color-warning: #9a6b33;
  --color-danger: #9e4d42;
  --radius-lg: 18px;
  --radius-xl: 26px;
  --shadow-soft: 0 18px 48px rgba(49, 33, 23, 0.08);
  --container-max: 1200px;
}
```

```css
/* frontend/src/styles/base.css */
* { box-sizing: border-box; }
html, body, #app { min-height: 100%; margin: 0; }
body {
  font-family: 'Segoe UI', 'PingFang SC', sans-serif;
  background: radial-gradient(circle at top, #fbf5ee 0%, #f4ebe1 35%, #f6f0e9 100%);
  color: var(--color-text);
}
a { color: inherit; text-decoration: none; }
button, input, textarea { font: inherit; }
img { display: block; max-width: 100%; }
```

```js
// frontend/src/test/setup.js
import { config } from '@vue/test-utils'

config.global.mocks = {
  $t: (value) => value
}
```

- [ ] **Step 4: Install dependencies and verify the new foundation builds**

Run: `npm install`  
Expected: new lockfile entries for UnoCSS/Vitest packages

Run: `npm run build`  
Expected: `vite build` completes successfully

- [ ] **Step 5: Commit**

```bash
git add frontend/package.json frontend/vite.config.js frontend/uno.config.js frontend/vitest.config.js frontend/src/main.js frontend/src/styles/tokens.css frontend/src/styles/base.css frontend/src/test/setup.js
git commit -m "build: add frontend theme and test foundation"
```

## Task 2: Responsive Shell and Navigation

**Files:**
- Create: `frontend/src/design/site.js`
- Create: `frontend/src/components/layout/AppTopNav.vue`
- Create: `frontend/src/components/layout/AppFooter.vue`
- Create: `frontend/src/components/__tests__/AppTopNav.spec.js`
- Modify: `frontend/src/layouts/MainLayout.vue`
- Modify: `frontend/src/components/AppTabBar.vue`
- Modify: `frontend/src/router/index.js`

- [ ] **Step 1: Write the failing navigation test**

```js
// frontend/src/components/__tests__/AppTopNav.spec.js
import { mount } from '@vue/test-utils'
import AppTopNav from '@/components/layout/AppTopNav.vue'

describe('AppTopNav', () => {
  it('renders the configured nav items and marks the current route', () => {
    const wrapper = mount(AppTopNav, {
      props: {
        items: [
          { label: '首页', to: '/home' },
          { label: '找房', to: '/houses' }
        ],
        currentPath: '/houses'
      }
    })

    expect(wrapper.text()).toContain('首页')
    expect(wrapper.text()).toContain('找房')
    expect(wrapper.get('[data-nav="/houses"]').classes()).toContain('is-active')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test:run -- src/components/__tests__/AppTopNav.spec.js`  
Expected: FAIL because `AppTopNav.vue` does not exist yet

- [ ] **Step 3: Implement the shell metadata, top nav, footer, and responsive main layout**

```js
// frontend/src/design/site.js
export const topNavItems = [
  { label: '首页', to: '/home' },
  { label: '找房', to: '/houses' },
  { label: '地图', to: '/map' },
  { label: '智能找房', to: '/messages' },
  { label: '消息', to: '/messages' }
]

export const mobileTabItems = [
  { path: '/home', label: '首页', icon: '🏠' },
  { path: '/houses', label: '找房', icon: '🔎' },
  { path: '/messages', label: '消息', icon: '💬' },
  { path: '/mine', label: '我的', icon: '👤' }
]
```

```vue
<!-- frontend/src/components/layout/AppTopNav.vue -->
<template>
  <header class="app-surface hidden items-center justify-between px-6 py-4 lg:flex">
    <div class="text-xs tracking-[0.3em] uppercase app-muted">MyRent</div>
    <nav class="flex items-center gap-2">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        :data-nav="item.to"
        class="rounded-full px-4 py-2 text-sm transition"
        :class="currentPath.startsWith(item.to) ? 'is-active bg-[var(--color-accent)] text-[var(--color-accent-contrast)]' : 'app-muted hover:bg-[var(--color-surface-strong)]'"
      >
        {{ item.label }}
      </RouterLink>
    </nav>
  </header>
</template>

<script setup>
defineProps({
  items: { type: Array, required: true },
  currentPath: { type: String, required: true }
})
</script>
```

```vue
<!-- frontend/src/layouts/MainLayout.vue -->
<template>
  <div class="app-shell">
    <div class="app-container flex min-h-screen flex-col gap-6 py-5 lg:py-8">
      <AppTopNav :items="topNavItems" :current-path="route.path" />
      <main class="min-h-0 flex-1">
        <router-view />
      </main>
      <AppFooter class="hidden lg:block" />
    </div>
    <AppTabBar class="lg:hidden" />
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
import AppTabBar from '@/components/AppTabBar.vue'
import AppFooter from '@/components/layout/AppFooter.vue'
import AppTopNav from '@/components/layout/AppTopNav.vue'
import { topNavItems } from '@/design/site'

const route = useRoute()
</script>
```

```js
// frontend/src/router/index.js
{
  path: 'houses',
  name: 'house-list',
  component: () => import('@/views/HouseListView.vue')
}
```

- [ ] **Step 4: Run tests to verify the shell passes**

Run: `npm run test:run -- src/components/__tests__/AppTopNav.spec.js`  
Expected: PASS

Run: `npm run build`  
Expected: PASS with `MainLayout.vue` compiling against the new shell

- [ ] **Step 5: Commit**

```bash
git add frontend/src/design/site.js frontend/src/components/layout/AppTopNav.vue frontend/src/components/layout/AppFooter.vue frontend/src/components/AppTabBar.vue frontend/src/layouts/MainLayout.vue frontend/src/router/index.js frontend/src/components/__tests__/AppTopNav.spec.js
git commit -m "feat: add responsive frontend shell"
```

## Task 3: Shared Cards and State Surfaces

**Files:**
- Create: `frontend/src/components/__tests__/HouseCard.spec.js`
- Modify: `frontend/src/components/HouseCard.vue`
- Modify: `frontend/src/components/EmptyState.vue`
- Modify: `frontend/src/components/LoadingState.vue`
- Modify: `frontend/src/utils/format.js`

- [ ] **Step 1: Write the failing shared-surface test**

```js
// frontend/src/components/__tests__/HouseCard.spec.js
import { mount } from '@vue/test-utils'
import HouseCard from '@/components/HouseCard.vue'

describe('HouseCard', () => {
  it('renders title, price, publisher fallback, and availability state', () => {
    const wrapper = mount(HouseCard, {
      props: {
        house: { id: 9, title: '天河创意公寓', price: 4800, depositAmount: 4800, status: 1 }
      }
    })

    expect(wrapper.text()).toContain('天河创意公寓')
    expect(wrapper.text()).toContain('¥4,800')
    expect(wrapper.text()).toContain('未知发布者')
    expect(wrapper.text()).toContain('可租')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test:run -- src/components/__tests__/HouseCard.spec.js`  
Expected: FAIL because the current card text and format helpers do not match the new copy/price format

- [ ] **Step 3: Implement the redesigned card and shared empty/loading states**

```js
// frontend/src/utils/format.js
export function formatPrice(value) {
  if (value === null || value === undefined || value === '') return '--'
  return `¥${Number(value).toLocaleString()}`
}

export function getHouseStatusText(status) {
  if (status === 1) return '可租'
  if (status === 2) return '锁定中'
  return '暂不可预订'
}
```

```vue
<!-- frontend/src/components/HouseCard.vue -->
<template>
  <article class="app-surface grid cursor-pointer gap-4 p-4 lg:grid-cols-[220px_1fr]" @click="$emit('click')">
    <img class="h-[170px] w-full rounded-[20px] object-cover bg-[var(--color-surface-strong)] lg:h-full" :src="cover" alt="house cover" />
    <div class="min-w-0">
      <div class="mb-3 flex items-start justify-between gap-3">
        <div>
          <p class="mb-2 text-xs uppercase tracking-[0.22em] app-muted">精选房源</p>
          <h3 class="line-clamp-2 text-xl font-semibold text-[var(--color-text)]">{{ house.title || '未命名房源' }}</h3>
        </div>
        <span class="rounded-full px-3 py-1 text-xs" :class="statusClass">{{ statusText }}</span>
      </div>
      <p class="text-2xl font-semibold text-[var(--color-accent)]">{{ formatPrice(house.price) }}<span class="ml-1 text-sm font-normal app-muted">/ 月</span></p>
      <p class="mt-3 text-sm app-muted">押金 {{ formatPrice(house.depositAmount) }}</p>
      <p class="mt-2 text-sm app-muted">发布者 {{ publisherText }}</p>
      <p v-if="distanceText" class="mt-2 text-sm text-[var(--color-warning)]">{{ distanceText }}</p>
      <p v-if="hotText" class="mt-2 text-sm text-[var(--color-warning)]">{{ hotText }}</p>
    </div>
  </article>
</template>
```

```vue
<!-- frontend/src/components/EmptyState.vue -->
<template>
  <section class="app-surface px-6 py-12 text-center">
    <p class="text-xs uppercase tracking-[0.24em] app-muted">No Data</p>
    <h3 class="mt-3 text-xl font-semibold">{{ title }}</h3>
    <p v-if="description" class="mx-auto mt-3 max-w-xl text-sm app-muted">{{ description }}</p>
    <button v-if="actionText" class="mt-6 rounded-full bg-[var(--color-surface-dark)] px-5 py-3 text-sm text-[var(--color-accent-contrast)]" @click="$emit('action')">{{ actionText }}</button>
  </section>
</template>
```

```vue
<!-- frontend/src/components/LoadingState.vue -->
<template>
  <div class="app-surface inline-flex items-center gap-3 px-4 py-3 text-sm app-muted">
    <span class="h-3 w-3 animate-spin rounded-full border-2 border-[var(--color-surface-strong)] border-t-[var(--color-accent)]"></span>
    <span>{{ text }}</span>
  </div>
</template>
```

- [ ] **Step 4: Run tests to verify the shared surfaces pass**

Run: `npm run test:run -- src/components/__tests__/HouseCard.spec.js`  
Expected: PASS

Run: `npm run build`  
Expected: PASS with updated shared components

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/HouseCard.vue frontend/src/components/EmptyState.vue frontend/src/components/LoadingState.vue frontend/src/utils/format.js frontend/src/components/__tests__/HouseCard.spec.js
git commit -m "feat: redesign shared housing surfaces"
```

## Task 4: Extract House Feed Logic and Rebuild the Homepage

**Files:**
- Create: `frontend/src/composables/useHouseFeed.js`
- Create: `frontend/src/composables/__tests__/useHouseFeed.spec.js`
- Create: `frontend/src/components/home/HomeHero.vue`
- Create: `frontend/src/components/home/HomeQuickLinks.vue`
- Create: `frontend/src/components/home/HomeStoryStrip.vue`
- Create: `frontend/src/views/__tests__/HomeView.spec.js`
- Modify: `frontend/src/design/site.js`
- Modify: `frontend/src/views/HomeView.vue`

- [ ] **Step 1: Write the failing composable and homepage tests**

```js
// frontend/src/composables/__tests__/useHouseFeed.spec.js
import { nextTick } from 'vue'
import { useHouseFeed } from '@/composables/useHouseFeed'

describe('useHouseFeed', () => {
  it('resets pagination when switching from hot to nearby mode', async () => {
    const hotLoader = vi.fn().mockResolvedValue({ houses: [{ id: 1 }] })
    const nearbyLoader = vi.fn().mockResolvedValue({ houses: [{ id: 2 }] })
    const feed = useHouseFeed({ hotLoader, nearbyLoader, defaultCity: '广州' })

    await feed.loadNext()
    feed.activateNearby('体育西路')
    await nextTick()

    expect(feed.mode.value).toBe('nearby')
    expect(feed.houses.value).toEqual([])
    expect(feed.current.value).toBe(1)
  })
})
```

```js
// frontend/src/views/__tests__/HomeView.spec.js
import { mount } from '@vue/test-utils'
import HomeView from '@/views/HomeView.vue'

vi.mock('@/design/site', () => ({
  homeQuickLinks: [{ title: '地图找房', description: '按区域探索', to: '/map' }],
  homeStoryCards: [{ eyebrow: 'City Edit', title: '通勤友好片区', description: '适合通勤和生活平衡' }]
}))

describe('HomeView', () => {
  it('renders the narrative hero and brand-content strip', () => {
    const wrapper = mount(HomeView, {
      global: { stubs: ['RouterLink'] }
    })

    expect(wrapper.text()).toContain('Rent with taste')
    expect(wrapper.text()).toContain('City Edit')
    expect(wrapper.text()).toContain('地图找房')
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm run test:run -- src/composables/__tests__/useHouseFeed.spec.js src/views/__tests__/HomeView.spec.js`  
Expected: FAIL because the composable and homepage sections do not exist yet

- [ ] **Step 3: Implement the shared feed composable and replace the old home page with the approved homepage IA**

```js
// frontend/src/composables/useHouseFeed.js
import { ref } from 'vue'

export function useHouseFeed({ hotLoader, nearbyLoader, defaultCity }) {
  const houses = ref([])
  const loading = ref(false)
  const error = ref('')
  const current = ref(1)
  const size = ref(10)
  const hasMore = ref(true)
  const mode = ref('hot')
  const activeLocation = ref('')
  const resultTip = ref('')

  function resetPaging() {
    houses.value = []
    current.value = 1
    hasMore.value = true
    error.value = ''
  }

  async function loadNext() {
    if (loading.value || !hasMore.value) return
    loading.value = true
    error.value = ''
    try {
      const result = mode.value === 'nearby'
        ? await nearbyLoader({ locationName: activeLocation.value, city: defaultCity, page: current.value, size: size.value })
        : await hotLoader({ page: current.value, size: size.value })
      const records = result?.houses || []
      houses.value = [...houses.value, ...records]
      hasMore.value = records.length >= size.value
      current.value += 1
      resultTip.value = result?.tipMessage || ''
    } catch (err) {
      error.value = err?.message || '房源加载失败'
    } finally {
      loading.value = false
    }
  }

  function activateNearby(locationName) {
    mode.value = 'nearby'
    activeLocation.value = locationName
    resultTip.value = ''
    resetPaging()
  }

  return { houses, loading, error, current, size, hasMore, mode, activeLocation, resultTip, resetPaging, loadNext, activateNearby }
}
```

```js
// frontend/src/design/site.js
export const homeQuickLinks = [
  { title: '地图找房', description: '按区域和地标快速探索', to: '/map' },
  { title: '智能找房', description: '通过偏好描述筛选房源', to: '/messages' },
  { title: '热门推荐', description: '直接进入精选房源列表', to: '/houses' }
]

export const homeStoryCards = [
  { eyebrow: 'City Edit', title: '通勤友好片区', description: '让第一眼就像一份精选居住指南。' },
  { eyebrow: 'Renter Notes', title: '预算与空间平衡', description: '把产品说明写得像成熟平台，而不是课堂作业。' }
]
```

```vue
<!-- frontend/src/views/HomeView.vue -->
<template>
  <div class="flex flex-col gap-6">
    <HomeHero />
    <HomeStoryStrip />
    <section class="grid gap-6 lg:grid-cols-[1.1fr_.9fr]">
      <div class="app-surface px-6 py-6">
        <p class="text-xs uppercase tracking-[0.24em] app-muted">Featured Listings</p>
        <div class="mt-4 flex flex-col gap-4">
          <HouseCard v-for="house in houses" :key="house.id" :house="house" @click="toDetail(house.id)" />
        </div>
      </div>
      <aside class="flex flex-col gap-4">
        <HomeQuickLinks />
        <EmptyState title="更多交易链路放在后续阶段" description="Phase 1 先把视觉、信息架构和关键浏览链路做完整。" />
      </aside>
    </section>
  </div>
</template>
```

- [ ] **Step 4: Run tests and a targeted homepage build check**

Run: `npm run test:run -- src/composables/__tests__/useHouseFeed.spec.js src/views/__tests__/HomeView.spec.js`  
Expected: PASS

Run: `npm run build`  
Expected: PASS with `HomeView.vue` compiling against the new home sections

- [ ] **Step 5: Commit**

```bash
git add frontend/src/composables/useHouseFeed.js frontend/src/composables/__tests__/useHouseFeed.spec.js frontend/src/components/home/HomeHero.vue frontend/src/components/home/HomeQuickLinks.vue frontend/src/components/home/HomeStoryStrip.vue frontend/src/views/HomeView.vue frontend/src/views/__tests__/HomeView.spec.js frontend/src/design/site.js
git commit -m "feat: rebuild homepage around editorial shell"
```

## Task 5: Build the Dedicated House Results Page

**Files:**
- Create: `frontend/src/views/HouseListView.vue`
- Create: `frontend/src/components/house/HouseResultsHero.vue`
- Create: `frontend/src/views/__tests__/HouseListView.spec.js`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/design/site.js`

- [ ] **Step 1: Write the failing house-list test**

```js
// frontend/src/views/__tests__/HouseListView.spec.js
import { mount } from '@vue/test-utils'
import HouseListView from '@/views/HouseListView.vue'

vi.mock('@/composables/useHouseFeed', () => ({
  useHouseFeed: () => ({
    houses: { value: [{ id: 1, title: '珠江新城公寓', price: 5200, depositAmount: 5200, status: 1 }] },
    loading: { value: false },
    error: { value: '' },
    hasMore: { value: false },
    mode: { value: 'hot' },
    resultTip: { value: '共 1 套房源' },
    loadNext: vi.fn(),
    activateNearby: vi.fn(),
    resetPaging: vi.fn()
  })
}))

describe('HouseListView', () => {
  it('renders a dedicated results heading and result cards', () => {
    const wrapper = mount(HouseListView, {
      global: { stubs: ['HouseCard', 'LoadingState', 'EmptyState'] }
    })

    expect(wrapper.text()).toContain('精选房源列表')
    expect(wrapper.text()).toContain('共 1 套房源')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test:run -- src/views/__tests__/HouseListView.spec.js`  
Expected: FAIL because `HouseListView.vue` does not exist yet

- [ ] **Step 3: Implement the results page and route it from `/houses`**

```vue
<!-- frontend/src/views/HouseListView.vue -->
<template>
  <div class="flex flex-col gap-6">
    <HouseResultsHero
      title="精选房源列表"
      :result-tip="resultTip.value"
      :is-nearby-mode="mode.value === 'nearby'"
      @search="handleSearch"
      @reset="handleReset"
    />

    <LoadingState v-if="loading.value && !houses.value.length" text="正在加载房源..." />
    <p v-else-if="error.value" class="text-sm text-[var(--color-danger)]">{{ error.value }}</p>

    <div v-if="houses.value.length" class="grid gap-4">
      <HouseCard v-for="house in houses.value" :key="house.id" :house="house" @click="toDetail(house.id)" />
    </div>

    <EmptyState
      v-else-if="!loading.value"
      title="暂时没有匹配房源"
      description="可以换一个地点名称，或者回到精选推荐继续浏览。"
      action-text="返回精选推荐"
      @action="router.push('/home')"
    />
  </div>
</template>
```

```js
// frontend/src/router/index.js
{
  path: 'houses',
  name: 'house-list',
  component: () => import('@/views/HouseListView.vue')
}
```

- [ ] **Step 4: Run the test and a list-page build verification**

Run: `npm run test:run -- src/views/__tests__/HouseListView.spec.js`  
Expected: PASS

Run: `npm run build`  
Expected: PASS with `HomeView` and `HouseListView` split cleanly

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/HouseListView.vue frontend/src/components/house/HouseResultsHero.vue frontend/src/views/__tests__/HouseListView.spec.js frontend/src/router/index.js
git commit -m "feat: add dedicated house results page"
```

## Task 6: Redesign the House Detail Page

**Files:**
- Create: `frontend/src/components/house/HouseDetailSummary.vue`
- Create: `frontend/src/components/house/HouseActionBar.vue`
- Create: `frontend/src/views/__tests__/HouseDetailView.spec.js`
- Modify: `frontend/src/views/HouseDetailView.vue`

- [ ] **Step 1: Write the failing detail-page test**

```js
// frontend/src/views/__tests__/HouseDetailView.spec.js
import { mount } from '@vue/test-utils'
import HouseDetailView from '@/views/HouseDetailView.vue'

vi.mock('@/api/house', () => ({
  fetchHouseById: vi.fn().mockResolvedValue({ id: 7, title: '天河北一居室', price: 5600, depositAmount: 5600, status: 1, publisherUserId: 9 }),
  fetchHouseFavoriteStatus: vi.fn().mockResolvedValue({ favorited: false, favoriteCount: 3 }),
  favoriteHouse: vi.fn(),
  unfavoriteHouse: vi.fn()
}))

vi.mock('@/api/user', () => ({
  fetchUserById: vi.fn().mockResolvedValue({ name: '房东 A' })
}))

describe('HouseDetailView', () => {
  it('shows the redesigned detail summary and primary action', async () => {
    const wrapper = mount(HouseDetailView, {
      global: { stubs: ['RouterLink'] }
    })

    await Promise.resolve()
    await Promise.resolve()

    expect(wrapper.text()).toContain('天河北一居室')
    expect(wrapper.text()).toContain('提交定金')
    expect(wrapper.text()).toContain('房东 A')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test:run -- src/views/__tests__/HouseDetailView.spec.js`  
Expected: FAIL because the new summary/action components do not exist yet

- [ ] **Step 3: Implement the detail summary, sticky action bar, and desktop-first layout**

```vue
<!-- frontend/src/views/HouseDetailView.vue -->
<template>
  <div class="flex flex-col gap-6">
    <LoadingState v-if="loading" text="正在加载房源详情..." />
    <p v-else-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>

    <template v-else-if="house">
      <HouseDetailSummary
        :house="house"
        :cover="cover"
        :publisher-name="publisherName"
        :favorite-count="favoriteCountText"
        :status-text="statusText"
        @back="goBack"
      />

      <div class="grid gap-6 lg:grid-cols-[1.1fr_.9fr]">
        <section class="app-surface px-6 py-6">
          <p class="text-xs uppercase tracking-[0.24em] app-muted">House Notes</p>
          <p class="mt-4 text-sm leading-7 app-muted">押金、收藏、咨询和定金动作都保留现有业务能力，但页面层次改成更适合桌面浏览的结构。</p>
        </section>
        <HouseActionBar
          :favorite-loading="favoriteLoading"
          :favorite-button-text="favoriteButtonText"
          :lock-loading="lockLoading"
          :can-submit="house.status === 1"
          @favorite="toggleFavorite"
          @consult="goConsult"
          @deposit="submitDeposit"
        />
      </div>
    </template>

    <EmptyState
      v-else
      title="房源不存在"
      description="返回首页继续浏览精选房源。"
      action-text="返回首页"
      @action="router.push('/home')"
    />
  </div>
</template>
```

- [ ] **Step 4: Run the detail test and full build**

Run: `npm run test:run -- src/views/__tests__/HouseDetailView.spec.js`  
Expected: PASS

Run: `npm run build`  
Expected: PASS with the updated detail page

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/HouseDetailView.vue frontend/src/components/house/HouseDetailSummary.vue frontend/src/components/house/HouseActionBar.vue frontend/src/views/__tests__/HouseDetailView.spec.js
git commit -m "feat: redesign house detail page"
```

## Task 7: Harmonize Secondary Pages and Shared States

**Files:**
- Create: `frontend/src/views/__tests__/SecondaryViews.spec.js`
- Modify: `frontend/src/views/auth/LoginView.vue`
- Modify: `frontend/src/views/auth/RegisterView.vue`
- Modify: `frontend/src/views/MessagesView.vue`
- Modify: `frontend/src/views/MineView.vue`
- Modify: `frontend/src/components/SessionItem.vue`

- [ ] **Step 1: Write the failing secondary-views test**

```js
// frontend/src/views/__tests__/SecondaryViews.spec.js
import { mount } from '@vue/test-utils'
import LoginView from '@/views/auth/LoginView.vue'
import MineView from '@/views/MineView.vue'

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    profile: { name: '测试用户', phone: '13800138000' },
    logout: vi.fn()
  })
}))

describe('secondary page shells', () => {
  it('renders the new auth messaging and mine overview copy', () => {
    const loginWrapper = mount(LoginView, {
      global: { stubs: ['RouterLink'] }
    })
    const mineWrapper = mount(MineView, {
      global: { stubs: ['RouterLink'] }
    })

    expect(loginWrapper.text()).toContain('登录 MyRent')
    expect(mineWrapper.text()).toContain('功能入口')
    expect(mineWrapper.text()).toContain('测试用户')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test:run -- src/views/__tests__/SecondaryViews.spec.js`  
Expected: FAIL until the updated shells and normalized copy are in place

- [ ] **Step 3: Implement consistent secondary-page shells**

```vue
<!-- frontend/src/views/auth/LoginView.vue -->
<template>
  <div class="app-shell flex min-h-screen items-center justify-center px-6 py-10">
    <div class="app-surface w-full max-w-[520px] px-8 py-10">
      <p class="text-xs uppercase tracking-[0.24em] app-muted">Welcome Back</p>
      <h1 class="mt-3 text-4xl font-semibold">登录 MyRent</h1>
      <p class="mt-3 text-sm leading-7 app-muted">先完成登录，再进入精选房源、消息和订单链路。</p>
      <div class="mt-8 flex flex-col gap-5">
        <label class="flex flex-col gap-2 text-sm">
          <span class="app-muted">手机号</span>
          <input v-model.trim="form.phone" class="rounded-[18px] border border-[var(--color-border)] bg-transparent px-4 py-3" placeholder="请输入 11 位手机号" />
        </label>
        <label class="flex flex-col gap-2 text-sm">
          <span class="app-muted">密码</span>
          <input v-model="form.password" type="password" class="rounded-[18px] border border-[var(--color-border)] bg-transparent px-4 py-3" placeholder="请输入密码" />
        </label>
        <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
        <button class="rounded-full bg-[var(--color-surface-dark)] px-5 py-3 text-sm text-[var(--color-accent-contrast)]" :disabled="loading" @click="handleLogin">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </div>
    </div>
  </div>
</template>
```

```vue
<!-- frontend/src/views/MineView.vue -->
<template>
  <div class="flex flex-col gap-6">
    <section class="app-surface grid gap-4 px-6 py-6 lg:grid-cols-[auto_1fr_auto] lg:items-center">
      <div class="flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-surface-strong)] text-2xl font-semibold text-[var(--color-accent)]">{{ avatarText }}</div>
      <div>
        <p class="text-xs uppercase tracking-[0.22em] app-muted">Profile</p>
        <h2 class="mt-2 text-2xl font-semibold">{{ authStore.profile?.name || '未命名用户' }}</h2>
        <p class="mt-2 text-sm app-muted">{{ authStore.profile?.phone || '--' }}</p>
      </div>
      <button class="rounded-full bg-[var(--color-surface-dark)] px-5 py-3 text-sm text-[var(--color-accent-contrast)]" @click="logout">退出登录</button>
    </section>
  </div>
</template>
```

- [ ] **Step 4: Run the test, then run the full suite and build**

Run: `npm run test:run -- src/views/__tests__/SecondaryViews.spec.js`  
Expected: PASS

Run: `npm run test:run`  
Expected: PASS for all specs added in earlier tasks

Run: `npm run build`  
Expected: PASS for the redesigned secondary pages

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/auth/LoginView.vue frontend/src/views/auth/RegisterView.vue frontend/src/views/MessagesView.vue frontend/src/views/MineView.vue frontend/src/components/SessionItem.vue frontend/src/views/__tests__/SecondaryViews.spec.js
git commit -m "feat: harmonize secondary frontend pages"
```

## Task 8: Final QA and Delivery Pass

**Files:**
- Modify: `frontend/src/views/HomeView.vue`
- Modify: `frontend/src/views/HouseListView.vue`
- Modify: `frontend/src/views/HouseDetailView.vue`
- Modify: `frontend/src/layouts/MainLayout.vue`
- Modify: `frontend/src/components/HouseCard.vue`
- Modify: `frontend/src/components/EmptyState.vue`
- Modify: `frontend/src/components/LoadingState.vue`

- [ ] **Step 1: Run the full automated verification suite**

Run: `npm run test:run`  
Expected: PASS across shell, card, composable, homepage, list page, detail page, and secondary-page specs

Run: `npm run build`  
Expected: PASS

- [ ] **Step 2: Run the app locally and manually verify the primary user path**

Run: `npm run dev`  
Expected: Vite dev server starts on `http://localhost:5173`

Manual path:

```text
/login -> /home -> /houses -> /house/:id -> /messages -> /mine
```

Expected:

- desktop shell uses top navigation
- mobile shell still has a usable bottom nav
- homepage first screen feels narrative + product-entry
- list page is distinct from homepage
- detail page keeps CTAs visible and clear

- [ ] **Step 3: Fix any QA regressions found in the manual pass**

```text
Allowed fixes in this step:
- spacing collisions
- overflow/wrapping bugs
- empty/loading/error copy mismatches
- desktop-only shell regressions
- mobile fallback regressions
```

- [ ] **Step 4: Re-run verification after the QA fixes**

Run: `npm run test:run && npm run build`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat: finalize MyRent v2 frontend phase 1"
```

## Self-Review

### Spec coverage

- Visual foundation: covered by Task 1 and Task 3.
- Desktop-first top navigation shell: covered by Task 2.
- Homepage IA approved in brainstorming: covered by Task 4.
- Dedicated list/search page: covered by Task 5.
- House detail redesign: covered by Task 6.
- Secondary-page harmonization: covered by Task 7.
- Verification criteria from the spec: covered by Task 8.

No spec gap remains for phase 1.

### Placeholder scan

- No `TODO` / `TBD` markers remain.
- All tasks reference exact file paths.
- Each task includes concrete commands and expected outcomes.

### Type consistency

- The plan consistently uses `/home` for homepage and `/houses` for results.
- Shared metadata lives in `src/design/site.js`.
- Shared feed logic lives in `src/composables/useHouseFeed.js`.
