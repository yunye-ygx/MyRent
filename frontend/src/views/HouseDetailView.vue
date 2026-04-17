<template>
  <div class="detail-view">
    <LoadingState v-if="loading" text="正在加载详情..." />

    <template v-else-if="house">
      <HouseDetailSummary
        :house="house"
        :cover="cover"
        :publisher-name="publisherName"
        :favorite-count="favoriteCountText"
        :status-text="statusText"
        @back="goBack"
      />

      <div class="detail-grid">
        <section class="notes app-surface">
          <p class="eyebrow">House Notes</p>
          <p class="copy">
            这个阶段先把浏览、咨询、收藏和定金动作整合成更清楚的桌面结构，后续再继续细化合同、预约和支付链路。
          </p>
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
      title="房源详情暂时不可用"
      :description="error || '请稍后再试，或先返回首页浏览其他房源。'"
      action-text="回到首页"
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
import { createOrder } from '@/api/order'
import { fetchUserById } from '@/api/user'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import HouseActionBar from '@/components/house/HouseActionBar.vue'
import HouseDetailSummary from '@/components/house/HouseDetailSummary.vue'
import { useAuthStore } from '@/stores/auth'
import { formatRequestError, getHouseStatusText } from '@/utils/format'

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
const cover = computed(() => `https://picsum.photos/seed/house-detail-${route.params.id}/960/640`)
const publisherName = computed(() => publisher.value?.name || '未知发布者')
const favoriteButtonText = computed(() => (
  favoriteLoading.value
    ? '处理中...'
    : favoriteStatus.value?.favorited
      ? '取消收藏'
      : '收藏房源'
))
const favoriteCountText = computed(() => favoriteStatus.value?.favoriteCount ?? 0)

function buildSessionId(firstUserId, secondUserId, houseId) {
  try {
    const first = BigInt(String(firstUserId))
    const second = BigInt(String(secondUserId))
    const targetHouse = BigInt(String(houseId))
    const minUserId = first <= second ? first : second
    const maxUserId = first <= second ? second : first
    return `${minUserId}_${maxUserId}_${targetHouse}`
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
    error.value = formatRequestError(err, '房源详情服务暂时不可用，请稍后再试。')
    house.value = null
    publisher.value = null
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
  if (!authStore.userId) {
    router.push('/login')
    return
  }

  favoriteLoading.value = true
  try {
    favoriteStatus.value = favoriteStatus.value?.favorited
      ? await unfavoriteHouse(house.value.id)
      : await favoriteHouse(house.value.id)
  } catch (err) {
    window.alert(formatRequestError(err, '收藏操作失败，请稍后再试。'))
  } finally {
    favoriteLoading.value = false
  }
}

function goConsult() {
  if (!house.value) {
    return
  }
  if (!house.value.publisherUserId) {
    window.alert('当前房源缺少发布者信息，暂时无法咨询。')
    return
  }
  if (!authStore.userId) {
    router.push('/login')
    return
  }
  if (String(authStore.userId) === String(house.value.publisherUserId)) {
    window.alert('这是你自己发布的房源，无需咨询自己。')
    return
  }

  const targetSessionId = buildSessionId(authStore.userId, house.value.publisherUserId, house.value.id)
  if (!targetSessionId) {
    window.alert('会话参数异常，请稍后重试。')
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
  if (!authStore.userId) {
    router.push('/login')
    return
  }
  if (String(authStore.userId) === String(house.value.publisherUserId)) {
    window.alert('不能给自己发布的房源提交定金。')
    return
  }

  lockLoading.value = true
  try {
    await createOrder({
      houseId: house.value.id,
      version: house.value.version || 0
    })
    window.alert('定金提交成功，请尽快支付。')
    await loadHouse()
  } catch (err) {
    window.alert(formatRequestError(err, '提交定金失败，请稍后再试。'))
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
.detail-view {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.detail-grid {
  display: grid;
  gap: 20px;
}

.notes {
  padding: 24px;
}

.eyebrow {
  margin: 0 0 12px;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.copy {
  margin: 0;
  font-size: 15px;
  line-height: 1.9;
  color: var(--color-text-muted);
}

@media (min-width: 1024px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
    align-items: start;
  }
}
</style>
