<template>
  <div class="page mine-sub-page">
    <section class="card topbar">
      <button class="ghost-btn" @click="router.back()">返回</button>
      <h2 class="section-title">我的订单</h2>
      <button class="ghost-btn" @click="reload">刷新</button>
    </section>

    <LoadingState v-if="loading && !orders.length" text="正在加载订单..." />
    <p v-if="error" class="error-text">{{ error }}</p>

    <section v-for="order in orders" :key="order.id" class="card order-card">
      <div class="order-head">
        <div>
          <h3 class="order-title">{{ order.houseTitle || `房源${order.houseId}` }}</h3>
          <p class="order-no">订单号：{{ order.orderNo }}</p>
        </div>
        <span :class="['order-status', `status-${order.status}`]">{{ getOrderStatusText(order.status) }}</span>
      </div>

      <div class="order-body">
        <p>定金：{{ formatPrice(order.amount) }}</p>
        <p>创建时间：{{ formatDateTime(order.createTime) }}</p>
        <p>过期时间：{{ formatDateTime(order.expireTime) }}</p>
        <p v-if="order.latestRefundStatus !== null" class="refund-text">
          退款状态：{{ getRefundStatusText(order.latestRefundStatus) }}
        </p>
      </div>

      <div class="order-actions">
        <button class="ghost-btn" @click="goDetail(order.houseId)">查看房源</button>
        <button
          v-if="order.status === 0"
          class="primary-btn"
          @click="continuePay(order.orderNo)"
        >
          继续支付
        </button>
        <button
          v-if="canCompleteOrder(order)"
          class="ghost-btn"
          :disabled="completingOrderNo === order.orderNo"
          @click="submitComplete(order)"
        >
          完成订单
        </button>
        <button
          v-if="canReviewOrder(order)"
          class="primary-btn"
          @click="goReview(order)"
        >
          去评价
        </button>
        <button
          v-if="order.canEditReview"
          class="ghost-btn"
          @click="goEditReview(order)"
        >
          修改评价
        </button>
        <button
          v-if="canRequestRefund(order)"
          class="ghost-btn refund-btn"
          :disabled="refundingOrderNo === order.orderNo"
          @click="submitRefund(order)"
        >
          申请退款
        </button>
      </div>
    </section>

    <EmptyState
      v-if="!loading && !orders.length"
      title="暂无订单"
      description="请先在房源详情页创建定金订单。"
      action-text="去首页"
      @action="router.push('/home')"
    />

    <div v-if="orders.length" class="load-more">
      <button v-if="hasMore && !loading" class="ghost-btn" @click="loadOrders">加载更多</button>
      <LoadingState v-else-if="loading" text="正在加载..." />
      <span v-else class="no-more">没有更多订单了</span>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchHouseById } from '@/api/house'
import { completeOrder, fetchMyOrderPage, repayOrder } from '@/api/order'
import { applyPaymentRefund, fetchOrderRefundStatuses } from '@/api/payment'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { formatDateTime, formatPrice, formatRequestError, getOrderStatusText } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const error = ref('')
const orders = ref([])
const current = ref(1)
const size = 10
const hasMore = ref(true)
const refundingOrderNo = ref('')
const completingOrderNo = ref('')

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
        const houseTitle = house?.title || `房源${order.houseId}`
        houseCache.set(order.houseId, houseTitle)
        return { ...order, houseTitle }
      } catch {
        const houseTitle = `房源${order.houseId}`
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
      latestRefundStatus: refundMap.get(order.orderNo)?.status ?? null,
      latestRefundNo: refundMap.get(order.orderNo)?.refundNo || '',
      latestRefundReasonCode: refundMap.get(order.orderNo)?.reasonCode || ''
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

async function loadOrders(reset = false) {
  if (loading.value || (!hasMore.value && !reset)) {
    return
  }

  if (reset) {
    current.value = 1
    hasMore.value = true
    orders.value = []
    error.value = ''
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
    error.value = formatRequestError(err, '加载订单失败')
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
    error.value = formatRequestError(err, '继续支付失败')
  }
}

function canRequestRefund(order) {
  return order.status === 1 && order.latestRefundStatus === null
}

function isRefundBlockingStatus(status) {
  return status === 0 || status === 1 || status === 2 || status === 3 || status === 5
}

function canCompleteOrder(order) {
  return Boolean(order?.canComplete) && !isRefundBlockingStatus(order?.latestRefundStatus)
}

function canReviewOrder(order) {
  return Boolean(order?.canReview) && !isRefundBlockingStatus(order?.latestRefundStatus)
}

function getRefundStatusText(status) {
  if (status === 0 || status === 1) return '退款处理中'
  if (status === 2) return '退款成功'
  if (status === 3) return '退款重试中'
  if (status === 4) return '退款失败'
  if (status === 5) return '待人工处理'
  if (status === 6) return '退款已取消'
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
    error.value = formatRequestError(err, '申请退款失败')
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
    error.value = formatRequestError(err, '完成订单失败')
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
</style>
