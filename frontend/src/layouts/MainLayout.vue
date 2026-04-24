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
      class="app-frame flex min-h-screen flex-col gap-6 py-5 lg:py-8"
      :class="{
        'app-frame--wide': isMessagesRoute,
        'app-frame--home': isHomeRoute,
        'app-frame--mine': isMineOverview
      }"
    >
      <AppTopNav :items="topNavItems" :current-path="route.path" :full-bleed="isHomeRoute" />
      <main
        class="min-h-0 flex-1"
        :class="{
          'main--wide': isMessagesRoute,
          'main--home': isHomeRoute
        }"
      >
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
const isMessagesRoute = computed(() => route.path.startsWith('/messages'))
const isMineOverview = computed(() => route.name === 'mine')

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
main {
  min-width: 0;
}

.app-frame {
  width: min(100%, var(--container-max));
  margin: 0 auto;
  padding-left: 24px;
  padding-right: 24px;
}

.app-frame--home {
  max-width: none;
  gap: 0;
  padding-top: 0;
  padding-left: 0;
  padding-right: 0;
}

.app-frame--mine {
  width: min(100%, 1680px);
}

.app-frame--wide {
  width: 100%;
  max-width: none;
  padding-left: 12px;
  padding-right: 12px;
}

.main--home {
  width: 100%;
}

.main--wide {
  display: flex;
}

.main--wide :deep(.message-desk) {
  flex: 1;
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
    padding-left: 40px;
    padding-right: 40px;
  }

  .app-frame--wide {
    padding-left: 6px;
    padding-right: 6px;
  }
}

@media (max-width: 768px) {
  .app-frame,
  .app-frame--wide,
  .app-frame--mine {
    width: 100%;
    padding-left: 12px;
    padding-right: 12px;
  }

  .app-frame--home {
    padding-left: 0;
    padding-right: 0;
  }

  .toast-stack {
    top: 12px;
    right: 12px;
  }
}
</style>
