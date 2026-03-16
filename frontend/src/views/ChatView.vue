<template>
  <div class="chat-page">
    <header class="chat-header card">
      <button class="ghost-btn" @click="router.back()">返回</button>
      <div class="title-wrap">
        <h2 class="title">{{ peerName || `用户${peerId || ''}` }}</h2>
        <p class="status" :class="{ online: wsConnected }">
          {{ wsConnected ? '实时连接已建立' : '连接中断，自动补拉中' }}
        </p>
      </div>
    </header>

    <div ref="messageContainer" class="message-list" @scroll="handleScroll">
      <div class="history-indicator">
        <LoadingState v-if="historyLoading" text="加载历史消息..." />
        <button v-else-if="hasMoreHistory" class="ghost-btn" @click="loadMoreHistory">加载更早消息</button>
        <span v-else class="history-end">没有更早消息了</span>
      </div>

      <EmptyState v-if="!messages.length && !historyLoading" title="暂无聊天记录" />

      <ChatBubble
        v-for="item in messages"
        :key="String(item.id)"
        :message="item"
        :current-user-id="currentUserId"
      />
    </div>

    <footer class="chat-input card">
      <input
        v-model.trim="content"
        class="input"
        placeholder="输入消息内容..."
        @keyup.enter="send"
      />
      <button class="primary-btn" :disabled="sending || !content" @click="send">
        {{ sending ? '发送中...' : '发送' }}
      </button>
    </footer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchMessagePage, markMessagesRead, pullHistoryMessages, pullNewMessages, sendChatMessage } from '@/api/chat'
import ChatBubble from '@/components/ChatBubble.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { useAuthStore } from '@/stores/auth'
import { getToken } from '@/utils/storage'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const sessionId = computed(() => String(route.params.sessionId || ''))
const peerId = computed(() => Number(route.query.peerId || 0))
const peerName = computed(() => String(route.query.peerName || ''))
const currentUserId = computed(() => Number(authStore.userId || 0))

const messageContainer = ref(null)
const messages = ref([])
const historyLoading = ref(false)
const hasMoreHistory = ref(true)
const historyCursor = ref(null)
const content = ref('')
const sending = ref(false)
const wsConnected = ref(false)
const wsRef = ref(null)
const reconnectTimer = ref(null)
const pullTimer = ref(null)
const lastReadUpToId = ref(0)

function toNumericId(id) {
  const value = Number(id)
  return Number.isFinite(value) ? value : null
}

function sortMessages(list) {
  return [...list].sort((a, b) => {
    const ida = toNumericId(a.id)
    const idb = toNumericId(b.id)
    if (ida === null && idb === null) {
      return 0
    }
    if (ida === null) {
      return 1
    }
    if (idb === null) {
      return -1
    }
    return ida - idb
  })
}

function mergeMessages(incoming = []) {
  if (!incoming.length) {
    return
  }
  const map = new Map(messages.value.map((item) => [String(item.id), item]))
  incoming.forEach((item) => {
    map.set(String(item.id), item)
  })
  messages.value = sortMessages(Array.from(map.values()))
}

function getLastMessageId() {
  for (let index = messages.value.length - 1; index >= 0; index -= 1) {
    const id = toNumericId(messages.value[index].id)
    if (id !== null) {
      return id
    }
  }
  return null
}

function getMaxReadableMessageId() {
  let maxId = 0
  messages.value.forEach((item) => {
    if (String(item.receiverId) !== String(currentUserId.value)) {
      return
    }
    const id = toNumericId(item.id)
    if (id && id > maxId) {
      maxId = id
    }
  })
  return maxId
}

async function syncReadState() {
  const upToMessageId = getMaxReadableMessageId()
  if (!upToMessageId || upToMessageId <= lastReadUpToId.value) {
    return
  }
  try {
    await markMessagesRead({
      sessionId: sessionId.value,
      upToMessageId
    })
    lastReadUpToId.value = upToMessageId
  } catch {
    // 忽略已读回执失败，不影响主流程
  }
}

function scrollToBottom(smooth = false) {
  const container = messageContainer.value
  if (!container) {
    return
  }
  container.scrollTo({
    top: container.scrollHeight,
    behavior: smooth ? 'smooth' : 'auto'
  })
}

async function loadInitialHistory() {
  historyLoading.value = true
  try {
    const result = await pullHistoryMessages({
      sessionId: sessionId.value,
      limit: 30
    })
    mergeMessages(result?.messages || [])
    historyCursor.value = result?.nextCursor || null
    hasMoreHistory.value = Boolean(result?.hasMore)
    await syncReadState()
    await nextTick()
    scrollToBottom()
  } catch (err) {
    if (err?.code === 403) {
      historyCursor.value = null
      hasMoreHistory.value = false
      return
    }
  } finally {
    historyLoading.value = false
  }
}

async function loadMoreHistory() {
  if (historyLoading.value || !hasMoreHistory.value || !historyCursor.value) {
    return
  }
  const container = messageContainer.value
  const beforeHeight = container?.scrollHeight || 0

  historyLoading.value = true
  try {
    const result = await pullHistoryMessages({
      sessionId: sessionId.value,
      beforeMessageId: historyCursor.value,
      limit: 30
    })
    mergeMessages(result?.messages || [])
    historyCursor.value = result?.nextCursor || historyCursor.value
    hasMoreHistory.value = Boolean(result?.hasMore)
    await syncReadState()
    await nextTick()
    if (container) {
      const afterHeight = container.scrollHeight
      container.scrollTop = afterHeight - beforeHeight + container.scrollTop
    }
  } catch (err) {
    if (err?.code === 403) {
      historyCursor.value = null
      hasMoreHistory.value = false
    }
  } finally {
    historyLoading.value = false
  }
}

async function refreshLatestMessages() {
  try {
    const page = await fetchMessagePage({
      current: 1,
      size: 40,
      sessionId: sessionId.value
    })
    const records = [...(page?.records || [])].reverse()
    mergeMessages(records)
    await syncReadState()
  } catch {
    // 忽略拉新失败，依赖 websocket/pull 继续补偿
  }
}

async function pullMissedMessages() {
  try {
    const result = await pullNewMessages({
      sessionId: sessionId.value,
      lastMessageId: getLastMessageId() || undefined,
      limit: 50
    })
    const incoming = result?.messages || []
    if (!incoming.length) {
      return
    }

    const container = messageContainer.value
    const nearBottom = container
      ? container.scrollHeight - container.scrollTop - container.clientHeight < 120
      : true

    mergeMessages(incoming)
    await syncReadState()
    await nextTick()
    if (nearBottom) {
      scrollToBottom(true)
    }
  } catch {
    // 忽略补拉失败，等待下次定时任务或重连
  }
}

function buildWsUrl() {
  const token = getToken()
  const wsBase = (import.meta.env.VITE_WS_BASE_URL || window.location.origin).replace(/^http/, 'ws')
  return `${wsBase}/ws/chat?token=${encodeURIComponent(token)}`
}

function connectWs() {
  closeWs()
  const ws = new WebSocket(buildWsUrl())
  wsRef.value = ws

  ws.onopen = async () => {
    wsConnected.value = true
    await pullMissedMessages()
  }

  ws.onmessage = async (event) => {
    try {
      const data = JSON.parse(event.data)
      if (String(data.sessionId) !== sessionId.value) {
        return
      }
      mergeMessages([data])
      await syncReadState()
      await nextTick()
      scrollToBottom(true)
    } catch {
      // 忽略无法解析的消息
    }
  }

  ws.onclose = () => {
    wsConnected.value = false
    reconnectLater()
  }

  ws.onerror = () => {
    wsConnected.value = false
  }
}

function reconnectLater() {
  if (reconnectTimer.value) {
    clearTimeout(reconnectTimer.value)
  }
  reconnectTimer.value = setTimeout(() => {
    connectWs()
  }, 3000)
}

function closeWs() {
  if (wsRef.value) {
    wsRef.value.close()
    wsRef.value = null
  }
}

function startPullTimer() {
  stopPullTimer()
  pullTimer.value = setInterval(() => {
    pullMissedMessages()
  }, 10000)
}

function stopPullTimer() {
  if (pullTimer.value) {
    clearInterval(pullTimer.value)
    pullTimer.value = null
  }
}

function handleScroll() {
  const container = messageContainer.value
  if (!container || historyLoading.value || !hasMoreHistory.value) {
    return
  }
  if (container.scrollTop <= 30) {
    loadMoreHistory()
  }
}

async function send() {
  if (!content.value || sending.value || !peerId.value) {
    return
  }
  sending.value = true
  try {
    await sendChatMessage({
      receiverId: peerId.value,
      content: content.value
    })
    content.value = ''
    await refreshLatestMessages()
    await nextTick()
    scrollToBottom(true)
  } catch (err) {
    window.alert(err?.message || '发送失败，请稍后重试')
  } finally {
    sending.value = false
  }
}

onMounted(async () => {
  await loadInitialHistory()
  await refreshLatestMessages()
  connectWs()
  startPullTimer()
})

onUnmounted(() => {
  stopPullTimer()
  closeWs()
  if (reconnectTimer.value) {
    clearTimeout(reconnectTimer.value)
  }
})
</script>

<style scoped>
.chat-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-wrap {
  min-width: 0;
}

.title {
  margin: 0;
  font-size: 16px;
}

.status {
  margin: 0;
  color: #b91c1c;
  font-size: 12px;
}

.status.online {
  color: #15803d;
}

.message-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border-radius: 12px;
  background: #f9fafb;
  padding: 10px;
}

.history-indicator {
  display: flex;
  justify-content: center;
  margin-bottom: 6px;
}

.history-end {
  font-size: 12px;
  color: #9ca3af;
}

.chat-input {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}
</style>
