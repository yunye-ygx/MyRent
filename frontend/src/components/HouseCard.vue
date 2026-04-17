<template>
  <article class="house-card app-surface" @click="$emit('click')">
    <img class="cover" :src="cover" alt="house cover" />
    <div class="content">
      <div class="header-row">
        <div class="title-wrap">
          <h3 class="title">{{ house.title || '未命名房源' }}</h3>
          <p class="location">{{ regionText }}</p>
        </div>
        <span class="status" :class="statusClass">{{ statusText }}</span>
      </div>

      <p class="price">
        {{ formatPrice(house.price) }}
        <span class="price-unit">/ 月</span>
      </p>

      <div class="facts">
        <span class="fact">{{ rentalTypeText }}</span>
        <span class="fact">{{ areaText }}</span>
        <span v-if="distanceText" class="fact fact-accent">{{ distanceText }}</span>
      </div>

      <p class="meta">押金 {{ formatPrice(house.depositAmount) }}</p>
      <p class="meta">发布者 {{ publisherText }}</p>
      <p v-if="hotText" class="meta meta-warning">{{ hotText }}</p>
    </div>
  </article>
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
const publisherText = computed(() => props.house.publisherName || '未知发布者')
const distanceText = computed(() => props.house.distance || '')
const regionText = computed(() => props.house.region || props.house.city || '区域待完善')
const rentalTypeText = computed(() => props.house.rentalType || '租住方式待完善')
const areaText = computed(() => {
  if (props.house.area === null || props.house.area === undefined || props.house.area === '') {
    return '面积待完善'
  }
  return `${props.house.area}㎡`
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

const statusClass = computed(() => {
  if (props.house.status === 1) {
    return 'status-available'
  }
  if (props.house.status === 2) {
    return 'status-locked'
  }
  return 'status-unavailable'
})

const cover = computed(() => `https://picsum.photos/seed/house-${props.house.id || 1}/480/320`)
</script>

<style scoped>
.house-card {
  display: grid;
  gap: 16px;
  cursor: pointer;
  padding: 16px;
}

.cover {
  width: 100%;
  height: 180px;
  object-fit: cover;
  border-radius: 20px;
  background: var(--color-surface-strong);
}

.content {
  min-width: 0;
}

.header-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.title-wrap {
  min-width: 0;
}

.title {
  margin: 0;
  font-size: 22px;
  line-height: 1.3;
  color: var(--color-text);
}

.location {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-muted);
}

.status {
  flex-shrink: 0;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
}

.status-available {
  background: rgba(68, 107, 85, 0.12);
  color: var(--color-success);
}

.status-locked {
  background: rgba(154, 107, 51, 0.12);
  color: var(--color-warning);
}

.status-unavailable {
  background: rgba(158, 77, 66, 0.12);
  color: var(--color-danger);
}

.price {
  margin: 14px 0 6px;
  font-size: 28px;
  font-weight: 600;
  color: var(--color-accent);
}

.price-unit {
  margin-left: 6px;
  font-size: 14px;
  font-weight: 400;
  color: var(--color-text-muted);
}

.facts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.fact {
  border-radius: 999px;
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.75);
  font-size: 13px;
  color: var(--color-text);
}

.fact-accent {
  background: rgba(68, 107, 85, 0.12);
  color: var(--color-accent);
}

.meta {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text-muted);
}

.meta-warning {
  color: var(--color-warning);
}

@media (min-width: 1024px) {
  .house-card {
    grid-template-columns: 220px minmax(0, 1fr);
    align-items: stretch;
  }

  .cover {
    height: 100%;
    min-height: 180px;
  }
}
</style>
