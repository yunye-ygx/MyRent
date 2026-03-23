<template>
  <div class="page detail-page">
    <div class="card head">
      <button class="ghost-btn" @click="goBack()">返回</button>
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
        <p class="base">收藏数：{{ favoriteCountText }}</p>
      </div>

      <div class="card actions">
        <button class="ghost-btn" :disabled="favoriteLoading" @click="toggleFavorite">
          {{ favoriteButtonText }}
        </button>
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
import {
  favoriteHouse,
  fetchHouseById,
  fetchHouseFavoriteStatus,
  unfavoriteHouse
} from '@/api/house'
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
const favoriteLoading = ref(false)
const error = ref('')
const house = ref(null)
const publisher = ref(null)
const favoriteStatus = ref({
  favorited: false,
  favoriteCount: 0
})

const statusText = computed(() => getHouseStatusText(house.value?.status))
const cover = computed(() => `https://picsum.photos/seed/house-detail-${route.params.id}/640/320`)
const publisherName = computed(() => publisher.value?.name || '未知发布人')
const favoriteButtonText = computed(() => {
  if (favoriteLoading.value) {
    return '处理中...'
  }
  return favoriteStatus.value?.favorited ? '取消收藏' : '收藏'
})
const favoriteCountText = computed(() => favoriteStatus.value?.favoriteCount ?? 0)

function buildSessionId(firstUserId, secondUserId, houseId) {
  try {
    const first = BigInt(String(firstUserId))
    const second = BigInt(String(secondUserId))
    const house = BigInt(String(houseId))
    const minUserId = first <= second ? first : second
    const maxUserId = first <= second ? second : first
    return `${minUserId}_${maxUserId}_${house}`
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

async function loadFavoriteStatus() {
  favoriteStatus.value = {
    favorited: false,
    favoriteCount: 0
  }
  if (!route.params.id || !authStore.userId) {
    return
  }
  try {
    favoriteStatus.value = await fetchHouseFavoriteStatus(route.params.id)
  } catch {
    favoriteStatus.value = {
      favorited: false,
      favoriteCount: 0
    }
  }
}

async function loadHouse() {
  loading.value = true
  error.value = ''
  try {
    house.value = await fetchHouseById(route.params.id)
    await loadPublisher()
    await loadFavoriteStatus()
  } catch (err) {
    error.value = err?.message || '获取房源详情失败'
    house.value = null
    publisher.value = null
    favoriteStatus.value = {
      favorited: false,
      favoriteCount: 0
    }
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.back()
}

async function toggleFavorite() {
  if (!house.value || favoriteLoading.value) {
    return
  }
  const currentUserId = authStore.userId
  if (!currentUserId) {
    router.push('/login')
    return
  }
  favoriteLoading.value = true
  try {
    favoriteStatus.value = favoriteStatus.value?.favorited
      ? await unfavoriteHouse(house.value.id)
      : await favoriteHouse(house.value.id)
  } catch (err) {
    window.alert(err?.message || '收藏操作失败')
  } finally {
    favoriteLoading.value = false
  }
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

  const targetSessionId = buildSessionId(currentUserId, house.value.publisherUserId, house.value.id)
  if (!targetSessionId) {
    window.alert('会话参数异常，请稍后重试')
    return
  }

  router.push({
    path: `/chat/${targetSessionId}`,
    query: {
      peerId: String(house.value.publisherUserId),
      peerName: publisher.value?.name || '',
      houseId: String(house.value.id)
    }
  })
}

async function submitDeposit() {
  if (!house.value || house.value.status !== 1 || lockLoading.value) {
    return
  }
  const currentUserId = authStore.userId
  if (!currentUserId) {
    router.push('/login')
    return
  }
  if (String(currentUserId) === String(house.value.publisherUserId)) {
    window.alert('这是你自己发布的房源，不能给自己的房源下单')
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
  () => [route.params.id, authStore.userId],
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
