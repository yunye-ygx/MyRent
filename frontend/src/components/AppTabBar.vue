<template>
  <nav class="tabbar grid" :style="{ gridTemplateColumns: `repeat(${mobileTabItems.length}, 1fr)` }">
    <button
      v-for="item in mobileTabItems"
      :key="item.path"
      class="tab-btn"
      :class="{ active: isActive(item.path), featured: item.featured }"
      @click="go(item.path)"
    >
      <span v-if="item.icon === 'dog'" class="icon icon-roam">
        <RoamMascotIcon size="tiny" />
      </span>
      <span v-else class="icon">{{ item.icon }}</span>
      <span class="label-row">
        <span>{{ item.label }}</span>
        <span v-if="item.path === '/messages' && messageCenterStore.totalUnread > 0" class="nav-badge">
          {{ messageCenterStore.totalUnread }}
        </span>
      </span>
    </button>
  </nav>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'
import { mobileTabItems } from '@/design/site'
import { useMessageCenterStore } from '@/stores/messageCenter'

const route = useRoute()
const router = useRouter()
const messageCenterStore = useMessageCenterStore()

function isActive(path) {
  return route.path.startsWith(path)
}

function go(path) {
  if (route.path === path) {
    return
  }
  router.push(path)
}
</script>

<style scoped>
.tabbar {
  gap: 8px;
  padding: 10px 14px calc(10px + env(safe-area-inset-bottom, 0px));
  background: rgba(255, 253, 249, 0.94);
  border-top: 1px solid var(--color-border);
  backdrop-filter: blur(18px);
}

.tab-btn {
  border: 0;
  border-radius: 16px;
  background: transparent;
  color: var(--color-text-muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 12px;
  gap: 4px;
  padding: 8px 0;
  cursor: pointer;
}

.tab-btn.active {
  background: var(--color-surface-strong);
  color: var(--color-accent);
}

.icon {
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.label-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.nav-badge {
  min-width: 18px;
  height: 18px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
  background: rgba(220, 38, 38, 0.92);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.tab-btn.featured {
  position: relative;
  overflow: visible;
}

.tab-btn.featured .icon-roam {
  position: relative;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #ffffff;
  display: grid;
  place-items: center;
  box-shadow:
    0 6px 14px rgba(80, 120, 200, 0.22),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  margin-top: -14px;
  isolation: isolate;
}

.tab-btn.featured .icon-roam::before {
  content: '';
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(184, 216, 255, 0.5), transparent 70%);
  z-index: -1;
}

.tab-btn.featured .icon-roam .roam-mascot-icon {
  width: 30px;
  height: 30px;
}

.tab-btn.featured.active {
  background: transparent;
  color: #3b4a6b;
}

.tab-btn.featured.active .icon-roam {
  box-shadow:
    0 8px 18px rgba(80, 120, 200, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
}
</style>
