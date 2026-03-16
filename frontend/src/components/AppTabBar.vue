<template>
  <nav class="tabbar">
    <button
      v-for="item in tabs"
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

const route = useRoute()
const router = useRouter()

const tabs = [
  { path: '/home', label: '首页', icon: '🏠' },
  { path: '/messages', label: '消息', icon: '💬' },
  { path: '/map', label: '找房', icon: '🗺️' },
  { path: '/mine', label: '我的', icon: '👤' }
]

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
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: #fff;
  border-top: 1px solid #e5e7eb;
  padding: 6px 0;
}

.tab-btn {
  border: 0;
  background: transparent;
  color: #6b7280;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 12px;
  gap: 2px;
  cursor: pointer;
}

.tab-btn.active {
  color: #2563eb;
}

.icon {
  font-size: 18px;
}
</style>
