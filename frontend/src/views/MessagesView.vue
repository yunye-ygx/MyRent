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
import { useRouter } from 'vue-router'
import { fetchSessionPage } from '@/api/chat'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import SessionItem from '@/components/SessionItem.vue'
import { useChatSessionList } from '@/composables/useChatSessionList'

const router = useRouter()
const { loading, error, sessions, loadSessions } = useChatSessionList(fetchSessionPage)

function goChat(session) {
  router.push({
    path: `/chat/${session.sessionId}`,
    query: {
      peerId: String(session.peerId || ''),
      peerName: session.peerName || '',
      houseId: session.houseId ? String(session.houseId) : ''
    }
  })
}
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
