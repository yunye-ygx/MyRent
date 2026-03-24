import { onMounted, onUnmounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { getToken } from '@/utils/storage'

function normalizeSession(session) {
  return {
    ...session,
    peerName: session.peerName || (session.peerId ? `用户${session.peerId}` : ''),
    houseLabel: session.houseTitle || '',
    unreadCount: Number(session.unreadCount || 0)
  }
}

export function useChatSessionList(fetchPage) {
  const authStore = useAuthStore()

  const loading = ref(false)
  const error = ref('')
  const sessions = ref([])

  let ws = null
  let reconnectTimer = null
  let pageActive = false

  async function loadSessions() {
    loading.value = true
    error.value = ''
    try {
      const page = await fetchPage({ current: 1, size: 50 })
      const records = Array.isArray(page?.records) ? page.records : []
      sessions.value = records.map(normalizeSession)
    } catch (err) {
      error.value = err?.message || '会话加载失败'
      sessions.value = []
    } finally {
      loading.value = false
    }
  }

  function upsertSessionFromMessage(message) {
    if (!message?.sessionId) {
      return
    }

    const currentUserId = String(authStore.userId || '')
    const nextUpdateTime = message.createTime || new Date().toISOString()
    const nextUnreadCount = String(message.receiverId) === currentUserId ? 1 : 0
    const index = sessions.value.findIndex((item) => String(item.sessionId) === String(message.sessionId))

    if (index < 0) {
      loadSessions()
      return
    }

    const current = sessions.value[index]
    const updated = {
      ...current,
      lastMsgContent: message.content || current.lastMsgContent,
      updateTime: nextUpdateTime,
      unreadCount: String(message.receiverId) === currentUserId
        ? Number(current.unreadCount || 0) + nextUnreadCount
        : Number(current.unreadCount || 0)
    }

    const nextSessions = [...sessions.value]
    nextSessions.splice(index, 1)
    nextSessions.unshift(updated)
    sessions.value = nextSessions
  }

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
    if (!pageActive) {
      return
    }
    clearReconnectTimer()
    reconnectTimer = setTimeout(() => {
      connectWs()
    }, 3000)
  }

  function connectWs() {
    if (!pageActive || !authStore.userId || !getToken()) {
      return
    }
    clearReconnectTimer()
    closeWs()

    ws = new WebSocket(buildWsUrl())

    ws.onmessage = (event) => {
      if (!pageActive) {
        return
      }
      try {
        const payload = JSON.parse(event.data)
        upsertSessionFromMessage(payload)
      } catch {
        // Ignore malformed websocket payloads in the session list.
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

  onMounted(() => {
    pageActive = true
    loadSessions()
    connectWs()
  })

  onUnmounted(() => {
    pageActive = false
    clearReconnectTimer()
    closeWs()
  })

  return {
    loading,
    error,
    sessions,
    loadSessions
  }
}
