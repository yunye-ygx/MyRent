<template>
  <nav class="tabbar grid">
    <button
      v-for="item in mobileTabItems"
      :key="item.path"
      class="tab-btn"
      :class="{ active: isActive(item.path) }"
      @click="go(item.path)"
    >
      <span class="icon">{{ item.icon }}</span>
      <span>{{ item.label }}</span>
    </button>
  </nav>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { mobileTabItems } from '@/design/site'

const route = useRoute()
const router = useRouter()

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
  grid-template-columns: repeat(4, 1fr);
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
</style>
