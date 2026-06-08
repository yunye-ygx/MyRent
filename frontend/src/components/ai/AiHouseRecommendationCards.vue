<template>
  <div v-if="houses.length" class="recommendations">
    <article v-for="house in houses" :key="houseIdOf(house)" class="recommendation-card">
      <img class="recommendation-card__cover" :src="coverOf(house)" alt="house cover" />
      <div class="recommendation-card__body">
        <div class="recommendation-card__header">
          <h3>{{ house.title || '未命名房源' }}</h3>
          <p>{{ priceText(house) }}</p>
        </div>

        <div class="recommendation-card__meta">
          <span v-if="house.rentMode">{{ house.rentMode }}</span>
          <span v-for="highlight in house.highlights || []" :key="highlight">{{ highlight }}</span>
        </div>

        <p v-if="firstReason(house)" class="recommendation-card__reason">{{ firstReason(house) }}</p>

        <div class="recommendation-card__actions">
          <button
            class="recommendation-card__detail"
            type="button"
            :data-testid="`house-detail-${houseIdOf(house)}`"
            @click="$emit('open-detail', houseIdOf(house))"
          >
            查看详情
          </button>
        </div>
      </div>
    </article>

    <div class="recommendation-actions">
      <button type="button" data-testid="action-more-houses" @click="$emit('action', 'more')">换一批</button>
      <button type="button" data-testid="action-relax-budget" @click="$emit('action', 'relax-budget')">放宽预算</button>
      <button type="button" data-testid="action-adjust-area" @click="$emit('action', 'adjust-area')">调整区域</button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  houses: {
    type: Array,
    default: () => []
  }
})

defineEmits(['open-detail', 'action'])

function houseIdOf(house) {
  return house?.houseId || house?.id || ''
}

function coverOf(house) {
  return `https://picsum.photos/seed/ai-house-${houseIdOf(house) || 1}/320/220`
}

function priceText(house) {
  if (house?.priceYuan === null || house?.priceYuan === undefined || house?.priceYuan === '') {
    return '价格待确认'
  }
  return `¥${house.priceYuan}/月`
}

function firstReason(house) {
  return Array.isArray(house?.reasons) ? house.reasons[0] : ''
}
</script>

<style scoped>
.recommendations {
  display: grid;
  gap: 10px;
  margin-left: 42px;
  max-width: 680px;
}

.recommendation-card {
  display: grid;
  grid-template-columns: 132px minmax(0, 1fr);
  gap: 12px;
  border: 1px solid rgba(184, 200, 224, 0.35);
  border-radius: 16px;
  padding: 10px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(100, 130, 200, 0.08);
}

.recommendation-card__cover {
  width: 100%;
  height: 108px;
  object-fit: cover;
  border-radius: 12px;
  background: #eef3f9;
}

.recommendation-card__body {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.recommendation-card__header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.recommendation-card__header h3,
.recommendation-card__header p,
.recommendation-card__reason {
  margin: 0;
}

.recommendation-card__header h3 {
  color: #2d3748;
  font-size: 15px;
  line-height: 1.4;
}

.recommendation-card__header p {
  flex-shrink: 0;
  color: #2d6cdf;
  font-weight: 800;
}

.recommendation-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.recommendation-card__meta span {
  border-radius: 999px;
  padding: 4px 8px;
  background: #f2f6fb;
  color: #52627a;
  font-size: 12px;
}

.recommendation-card__reason {
  color: #5b6a8a;
  font-size: 13px;
  line-height: 1.5;
}

.recommendation-card__actions {
  display: flex;
  justify-content: flex-end;
}

.recommendation-card__detail,
.recommendation-actions button {
  border: 0;
  border-radius: 999px;
  padding: 8px 12px;
  background: #eef5ff;
  color: #2d6cdf;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.recommendation-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 640px) {
  .recommendations {
    margin-left: 0;
  }

  .recommendation-card {
    grid-template-columns: 1fr;
  }

  .recommendation-card__cover {
    height: 150px;
  }
}
</style>
