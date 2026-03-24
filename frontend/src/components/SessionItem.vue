<template>
  <div class="session card" @click="$emit('click')">
    <div class="avatar">{{ avatarText }}</div>
    <div class="main">
      <div class="top">
        <h3 class="name">{{ session.peerName || `用户${session.peerId}` }}</h3>
        <span class="time">{{ formatRelativeTime(session.updateTime) }}</span>
      </div>
      <p v-if="session.houseLabel || session.houseTitle" class="house">{{ session.houseLabel || session.houseTitle }}</p>
      <div class="bottom">
        <p class="last-msg">{{ session.lastMsgContent || '暂无消息' }}</p>
        <span v-if="Number(session.unreadCount || 0) > 0" class="badge">{{ session.unreadCount }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatRelativeTime } from '@/utils/format'

const props = defineProps({
  session: {
    type: Object,
    required: true
  }
})

defineEmits(['click'])

const avatarText = computed(() => {
  const text = props.session.peerName || `U${props.session.peerId || ''}`
  return text.slice(0, 1).toUpperCase()
})
</script>

<style scoped>
.session {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: #dbeafe;
  color: #1d4ed8;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}

.main {
  flex: 1;
  min-width: 0;
}

.top,
.bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.name {
  margin: 0;
  font-size: 15px;
  color: #111827;
}

.time {
  color: #9ca3af;
  font-size: 12px;
}

.house {
  margin: 4px 0 0;
  font-size: 12px;
  color: #2563eb;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.last-msg {
  margin: 2px 0 0;
  font-size: 13px;
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.badge {
  min-width: 18px;
  height: 18px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
}
</style>
