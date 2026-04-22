<template>
  <div class="home-view">
    <HomeHero
      :result-tip="feed.resultTip.value"
      :is-nearby-mode="feed.mode.value === 'nearby'"
      @search="handleSearch"
      @suggestion-select="handleSuggestionSelect"
    />

    <HomeQuickLinks />

    <section class="content-grid">
      <div class="featured app-surface">
        <div class="section-head">
          <div>
            <p class="section-eyebrow">Featured Listings</p>
            <h2 class="section-title">先看值得点开的房源</h2>
          </div>
          <RouterLink class="section-link" to="/houses">查看全部房源</RouterLink>
        </div>

        <div v-if="feed.houses.value.length" class="listing-grid">
          <HouseCard
            v-for="house in feed.houses.value"
            :key="house.id"
            :house="house"
            @click="toDetail(house.id)"
          />
        </div>
        <LoadingState v-else-if="feed.loading.value" text="正在加载精选房源..." />
        <EmptyState
          v-else
          title="精选房源暂时不可用"
          :description="feed.error.value || '可以先从地图找房，或者重新输入地点开始搜索。'"
        />
      </div>

      <aside class="aside">
        <section class="mini-panel app-surface">
          <p class="section-eyebrow">Today</p>
          <h3 class="mini-title">今日新上</h3>
          <p class="mini-copy">优先查看刚刚进入列表的房源，减少错过热门房的概率。</p>
        </section>

        <section class="mini-panel app-surface">
          <p class="section-eyebrow">Budget</p>
          <h3 class="mini-title">低总价优先</h3>
          <p class="mini-copy">先从总价更友好的房源开始浏览，再决定是否交换空间和位置。</p>
        </section>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { fetchHotHousePage, searchNearbyHouse } from '@/api/house'
import EmptyState from '@/components/EmptyState.vue'
import HouseCard from '@/components/HouseCard.vue'
import LoadingState from '@/components/LoadingState.vue'
import HomeHero from '@/components/home/HomeHero.vue'
import HomeQuickLinks from '@/components/home/HomeQuickLinks.vue'
import { useHouseFeed } from '@/composables/useHouseFeed'

const router = useRouter()
const feed = useHouseFeed({
  hotLoader: fetchHotHousePage,
  nearbyLoader: searchNearbyHouse,
  defaultCity: '广州'
})

async function handleSearch(keyword) {
  if (!keyword) {
    feed.activateHot()
    await feed.loadNext()
    return
  }

  feed.activateNearby(keyword)
  await feed.loadNext()
}

function toDetail(id) {
  router.push(`/house/${id}`)
}

function handleSuggestionSelect(item) {
  if (!item?.id && item?.id !== 0) return
  router.push(`/house/${item.id}`)
}

onMounted(() => {
  feed.loadNext()
})
</script>

<style scoped>
.home-view {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.content-grid {
  display: grid;
  gap: 24px;
}

.featured,
.mini-panel {
  padding: 24px;
}

.section-head {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.section-eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.section-title,
.mini-title {
  margin: 0;
  color: var(--color-text);
}

.section-title {
  font-size: 28px;
}

.section-link {
  color: var(--color-accent);
  text-decoration: none;
}

.listing-grid {
  display: grid;
  gap: 16px;
}

.aside {
  display: grid;
  gap: 16px;
}

.mini-copy {
  margin: 14px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text-muted);
}

@media (min-width: 1024px) {
  .content-grid {
    grid-template-columns: minmax(0, 1.2fr) minmax(300px, 0.8fr);
    align-items: start;
  }
}
</style>
