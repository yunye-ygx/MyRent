<template>
  <section class="results-hero app-surface">
    <div>
      <p class="eyebrow">Results</p>
      <h1 class="title">{{ title }}</h1>
      <p class="tip">{{ resultTip || defaultTip }}</p>
    </div>
    <div class="search-box">
      <div class="search-row">
        <input
          v-model.trim="keyword"
          class="input"
          placeholder="输入地点，切换到附近房源"
          @keyup.enter="emitSearch"
        />
        <button class="primary-btn" @click="emitSearch">搜索</button>
      </div>
      <button v-if="isNearbyMode" class="ghost-btn reset-btn" @click="$emit('reset')">回到精选推荐</button>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  resultTip: {
    type: String,
    default: ''
  },
  isNearbyMode: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['search', 'reset'])
const keyword = ref('')

const defaultTip = computed(() => (
  props.isNearbyMode
    ? '已切换到附近房源模式。'
    : '默认展示热门精选，可继续按地点缩小范围。'
))

function emitSearch() {
  emit('search', keyword.value)
}
</script>

<style scoped>
.results-hero {
  display: grid;
  gap: 18px;
  padding: 24px;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.title {
  margin: 0;
  font-size: clamp(28px, 5vw, 42px);
  color: var(--color-text);
}

.tip {
  margin: 10px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text-muted);
}

.search-box {
  display: grid;
  gap: 12px;
}

.search-row {
  display: grid;
  gap: 10px;
}

.reset-btn {
  justify-self: start;
}

@media (min-width: 1024px) {
  .results-hero {
    grid-template-columns: 1fr auto;
    align-items: end;
  }

  .search-row {
    grid-template-columns: minmax(280px, 1fr) auto;
  }
}
</style>
