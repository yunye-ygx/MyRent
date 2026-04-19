<template>
  <section class="action-bar app-surface">
    <p class="eyebrow">Primary Actions</p>
    <button class="ghost-btn" :disabled="favoriteLoading" @click="$emit('favorite')">
      {{ favoriteButtonText }}
    </button>
    <button class="ghost-btn" @click="$emit('consult')">发起咨询</button>
    <button class="primary-btn" :disabled="lockLoading || !canSubmit" @click="$emit('deposit')">
      {{ lockLoading ? '提交中...' : '提交定金' }}
    </button>
  </section>
</template>

<script setup>
defineProps({
  favoriteLoading: {
    type: Boolean,
    default: false
  },
  favoriteButtonText: {
    type: String,
    default: '收藏'
  },
  lockLoading: {
    type: Boolean,
    default: false
  },
  canSubmit: {
    type: Boolean,
    default: true
  }
})

defineEmits(['favorite', 'consult', 'deposit'])
</script>

<style scoped>
.action-bar {
  display: grid;
  gap: 12px;
  padding: 20px;
}

.eyebrow {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}
</style>
