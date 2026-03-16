<template>
  <div class="page home-page">
    <section class="home-header card">
      <div>
        <p class="city">广州</p>
        <h2 class="brand">MyRent 租房</h2>
      </div>
      <button class="ghost-btn" @click="reload">刷新</button>
    </section>

    <section class="card search-wrap">
      <input v-model.trim="keyword" class="input" placeholder="搜索房源标题（本地过滤）" />
    </section>

    <LoadingState v-if="loading && !houses.length" text="正在加载房源..." />

    <p v-if="error" class="error-text">{{ error }}</p>

    <template v-if="displayHouses.length">
      <HouseCard
        v-for="house in displayHouses"
        :key="house.id"
        :house="house"
        @click="toDetail(house.id)"
      />
    </template>
    <EmptyState
      v-else-if="!loading"
      title="暂无房源"
      description="可点击刷新按钮重试或稍后再看"
      action-text="刷新"
      @action="reload"
    />

    <div class="load-more">
      <button v-if="hasMore && !loading" class="ghost-btn" @click="loadNext">加载更多</button>
      <LoadingState v-else-if="loading && houses.length" text="加载中..." />
      <span v-else-if="!hasMore && houses.length" class="no-more">没有更多了</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchHousePage } from '@/api/house'
import { fetchUserById } from '@/api/user'
import EmptyState from '@/components/EmptyState.vue'
import HouseCard from '@/components/HouseCard.vue'
import LoadingState from '@/components/LoadingState.vue'

const router = useRouter()

const houses = ref([])
const loading = ref(false)
const error = ref('')
const current = ref(1)
const size = ref(10)
const hasMore = ref(true)
const keyword = ref('')
const userNameCache = new Map()

const displayHouses = computed(() => {
  if (!keyword.value) {
    return houses.value
  }
  return houses.value.filter((item) => String(item.title || '').includes(keyword.value))
})

async function loadNext() {
  if (loading.value || !hasMore.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const pageData = await fetchHousePage({ current: current.value, size: size.value })
    const records = pageData?.records || []
    const enrichedRecords = await enrichPublisherName(records)
    houses.value = [...houses.value, ...enrichedRecords]
    hasMore.value = current.value < (pageData?.pages || 0)
    current.value += 1
  } catch (err) {
    error.value = err?.message || '房源加载失败'
  } finally {
    loading.value = false
  }
}

async function enrichPublisherName(records) {
  if (!Array.isArray(records) || !records.length) {
    return []
  }

  return Promise.all(
    records.map(async (item) => {
      const publisherUserId = item?.publisherUserId
      if (!publisherUserId) {
        return {
          ...item,
          publisherName: '未知发布人'
        }
      }

      const key = String(publisherUserId)
      if (userNameCache.has(key)) {
        return {
          ...item,
          publisherName: userNameCache.get(key)
        }
      }

      try {
        const user = await fetchUserById(publisherUserId)
        const name = user?.name || '未知发布人'
        userNameCache.set(key, name)
        return {
          ...item,
          publisherName: name
        }
      } catch {
        const fallbackName = '未知发布人'
        userNameCache.set(key, fallbackName)
        return {
          ...item,
          publisherName: fallbackName
        }
      }
    })
  )
}

function reload() {
  houses.value = []
  current.value = 1
  hasMore.value = true
  loadNext()
}

function toDetail(id) {
  router.push(`/house/${id}`)
}

onMounted(() => {
  loadNext()
})
</script>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.city {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
}

.brand {
  margin: 0;
  font-size: 20px;
}

.search-wrap {
  padding: 10px;
}

.load-more {
  display: flex;
  justify-content: center;
  padding: 10px 0 18px;
}

.no-more {
  color: #9ca3af;
  font-size: 12px;
}
</style>
