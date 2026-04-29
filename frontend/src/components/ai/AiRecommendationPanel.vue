<template>
  <section class="recommend-panel">
    <header class="recommend-head">
      <div>
        <h3>真实房源推荐</h3>
        <p v-if="recommendation?.tipMessage" class="recommend-tip">{{ recommendation.tipMessage }}</p>
      </div>
      <span v-if="recommendation?.relaxedBudget" class="relaxed-tag">
        已放宽到 {{ recommendation.relaxedBudgetYuan }} 元
      </span>
    </header>

    <div v-if="items.length" class="recommend-list">
      <article
        v-for="item in items"
        :key="String(item.houseId)"
        class="recommend-card"
        @click="$emit('open-house', item.houseId)"
      >
        <div class="recommend-row">
          <div>
            <h4>{{ item.title || '未命名房源' }}</h4>
            <p class="recommend-price">{{ formatPrice(item.price) }}/月</p>
          </div>
          <span class="recommend-score">评分 {{ formatScore(item.score) }}</span>
        </div>
        <div class="recommend-meta">
          <span v-if="item.distanceToMetroKm != null">距地铁 {{ item.distanceToMetroKm }} km</span>
          <span v-if="item.estimatedCommuteMinutes != null">通勤约 {{ item.estimatedCommuteMinutes }} 分钟</span>
        </div>
        <div v-if="item.reasons?.length" class="recommend-tags">
          <span v-for="reason in item.reasons" :key="reason" class="recommend-tag">{{ reason }}</span>
        </div>
      </article>
    </div>
    <p v-else class="recommend-empty">暂时还没有可展示的房源结果。</p>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  recommendation: {
    type: Object,
    default: null
  }
})

defineEmits(['open-house'])

const items = computed(() => props.recommendation?.recommendations || [])

function formatPrice(value) {
  if (value == null || value === '') {
    return '--'
  }
  return String(value)
}

function formatScore(value) {
  if (value == null || value === '') {
    return '--'
  }
  return String(value)
}
</script>

<style scoped>
.recommend-panel {
  display: grid;
  gap: 14px;
}

.recommend-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.recommend-head h3 {
  margin: 0;
  font-size: 16px;
}

.recommend-tip {
  margin: 6px 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.relaxed-tag {
  flex-shrink: 0;
  border-radius: 999px;
  background: rgba(154, 107, 51, 0.12);
  color: var(--color-warning);
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 700;
}

.recommend-list {
  display: grid;
  gap: 12px;
}

.recommend-card {
  border: 1px solid rgba(53, 36, 25, 0.08);
  border-radius: 22px;
  background: linear-gradient(180deg, #fffdf8 0%, #f9f1e7 100%);
  padding: 16px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.recommend-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 32px rgba(49, 33, 23, 0.08);
}

.recommend-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.recommend-row h4 {
  margin: 0;
  font-size: 16px;
}

.recommend-price {
  margin: 6px 0 0;
  color: #6a503f;
  font-weight: 700;
}

.recommend-score {
  color: var(--color-text-muted);
  font-size: 13px;
  font-weight: 600;
}

.recommend-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.recommend-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.recommend-tag {
  border-radius: 999px;
  padding: 6px 10px;
  background: rgba(77, 107, 66, 0.12);
  color: #446b55;
  font-size: 12px;
  font-weight: 600;
}

.recommend-empty {
  margin: 0;
  color: var(--color-text-muted);
}

@media (max-width: 768px) {
  .recommend-row {
    flex-direction: column;
  }
}
</style>
