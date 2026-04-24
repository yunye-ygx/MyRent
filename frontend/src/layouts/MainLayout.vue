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
    <div
      class="app-container flex min-h-screen flex-col gap-6 py-5 lg:py-8"
      :class="{ 'app-container--home': isHomeRoute }"
    >
      <AppTopNav :items="topNavItems" :current-path="route.path" :full-bleed="isHomeRoute" />
      <main class="min-h-0 flex-1" :class="{ 'main--home': isHomeRoute }">
        <router-view />
      </main>
    </div>
    <AppTabBar class="lg:hidden" />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, watch } from 'vue'
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
const isHomeRoute = computed(() => route.path === '/home')

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
.app-container--home {
  max-width: none;
  gap: 0;
  padding-top: 0;
  padding-left: 0;
  padding-right: 0;
}

.main--home {
  width: 100%;
}

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
