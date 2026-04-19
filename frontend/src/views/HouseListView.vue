<template>
  <div class="house-list-view">
    <HouseResultsHero
      title="精选房源列表"
      :result-tip="feed.resultTip.value"
      :is-nearby-mode="feed.mode.value === 'nearby'"
      @search="handleSearch"
      @reset="handleReset"
    />

    <LoadingState v-if="feed.loading.value && !feed.houses.value.length" text="正在加载房源..." />

    <div v-else-if="feed.houses.value.length" class="listing-grid">
      <HouseCard
        v-for="house in feed.houses.value"
        :key="house.id"
        :house="house"
        @click="toDetail(house.id)"
      />
    </div>

    <EmptyState
      v-else
      title="暂时没有匹配房源"
      :description="feed.error.value || '可以换一个地点名称，或者先回到精选推荐继续浏览。'"
      action-text="回到首页"
      @action="router.push('/home')"
    />

    <div v-if="feed.houses.value.length && feed.hasMore.value && !feed.loading.value && !feed.error.value" class="load-more">
      <button class="ghost-btn" @click="feed.loadNext()">加载更多</button>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { fetchHotHousePage, searchNearbyHouse } from '@/api/house'
import EmptyState from '@/components/EmptyState.vue'
import HouseCard from '@/components/HouseCard.vue'
import LoadingState from '@/components/LoadingState.vue'
import HouseResultsHero from '@/components/house/HouseResultsHero.vue'
import { useHouseFeed } from '@/composables/useHouseFeed'

const router = useRouter()
const feed = useHouseFeed({
  hotLoader: fetchHotHousePage,
  nearbyLoader: searchNearbyHouse,
  defaultCity: '广州'
})

async function handleSearch(keyword) {
  if (!keyword) {
    await handleReset()
    return
  }
  feed.activateNearby(keyword)
  await feed.loadNext()
}

async function handleReset() {
  feed.activateHot()
  await feed.loadNext()
}

function toDetail(id) {
  router.push(`/house/${id}`)
}

onMounted(() => {
  feed.loadNext()
})
</script>

<style scoped>
.house-list-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.listing-grid {
  display: grid;
  gap: 16px;
}

.load-more {
  display: flex;
  justify-content: center;
  padding-bottom: 12px;
}
</style>
