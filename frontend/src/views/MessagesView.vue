<template>
  <div class="messages-view">
    <section class="intro app-surface">
      <div>
        <p class="eyebrow">Inbox</p>
        <h1 class="title">消息与咨询</h1>
        <p class="description">这里保留看房咨询的主链路，方便从房源详情直接回到会话。</p>
      </div>
      <button class="ghost-btn" @click="loadSessions">刷新列表</button>
    </section>

    <LoadingState v-if="loading && !sessions.length" text="正在加载会话..." />

    <section v-else-if="sessions.length" class="session-list">
      <SessionItem
        v-for="session in sessions"
        :key="session.sessionId"
        :session="session"
        @click="goChat(session)"
      />
    </section>

    <EmptyState
      v-else
      title="暂时没有会话"
      :description="error || '你可以先从房源详情页进入咨询流程。'"
      action-text="回到首页"
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
.messages-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.intro {
  display: grid;
  gap: 16px;
  padding: 24px;
}

.eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.title {
  margin: 0;
  font-size: 32px;
}

.description {
  margin: 10px 0 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--color-text-muted);
}

.session-list {
  display: grid;
  gap: 12px;
}

@media (min-width: 1024px) {
  .intro {
    grid-template-columns: 1fr auto;
    align-items: end;
  }
}
</style>
