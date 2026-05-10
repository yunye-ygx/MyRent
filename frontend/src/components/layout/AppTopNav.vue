<template>
  <header class="app-top-nav app-surface hidden lg:grid">
    <div class="brand">
      <div class="brand-mark">青</div>
      <div class="brand-copy">
        <div class="brand-name">青年租房</div>
        <div class="brand-subtitle">城市租住消息台</div>
      </div>
    </div>

    <nav class="nav-list">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        :data-nav="item.to"
        class="nav-link"
        :class="{
          'is-active': currentPath.startsWith(item.to),
          'is-featured': item.featured
        }"
      >
        <span v-if="item.featured" class="featured-shell">
          <span class="featured-core">
            <RoamMascotIcon size="tiny" />
          </span>
          <span class="featured-label">{{ item.label }}</span>
        </span>
        <template v-else>
          <span>{{ item.label }}</span>
          <span v-if="isMessageItem(item) && messageCenterStore.totalUnread > 0" class="nav-badge">
            {{ messageCenterStore.totalUnread }}
          </span>
        </template>
      </RouterLink>
    </nav>

    <div class="nav-actions">
      <label class="action-chip city-chip">
        <select
          class="city-select"
          :value="activeCity"
          aria-label="切换城市"
          @change="handleCityChange"
        >
          <option v-for="city in HOT_CITY_OPTIONS" :key="city.name" :value="city.name">
            {{ city.name }}
          </option>
        </select>
      </label>
      <RouterLink class="profile-chip" to="/mine">
        <span class="profile-avatar">{{ avatarText }}</span>
        <span class="profile-name">{{ displayName }}</span>
      </RouterLink>
    </div>
  </header>
</template>

<script setup>
import { computed, toRefs } from 'vue'
import { HOT_CITY_OPTIONS } from '@/config/cityFilters'
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { useMessageCenterStore } from '@/stores/messageCenter'

const props = defineProps({
  items: {
    type: Array,
    required: true
  },
  currentPath: {
    type: String,
    required: true
  }
})

const { items, currentPath } = toRefs(props)

const authStore = useAuthStore()
const messageCenterStore = useMessageCenterStore()

const activeCity = computed(() => authStore.currentCity || authStore.profile?.city || HOT_CITY_OPTIONS[0].name)
const displayName = computed(() => authStore.profile?.name || '登录 / 注册')
const avatarText = computed(() => {
  const name = String(displayName.value || '').trim()
  return name ? name.slice(0, 1) : '我'
})

function isMessageItem(item) {
  return item?.to === '/messages'
}

function handleCityChange(event) {
  authStore.switchCity(event.target.value)
}
</script>

<style scoped>
.app-top-nav {
  width: 100%;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 28px;
  padding: 16px clamp(18px, 2vw, 28px);
  border-radius: 24px;
  border: 1px solid rgba(229, 231, 235, 0.92);
  box-shadow: 0 16px 36px rgba(148, 163, 184, 0.12);
}

.brand,
.brand-copy,
.nav-list,
.nav-actions,
.profile-chip,
.nav-link {
  display: flex;
  align-items: center;
}

.brand {
  gap: 12px;
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #98b47a, #5e7f42);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
}

.brand-copy {
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.brand-name {
  color: #365032;
  font-size: 18px;
  font-weight: 700;
}

.brand-subtitle {
  color: #98a1ab;
  font-size: 12px;
}

.nav-list {
  justify-content: center;
  gap: 8px;
  min-height: 72px;
}

.nav-link {
  gap: 8px;
  position: relative;
  border-radius: 999px;
  padding: 10px 16px;
  color: #667085;
  font-size: 14px;
  font-weight: 600;
  transition: color 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

.nav-link:hover {
  background: #f7f7f3;
}

.nav-link.is-active {
  color: #2f4b28;
  background: #f3f7ed;
}

.nav-link.is-active::after {
  content: '';
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 6px;
  height: 3px;
  border-radius: 999px;
  background: #8da56f;
}

.nav-link.is-featured {
  padding: 0;
  background: transparent;
  transform: translateY(-10px);
}

.nav-link.is-featured:hover {
  background: transparent;
  transform: translateY(-12px);
}

.nav-link.is-featured::after {
  display: none;
}

.featured-shell {
  display: inline-grid;
  justify-items: center;
  gap: 6px;
  background: transparent;
  border: 0;
  box-shadow: none;
  padding: 0;
}

.featured-core {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #ffffff;
  box-shadow:
    0 10px 24px rgba(80, 120, 200, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  isolation: isolate;
}

.featured-core::before {
  content: '';
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(184, 216, 255, 0.5), transparent 70%);
  z-index: -1;
}

.featured-core::after {
  content: '';
  position: absolute;
  inset: -2px;
  border-radius: 50%;
  border: 2px dashed rgba(140, 180, 240, 0.55);
  animation: roam-badge-spin 18s linear infinite;
}

.featured-core .roam-mascot-icon {
  width: 44px;
  height: 44px;
}

.featured-label {
  font-size: 13px;
  font-weight: 800;
  color: #3b4a6b;
}

.nav-link.is-featured.is-active .featured-core {
  box-shadow:
    0 12px 28px rgba(80, 120, 200, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

@keyframes roam-badge-spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .featured-core::after { animation: none; }
}

.nav-badge {
  min-width: 18px;
  height: 18px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
  background: #ff6f3c;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.nav-actions {
  gap: 10px;
}

.action-chip,
.profile-chip {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  border: 1px solid #ece7db;
  background: #fffdf8;
  color: #5f6670;
  border-radius: 999px;
  padding: 8px 14px;
}

.city-chip {
  padding-right: 10px;
}

.city-select {
  border: 0;
  outline: 0;
  background: transparent;
  color: inherit;
}

.profile-chip {
  gap: 10px;
  padding-right: 12px;
}

.profile-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #d3b79d;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.profile-name {
  font-size: 14px;
  font-weight: 600;
}

@media (max-width: 1280px) {
  .app-top-nav {
    gap: 18px;
  }
}
</style>
