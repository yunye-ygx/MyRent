<template>
  <div class="page mine-sub-page">
    <section class="card topbar">
      <button class="ghost-btn" @click="router.back()">返回</button>
      <h2 class="section-title">我的咨询</h2>
      <button class="ghost-btn" @click="loadSessions">刷新</button>
    </section>

    <LoadingState v-if="loading && !sessions.length" text="咨询加载中..." />
    <p v-if="error" class="error-text">{{ error }}</p>

    <SessionItem
      v-for="session in sessions"
      :key="session.sessionId"
      :session="session"
      @click="goChat(session)"
    />

    <EmptyState
      v-if="!loading && !sessions.length"
      title="暂无咨询记录"
      description="你可以先从房源详情页发起咨询"
      action-text="去首页"
      @action="router.push('/home')"
    />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { fetchMyConsultPage } from '@/api/chat'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import SessionItem from '@/components/SessionItem.vue'
import { useChatSessionList } from '@/composables/useChatSessionList'

const router = useRouter()
const { loading, error, sessions, loadSessions } = useChatSessionList(fetchMyConsultPage)

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
.mine-sub-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-title {
  margin: 0;
}
</style>
