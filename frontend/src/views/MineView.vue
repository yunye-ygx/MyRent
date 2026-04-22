<template>
  <div class="mine-view">
    <section class="profile app-surface">
      <div class="avatar">{{ avatarText }}</div>
      <div class="profile-main">
        <p class="eyebrow">Profile</p>
        <div class="name-row">
          <h2 class="name" data-testid="mine-name">{{ displayName }}</h2>
          <span class="status-chip" data-testid="mine-status">{{ accountStatus }}</span>
        </div>
        <p class="phone" data-testid="mine-phone">{{ displayPhone }}</p>
        <p class="status-copy">{{ statusDescription }}</p>
      </div>
      <div class="profile-actions">
        <button class="ghost-btn" type="button" @click="goProfile">个人资料</button>
        <button class="primary-btn" type="button" @click="logout">退出登录</button>
      </div>
    </section>

    <p v-if="profileError" class="error-text">{{ profileError }}</p>

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
        当前个人资料已经接入真实用户数据，并支持修改名称。头像、生日等资料后续可以继续在这个入口扩展。
      </p>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchCurrentUser } from '@/api/user'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const profileError = ref('')

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

const displayName = computed(() => authStore.profile?.name || '未命名用户')
const displayPhone = computed(() => authStore.profile?.phone || '--')
const accountStatus = computed(() => (authStore.isLoggedIn ? '已登录' : '未登录'))
const statusDescription = computed(() =>
  authStore.isLoggedIn ? '当前账号状态正常，可继续查看和修改基础资料。' : '当前未登录。'
)

const avatarText = computed(() => {
  const name = displayName.value || 'U'
  return name.slice(0, 1).toUpperCase()
})

async function loadProfile() {
  profileError.value = ''
  try {
    const profile = await fetchCurrentUser()
    authStore.syncProfile({
      userId: profile.id,
      phone: profile.phone,
      name: profile.name
    })
  } catch (error) {
    profileError.value = error?.message || '用户资料加载失败'
  }
}

function goProfile() {
  router.push('/mine/profile')
}

function openModule(item) {
  if (item.key === 'profile') {
    goProfile()
    return
  }
  if (item.key === 'favorite') {
    router.push('/mine/favorites')
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

onMounted(() => {
  loadProfile()
})
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

.profile-main {
  min-width: 0;
}

.profile-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
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

.name-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.name {
  margin: 0;
  font-size: 30px;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(32, 120, 244, 0.12);
  color: var(--color-accent);
  font-size: 13px;
  font-weight: 600;
}

.phone,
.status-copy {
  margin: 10px 0 0;
  font-size: 14px;
  color: var(--color-text-muted);
}

.menu-section,
.mock-note {
  padding: 24px;
}

.section-title {
  margin: 0 0 12px;
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

.error-text {
  margin: 0;
  color: #dc2626;
  font-size: 14px;
}

@media (min-width: 1024px) {
  .profile {
    grid-template-columns: auto 1fr auto;
    align-items: center;
  }
}
</style>
