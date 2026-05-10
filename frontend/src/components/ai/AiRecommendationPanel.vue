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
        </div>
        <div class="recommend-meta">
          <span v-if="item.distanceToMetroKm != null">距目标地点 {{ item.distanceToMetroKm }} km</span>
          <span v-if="item.estimatedCommuteMinutes != null">预计通勤 {{ item.estimatedCommuteMinutes }} 分钟</span>
        </div>
        <p v-if="item.recommendationSummary" class="recommend-summary">{{ item.recommendationSummary }}</p>
        <div v-if="item.primaryReasons?.length" class="recommend-tags recommend-tags--primary">
          <span v-for="reason in item.primaryReasons" :key="reason" class="recommend-tag recommend-tag--primary">{{ reason }}</span>
        </div>
        <div v-if="item.secondaryReasons?.length" class="recommend-tags">
          <span v-for="reason in item.secondaryReasons" :key="reason" class="recommend-tag recommend-tag--secondary">{{ reason }}</span>
        </div>
        <div v-if="item.relaxationNotes?.length" class="recommend-tags recommend-tags--relaxation">
          <span v-for="reason in item.relaxationNotes" :key="reason" class="recommend-tag recommend-tag--relaxation">{{ reason }}</span>
        </div>
        <div v-else-if="item.reasons?.length" class="recommend-tags">
          <span v-for="reason in item.reasons" :key="reason" class="recommend-tag recommend-tag--secondary">{{ reason }}</span>
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
  color: #2d3748;
}

.recommend-tip {
  margin: 6px 0 0;
  color: #5b6a8a;
  font-size: 13px;
}

.relaxed-tag {
  flex-shrink: 0;
  border-radius: 999px;
  background: #fff8e6;
  color: #a37920;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid rgba(232, 196, 136, 0.5);
}

.recommend-list {
  display: grid;
  gap: 12px;
}

.recommend-card {
  border: 1px solid rgba(184, 200, 224, 0.35);
  border-radius: 22px;
  background: #ffffff;
  padding: 16px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  box-shadow: 0 4px 12px rgba(100, 130, 200, 0.06);
}

.recommend-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 28px rgba(100, 130, 200, 0.14);
}

.recommend-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.recommend-row h4 {
  margin: 0;
  font-size: 16px;
  color: #2d3748;
}

.recommend-price {
  margin: 6px 0 0;
  color: #3b4a6b;
  font-weight: 700;
}

.recommend-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  color: #5b6a8a;
  font-size: 13px;
}

.recommend-summary {
  margin: 12px 0 0;
  color: #2d3748;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.5;
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
  font-size: 12px;
  font-weight: 600;
}

.recommend-tag--primary {
  background: #dbe8fb;
  color: #2a4a7a;
}

.recommend-tag--secondary {
  background: #eef3fa;
  color: #5b6a8a;
}

.recommend-tag--relaxation {
  background: #fff8e6;
  color: #a37920;
}

.recommend-empty {
  margin: 0;
  color: #5b6a8a;
}

@media (max-width: 768px) {
  .recommend-row { flex-direction: column; }
}
</style>
