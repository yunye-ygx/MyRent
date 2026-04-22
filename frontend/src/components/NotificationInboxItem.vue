<template>
  <article class="notification app-surface" :class="{ unread: Number(item.isRead || 0) === 0 }" @click="$emit('click')">
    <div class="head">
      <h3>{{ item.title }}</h3>
      <span v-if="Number(item.isRead || 0) === 0" class="badge">New</span>
    </div>
    <p class="copy">{{ item.content }}</p>
  </article>
</template>

<script setup>
defineProps({
  item: {
    type: Object,
    required: true
  }
})

defineEmits(['click'])
</script>

<style scoped>
.notification {
  cursor: pointer;
  padding: 18px 20px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.notification:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.08);
}

.notification.unread {
  border: 1px solid rgba(15, 118, 110, 0.18);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.head h3 {
  margin: 0;
  font-size: 16px;
  color: var(--color-text);
}

.badge {
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.12);
  color: #0f766e;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
}

.copy {
  margin: 10px 0 0;
  color: var(--color-text-muted);
  line-height: 1.7;
}
</style>
