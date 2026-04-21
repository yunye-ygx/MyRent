<template>
  <div class="detail-view">
    <LoadingState v-if="loading" text="Loading details..." />

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
            This stage keeps browsing, consultation, favorites, and deposit actions together while the
            later tasks finish the payment closed loop.
          </p>
        </section>

        <section class="reviews app-surface">
          <p class="eyebrow">Reviews</p>
          <div class="review-meta">
            <strong>{{ Number(reviewSummary.averageScore || 0).toFixed(1) }}</strong>
            <span>{{ reviewSummary.reviewCount || 0 }} 条评价</span>
          </div>
          <ul v-if="reviewSummary.records?.length" class="review-list">
            <li v-for="item in reviewSummary.records" :key="item.reviewId" class="review-item">
              <div class="review-head">
                <span>{{ item.reviewerName }}</span>
                <span>{{ item.score }} 星</span>
              </div>
              <p>{{ item.content }}</p>
            </li>
          </ul>
          <p v-else class="copy">当前还没有评价，完成订单后的租客可以在“我的订单”中提交评价。</p>
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
      title="House detail unavailable"
      :description="error || 'Please try again later or go back to the home page.'"
      action-text="Go Home"
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
  fetchHouseReviews,
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
const reviewSummary = ref({
  averageScore: 0,
  reviewCount: 0,
  records: []
})

const statusText = computed(() => getHouseStatusText(house.value?.status))
const cover = computed(() => `https://picsum.photos/seed/house-detail-${route.params.id}/960/640`)
const publisherName = computed(() => publisher.value?.name || 'Unknown Publisher')
const favoriteButtonText = computed(() => (
  favoriteLoading.value
    ? 'Processing...'
    : favoriteStatus.value?.favorited
      ? 'Unfavorite'
      : 'Favorite House'
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

async function loadReviews() {
  if (!route.params.id) {
    reviewSummary.value = {
      averageScore: 0,
      reviewCount: 0,
      records: []
    }
    return
  }

  try {
    reviewSummary.value = await fetchHouseReviews(route.params.id, { current: 1, size: 5 })
  } catch {
    reviewSummary.value = {
      averageScore: 0,
      reviewCount: 0,
      records: []
    }
  }
}

async function loadHouse() {
  loading.value = true
  error.value = ''

  try {
    house.value = await fetchHouseById(route.params.id)
    await Promise.all([
      loadPublisher(),
      loadFavoriteStatus(),
      loadReviews()
    ])
  } catch (err) {
    error.value = formatRequestError(err, 'House detail is temporarily unavailable.')
    house.value = null
    publisher.value = null
    reviewSummary.value = {
      averageScore: 0,
      reviewCount: 0,
      records: []
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
    window.alert(formatRequestError(err, 'Favorite action failed.'))
  } finally {
    favoriteLoading.value = false
  }
}

function goConsult() {
  if (!house.value) {
    return
  }
  if (!house.value.publisherUserId) {
    window.alert('Current house is missing publisher info.')
    return
  }
  if (!authStore.userId) {
    router.push('/login')
    return
  }
  if (String(authStore.userId) === String(house.value.publisherUserId)) {
    window.alert('You cannot consult your own house.')
    return
  }

  const targetSessionId = buildSessionId(authStore.userId, house.value.publisherUserId, house.value.id)
  if (!targetSessionId) {
    window.alert('Session parameters are invalid.')
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
    window.alert('You cannot submit a deposit for your own house.')
    return
  }

  lockLoading.value = true
  try {
    const result = await createOrder({
      houseId: house.value.id,
      version: house.value.version || 0
    })
    if (result?.mockPayUrl?.startsWith('/')) {
      window.location.assign(result.mockPayUrl)
      return
    }
    window.alert('Deposit order created, but no checkout URL was returned.')
  } catch (err) {
    window.alert(formatRequestError(err, 'Submit deposit failed.'))
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

.notes,
.reviews {
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

.review-meta {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 16px;
  color: #111827;
}

.review-meta strong {
  font-size: 28px;
  line-height: 1;
}

.review-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.review-item {
  padding-top: 14px;
  border-top: 1px solid rgba(17, 24, 39, 0.08);
}

.review-item:first-child {
  padding-top: 0;
  border-top: none;
}

.review-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: #111827;
  font-size: 14px;
  font-weight: 600;
}

.review-item p {
  margin: 0;
  color: #4b5563;
  line-height: 1.7;
}

@media (min-width: 1024px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr) minmax(320px, 0.8fr);
    align-items: start;
  }
}
</style>
