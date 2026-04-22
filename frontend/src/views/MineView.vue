<template>
  <div class="mine-view">
    <section class="profile app-surface">
      <div class="avatar">{{ avatarText }}</div>
      <div>
        <p class="eyebrow">Profile</p>
        <h2 class="name">{{ authStore.profile?.name || '未命名用户' }}</h2>
        <p class="phone">{{ authStore.profile?.phone || '--' }}</p>
      </div>
      <button class="primary-btn" @click="logout">退出登录</button>
    </section>

    <section class="menu-section app-surface">
      <h3 class="section-title">功能入口</h3>
      <ul class="menu">
        <li v-for="item in menus" :key="item.key" class="menu-item" @click="openModule(item)">
          <span>{{ item.label }}</span>
          <span class="arrow">→</span>
        </li>
      </ul>
    </section>

    <section class="mock-note app-surface">
      <p class="eyebrow">Phase 1 Note</p>
      <p class="copy">
        学生认证、预约、收藏、浏览记录和订单模块都保留入口，后续可以继续替换成真实接口和更完整的状态流转。
      </p>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const menus = [
  { key: 'profile', label: '个人资料' },
  { key: 'verify', label: '学生认证 / 应届生认证' },
  { key: 'reservation', label: '我的预约' },
  { key: 'favorite', label: '我的收藏' },
  { key: 'history', label: '浏览记录' },
  { key: 'consult', label: '我的咨询' },
  { key: 'contract', label: '我的合同 / 订单' },
  { key: 'setting', label: '设置' },
  { key: 'support', label: '客服与帮助' },
  { key: 'feedback', label: '意见反馈' }
]

const avatarText = computed(() => {
  const name = authStore.profile?.name || 'U'
  return name.slice(0, 1).toUpperCase()
})

function openModule(item) {
  if (item.key === 'favorite') {
    router.push('/mine/favorites')
    return
  }
  if (item.key === 'history') {
    router.push('/mine/history')
    return
  }
  if (item.key === 'consult') {
    router.push('/mine/consults')
    return
  }
  if (item.key === 'contract') {
    router.push('/mine/orders')
    return
  }

  router.push(`/placeholder/${item.key}?title=${encodeURIComponent(item.label)}`)
}

function logout() {
  authStore.logout()
  router.replace('/login')
}
</script>

<style scoped>
.mine-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.profile {
  display: grid;
  gap: 16px;
  padding: 24px;
}

.avatar {
  width: 68px;
  height: 68px;
  border-radius: 999px;
  background: var(--color-surface-strong);
  color: var(--color-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 600;
}

.eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.name {
  margin: 0;
  font-size: 30px;
}

.phone {
  margin: 10px 0 0;
  font-size: 14px;
  color: var(--color-text-muted);
}

.menu-section,
.mock-note {
  padding: 24px;
}

.menu {
  margin: 0;
  padding: 0;
  list-style: none;
}

.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-border);
  cursor: pointer;
}

.menu-item:last-child {
  border-bottom: 0;
}

.arrow {
  color: var(--color-text-muted);
}

.copy {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--color-text-muted);
}

@media (min-width: 1024px) {
  .profile {
    grid-template-columns: auto 1fr auto;
    align-items: center;
  }
}
</style>
