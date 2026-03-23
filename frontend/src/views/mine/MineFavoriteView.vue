<template>
  <div class="page mine-sub-page">
    <section class="card topbar">
      <button class="ghost-btn" @click="router.back()">返回</button>
      <h2 class="section-title">我的收藏</h2>
      <button class="ghost-btn" @click="reload">刷新</button>
    </section>

    <LoadingState v-if="loading && !houses.length" text="收藏加载中..." />
    <p v-if="error" class="error-text">{{ error }}</p>

    <HouseCard
      v-for="house in houses"
      :key="house.id"
      :house="house"
      @click="goDetail(house.id)"
    />

    <EmptyState
      v-if="!loading && !houses.length"
      title="暂无收藏房源"
      description="你可以先去首页收藏感兴趣的房源"
      action-text="去首页"
      @action="router.push('/home')"
    />

    <div v-if="houses.length" class="load-more">
      <button v-if="hasMore && !loading" class="ghost-btn" @click="loadFavorites">加载更多</button>
      <LoadingState v-else-if="loading" text="正在加载..." />
      <span v-else class="no-more">没有更多了</span>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchMyFavoritePage } from '@/api/house'
import { fetchUserById } from '@/api/user'
import EmptyState from '@/components/EmptyState.vue'
import HouseCard from '@/components/HouseCard.vue'
import LoadingState from '@/components/LoadingState.vue'

const router = useRouter()

const loading = ref(false)
const error = ref('')
const houses = ref([])
const current = ref(1)
const size = 10
const hasMore = ref(true)

const userCache = new Map()

async function attachPublisher(records = []) {
  if (!records.length) {
    return []
  }

  return Promise.all(
    records.map(async (house) => {
      const publisherUserId = house?.publisherUserId
      if (!publisherUserId) {
        return { ...house, publisherName: '未知发布人' }
      }
      if (userCache.has(publisherUserId)) {
        return { ...house, publisherName: userCache.get(publisherUserId) }
      }
      try {
        const user = await fetchUserById(publisherUserId)
        const publisherName = user?.name || '未知发布人'
        userCache.set(publisherUserId, publisherName)
        return { ...house, publisherName }
      } catch {
        const publisherName = '未知发布人'
        userCache.set(publisherUserId, publisherName)
        return { ...house, publisherName }
      }
    })
  )
}

async function loadFavorites(reset = false) {
  if (loading.value || (!hasMore.value && !reset)) {
    return
  }

  if (reset) {
    current.value = 1
    hasMore.value = true
    houses.value = []
    error.value = ''
  }

  loading.value = true
  try {
    const page = await fetchMyFavoritePage({ current: current.value, size })
    const records = await attachPublisher(page?.records || [])
    houses.value = reset ? records : [...houses.value, ...records]
    const total = Number(page?.total || 0)
    hasMore.value = current.value * size < total
    current.value += 1
  } catch (err) {
    error.value = err?.message || '收藏加载失败'
    if (reset) {
      houses.value = []
    }
  } finally {
    loading.value = false
  }
}

function reload() {
  loadFavorites(true)
}

function goDetail(id) {
  router.push(`/house/${id}`)
}

onMounted(() => {
  loadFavorites(true)
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
