<template>
  <section class="summary-card">
    <div class="summary-head">
      <h3>当前已知条件</h3>
      <span v-if="missingSlots.length" class="summary-tip">还差 {{ missingLabels }}</span>
    </div>
    <dl class="summary-grid">
      <div class="summary-item">
        <dt>城市</dt>
        <dd>{{ displayValue(slots.city) }}</dd>
      </div>
      <div class="summary-item">
        <dt>区域</dt>
        <dd>{{ displayValue(slots.locationName) }}</dd>
      </div>
      <div class="summary-item">
        <dt>预算</dt>
        <dd>{{ budgetText }}</dd>
      </div>
      <div class="summary-item">
        <dt>租住方式</dt>
        <dd>{{ rentModeText }}</dd>
      </div>
      <div class="summary-item">
        <dt>优先级</dt>
        <dd>{{ priorityText }}</dd>
      </div>
      <div class="summary-item">
        <dt>偏好</dt>
        <dd>{{ preferencesText }}</dd>
      </div>
    </dl>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  slots: {
    type: Object,
    default: () => ({})
  },
  missingSlots: {
    type: Array,
    default: () => []
  }
})

const missingLabels = computed(() =>
  props.missingSlots.map((slot) => {
    switch (slot) {
      case 'budgetYuan':
        return '预算'
      case 'rentMode':
        return '整租/合租'
      case 'locationName':
        return '区域'
      default:
        return slot
    }
  }).join('、')
)

const budgetText = computed(() => {
  if (!props.slots?.budgetYuan) {
    return '待补充'
  }
  return `${props.slots.budgetYuan} 元 / 月`
})

const rentModeText = computed(() => {
  if (props.slots?.rentMode === 'WHOLE') {
    return '整租'
  }
  if (props.slots?.rentMode === 'SHARED') {
    return '合租'
  }
  return '待补充'
})

const priorityText = computed(() => {
  switch (props.slots?.priority) {
    case 'PRICE':
      return '价格'
    case 'COMMUTE':
      return '通勤'
    case 'QUALITY':
      return '居住品质'
    default:
      return '待补充'
  }
})

const preferencesText = computed(() => {
  if (!props.slots?.preferences?.length) {
    return '待补充'
  }
  return props.slots.preferences.join('、')
})

function displayValue(value) {
  return value || '待补充'
}
</script>

<style scoped>
.summary-card {
  display: grid;
  gap: 14px;
}

.summary-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.summary-head h3 {
  margin: 0;
  font-size: 16px;
}

.summary-tip {
  color: var(--color-warning);
  font-size: 13px;
  font-weight: 600;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.summary-item {
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(255, 248, 239, 0.84);
}

.summary-item dt {
  color: var(--color-text-muted);
  font-size: 12px;
  margin-bottom: 6px;
}

.summary-item dd {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

@media (max-width: 768px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
