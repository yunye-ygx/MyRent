# Mine Order Status Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (recommended by this repo) or superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Segment `/mine/orders` into explicit status tabs so renters can switch between unpaid, paid, cancelled, review, and refund contexts without changing routes or backend APIs.

**Architecture:** Keep the existing `MineOrderView.vue` route and API contract. Add local primary and secondary tab state, explicit frontend predicates for each tab, and a computed `visibleOrders` list that filters the currently loaded page data before rendering. Preserve the current action handlers, pagination loader, refresh button, and empty/error states, but make the list and empty-state copy tab-aware.

**Tech Stack:** Vue 3 `script setup`, Vue Router 4, Vitest, Vue Test Utils, Vite

---

## File Map

- Modify: `C:\Users\黄昊\.codex\worktrees\e47e\MyRent\frontend\src\views\mine\MineOrderView.vue`
  Responsibility: add tab state, filtering predicates, tab-aware empty/loading behavior, and the segmented navigation UI.
- Modify: `C:\Users\黄昊\.codex\worktrees\e47e\MyRent\frontend\src\views\__tests__\MineOrderView.spec.js`
  Responsibility: cover primary and secondary tab switching, filtering rules, default subtab selection, and tab-aware empty-state behavior.

### Task 1: Lock the Tab Rules in the Frontend Test

**Files:**
- Modify: `C:\Users\黄昊\.codex\worktrees\e47e\MyRent\frontend\src\views\__tests__\MineOrderView.spec.js`
- Test: `C:\Users\黄昊\.codex\worktrees\e47e\MyRent\frontend\src\views\__tests__\MineOrderView.spec.js`

- [ ] **Step 1: Replace the one-record fixture with a reusable mixed-status dataset**

Update `MineOrderView.spec.js` so the shared mock covers every target tab:

```js
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MineOrderView from '@/views/mine/MineOrderView.vue'
import { completeOrder, fetchMyOrderPage, repayOrder } from '@/api/order'

function buildOrder(overrides = {}) {
  return {
    id: overrides.id ?? 1,
    orderNo: overrides.orderNo ?? 'ORDER-1001',
    houseId: overrides.houseId ?? 101,
    amount: overrides.amount ?? 100000,
    status: overrides.status ?? 1,
    createTime: overrides.createTime ?? '2026-04-18T20:00:00',
    expireTime: overrides.expireTime ?? '2026-04-18T20:00:30',
    canComplete: overrides.canComplete ?? false,
    canReview: overrides.canReview ?? false,
    canEditReview: overrides.canEditReview ?? false,
    reviewId: overrides.reviewId ?? null,
    hasReview: overrides.hasReview ?? false,
    latestRefundStatus: overrides.latestRefundStatus ?? null
  }
}

const mixedOrders = [
  buildOrder({ id: 1, orderNo: 'UNPAID-1', status: 0 }),
  buildOrder({ id: 2, orderNo: 'PAID-1', status: 1, canComplete: true }),
  buildOrder({ id: 3, orderNo: 'PAID-REFUND-1', status: 1, latestRefundStatus: 0 }),
  buildOrder({ id: 4, orderNo: 'CANCELLED-1', status: 3 }),
  buildOrder({ id: 5, orderNo: 'PENDING-REVIEW-1', status: 5, canReview: true }),
  buildOrder({ id: 6, orderNo: 'REVIEWED-1', status: 6, hasReview: true, reviewId: 91, canEditReview: true }),
  buildOrder({ id: 7, orderNo: 'REFUNDING-1', status: 1, latestRefundStatus: 3 }),
  buildOrder({ id: 8, orderNo: 'REFUND-DONE-1', status: 4, latestRefundStatus: 2 })
]

vi.mock('@/api/order', () => ({
  fetchMyOrderPage: vi.fn(async () => ({
    records: mixedOrders,
    total: mixedOrders.length
  })),
  completeOrder: vi.fn(async () => undefined),
  repayOrder: vi.fn(async () => ({
    orderNo: 'UNPAID-1',
    paymentNo: 'PAY-1001',
    mockPayUrl: '/mock-pay/checkout?paymentNo=PAY-1001',
    expireTime: '2026-04-18T20:00:30'
  }))
}))
```

- [ ] **Step 2: Add a mount helper and failing tab-switch assertions**

Append these helpers and tests to `MineOrderView.spec.js`:

```js
async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/mine/orders', component: MineOrderView },
      { path: '/mine/orders/:orderNo/review', component: { template: '<div>review</div>' } },
      { path: '/mock-pay/checkout', component: { template: '<div />' } }
    ]
  })
  router.push('/mine/orders')
  await router.isReady()

  const wrapper = mount(MineOrderView, {
    global: { plugins: [router] }
  })
  await flushPromises()

  return { wrapper, router }
}

it('defaults to the unpaid tab and only renders unpaid orders', async () => {
  const { wrapper } = await mountView()

  expect(wrapper.text()).toContain('UNPAID-1')
  expect(wrapper.text()).not.toContain('PAID-1')
  expect(wrapper.text()).not.toContain('CANCELLED-1')
})

it('shows paid orders but excludes paid records already in refund flow', async () => {
  const { wrapper } = await mountView()

  await wrapper.get('[data-testid="primary-tab-PAID"]').trigger('click')
  await flushPromises()

  expect(wrapper.text()).toContain('PAID-1')
  expect(wrapper.text()).not.toContain('PAID-REFUND-1')
  expect(wrapper.text()).not.toContain('REFUNDING-1')
})

it('shows cancelled orders on the cancelled tab', async () => {
  const { wrapper } = await mountView()

  await wrapper.get('[data-testid="primary-tab-CANCELLED"]').trigger('click')
  await flushPromises()

  expect(wrapper.text()).toContain('CANCELLED-1')
  expect(wrapper.text()).not.toContain('UNPAID-1')
})
```

- [ ] **Step 3: Add failing tests for the two secondary-tab groups**

Append these tests:

```js
it('defaults review to pending review and can switch to reviewed', async () => {
  const { wrapper } = await mountView()

  await wrapper.get('[data-testid="primary-tab-REVIEW"]').trigger('click')
  await flushPromises()

  expect(wrapper.text()).toContain('PENDING-REVIEW-1')
  expect(wrapper.text()).not.toContain('REVIEWED-1')

  await wrapper.get('[data-testid="secondary-tab-REVIEWED"]').trigger('click')
  await flushPromises()

  expect(wrapper.text()).toContain('REVIEWED-1')
  expect(wrapper.text()).not.toContain('PENDING-REVIEW-1')
})

it('defaults refund to in-progress and can switch to finished', async () => {
  const { wrapper } = await mountView()

  await wrapper.get('[data-testid="primary-tab-REFUND"]').trigger('click')
  await flushPromises()

  expect(wrapper.text()).toContain('REFUNDING-1')
  expect(wrapper.text()).not.toContain('REFUND-DONE-1')

  await wrapper.get('[data-testid="secondary-tab-FINISHED"]').trigger('click')
  await flushPromises()

  expect(wrapper.text()).toContain('REFUND-DONE-1')
  expect(wrapper.text()).not.toContain('REFUNDING-1')
})
```

- [ ] **Step 4: Add a failing tab-aware empty-state test**

Append this test so the implementation must wire the empty-state copy to the active tab:

```js
it('shows a tab-aware empty state when the active tab has no matching orders', async () => {
  fetchMyOrderPage.mockResolvedValueOnce({
    records: [buildOrder({ id: 10, orderNo: 'ONLY-UNPAID', status: 0 })],
    total: 1
  })

  const { wrapper } = await mountView()

  await wrapper.get('[data-testid="primary-tab-REFUND"]').trigger('click')
  await flushPromises()

  expect(wrapper.text()).toContain('No refunds in progress')
  expect(wrapper.text()).not.toContain('ONLY-UNPAID')
})
```

- [ ] **Step 5: Run the focused frontend test to verify the new expectations fail**

Run:

```bash
cd frontend
npm run test:run -- src/views/__tests__/MineOrderView.spec.js
```

Expected: FAIL because the page still renders the raw mixed `orders` array, does not expose tab buttons, and does not provide tab-aware empty-state copy.

- [ ] **Step 6: Commit the failing test checkpoint**

```bash
git add frontend/src/views/__tests__/MineOrderView.spec.js
git commit -m "test(mine-order): lock status tab filtering rules"
```

### Task 2: Add Local Tab State and Filtering Predicates

**Files:**
- Modify: `C:\Users\黄昊\.codex\worktrees\e47e\MyRent\frontend\src\views\mine\MineOrderView.vue`
- Test: `C:\Users\黄昊\.codex\worktrees\e47e\MyRent\frontend\src\views\__tests__\MineOrderView.spec.js`

- [ ] **Step 1: Add the tab configuration and default state in `script setup`**

Update the imports and top-level constants in `MineOrderView.vue`:

```js
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchHouseById } from '@/api/house'
import { completeOrder, fetchMyOrderPage, repayOrder } from '@/api/order'
import { applyPaymentRefund, fetchOrderRefundStatuses } from '@/api/payment'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { formatDateTime, formatPrice, formatRequestError, getOrderStatusText } from '@/utils/format'

const PRIMARY_TABS = [
  { key: 'UNPAID', label: 'UNPAID' },
  { key: 'PAID', label: 'PAID' },
  { key: 'CANCELLED', label: 'CANCELLED' },
  { key: 'REVIEW', label: 'REVIEW' },
  { key: 'REFUND', label: 'REFUND' }
]

const REVIEW_TABS = [
  { key: 'PENDING_REVIEW', label: 'PENDING_REVIEW' },
  { key: 'REVIEWED', label: 'REVIEWED' }
]

const REFUND_TABS = [
  { key: 'IN_PROGRESS', label: 'IN_PROGRESS' },
  { key: 'FINISHED', label: 'FINISHED' }
]

const DEFAULT_PRIMARY_TAB = 'UNPAID'
const DEFAULT_REVIEW_TAB = 'PENDING_REVIEW'
const DEFAULT_REFUND_TAB = 'IN_PROGRESS'
```

Add these refs below the existing pagination refs:

```js
const activePrimaryTab = ref(DEFAULT_PRIMARY_TAB)
const activeReviewTab = ref(DEFAULT_REVIEW_TAB)
const activeRefundTab = ref(DEFAULT_REFUND_TAB)
```

- [ ] **Step 2: Add explicit predicate helpers for every tab bucket**

Append these helpers below `isRefundBlockingStatus(...)`:

```js
function isUnpaidOrder(order) {
  return order?.status === 0
}

function isPaidOrder(order) {
  return order?.status === 1 && order?.latestRefundStatus === null
}

function isCancelledOrder(order) {
  return order?.status === 3
}

function isPendingReviewOrder(order) {
  return Boolean(order?.canReview) && !isRefundBlockingStatus(order?.latestRefundStatus)
}

function isReviewedOrder(order) {
  return order?.status === 6 || Boolean(order?.hasReview) || Boolean(order?.reviewId)
}

function isRefundInProgressOrder(order) {
  return [0, 1, 3, 5].includes(order?.latestRefundStatus)
}

function isRefundFinishedOrder(order) {
  return [2, 4, 6].includes(order?.latestRefundStatus)
}
```

- [ ] **Step 3: Add the computed tab selectors and tab-aware empty-state mapping**

Append these computed values:

```js
const secondaryTabs = computed(() => {
  if (activePrimaryTab.value === 'REVIEW') return REVIEW_TABS
  if (activePrimaryTab.value === 'REFUND') return REFUND_TABS
  return []
})

const showSecondaryTabs = computed(() => secondaryTabs.value.length > 0)

const activeTabKey = computed(() => {
  if (activePrimaryTab.value === 'REVIEW') {
    return `REVIEW:${activeReviewTab.value}`
  }
  if (activePrimaryTab.value === 'REFUND') {
    return `REFUND:${activeRefundTab.value}`
  }
  return activePrimaryTab.value
})

const visibleOrders = computed(() => {
  if (activePrimaryTab.value === 'UNPAID') return orders.value.filter(isUnpaidOrder)
  if (activePrimaryTab.value === 'PAID') return orders.value.filter(isPaidOrder)
  if (activePrimaryTab.value === 'CANCELLED') return orders.value.filter(isCancelledOrder)
  if (activePrimaryTab.value === 'REVIEW' && activeReviewTab.value === 'PENDING_REVIEW') {
    return orders.value.filter(isPendingReviewOrder)
  }
  if (activePrimaryTab.value === 'REVIEW' && activeReviewTab.value === 'REVIEWED') {
    return orders.value.filter(isReviewedOrder)
  }
  if (activePrimaryTab.value === 'REFUND' && activeRefundTab.value === 'IN_PROGRESS') {
    return orders.value.filter(isRefundInProgressOrder)
  }
  if (activePrimaryTab.value === 'REFUND' && activeRefundTab.value === 'FINISHED') {
    return orders.value.filter(isRefundFinishedOrder)
  }
  return []
})

const emptyStateConfig = computed(() => {
  const copyMap = {
    UNPAID: {
      title: 'No unpaid orders yet',
      description: 'Create a deposit order from a house detail page first.'
    },
    PAID: {
      title: 'No paid orders yet',
      description: 'Paid orders will appear here before they enter refund flow.'
    },
    CANCELLED: {
      title: 'No cancelled orders yet',
      description: 'Cancelled orders will appear here once you cancel one.'
    },
    'REVIEW:PENDING_REVIEW': {
      title: 'No orders waiting for review',
      description: 'Complete an order first and it will appear here.'
    },
    'REVIEW:REVIEWED': {
      title: 'No reviewed orders yet',
      description: 'Orders you already reviewed will appear here.'
    },
    'REFUND:IN_PROGRESS': {
      title: 'No refunds in progress',
      description: 'Refund requests being processed will appear here.'
    },
    'REFUND:FINISHED': {
      title: 'No finished refund records yet',
      description: 'Completed, failed, or cancelled refunds will appear here.'
    }
  }

  return copyMap[activeTabKey.value]
})
```

- [ ] **Step 4: Add reset-and-reload handlers for primary and secondary tab switches**

Append these methods above `onMounted(...)`:

```js
function resetOrderListState() {
  current.value = 1
  hasMore.value = true
  orders.value = []
  error.value = ''
}

async function loadOrders(reset = false) {
  if (loading.value || (!hasMore.value && !reset)) {
    return
  }

  if (reset) {
    resetOrderListState()
  }

  loading.value = true
  try {
    const page = await fetchMyOrderPage({ current: current.value, size })
    const withTitle = await attachHouseTitle(page?.records || [])
    const records = await attachRefundStatus(withTitle)
    orders.value = reset ? records : [...orders.value, ...records]
    const total = Number(page?.total || 0)
    hasMore.value = current.value * size < total
    current.value += 1
  } catch (err) {
    error.value = formatRequestError(err, 'Failed to load orders')
    if (reset) {
      orders.value = []
    }
  } finally {
    loading.value = false
  }
}

async function switchPrimaryTab(nextTab) {
  if (activePrimaryTab.value === nextTab) {
    return
  }

  activePrimaryTab.value = nextTab
  activeReviewTab.value = DEFAULT_REVIEW_TAB
  activeRefundTab.value = DEFAULT_REFUND_TAB
  await loadOrders(true)
}

async function switchSecondaryTab(nextTab) {
  if (activePrimaryTab.value === 'REVIEW') {
    if (activeReviewTab.value === nextTab) return
    activeReviewTab.value = nextTab
    await loadOrders(true)
    return
  }

  if (activePrimaryTab.value === 'REFUND') {
    if (activeRefundTab.value === nextTab) return
    activeRefundTab.value = nextTab
    await loadOrders(true)
  }
}
```

- [ ] **Step 5: Run the focused test again to verify the script-only refactor still fails on missing UI**

Run:

```bash
cd frontend
npm run test:run -- src/views/__tests__/MineOrderView.spec.js
```

Expected: FAIL because the template still renders `orders` directly and does not yet expose the tab buttons or the tab-aware empty state.

- [ ] **Step 6: Commit the state-management refactor**

```bash
git add frontend/src/views/mine/MineOrderView.vue
git commit -m "refactor(mine-order): add local status tab state and filters"
```

### Task 3: Render the Tab Navigation and Tab-Aware List States

**Files:**
- Modify: `C:\Users\黄昊\.codex\worktrees\e47e\MyRent\frontend\src\views\mine\MineOrderView.vue`
- Test: `C:\Users\黄昊\.codex\worktrees\e47e\MyRent\frontend\src\views\__tests__\MineOrderView.spec.js`

- [ ] **Step 1: Insert the primary and secondary tab rows above the list**

Replace the top of the template in `MineOrderView.vue` with:

```vue
    <section class="card topbar">
      <button class="ghost-btn" @click="router.back()">Back</button>
      <h2 class="section-title">My Orders</h2>
      <button class="ghost-btn" @click="reload">Refresh</button>
    </section>

    <section class="card order-tabs">
      <div class="tab-row primary-tab-row">
        <button
          v-for="tab in PRIMARY_TABS"
          :key="tab.key"
          :data-testid="`primary-tab-${tab.key}`"
          :class="['status-tab', { active: activePrimaryTab === tab.key }]"
          @click="switchPrimaryTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-if="showSecondaryTabs" class="tab-row secondary-tab-row">
        <button
          v-for="tab in secondaryTabs"
          :key="tab.key"
          :data-testid="`secondary-tab-${tab.key}`"
          :class="[
            'status-tab',
            'secondary',
            {
              active:
                (activePrimaryTab === 'REVIEW' && activeReviewTab === tab.key) ||
                (activePrimaryTab === 'REFUND' && activeRefundTab === tab.key)
            }
          ]"
          @click="switchSecondaryTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>
    </section>
```

- [ ] **Step 2: Switch the list rendering from `orders` to `visibleOrders`**

Update the list, loading, and load-more conditions:

```vue
    <LoadingState v-if="loading && !visibleOrders.length" text="Loading orders..." />
    <p v-if="error" class="error-text">{{ error }}</p>

    <section v-for="order in visibleOrders" :key="order.id" class="card order-card">
      <div class="order-head">
        <div>
          <h3 class="order-title">{{ order.houseTitle || `House ${order.houseId}` }}</h3>
          <p class="order-no">Order No: {{ order.orderNo }}</p>
        </div>
        <span :class="['order-status', `status-${order.status}`]">{{ getOrderStatusText(order.status) }}</span>
      </div>
```

Update the footer:

```vue
    <EmptyState
      v-if="!loading && !visibleOrders.length && !hasMore"
      :title="emptyStateConfig.title"
      :description="emptyStateConfig.description"
      action-text="Go Home"
      @action="router.push('/home')"
    />

    <div v-if="hasMore || loading" class="load-more">
      <button v-if="hasMore && !loading" class="ghost-btn" @click="loadOrders">Load more</button>
      <LoadingState v-else text="Loading..." />
    </div>
```

- [ ] **Step 3: Keep the action buttons exactly as they are today**

Do not rewrite the action logic. Keep the existing handlers and conditions on each rendered card:

```vue
        <button class="ghost-btn" @click="goDetail(order.houseId)">View House</button>
        <button
          v-if="order.status === 0"
          class="primary-btn"
          @click="continuePay(order.orderNo)"
        >
          Continue Payment
        </button>
        <button
          v-if="canCompleteOrder(order)"
          class="ghost-btn"
          :disabled="completingOrderNo === order.orderNo"
          @click="submitComplete(order)"
        >
          Complete Order
        </button>
        <button
          v-if="canReviewOrder(order)"
          class="primary-btn"
          @click="goReview(order)"
        >
          Go Review
        </button>
        <button
          v-if="order.canEditReview"
          class="ghost-btn"
          @click="goEditReview(order)"
        >
          Edit Review
        </button>
        <button
          v-if="canRequestRefund(order)"
          class="ghost-btn refund-btn"
          :disabled="refundingOrderNo === order.orderNo"
          @click="submitRefund(order)"
        >
          Apply Refund
        </button>
```

- [ ] **Step 4: Add styles for segmented tabs without breaking the existing card layout**

Append these styles to `MineOrderView.vue`:

```css
.order-tabs {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tab-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.status-tab {
  border: 1px solid #d1d5db;
  border-radius: 999px;
  padding: 6px 12px;
  background: #ffffff;
  color: #4b5563;
  font-size: 12px;
  line-height: 1;
}

.status-tab.active {
  border-color: #111827;
  background: #111827;
  color: #ffffff;
}

.status-tab.secondary {
  border-color: #cbd5e1;
  background: #f8fafc;
}

@media (max-width: 640px) {
  .tab-row {
    flex-wrap: nowrap;
    overflow-x: auto;
    padding-bottom: 4px;
  }
}
```

- [ ] **Step 5: Run the focused test to verify tab rendering and filtering now pass**

Run:

```bash
cd frontend
npm run test:run -- src/views/__tests__/MineOrderView.spec.js
```

Expected: PASS with the new primary and secondary tabs driving the rendered list.

- [ ] **Step 6: Commit the tab UI**

```bash
git add frontend/src/views/mine/MineOrderView.vue
git commit -m "feat(mine-order): render status tabs on mine orders page"
```

### Task 4: Re-Verify Existing Order Actions Inside the New Tabs

**Files:**
- Modify: `C:\Users\黄昊\.codex\worktrees\e47e\MyRent\frontend\src\views\__tests__\MineOrderView.spec.js`
- Test: `C:\Users\黄昊\.codex\worktrees\e47e\MyRent\frontend\src\views\__tests__\MineOrderView.spec.js`

- [ ] **Step 1: Rewrite the action-button tests so they execute under the correct active tab**

Update the existing action tests to switch to the matching tab before clicking:

```js
it('shows continue payment button for unpaid orders', async () => {
  fetchMyOrderPage.mockResolvedValueOnce({
    records: [buildOrder({ id: 20, orderNo: 'UNPAID-ACTION', status: 0 })],
    total: 1
  })

  const { wrapper } = await mountView()

  const continueButton = wrapper.findAll('button').find((item) => item.text().includes('Continue Payment'))
  expect(continueButton.exists()).toBe(true)
  await continueButton.trigger('click')
  await flushPromises()

  expect(repayOrder).toHaveBeenCalledWith('UNPAID-ACTION')
  expect(window.location.assign).toHaveBeenCalledWith('/mock-pay/checkout?paymentNo=PAY-1001')
})

it('shows complete order button for paid orders', async () => {
  fetchMyOrderPage.mockResolvedValueOnce({
    records: [buildOrder({ id: 21, orderNo: 'PAID-ACTION', status: 1, canComplete: true })],
    total: 1
  })

  const { wrapper } = await mountView()
  await wrapper.get('[data-testid="primary-tab-PAID"]').trigger('click')
  await flushPromises()

  const button = wrapper.findAll('button').find((item) => item.text().includes('Complete Order'))
  expect(button.exists()).toBe(true)
  await button.trigger('click')
  await flushPromises()

  expect(completeOrder).toHaveBeenCalledWith('PAID-ACTION')
})

it('shows review entry when backend says order can review', async () => {
  fetchMyOrderPage.mockResolvedValueOnce({
    records: [buildOrder({ id: 22, orderNo: 'REVIEW-ACTION', status: 5, canReview: true })],
    total: 1
  })

  const { wrapper, router } = await mountView()
  await wrapper.get('[data-testid="primary-tab-REVIEW"]').trigger('click')
  await flushPromises()

  const button = wrapper.findAll('button').find((item) => item.text().includes('Go Review'))
  expect(button.exists()).toBe(true)
  await button.trigger('click')
  await flushPromises()

  expect(router.currentRoute.value.fullPath).toBe('/mine/orders/REVIEW-ACTION/review')
})
```

- [ ] **Step 2: Run the focused test again to verify the interaction coverage still passes**

Run:

```bash
cd frontend
npm run test:run -- src/views/__tests__/MineOrderView.spec.js
```

Expected: PASS with filtering and action buttons both green.

- [ ] **Step 3: Build the frontend once to catch template or import regressions**

Run:

```bash
cd frontend
npm run build
```

Expected: Vite build completes successfully.

- [ ] **Step 4: Commit the test refresh**

```bash
git add frontend/src/views/__tests__/MineOrderView.spec.js
git commit -m "test(mine-order): cover tabbed order actions"
```

### Task 5: Run the Final Verification Checkpoint

**Files:**
- Test: `C:\Users\黄昊\.codex\worktrees\e47e\MyRent\frontend\src\views\__tests__\MineOrderView.spec.js`
- Test: `C:\Users\黄昊\.codex\worktrees\e47e\MyRent\frontend\package.json`

- [ ] **Step 1: Run the focused Mine Order test file one last time**

Run:

```bash
cd frontend
npm run test:run -- src/views/__tests__/MineOrderView.spec.js
```

Expected: PASS.

- [ ] **Step 2: Run the full frontend test suite if the repository keeps it fast**

Run:

```bash
cd frontend
npm run test:run
```

Expected: PASS, or a small number of unrelated pre-existing failures that should be documented before merge.

- [ ] **Step 3: Run one more production build before handing off**

Run:

```bash
cd frontend
npm run build
```

Expected: build success with no missing import or template compile errors.

- [ ] **Step 4: Commit the verification checkpoint**

```bash
git add frontend/src/views/mine/MineOrderView.vue frontend/src/views/__tests__/MineOrderView.spec.js
git commit -m "test(mine-order): verify status tab implementation"
```

## Self-Review

- Spec coverage: The plan covers all five primary tabs, both secondary-tab groups, default subtab reset behavior, tab-aware empty states, local pagination reset on tab switches, and preservation of existing order actions.
- Placeholder scan: No `TODO`, `TBD`, or vague "handle later" instructions remain.
- Type consistency: `PRIMARY_TABS`, `REVIEW_TABS`, `REFUND_TABS`, `visibleOrders`, `emptyStateConfig`, `switchPrimaryTab`, and `switchSecondaryTab` are named consistently across tests and implementation steps.
