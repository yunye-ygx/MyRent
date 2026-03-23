<template>
  <div class="page home-page">
    <section class="home-header card">
      <div>
        <p class="city">广州</p>
        <h2 class="brand">{{ pageTitle }}</h2>
      </div>
      <button class="ghost-btn" @click="reload">刷新</button>
    </section>

    <section class="card search-wrap">
      <div class="search-row">
        <input
          v-model.trim="keyword"
          class="input"
          placeholder="输入地点名称，如珠江新城、体育西路"
          @keyup.enter="handleSearch"
        />
        <button class="primary-btn search-btn" :disabled="loading || !keyword" @click="handleSearch">
          搜索
        </button>
      </div>
      <div v-if="isNearbyMode" class="search-actions">
        <button class="ghost-btn" :disabled="loading" @click="resetToHot">返回热门</button>
      </div>
      <p class="search-tip" :class="{ warning: isNearbyMode }">{{ searchTip }}</p>
    </section>

    <LoadingState v-if="loading && !houses.length" text="正在加载房源..." />

    <p v-if="error" class="error-text">{{ error }}</p>

    <template v-if="houses.length">
      <HouseCard
        v-for="house in houses"
        :key="house.id"
        :house="house"
        @click="toDetail(house.id)"
      />
    </template>
    <EmptyState
      v-else-if="!loading"
      :title="emptyTitle"
      :description="emptyDescription"
      :action-text="isNearbyMode ? '返回热门' : '刷新'"
      @action="handleEmptyAction"
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
import { fetchHotHousePage, searchNearbyHouse } from '@/api/house'
import { fetchUserById } from '@/api/user'
import EmptyState from '@/components/EmptyState.vue'
import HouseCard from '@/components/HouseCard.vue'
import LoadingState from '@/components/LoadingState.vue'

const router = useRouter()
const DEFAULT_CITY = '广州'

const houses = ref([])
const loading = ref(false)
const error = ref('')
const current = ref(1)
const size = ref(10)
const hasMore = ref(true)
const keyword = ref('')
const mode = ref('hot')
const activeLocation = ref('')
const resultTip = ref('')
const userNameCache = new Map()

const isNearbyMode = computed(() => mode.value === 'nearby')

const pageTitle = computed(() => {
  if (!isNearbyMode.value) {
    return 'MyRent 热门房源'
  }
  return `${activeLocation.value || '附近'}周边房源`
})

const searchTip = computed(() => {
  if (resultTip.value) {
    return resultTip.value
  }
  if (isNearbyMode.value) {
    return '输入地点后会自动解析坐标，再调用附近房源接口。'
  }
  return '首页默认展示热门缓存内容，输入地点后可切换为附近搜索。'
})

const emptyTitle = computed(() => (isNearbyMode.value ? '附近暂无房源' : '暂无房源'))

const emptyDescription = computed(() => {
  if (isNearbyMode.value) {
    return '可以换个地点名称搜索，或返回热门房源查看推荐结果。'
  }
  return '可点击刷新按钮重试或稍后再看'
})

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

async function loadHotPage() {
  return fetchHotHousePage({ page: current.value, size: size.value })
}

async function loadNearbyPage() {
  return searchNearbyHouse({
    locationName: activeLocation.value,
    city: DEFAULT_CITY,
    page: current.value,
    size: size.value
  })
}

async function loadNext() {
  if (loading.value || !hasMore.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = isNearbyMode.value ? await loadNearbyPage() : await loadHotPage()
    const records = result?.houses || []
    const enrichedRecords = await enrichPublisherName(records)
    houses.value = [...houses.value, ...enrichedRecords]
    hasMore.value = records.length >= size.value
    current.value += 1
    resultTip.value = result?.tipMessage || ''
  } catch (err) {
    error.value = err?.message || '房源加载失败'
  } finally {
    loading.value = false
  }
}

function resetPaging() {
  houses.value = []
  current.value = 1
  hasMore.value = true
  error.value = ''
}

function reload() {
  if (isNearbyMode.value && !activeLocation.value) {
    resetToHot()
    return
  }
  resetPaging()
  loadNext()
}

function resetToHot() {
  mode.value = 'hot'
  keyword.value = ''
  activeLocation.value = ''
  resultTip.value = ''
  resetPaging()
  loadNext()
}

function handleSearch() {
  if (!keyword.value) {
    resetToHot()
    return
  }
  mode.value = 'nearby'
  activeLocation.value = keyword.value
  resultTip.value = ''
  resetPaging()
  loadNext()
}

function handleEmptyAction() {
  if (isNearbyMode.value) {
    resetToHot()
    return
  }
  reload()
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

.search-row {
  display: flex;
  gap: 8px;
}

.search-btn {
  flex: 0 0 auto;
}

.search-actions {
  margin-top: 8px;
}

.search-tip {
  margin: 8px 0 0;
  color: #92400e;
  font-size: 12px;
}

.search-tip.warning {
  color: #1d4ed8;
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
