<template>
  <div class="page mine-page">
    <section class="card profile">
      <div class="avatar">{{ avatarText }}</div>
      <div class="info">
        <h2 class="name">{{ authStore.profile?.name || '未命名用户' }}</h2>
        <p class="phone">{{ authStore.profile?.phone || '--' }}</p>
      </div>
      <button class="ghost-btn" @click="logout">退出</button>
    </section>

    <section class="card">
      <h3 class="section-title">功能入口</h3>
      <ul class="menu">
        <li v-for="item in menus" :key="item.key" class="menu-item" @click="openModule(item)">
          <span>{{ item.label }}</span>
          <span class="arrow">›</span>
        </li>
      </ul>
    </section>

    <section class="card mock">
      <h3 class="section-title">本期 mock 模块说明</h3>
      <p>学生认证、预约、收藏、浏览记录、合同订单等模块已预留入口，可逐步替换成真实接口。</p>
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
  { key: 'contract', label: '我的合同/订单' },
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
.mine-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.profile {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: #dbeafe;
  color: #1d4ed8;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 600;
}

.info {
  flex: 1;
}

.name {
  margin: 0;
  font-size: 18px;
}

.phone {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 13px;
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
  padding: 10px 4px;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
}

.menu-item:last-child {
  border-bottom: 0;
}

.arrow {
  color: #9ca3af;
}

.mock p {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
}
</style>
