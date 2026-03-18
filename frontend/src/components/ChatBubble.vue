<template>
  <div class="bubble-row" :class="{ self: isSelf }">
    <div class="bubble-meta">{{ displayName }}</div>
    <div class="bubble" :class="{ self: isSelf }">
      {{ message.content }}
    </div>
    <div class="time">{{ formatDateTime(message.createTime) }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatDateTime } from '@/utils/format'

const props = defineProps({
  message: {
    type: Object,
    required: true
  },
  currentUserId: {
    type: [Number, String],
    required: true
  }
})

const isSelf = computed(() => String(props.message.senderId) === String(props.currentUserId))

const displayName = computed(() => {
  if (isSelf.value) {
    return '我'
  }
  return props.message.senderName || `用户${props.message.senderId}`
})
</script>

<style scoped>
.bubble-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin: 10px 0;
}

.bubble-row.self {
  align-items: flex-end;
}

.bubble-meta {
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 4px;
}

.bubble {
  max-width: 78%;
  background: #fff;
  padding: 10px 12px;
  border-radius: 12px;
  color: #111827;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
  word-break: break-word;
  white-space: pre-wrap;
}

.bubble.self {
  background: #2563eb;
  color: #fff;
}

.time {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 2px;
}
</style>
