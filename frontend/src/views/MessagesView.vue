<template>
  <div class="page msg-page">
    <section class="card topbar">
      <h2 class="section-title">消息</h2>
      <button class="ghost-btn" @click="loadSessions">刷新</button>
    </section>

    <LoadingState v-if="loading && !sessions.length" text="会话加载中..." />
    <p v-if="error" class="error-text">{{ error }}</p>

    <SessionItem
      v-for="session in sessions"
      :key="session.sessionId"
      :session="session"
      @click="goChat(session)"
    />

    <EmptyState
      v-if="!loading && !sessions.length"
      title="暂无会话"
      description="你可以先从房源详情页进入咨询流程"
      action-text="去首页"
      @action="router.push('/home')"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchSessionPage } from '@/api/chat'
import { fetchUserById } from '@/api/user'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import SessionItem from '@/components/SessionItem.vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()

const loading = ref(false)
const error = ref('')
const sessions = ref([])
const userCache = new Map()

async function fetchUserName(userId) {
  if (!userId) {
    return ''
  }
  if (userCache.has(userId)) {
    return userCache.get(userId)
  }
  try {
    const user = await fetchUserById(userId)
    const name = user?.name || `用户${userId}`
    userCache.set(userId, name)
    return name
  } catch {
    const fallback = `用户${userId}`
    userCache.set(userId, fallback)
    return fallback
  }
}

async function loadSessions() {
  loading.value = true
  error.value = ''
  try {
    const page = await fetchSessionPage({ current: 1, size: 50 })
    const records = page?.records || []
    const currentUserId = authStore.userId
    const enriched = await Promise.all(
      records.map(async (session) => {
        const peerId = String(session.userId1) === String(currentUserId) ? session.userId2 : session.userId1
        const peerName = await fetchUserName(peerId)
        return {
          ...session,
          peerId,
          peerName,
          unreadCount: 0
        }
      })
    )
    sessions.value = enriched
  } catch (err) {
    error.value = err?.message || '会话加载失败'
    sessions.value = []
  } finally {
    loading.value = false
  }
}

function goChat(session) {
  router.push({
    path: `/chat/${session.sessionId}`,
    query: {
      peerId: String(session.peerId),
      peerName: session.peerName || ''
    }
  })
}

onMounted(() => {
  loadSessions()
})
</script>

<style scoped>
.msg-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-title {
  margin: 0;
}
</style>
