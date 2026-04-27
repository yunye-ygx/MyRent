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
    <div class="app-frame flex min-h-screen flex-col">
      <AppTopNav :items="topNavItems" :current-path="route.path" />
      <main class="app-main min-h-0 flex-1">
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
import { useAuthStore } from '@/stores/auth'
import { useChatSessionStore } from '@/stores/chatSession'
import { useMessageCenterStore } from '@/stores/messageCenter'
import { getToken } from '@/utils/storage'

const authStore = useAuthStore()
const chatSessionStore = useChatSessionStore()
const route = useRoute()
const router = useRouter()
const messageCenterStore = useMessageCenterStore()

let ws = null
let reconnectTimer = null
let active = false
let hasOpenedWs = false

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

  ws.onopen = () => {
    if (!active) {
      return
    }
    const isReconnect = hasOpenedWs
    hasOpenedWs = true
    if (!isReconnect) {
      chatSessionStore.loadSessions({ minFreshMs: 5000 })
      return
    }
    messageCenterStore.loadUnreadTotals()
    chatSessionStore.loadSessions({ force: true })
  }

  ws.onmessage = (event) => {
    if (!active) {
      return
    }
    try {
      const payload = JSON.parse(event.data)
      messageCenterStore.handleIncomingChatMessage(payload)
      chatSessionStore.upsertSessionFromMessage(payload, authStore.userId)
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
  messageCenterStore.setMessageDeskPendingTarget({
    kind: 'chat',
    sessionId: toast.sessionId,
    peerId: toast.peerId || 0,
    peerName: toast.senderName || '',
    houseId: toast.houseId || 0
  })
  router.push('/messages')
}

watch(
  () => route.params.sessionId,
  (sessionId) => {
    messageCenterStore.setCurrentChatSession(sessionId)
    chatSessionStore.setCurrentSessionId(sessionId)
  },
  { immediate: true }
)

onMounted(() => {
  active = true
  hasOpenedWs = false
  messageCenterStore.loadUnreadTotals()
  connectWs()
})

onUnmounted(() => {
  active = false
  hasOpenedWs = false
  clearReconnectTimer()
  closeWs()
})
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
}

main {
  min-width: 0;
}

.app-frame {
  width: calc(100% - clamp(16px, 1.6vw, 28px));
  margin: 0 auto;
  max-width: none;
  padding: clamp(10px, 1.2vw, 16px) 0 calc(72px + clamp(20px, 2vw, 28px));
  gap: clamp(14px, 1.6vw, 20px);
}

.app-main {
  width: 100%;
}

.toast-stack {
  position: fixed;
  top: 18px;
  right: 18px;
  z-index: 40;
  display: grid;
  gap: 12px;
}

@media (min-width: 1024px) {
  .app-frame {
    width: calc(100% - clamp(20px, 1.8vw, 32px));
  }
}

@media (max-width: 768px) {
  .app-frame {
    width: 100%;
    padding: 10px 12px 92px;
    gap: 12px;
  }

  .toast-stack {
    top: 12px;
    right: 12px;
  }
}
</style>
