<template>
  <section class="empty-state app-surface">
    <p class="eyebrow">No Data</p>
    <h3 class="title">{{ title }}</h3>
    <p v-if="description" class="description">{{ description }}</p>
    <button v-if="actionText" class="primary-btn action" @click="$emit('action')">{{ actionText }}</button>
  </section>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: '暂无数据'
  },
  description: {
    type: String,
    default: ''
  },
  actionText: {
    type: String,
    default: ''
  }
})

defineEmits(['action'])
</script>

<style scoped>
.empty-state {
  padding: 40px 24px;
  text-align: center;
}

.eyebrow {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.title {
  margin: 14px 0 0;
  font-size: 24px;
  color: var(--color-text);
}

.description {
  margin: 14px auto 0;
  max-width: 520px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text-muted);
}

.action {
  margin-top: 20px;
}
</style>
