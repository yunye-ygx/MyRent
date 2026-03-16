<template>
  <div class="page detail-page">
    <div class="card head">
      <button class="ghost-btn" @click="goBack">返回</button>
      <h2 class="section-title">房源详情</h2>
    </div>

    <LoadingState v-if="loading" text="正在加载详情..." />
    <p v-else-if="error" class="error-text">{{ error }}</p>

    <template v-else-if="house">
      <div class="card">
        <img class="banner" :src="cover" alt="house" />
        <h3 class="title">{{ house.title }}</h3>
        <p class="price">{{ formatPrice(house.price) }}/月</p>
        <p class="base">押金：{{ formatPrice(house.depositAmount) }}</p>
        <p class="base">状态：{{ statusText }}</p>
        <p class="base">发布人：{{ publisherName }}</p>
      </div>

      <div class="card actions">
        <button class="ghost-btn" @click="mockCollect">收藏</button>
        <button class="ghost-btn" @click="goConsult">咨询</button>
        <button class="primary-btn" :disabled="lockLoading || house.status !== 1" @click="submitDeposit">
          {{ lockLoading ? '提交中...' : '提交定金' }}
        </button>
      </div>
      <p class="tips">说明：锁房超时释放由后端自动处理，前端只负责提交与状态刷新。</p>
    </template>

    <EmptyState
      v-else
      title="房源不存在"
      description="请返回首页重新选择房源"
      action-text="返回首页"
      @action="router.push('/home')"
    />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchHouseById } from '@/api/house'
import { fetchUserById } from '@/api/user'
import { createOrder } from '@/api/order'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { useAuthStore } from '@/stores/auth'
import { formatPrice, getHouseStatusText } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const lockLoading = ref(false)
const error = ref('')
const house = ref(null)
const publisher = ref(null)

const statusText = computed(() => getHouseStatusText(house.value?.status))
const cover = computed(() => `https://picsum.photos/seed/house-detail-${route.params.id}/640/320`)
const publisherName = computed(() => {
  if (publisher.value?.name) {
    return publisher.value.name
  }
  return '未知发布人'
})

function buildSessionId(firstUserId, secondUserId) {
  try {
    const first = BigInt(String(firstUserId))
    const second = BigInt(String(secondUserId))
    return first <= second ? `${first}_${second}` : `${second}_${first}`
  } catch {
    return ''
  }
}

async function loadPublisher() {
  publisher.value = null
  const publisherUserId = house.value?.publisherUserId
  if (!publisherUserId) {
    return
  }
  try {
    publisher.value = await fetchUserById(publisherUserId)
  } catch {
    publisher.value = null
  }
}

async function loadHouse() {
  loading.value = true
  error.value = ''
  try {
    house.value = await fetchHouseById(route.params.id)
    await loadPublisher()
  } catch (err) {
    error.value = err?.message || '获取房源详情失败'
    house.value = null
    publisher.value = null
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.back()
}

function mockCollect() {
  window.alert('收藏功能后续可接真实接口，本阶段先保留入口')
}

function goConsult() {
  if (!house.value) {
    return
  }
  if (!house.value.publisherUserId) {
    window.alert('当前房源缺少发布人信息，暂时无法咨询')
    return
  }

  const currentUserId = authStore.userId
  if (!currentUserId) {
    router.push('/login')
    return
  }

  if (String(currentUserId) === String(house.value.publisherUserId)) {
    window.alert('这是你自己发布的房源，无需咨询自己')
    return
  }

  const targetSessionId = buildSessionId(currentUserId, house.value.publisherUserId)
  if (!targetSessionId) {
    window.alert('会话参数异常，请稍后重试')
    return
  }

  router.push({
    path: `/chat/${targetSessionId}`,
    query: {
      peerId: String(house.value.publisherUserId),
      peerName: publisher.value?.name || ''
    }
  })
}

async function submitDeposit() {
  if (!house.value || house.value.status !== 1 || lockLoading.value) {
    return
  }
  lockLoading.value = true
  try {
    await createOrder({
      houseId: house.value.id,
      version: house.value.version || 0
    })
    window.alert('定金提交成功，请尽快支付')
    await loadHouse()
  } catch (err) {
    window.alert(err?.message || '提交定金失败')
  } finally {
    lockLoading.value = false
  }
}

watch(
  () => route.params.id,
  () => {
    loadHouse()
  },
  { immediate: true }
)
</script>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.banner {
  width: 100%;
  height: 180px;
  object-fit: cover;
  border-radius: 10px;
  background: #e5e7eb;
}

.title {
  margin: 10px 0 6px;
  font-size: 20px;
}

.price {
  margin: 0;
  font-size: 24px;
  color: #dc2626;
  font-weight: 700;
}

.base {
  margin: 6px 0 0;
  font-size: 14px;
  color: #374151;
}

.actions {
  display: grid;
  grid-template-columns: 1fr 1fr 1.6fr;
  gap: 8px;
}

.tips {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
}
</style>
