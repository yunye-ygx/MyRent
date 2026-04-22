<template>
  <header class="app-surface hidden items-center justify-between px-6 py-4 lg:flex">
    <div class="text-sm font-medium app-muted">我的租房</div>
    <nav class="flex items-center gap-2">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        :data-nav="item.to"
        class="nav-link rounded-full px-4 py-2 text-sm transition"
        :class="currentPath.startsWith(item.to)
          ? 'is-active bg-[var(--color-accent)] text-[var(--color-accent-contrast)]'
          : 'app-muted hover:bg-[var(--color-surface-strong)]'"
      >
        <span>{{ item.label }}</span>
        <span v-if="isMessageItem(item) && messageCenterStore.totalUnread > 0" class="nav-badge">
          {{ messageCenterStore.totalUnread }}
        </span>
      </RouterLink>
    </nav>
  </header>
</template>

<script setup>
import { useMessageCenterStore } from '@/stores/messageCenter'

defineProps({
  items: {
    type: Array,
    required: true
  },
  currentPath: {
    type: String,
    required: true
  }
})

const messageCenterStore = useMessageCenterStore()

function isMessageItem(item) {
  return item?.to === '/messages'
}
</script>

<style scoped>
.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.nav-badge {
  min-width: 20px;
  height: 20px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  background: rgba(220, 38, 38, 0.92);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.is-active .nav-badge {
  background: rgba(255, 255, 255, 0.22);
}
</style>
