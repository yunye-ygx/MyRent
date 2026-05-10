<template>
  <section class="summary-bar">
    <div class="summary-bar__avatar">
      <RoamMascotIcon size="mini" />
    </div>
    <div class="summary-bar__label">Roam 知道的</div>
    <div class="summary-bar__tags">
      <span v-for="tag in doneTags" :key="`d-${tag.slotKey}`" class="tag done">
        <span class="k">{{ tag.key }}</span>
        <span class="v">{{ tag.value }}</span>
      </span>
      <span v-for="tag in todoTags" :key="`t-${tag.key}`" class="tag todo">
        ? {{ tag.label }}待补充
      </span>
    </div>
    <div class="progress-ring" :style="ringStyle">
      <div class="progress-ring__inner">{{ knownCount }}/{{ totalCount }}</div>
    </div>
    <p v-if="missingSlots.length" class="missing-hint">
      还差 {{ missingSlots.length }} 项信息，Roam 就可以去筛房源啦 ～
    </p>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'

const props = defineProps({
  slots: { type: Object, default: () => ({}) },
  missingSlots: { type: Array, default: () => [] }
})

const SLOT_ORDER = ['city', 'locationName', 'budgetYuan', 'rentMode', 'priority', 'preferences']
const SLOT_LABEL = {
  city: '城市',
  locationName: '区域',
  budgetYuan: '预算',
  rentMode: '方式',
  priority: '优先',
  preferences: '偏好'
}

function rentModeText(v) {
  if (v === 'WHOLE') return '整租'
  if (v === 'SHARED') return '合租'
  return v || ''
}
function priorityText(v) {
  if (v === 'PRICE') return '价格'
  if (v === 'COMMUTE') return '通勤'
  if (v === 'QUALITY') return '品质'
  return v || ''
}
function preferencesText(list) {
  if (!list?.length) return ''
  const map = { nearSubway: '近地铁', balcony: '阳台', quiet: '安静' }
  return list.map((p) => map[p] || p).join('·')
}
function budgetText(v) {
  if (v == null || v === '') return ''
  return `${v}/月`
}

function readValue(key, raw) {
  switch (key) {
    case 'budgetYuan': return budgetText(raw)
    case 'rentMode': return rentModeText(raw)
    case 'priority': return priorityText(raw)
    case 'preferences': return preferencesText(raw)
    default: return raw || ''
  }
}

const doneTags = computed(() =>
  SLOT_ORDER
    .filter((k) => !props.missingSlots.includes(k))
    .map((k) => {
      const value = readValue(k, props.slots?.[k])
      return { slotKey: k, key: SLOT_LABEL[k], value: value || '—' }
    })
)

const todoTags = computed(() =>
  props.missingSlots.map((k) => ({ key: k, label: SLOT_LABEL[k] || k }))
)

const knownCount = computed(() => doneTags.value.length)
const totalCount = SLOT_ORDER.length

const ringStyle = computed(() => {
  const ratio = totalCount === 0 ? 0 : knownCount.value / totalCount
  const deg = Math.round(ratio * 360)
  return {
    background: `conic-gradient(#7aa3e0 0deg ${deg}deg, #f0f5ff ${deg}deg 360deg)`
  }
})
</script>

<style scoped>
.summary-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  background: #ffffff;
  border-radius: 18px;
  border: 1px solid rgba(184, 200, 224, 0.3);
  box-shadow: 0 4px 10px rgba(100, 130, 200, 0.05);
  flex-wrap: wrap;
}

.summary-bar__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #f0f5ff;
  display: grid;
  place-items: center;
  border: 1px solid rgba(184, 200, 224, 0.4);
  flex-shrink: 0;
}
.summary-bar__avatar .roam-mascot-icon {
  width: 22px;
  height: 22px;
}

.summary-bar__label {
  font-size: 12.5px;
  color: #5b6a8a;
  font-weight: 700;
  flex-shrink: 0;
  white-space: nowrap;
}

.summary-bar__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1;
  min-width: 200px;
}

.tag {
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.tag.done {
  background: #f0f5ff;
  color: #3b4a6b;
  border: 1px solid rgba(140, 180, 240, 0.35);
}
.tag.todo {
  background: #fff8e6;
  color: #a37920;
  border: 1.2px dashed #e8c488;
}
.tag .k { opacity: 0.65; font-weight: 600; font-size: 10px; }

.progress-ring {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.progress-ring__inner {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #ffffff;
  display: grid;
  place-items: center;
  font-size: 10.5px;
  font-weight: 800;
  color: #3b4a6b;
}

.missing-hint {
  margin: 0;
  flex-basis: 100%;
  padding: 8px 12px;
  border-radius: 12px;
  background: #fff8e6;
  color: #a37920;
  font-size: 12.5px;
  line-height: 1.5;
}

@media (max-width: 640px) {
  .summary-bar {
    gap: 10px;
    padding: 10px 12px;
  }
  .summary-bar__tags { min-width: 0; }
}
</style>
