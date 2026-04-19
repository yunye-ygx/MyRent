<template>
  <section class="summary app-surface">
    <div class="toolbar">
      <button class="ghost-btn" @click="$emit('back')">返回</button>
      <span class="eyebrow">House Detail</span>
    </div>
    <div class="content">
      <img class="cover" :src="cover" alt="house cover" />
      <div class="info">
        <span class="status">{{ statusText }}</span>
        <h1 class="title">{{ house.title }}</h1>
        <p class="price">
          {{ formatPrice(house.price) }}
          <span class="price-unit">/ 月</span>
        </p>
        <dl class="meta-list">
          <div>
            <dt>押金</dt>
            <dd>{{ formatPrice(house.depositAmount) }}</dd>
          </div>
          <div>
            <dt>发布者</dt>
            <dd>{{ publisherName }}</dd>
          </div>
          <div>
            <dt>收藏数</dt>
            <dd>{{ favoriteCount }}</dd>
          </div>
        </dl>
      </div>
    </div>
  </section>
</template>

<script setup>
import { formatPrice } from '@/utils/format'

defineProps({
  house: {
    type: Object,
    required: true
  },
  cover: {
    type: String,
    required: true
  },
  publisherName: {
    type: String,
    default: '未知发布者'
  },
  favoriteCount: {
    type: [String, Number],
    default: 0
  },
  statusText: {
    type: String,
    default: ''
  }
})

defineEmits(['back'])
</script>

<style scoped>
.summary {
  padding: 20px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.eyebrow {
  font-size: 12px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.content {
  display: grid;
  gap: 18px;
}

.cover {
  width: 100%;
  min-height: 240px;
  object-fit: cover;
  border-radius: 24px;
  background: var(--color-surface-strong);
}

.status {
  display: inline-flex;
  border-radius: 999px;
  padding: 6px 10px;
  background: rgba(68, 107, 85, 0.12);
  color: var(--color-success);
  font-size: 12px;
}

.title {
  margin: 14px 0 0;
  font-size: clamp(30px, 5vw, 44px);
  color: var(--color-text);
}

.price {
  margin: 14px 0 0;
  font-size: 32px;
  font-weight: 600;
  color: var(--color-accent);
}

.price-unit {
  margin-left: 6px;
  font-size: 14px;
  font-weight: 400;
  color: var(--color-text-muted);
}

.meta-list {
  display: grid;
  gap: 12px;
  margin: 18px 0 0;
}

.meta-list div {
  padding: 14px 16px;
  border-radius: 18px;
  background: var(--color-surface-strong);
}

.meta-list dt {
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.meta-list dd {
  margin: 8px 0 0;
  font-size: 16px;
  color: var(--color-text);
}

@media (min-width: 1024px) {
  .content {
    grid-template-columns: 1.05fr 0.95fr;
    align-items: stretch;
  }
}
</style>
