<template>
  <article class="session app-surface" @click="$emit('click')">
    <div class="avatar">{{ avatarText }}</div>
    <div class="main">
      <div class="top">
        <h3 class="name">{{ session.peerName || `用户 ${session.peerId}` }}</h3>
        <span class="time">{{ formatRelativeTime(session.updateTime) }}</span>
      </div>
      <p v-if="session.houseLabel || session.houseTitle" class="house">{{ session.houseLabel || session.houseTitle }}</p>
      <div class="bottom">
        <p class="last-msg">{{ session.lastMsgContent || '暂无消息' }}</p>
        <span v-if="Number(session.unreadCount || 0) > 0" class="badge">{{ session.unreadCount }}</span>
      </div>
    </div>
  </article>
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
  gap: 14px;
  padding: 16px;
  cursor: pointer;
}

.avatar {
  width: 46px;
  height: 46px;
  border-radius: 999px;
  background: var(--color-surface-strong);
  color: var(--color-accent);
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
  font-size: 16px;
  color: var(--color-text);
}

.time {
  color: var(--color-text-muted);
  font-size: 12px;
}

.house {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--color-warning);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.last-msg {
  margin: 4px 0 0;
  font-size: 14px;
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.badge {
  min-width: 20px;
  height: 20px;
  border-radius: 999px;
  background: var(--color-danger);
  color: #fff;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
}
</style>
