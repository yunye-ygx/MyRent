<template>
  <button class="history-card app-surface" type="button" data-test="history-card" @click="$emit('select', item.houseId)">
    <img class="cover" :src="cover" :alt="`house-${item.houseId}`" />
    <p class="price">
      {{ formatPrice(item.price) }}
      <span class="price-unit">/ month</span>
    </p>
  </button>
</template>

<script setup>
import { computed } from 'vue'
import { formatPrice } from '@/utils/format'

const props = defineProps({
  item: {
    type: Object,
    required: true
  }
})

defineEmits(['select'])

const cover = computed(() => props.item.cover || `https://picsum.photos/seed/history-${props.item.houseId}/480/320`)
</script>

<style scoped>
.history-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  border: 0;
  padding: 14px;
  text-align: left;
  cursor: pointer;
}

.cover {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  border-radius: 18px;
  background: var(--color-surface-strong);
}

.price {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--color-accent);
}

.price-unit {
  margin-left: 6px;
  font-size: 13px;
  font-weight: 400;
  color: var(--color-text-muted);
}
</style>
