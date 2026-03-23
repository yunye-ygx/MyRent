<template>
  <div class="house-card card" @click="$emit('click')">
    <img class="cover" :src="cover" alt="house" />
    <div class="content">
      <div class="header-row">
        <h3 class="title">{{ house.title || '未命名房源' }}</h3>
        <span class="status" :class="statusClass">{{ statusText }}</span>
      </div>
      <p class="price">{{ formatPrice(house.price) }}/月</p>
      <p class="meta">押金：{{ formatPrice(house.depositAmount) }}</p>
      <p class="meta">发布人：{{ publisherText }}</p>
      <p v-if="distanceText" class="meta distance-meta">距离：{{ distanceText }}</p>
      <p v-if="hotText" class="meta hot-meta">{{ hotText }}</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatPrice, getHouseStatusText } from '@/utils/format'

const props = defineProps({
  house: {
    type: Object,
    required: true
  }
})

defineEmits(['click'])

const statusText = computed(() => getHouseStatusText(props.house.status))

const publisherText = computed(() => {
  if (props.house.publisherName) {
    return props.house.publisherName
  }
  return '未知发布人'
})

const hotText = computed(() => {
  if (props.house.hotScore === null || props.house.hotScore === undefined) {
    return ''
  }
  const parts = [`热度 ${Number(props.house.hotScore).toFixed(2)}`]
  if (props.house.favoriteCount !== null && props.house.favoriteCount !== undefined) {
    parts.push(`收藏 ${props.house.favoriteCount}`)
  }
  return parts.join(' · ')
})

const distanceText = computed(() => props.house.distance || '')

const statusClass = computed(() => {
  if (props.house.status === 1) {
    return 'ok'
  }
  if (props.house.status === 2) {
    return 'locking'
  }
  return 'disabled'
})

const cover = computed(() => `https://picsum.photos/seed/house-${props.house.id || 1}/240/180`)
</script>

<style scoped>
.house-card {
  display: flex;
  gap: 10px;
  cursor: pointer;
}

.cover {
  width: 110px;
  height: 82px;
  object-fit: cover;
  border-radius: 8px;
  background: #e5e7eb;
}

.content {
  flex: 1;
  min-width: 0;
}

.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.title {
  margin: 0;
  font-size: 15px;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status {
  font-size: 11px;
  border-radius: 999px;
  padding: 2px 8px;
}

.status.ok {
  background: #dcfce7;
  color: #15803d;
}

.status.locking {
  background: #fef3c7;
  color: #b45309;
}

.status.disabled {
  background: #fee2e2;
  color: #b91c1c;
}

.price {
  margin: 4px 0 2px;
  color: #dc2626;
  font-size: 16px;
  font-weight: 600;
}

.meta {
  margin: 0;
  color: #6b7280;
  font-size: 12px;
}

.hot-meta {
  color: #b45309;
}

.distance-meta {
  color: #2563eb;
}
</style>
