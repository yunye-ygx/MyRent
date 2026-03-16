<template>
  <div class="page map-page">
    <section class="card">
      <h2 class="section-title">找房（地图）</h2>
      <p class="desc">本阶段先提供地图跳转入口，并预留 LBS 房源展示。</p>
      <p v-if="fallbackTip" class="fallback-tip">{{ fallbackTip }}</p>
      <div class="actions">
        <button class="primary-btn" @click="openAmap">打开高德地图</button>
        <button class="ghost-btn" :disabled="loading" @click="loadNearby">
          {{ loading ? '定位中...' : '查看附近房源' }}
        </button>
      </div>
      <p v-if="error" class="error-text">{{ error }}</p>
    </section>

    <section class="card">
      <h3 class="section-title">附近房源（LBS 预览）</h3>
      <LoadingState v-if="loading && !nearbyList.length" text="正在获取附近房源..." />
      <EmptyState v-else-if="!nearbyList.length" title="暂无附近房源" description="可先点击按钮触发定位查询" />
      <ul v-else class="nearby-list">
        <li v-for="house in nearbyList" :key="house.id" class="nearby-item" @click="goDetail(house.id)">
          <div>
            <p class="name">{{ house.title }}</p>
            <p class="meta">距离：{{ house.distance || '--' }}</p>
          </div>
          <p class="price">{{ formatPrice(house.price) }}/月</p>
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { searchNearbyHouse } from '@/api/house'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { formatPrice } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const error = ref('')
const fallbackTip = ref('')
const nearbyList = ref([])

function openAmap() {
  window.open('https://uri.amap.com/search?keyword=租房', '_blank')
}

function getCurrentPosition() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('浏览器不支持定位'))
      return
    }
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: true,
      timeout: 10000
    })
  })
}

async function loadNearby() {
  loading.value = true
  error.value = ''
  fallbackTip.value = ''
  try {
    const pos = await getCurrentPosition()
    const result = await searchNearbyHouse({
      latitude: pos.coords.latitude,
      longitude: pos.coords.longitude,
      radius: '5km',
      page: 1,
      size: 10,
      city: '广州'
    })
    nearbyList.value = Array.isArray(result?.houses) ? result.houses : []
    fallbackTip.value = result?.tipMessage || ''
  } catch (err) {
    error.value = err?.message || '附近房源查询失败'
    nearbyList.value = []
    fallbackTip.value = ''
  } finally {
    loading.value = false
  }
}

function goDetail(houseId) {
  if (!houseId) {
    return
  }
  router.push(`/house/${houseId}`)
}
</script>

<style scoped>
.map-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.desc {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
}

.fallback-tip {
  margin: 8px 0 0;
  color: #b45309;
  font-size: 12px;
}

.actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.nearby-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nearby-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px 10px;
  cursor: pointer;
}

.name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.meta {
  margin: 2px 0 0;
  color: #6b7280;
  font-size: 12px;
}

.price {
  margin: 0;
  color: #dc2626;
  font-size: 14px;
  font-weight: 600;
}
</style>
