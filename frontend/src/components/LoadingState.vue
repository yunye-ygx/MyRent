<template>
  <div class="loading-state app-surface">
    <span class="dot" />
    <span>{{ text }}</span>
  </div>
</template>

<script setup>
defineProps({
  text: {
    type: String,
    default: '加载中...'
  }
})
</script>

<style scoped>
.loading-state {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  font-size: 14px;
  color: var(--color-text-muted);
}

.dot {
  width: 14px;
  height: 14px;
  border-radius: 999px;
  border: 2px solid var(--color-surface-strong);
  border-top-color: var(--color-accent);
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}
</style>
