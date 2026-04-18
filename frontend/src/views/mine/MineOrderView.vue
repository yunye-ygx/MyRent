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
import { fetchMyOrderPage, repayOrder } from '@/api/order'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { formatDateTime, formatPrice } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const error = ref('')
const orders = ref([])
const current = ref(1)
const size = 10
const hasMore = ref(true)

const houseCache = new Map()

function getOrderStatusText(status) {
  if (status === 0) return '待支付'
  if (status === 1) return '已支付'
  if (status === 2) return '超时关闭'
  if (status === 3) return '已取消'
  return '未知状态'
}

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
    const records = await attachHouseTitle(page?.records || [])
    orders.value = reset ? records : [...orders.value, ...records]
    const total = Number(page?.total || 0)
    hasMore.value = current.value * size < total
    current.value += 1
  } catch (err) {
    error.value = err?.message || '加载订单失败'
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

async function continuePay(orderNo) {
  try {
    const result = await repayOrder(orderNo)
    if (result?.mockPayUrl?.startsWith('/')) {
      window.location.assign(result.mockPayUrl)
    }
  } catch (err) {
    error.value = err?.message || '继续支付失败'
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

.order-actions {
  display: flex;
  justify-content: flex-end;
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

.status-2 {
  background: #f3f4f6;
  color: #6b7280;
}

.status-3 {
  background: #fee2e2;
  color: #b91c1c;
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
