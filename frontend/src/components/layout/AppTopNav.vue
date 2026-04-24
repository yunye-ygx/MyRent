<template>
  <header class="app-top-nav app-surface hidden lg:flex" :class="{ 'app-top-nav--full': fullBleed }">
    <div class="brand-block">
      <div class="brand-mark">
        <span class="brand-mark-dot"></span>
      </div>
      <div class="brand-text">青禾租房</div>
    </div>

    <nav class="nav-list">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        :data-nav="item.to"
        class="nav-link"
        :class="{ 'is-active': currentPath.startsWith(item.to) }"
      >
        <span>{{ item.label }}</span>
        <span v-if="isMessageItem(item) && messageCenterStore.totalUnread > 0" class="nav-badge">
          {{ messageCenterStore.totalUnread }}
        </span>
      </RouterLink>
    </nav>

    <div class="nav-actions">
      <label class="city-pill">
        <select class="city-select" :value="activeCity" aria-label="切换城市">
          <option>{{ activeCity }}</option>
        </select>
      </label>
      <RouterLink class="account-pill" to="/mine">
        {{ authStore.profile?.name || '登录 / 注册' }}
      </RouterLink>
    </div>
  </header>
</template>

<script setup>
import { computed, toRefs } from 'vue'
import { useMessageCenterStore } from '@/stores/messageCenter'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({
  items: {
    type: Array,
    required: true
  },
  currentPath: {
    type: String,
    required: true
  },
  fullBleed: {
    type: Boolean,
    default: false
  }
})

const { items, currentPath } = toRefs(props)

const authStore = useAuthStore()
const messageCenterStore = useMessageCenterStore()

const activeCity = computed(() => authStore.profile?.city || '南京')

function isMessageItem(item) {
  return item?.to === '/messages'
}
</script>

<style scoped>
.app-top-nav {
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 22px;
  border: 1px solid rgba(186, 172, 148, 0.16);
}

.app-top-nav--full {
  border-radius: 0;
  border-left: 0;
  border-right: 0;
  box-shadow: 0 8px 24px rgba(49, 33, 23, 0.05);
  padding-left: clamp(24px, 7vw, 160px);
  padding-right: clamp(24px, 7vw, 160px);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 160px;
}

.brand-mark {
  position: relative;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: linear-gradient(180deg, #f0f4ea, #dfead6);
}

.brand-mark::before,
.brand-mark::after {
  content: '';
  position: absolute;
  background: #5f7f4c;
}

.brand-mark::before {
  left: 8px;
  bottom: 7px;
  width: 14px;
  height: 10px;
  border-radius: 2px;
}

.brand-mark::after {
  left: 6px;
  top: 7px;
  width: 18px;
  height: 12px;
  clip-path: polygon(50% 0, 100% 45%, 100% 100%, 0 100%, 0 45%);
}

.brand-mark-dot {
  position: absolute;
  top: 6px;
  right: 5px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #7fa165;
}

.brand-text {
  font-size: 22px;
  font-weight: 700;
  color: #325235;
  letter-spacing: 0.02em;
}

.nav-list {
  display: flex;
  align-items: center;
  gap: 26px;
}

.nav-link {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 0;
  color: #70695f;
  font-size: 14px;
  font-weight: 600;
}

.nav-link::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 0;
  height: 3px;
  border-radius: 999px;
  background: #7e9d63;
  transform: translateX(-50%);
  transition: width 0.2s ease;
}

.nav-link.is-active {
  color: #39352f;
}

.nav-link.is-active::after {
  width: 24px;
}

.nav-badge {
  min-width: 18px;
  height: 18px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
  background: #ff7d42;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 210px;
  justify-content: flex-end;
}

.city-pill,
.account-pill {
  display: inline-flex;
  align-items: center;
  min-height: 38px;
  border-radius: 999px;
  border: 1px solid rgba(196, 187, 173, 0.55);
  background: rgba(255, 255, 255, 0.78);
  color: #695f54;
}

.city-pill {
  padding: 0 10px;
}

.city-select {
  border: 0;
  outline: 0;
  background: transparent;
  color: inherit;
}

.account-pill {
  padding: 0 16px;
  font-size: 13px;
  font-weight: 600;
}
</style>
