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

    <section v-if="showConsultCard" class="consult-context card">
      <div class="consult-cover">
        <img :src="consultHouseCover" :alt="consultHouse.title || 'house'" />
      </div>
      <div class="consult-main">
        <p class="consult-tag">当前咨询房源</p>
        <h3 class="consult-title">{{ consultHouse.title }}</h3>
        <p class="consult-meta">
          {{ formatPrice(consultHouse.price) }}/月 · {{ getHouseStatusText(consultHouse.status) }}
        </p>
        <button
          class="primary-btn consult-action"
          :disabled="sending"
          @click="sendAppointmentMessage"
        >
          {{ sending ? '发送中...' : '一键预约看房' }}
        </button>
      </div>
    </section>

    <section v-if="quickActionsVisible" class="quick-actions card">
      <p class="quick-actions-title">快捷咨询</p>
      <div class="quick-actions-list">
        <button
          v-for="action in quickActions"
          :key="action"
          type="button"
          class="quick-action"
          :disabled="sending"
          @click="sendQuickAction(action)"
        >
          {{ action }}
        </button>
      </div>
    </section>

    <footer class="chat-input card">
      <input
        v-model.trim="content"
        class="input"
        placeholder="输入消息内容..."
        @focus="handleInputFocus"
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
import { fetchHouseById } from '@/api/house'
import { markMessagesRead, pullHistoryMessages, pullNewMessages, sendChatMessage } from '@/api/chat'
import ChatBubble from '@/components/ChatBubble.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { useAuthStore } from '@/stores/auth'
import { formatPrice, getHouseStatusText } from '@/utils/format'
import { getToken } from '@/utils/storage'

const quickActions = ['还在吗', '能否周末看房', '押几付几', '是否可养宠物']

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const sessionId = computed(() => String(route.params.sessionId || ''))
const peerId = computed(() => Number(route.query.peerId || 0))
const peerName = computed(() => String(route.query.peerName || ''))
const consultHouseId = computed(() => Number(route.query.houseId || 0))
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
const consultHouse = ref(null)
const consultCardDismissed = ref(false)
const quickActionsVisible = ref(false)
const quickActionsTriggered = ref(false)
let pageActive = true

const consultHouseCover = computed(() => {
  if (!consultHouse.value?.id) {
    return ''
  }
  return `https://picsum.photos/seed/chat-house-${consultHouse.value.id}/160/120`
})

const showConsultCard = computed(() => Boolean(consultHouse.value) && !consultCardDismissed.value)

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
    // Ignore read receipt failures to keep the chat flow responsive.
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

async function loadConsultHouse() {
  consultHouse.value = null
  if (!consultHouseId.value) {
    return
  }
  try {
    consultHouse.value = await fetchHouseById(consultHouseId.value)
  } catch {
    consultHouse.value = null
  }
}

function dismissConsultCard() {
  consultCardDismissed.value = true
}

function loadQuickActionState() {
  quickActionsTriggered.value = false
  quickActionsVisible.value = false
}

function markQuickActionsAsSeen() {
  quickActionsTriggered.value = true
  quickActionsVisible.value = false
}

function handleInputFocus() {
  if (quickActionsTriggered.value) {
    return
  }
  quickActionsVisible.value = true
  quickActionsTriggered.value = true
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
    // Ignore fallback polling failures and retry on the next cycle.
  }
}

function buildWsUrl() {
  const token = getToken()
  const wsBase = (import.meta.env.VITE_WS_BASE_URL || window.location.origin).replace(/^http/, 'ws')
  return `${wsBase}/ws/chat?token=${encodeURIComponent(token)}`
}

function connectWs() {
  if (!pageActive) {
    return
  }
  if (reconnectTimer.value) {
    clearTimeout(reconnectTimer.value)
    reconnectTimer.value = null
  }
  closeWs()
  const ws = new WebSocket(buildWsUrl())
  wsRef.value = ws

  ws.onopen = async () => {
    if (!pageActive || wsRef.value !== ws) {
      return
    }
    wsConnected.value = true
    stopPullTimer()
    await pullMissedMessages()
  }

  ws.onmessage = async (event) => {
    if (!pageActive || wsRef.value !== ws) {
      return
    }
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
      // Ignore malformed websocket payloads.
    }
  }

  ws.onclose = () => {
    if (!pageActive) {
      return
    }
    if (wsRef.value === ws) {
      wsRef.value = null
    }
    wsConnected.value = false
    startPullTimer()
    reconnectLater()
  }

  ws.onerror = () => {
    if (!pageActive || wsRef.value !== ws) {
      return
    }
    wsConnected.value = false
    startPullTimer()
  }
}

function reconnectLater() {
  if (!pageActive) {
    return
  }
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
  if (!pageActive) {
    return
  }
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

async function sendMessage(messageContent) {
  if (!messageContent || sending.value || !peerId.value) {
    return
  }
  sending.value = true
  try {
    const sentMessage = await sendChatMessage({
      receiverId: peerId.value,
      content: messageContent
    })
    content.value = ''
    mergeMessages(sentMessage ? [sentMessage] : [])
    dismissConsultCard()
    markQuickActionsAsSeen()
    await syncReadState()
    await nextTick()
    scrollToBottom(true)
  } catch (err) {
    window.alert(err?.message || '发送失败，请稍后重试')
  } finally {
    sending.value = false
  }
}

async function send() {
  await sendMessage(content.value)
}

async function sendQuickAction(action) {
  await sendMessage(action)
}

async function sendAppointmentMessage() {
  if (!consultHouse.value) {
    return
  }
  const appointmentMessage = `你好，我想预约看房，房源“${consultHouse.value.title}”，如果方便的话想约个周末时间。`
  await sendMessage(appointmentMessage)
}

onMounted(async () => {
  pageActive = true
  loadQuickActionState()
  await Promise.all([loadInitialHistory(), loadConsultHouse()])
  connectWs()
})

onUnmounted(() => {
  pageActive = false
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
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto auto auto;
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

.consult-context {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.consult-cover img {
  width: 88px;
  height: 88px;
  object-fit: cover;
  border-radius: 12px;
  background: #e5e7eb;
}

.consult-main {
  min-width: 0;
}

.consult-tag {
  margin: 0 0 4px;
  font-size: 12px;
  color: #2563eb;
}

.consult-title {
  margin: 0;
  font-size: 15px;
  color: #111827;
}

.consult-meta {
  margin: 6px 0 10px;
  font-size: 13px;
  color: #6b7280;
}

.consult-action {
  width: 100%;
}

.quick-actions {
  padding-top: 10px;
  padding-bottom: 10px;
}

.quick-actions-title {
  margin: 0 0 8px;
  font-size: 13px;
  color: #6b7280;
}

.quick-actions-list {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.quick-action {
  border: none;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  padding: 8px 12px;
  font-size: 13px;
  white-space: nowrap;
}

.quick-action:disabled {
  opacity: 0.6;
}

.chat-input {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}

@media (max-width: 640px) {
  .consult-context {
    grid-template-columns: 72px minmax(0, 1fr);
  }

  .consult-cover img {
    width: 72px;
    height: 72px;
  }
}
</style>
