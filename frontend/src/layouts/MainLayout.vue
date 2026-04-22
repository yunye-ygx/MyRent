<template>
  <div class="app-shell">
    <div class="toast-stack">
      <OnlineMessageToast
        v-for="toast in messageCenterStore.chatToasts"
        :key="toast.id"
        :toast="toast"
        @click="openToast(toast)"
      />
    </div>
    <div class="app-container flex min-h-screen flex-col gap-6 py-5 lg:py-8">
      <AppTopNav :items="topNavItems" :current-path="route.path" />
      <main class="min-h-0 flex-1">
        <router-view />
      </main>
    </div>
    <AppTabBar class="lg:hidden" />
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTabBar from '@/components/AppTabBar.vue'
import OnlineMessageToast from '@/components/chat/OnlineMessageToast.vue'
import AppTopNav from '@/components/layout/AppTopNav.vue'
import { topNavItems } from '@/design/site'
import { useMessageCenterStore } from '@/stores/messageCenter'
import { getToken } from '@/utils/storage'

const route = useRoute()
const router = useRouter()
const messageCenterStore = useMessageCenterStore()

let ws = null
let reconnectTimer = null
let active = false

function buildWsUrl() {
  const token = getToken()
  const wsBase = (import.meta.env.VITE_WS_BASE_URL || window.location.origin).replace(/^http/, 'ws')
  return `${wsBase}/ws/chat?token=${encodeURIComponent(token)}`
}

function clearReconnectTimer() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

function closeWs() {
  if (ws) {
    ws.close()
    ws = null
  }
}

function reconnectLater() {
  if (!active || !getToken()) {
    return
  }
  clearReconnectTimer()
  reconnectTimer = setTimeout(() => {
    connectWs()
  }, 3000)
}

function connectWs() {
  if (!active || !getToken()) {
    return
  }

  clearReconnectTimer()
  closeWs()
  ws = new WebSocket(buildWsUrl())

  ws.onmessage = (event) => {
    if (!active) {
      return
    }
    try {
      const payload = JSON.parse(event.data)
      messageCenterStore.handleIncomingChatMessage(payload)
    } catch {
      // Ignore malformed websocket payloads.
    }
  }

  ws.onclose = () => {
    ws = null
    reconnectLater()
  }

  ws.onerror = () => {
    reconnectLater()
  }
}

function openToast(toast) {
  messageCenterStore.dismissChatToast(toast.id)
  router.push({
    path: `/chat/${toast.sessionId}`,
    query: {
      peerId: String(toast.peerId || ''),
      peerName: toast.senderName || '',
      houseId: toast.houseId ? String(toast.houseId) : ''
    }
  })
}

watch(
  () => route.params.sessionId,
  (sessionId) => {
    messageCenterStore.setCurrentChatSession(sessionId)
  },
  { immediate: true }
)

onMounted(() => {
  active = true
  messageCenterStore.loadUnreadTotals()
  connectWs()
})

onUnmounted(() => {
  active = false
  clearReconnectTimer()
  closeWs()
})
</script>

<style scoped>
main {
  min-width: 0;
}

.toast-stack {
  position: fixed;
  top: 18px;
  right: 18px;
  z-index: 40;
  display: grid;
  gap: 12px;
}

@media (max-width: 768px) {
  .toast-stack {
    top: 12px;
    right: 12px;
  }
}
</style>
