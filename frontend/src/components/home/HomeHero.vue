<template>
  <section class="hero app-surface">
    <div class="search-card">
      <div class="heading-row">
        <div>
          <p class="eyebrow">Search</p>
          <h1 class="title">开始找房</h1>
        </div>
        <p class="tip">{{ tipText }}</p>
      </div>

      <div class="primary-row">
        <input
          v-model.trim="keyword"
          class="input"
          placeholder="区域 / 地点 / 地铁站"
          @keyup.enter="emitSearch"
        />
        <button data-test="search-submit" class="primary-btn" @click="emitSearch">
          开始找房
        </button>
      </div>

      <div class="filter-row">
        <button class="ghost-chip" type="button" @click="$emit('preset', 'budget')">预算</button>
        <button class="ghost-chip" type="button" @click="$emit('preset', 'rentalType')">整租 / 合租</button>
        <button class="ghost-chip" type="button" @click="$emit('preset', 'commute')">通勤 / 地铁</button>
      </div>

      <div class="preset-row">
        <button class="preset-chip" type="button" @click="$emit('search', '近地铁')">近地铁</button>
        <button class="preset-chip" type="button" @click="$emit('search', '低总价')">低总价</button>
        <button class="preset-chip" type="button" @click="$emit('search', '整租优先')">整租优先</button>
        <button class="preset-chip" type="button" @click="$emit('search', '新上房源')">新上房源</button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  resultTip: {
    type: String,
    default: ''
  },
  isNearbyMode: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['search', 'preset'])
const keyword = ref('')

const tipText = computed(() => {
  if (props.resultTip) {
    return props.resultTip
  }
  if (props.isNearbyMode) {
    return '已切换到附近搜索，可以继续缩小地点范围。'
  }
  return '输入地点、预算和通勤偏好，快速开始找房。'
})

function emitSearch() {
  emit('search', keyword.value)
}
</script>

<style scoped>
.hero {
  padding: 24px;
}

.search-card {
  display: grid;
  gap: 18px;
  border: 1px solid var(--color-border);
  border-radius: 28px;
  padding: 24px;
  background:
    radial-gradient(circle at top right, rgba(68, 107, 85, 0.12), transparent 36%),
    rgba(255, 255, 255, 0.7);
}

.heading-row {
  display: grid;
  gap: 12px;
}

.eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.title {
  margin: 0;
  font-size: clamp(32px, 5vw, 52px);
  line-height: 1.05;
  color: var(--color-text);
}

.tip {
  margin: 0;
  max-width: 420px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text-muted);
}

.primary-row {
  display: grid;
  gap: 10px;
}

.filter-row,
.preset-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.ghost-chip,
.preset-chip {
  border: 0;
  cursor: pointer;
}

.ghost-chip {
  border-radius: 999px;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--color-text);
}

.preset-chip {
  border-radius: 999px;
  padding: 10px 14px;
  background: rgba(68, 107, 85, 0.1);
  color: var(--color-accent);
}

@media (min-width: 900px) {
  .hero {
    padding: 32px;
  }

  .search-card {
    gap: 22px;
    padding: 32px;
  }

  .heading-row {
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: end;
  }

  .primary-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }
}
</style>
