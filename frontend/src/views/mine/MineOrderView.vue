<template>
  <div class="page mine-sub-page">
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

      <div class="order-body">
        <p>Amount: {{ formatPrice(order.amount) }}</p>
        <p>Created At: {{ formatDateTime(order.createTime) }}</p>
        <p>Expires At: {{ formatDateTime(order.expireTime) }}</p>
        <p v-if="order.latestRefundStatus !== null" class="refund-text">
          Refund Status: {{ getRefundStatusText(order.latestRefundStatus) }}
        </p>
      </div>

      <div class="order-actions">
        <button
          class="ghost-btn"
          :data-testid="`action-detail-${order.orderNo}`"
          @click="goDetail(order.houseId)"
        >
          View House
        </button>
        <button
          v-if="order.status === 0"
          class="primary-btn"
          :data-testid="`action-continue-pay-${order.orderNo}`"
          @click="continuePay(order.orderNo)"
        >
          Continue Payment
        </button>
        <button
          v-if="canCompleteOrder(order)"
          class="ghost-btn"
          :data-testid="`action-complete-${order.orderNo}`"
          :disabled="completingOrderNo === order.orderNo"
          @click="submitComplete(order)"
        >
          Complete Order
        </button>
        <button
          v-if="canReviewOrder(order)"
          class="primary-btn"
          :data-testid="`action-review-${order.orderNo}`"
          @click="goReview(order)"
        >
          Go Review
        </button>
        <button
          v-if="order.canEditReview"
          class="ghost-btn"
          :data-testid="`action-edit-review-${order.orderNo}`"
          @click="goEditReview(order)"
        >
          Edit Review
        </button>
        <button
          v-if="canRequestRefund(order)"
          class="ghost-btn refund-btn"
          :data-testid="`action-refund-${order.orderNo}`"
          :disabled="refundingOrderNo === order.orderNo"
          @click="submitRefund(order)"
        >
          Apply Refund
        </button>
      </div>
    </section>

    <EmptyState
      v-if="!loading && !error && !visibleOrders.length && !hasMore"
      :title="emptyStateConfig.title"
      :description="emptyStateConfig.description"
      action-text="Go Home"
      @action="router.push('/home')"
    />

    <div v-if="visibleOrders.length || (!loading && hasMore)" class="load-more">
      <button v-if="hasMore && !loading" class="ghost-btn" @click="loadOrders">Load More</button>
      <LoadingState v-else-if="loading" text="Loading..." />
      <span v-else class="no-more">No more orders</span>
    </div>
  </div>
</template>

<script setup>
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

const EMPTY_STATE_BY_TAB = {
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

const router = useRouter()

const loading = ref(false)
const error = ref('')
const orders = ref([])
const current = ref(1)
const size = 10
const hasMore = ref(true)
const refundingOrderNo = ref('')
const completingOrderNo = ref('')
const activePrimaryTab = ref(DEFAULT_PRIMARY_TAB)
const activeReviewTab = ref(DEFAULT_REVIEW_TAB)
const activeRefundTab = ref(DEFAULT_REFUND_TAB)

const secondaryTabs = computed(() => {
  if (activePrimaryTab.value === 'REVIEW') {
    return REVIEW_TABS
  }
  if (activePrimaryTab.value === 'REFUND') {
    return REFUND_TABS
  }
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
  if (activePrimaryTab.value === 'UNPAID') {
    return orders.value.filter(isUnpaidOrder)
  }
  if (activePrimaryTab.value === 'PAID') {
    return orders.value.filter(isPaidOrder)
  }
  if (activePrimaryTab.value === 'CANCELLED') {
    return orders.value.filter(isCancelledOrder)
  }
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

const emptyStateConfig = computed(() => EMPTY_STATE_BY_TAB[activeTabKey.value] || EMPTY_STATE_BY_TAB.UNPAID)

const houseCache = new Map()

async function attachHouseTitle(records = []) {
  if (!records.length) {
    return []
  }

  return Promise.all(
    records.map(async (order) => {
      if (!order?.houseId) {
        return { ...order, houseTitle: '' }
      }
      if (houseCache.has(order.houseId)) {
        return { ...order, houseTitle: houseCache.get(order.houseId) }
      }
      try {
        const house = await fetchHouseById(order.houseId)
        const houseTitle = house?.title || `House ${order.houseId}`
        houseCache.set(order.houseId, houseTitle)
        return { ...order, houseTitle }
      } catch {
        const houseTitle = `House ${order.houseId}`
        houseCache.set(order.houseId, houseTitle)
        return { ...order, houseTitle }
      }
    })
  )
}

async function attachRefundStatus(records = []) {
  if (!records.length) {
    return []
  }

  try {
    const refundStatuses = await fetchOrderRefundStatuses(records.map((order) => order.orderNo))
    const refundMap = new Map((refundStatuses || []).map((item) => [item.orderNo, item]))
    return records.map((order) => ({
      ...order,
      latestRefundStatus: refundMap.has(order.orderNo)
        ? refundMap.get(order.orderNo)?.status ?? null
        : order.latestRefundStatus ?? null,
      latestRefundNo: refundMap.has(order.orderNo)
        ? refundMap.get(order.orderNo)?.refundNo || ''
        : order.latestRefundNo || '',
      latestRefundReasonCode: refundMap.has(order.orderNo)
        ? refundMap.get(order.orderNo)?.reasonCode || ''
        : order.latestRefundReasonCode || ''
    }))
  } catch {
    return records.map((order) => ({
      ...order,
      latestRefundStatus: order.latestRefundStatus ?? null,
      latestRefundNo: order.latestRefundNo || '',
      latestRefundReasonCode: order.latestRefundReasonCode || ''
    }))
  }
}

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

function reload() {
  loadOrders(true)
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
    if (activeReviewTab.value === nextTab) {
      return
    }
    activeReviewTab.value = nextTab
    await loadOrders(true)
    return
  }

  if (activePrimaryTab.value === 'REFUND') {
    if (activeRefundTab.value === nextTab) {
      return
    }
    activeRefundTab.value = nextTab
    await loadOrders(true)
  }
}

function goDetail(houseId) {
  router.push(`/house/${houseId}`)
}

function goReview(order) {
  router.push(`/mine/orders/${order.orderNo}/review`)
}

function goEditReview(order) {
  router.push({
    path: `/mine/orders/${order.orderNo}/review`,
    query: { reviewId: String(order.reviewId || '') }
  })
}

async function continuePay(orderNo) {
  try {
    const result = await repayOrder(orderNo)
    if (result?.mockPayUrl?.startsWith('/')) {
      window.location.assign(result.mockPayUrl)
    }
  } catch (err) {
    error.value = formatRequestError(err, 'Failed to continue payment')
  }
}

function canRequestRefund(order) {
  return order.status === 1 && order.latestRefundStatus === null
}

function isRefundBlockingStatus(status) {
  return status === 0 || status === 1 || status === 2 || status === 3 || status === 5
}

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

function canCompleteOrder(order) {
  return Boolean(order?.canComplete) && !isRefundBlockingStatus(order?.latestRefundStatus)
}

function canReviewOrder(order) {
  return Boolean(order?.canReview) && !isRefundBlockingStatus(order?.latestRefundStatus)
}

function getRefundStatusText(status) {
  if (status === 0 || status === 1) return 'Processing'
  if (status === 2) return 'Refund Success'
  if (status === 3) return 'Retrying'
  if (status === 4) return 'Refund Failed'
  if (status === 5) return 'Manual Processing'
  if (status === 6) return 'Refund Cancelled'
  return ''
}

async function submitRefund(order) {
  if (!order?.orderNo || refundingOrderNo.value) {
    return
  }

  refundingOrderNo.value = order.orderNo
  try {
    const refund = await applyPaymentRefund({
      orderNo: order.orderNo,
      reasonDetail: ''
    })
    orders.value = orders.value.map((item) => {
      if (item.orderNo !== order.orderNo) {
        return item
      }
      return {
        ...item,
        latestRefundStatus: refund?.status ?? 0,
        latestRefundNo: refund?.refundNo || '',
        latestRefundReasonCode: refund?.reasonCode || 'USER_APPLY'
      }
    })
  } catch (err) {
    error.value = formatRequestError(err, 'Failed to request refund')
  } finally {
    refundingOrderNo.value = ''
  }
}

async function submitComplete(order) {
  if (!order?.orderNo || completingOrderNo.value) {
    return
  }

  completingOrderNo.value = order.orderNo
  try {
    await completeOrder(order.orderNo)
    await loadOrders(true)
  } catch (err) {
    error.value = formatRequestError(err, 'Failed to complete order')
  } finally {
    completingOrderNo.value = ''
  }
}

onMounted(() => {
  loadOrders(true)
})
</script>

<style scoped>
.mine-sub-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-title {
  margin: 0;
}

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

.order-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.order-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.order-title {
  margin: 0;
  font-size: 16px;
  color: #111827;
}

.order-no {
  margin: 6px 0 0;
  font-size: 12px;
  color: #6b7280;
  word-break: break-all;
}

.order-body {
  display: grid;
  gap: 6px;
  color: #374151;
  font-size: 13px;
}

.order-body p {
  margin: 0;
}

.refund-text {
  color: #b45309;
}

.order-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
}

.order-status {
  flex-shrink: 0;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  line-height: 1;
}

.status-0 {
  background: #dbeafe;
  color: #1d4ed8;
}

.status-1 {
  background: #dcfce7;
  color: #15803d;
}

.status-2,
.status-4 {
  background: #f3f4f6;
  color: #6b7280;
}

.status-3 {
  background: #fee2e2;
  color: #b91c1c;
}

.status-5 {
  background: #fef3c7;
  color: #b45309;
}

.status-6 {
  background: #ede9fe;
  color: #6d28d9;
}

.load-more {
  display: flex;
  justify-content: center;
  padding-bottom: 4px;
}

.no-more {
  color: #9ca3af;
  font-size: 13px;
}

@media (max-width: 640px) {
  .tab-row {
    flex-wrap: nowrap;
    overflow-x: auto;
    padding-bottom: 4px;
  }
}
</style>
