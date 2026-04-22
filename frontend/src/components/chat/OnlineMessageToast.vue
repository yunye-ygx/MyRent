<template>
  <button class="toast app-surface" @click="$emit('click')">
    <strong>{{ toast.senderName }} sent a new message</strong>
    <p>{{ toast.content }}</p>
  </button>
</template>

<script setup>
defineProps({
  toast: {
    type: Object,
    required: true
  }
})

defineEmits(['click'])
</script>

<style scoped>
.toast {
  width: min(360px, calc(100vw - 32px));
  border: 0;
  cursor: pointer;
  display: grid;
  gap: 8px;
  padding: 16px 18px;
  text-align: left;
  background: rgba(255, 252, 247, 0.96);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.12);
}

.toast strong {
  color: var(--color-text);
  font-size: 14px;
}

.toast p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.6;
}
</style>
