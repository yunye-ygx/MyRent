<template>
  <div class="message-desk">
    <aside class="desk-sidebar app-surface">
      <div class="sidebar-header">
        <p class="sidebar-kicker">消息中心</p>
        <h1>聊天</h1>
      </div>

      <div class="sidebar-groups">
        <button
          v-for="item in filterItems"
          :key="item.key"
          :data-filter="item.key"
          class="sidebar-filter"
          :class="{ active: activeFilter === item.key }"
          @click="activeFilter = item.key"
        >
          <span class="filter-icon">{{ item.icon }}</span>
          <span class="filter-label">{{ item.label }}</span>
          <span class="filter-count">{{ item.count }}</span>
        </button>
      </div>

      <p class="sidebar-footnote" :class="{ error: listErrorMessage }">
        {{ listErrorMessage || '消息列表仅展示真实聊天记录和系统通知。' }}
      </p>
    </aside>

    <section class="desk-list app-surface">
      <div class="list-header">
        <div>
          <p class="panel-kicker">近期会话</p>
          <h2>{{ listTitle }}</h2>
        </div>
        <button class="ghost-btn compact-btn" @click="refreshCurrentFeed">刷新列表</button>
      </div>

      <LoadingState v-if="loadingSessions && !filteredEntries.length" text="正在加载消息列表..." />

      <div v-else class="conversation-list">
        <button
          v-for="entry in filteredEntries"
          :key="entry.id"
          :data-entry-id="entry.id"
          class="conversation-card"
          :class="{ active: selectedEntry?.id === entry.id }"
          @click="selectedEntryId = entry.id"
        >
          <div class="conversation-avatar" :class="entry.kind">
            {{ entry.avatarText }}
          </div>

          <div class="conversation-main">
            <div class="conversation-top">
              <h3>{{ entry.title }}</h3>
              <span class="conversation-time">{{ formatListTime(entry.updatedAt) }}</span>
            </div>

            <div class="conversation-mid">
              <p>{{ entry.subtitle }}</p>
              <span v-if="entry.statusText" class="status-chip" :class="entry.statusTone">
                {{ entry.statusText }}
              </span>
            </div>

            <div class="conversation-bottom">
              <p>{{ entry.preview }}</p>
              <span v-if="entry.unreadCount > 0" class="conversation-badge">{{ entry.unreadCount }}</span>
            </div>
          </div>
        </button>

        <EmptyState
          v-if="!filteredEntries.length"
          title="暂无消息"
          description="当前分类下没有可展示的会话。"
        />
      </div>
    </section>

    <section class="desk-thread app-surface">
      <template v-if="selectedEntry">
        <div class="thread-header">
          <div class="thread-identity">
            <div class="thread-avatar" :class="selectedEntry.kind">
              {{ selectedEntry.avatarText }}
            </div>
            <div class="thread-copy">
              <div class="thread-title-row">
                <h2 data-thread-title>{{ selectedEntry.title }}</h2>
              </div>
              <p>{{ selectedEntry.headerSubtitle }}</p>
            </div>
          </div>

          <div class="thread-actions">
            <span class="price-pill">{{ selectedEntry.priceText }}</span>
            <button
              class="ghost-btn compact-btn"
              :disabled="!selectedEntry.linkTarget"
              @click="openEntryTarget"
            >
              查看房源
            </button>
          </div>
        </div>

        <div ref="threadScroller" class="thread-body">
          <LoadingState v-if="threadLoading" text="正在加载会话内容..." />

          <template v-else>
            <EmptyState
              v-if="!selectedMessages.length"
              title="暂无会话内容"
              :description="threadHint || '当前会话还没有产生消息。'"
            />

            <template v-else>
              <p v-if="threadHint" class="thread-hint">{{ threadHint }}</p>

              <div
                v-for="message in selectedMessages"
                :key="message.id"
                class="thread-message-row"
                :class="{ self: message.side === 'self' }"
              >
                <div v-if="message.side !== 'self'" class="message-avatar">
                  {{ message.avatarText }}
                </div>

                <div class="message-column">
                  <div v-if="message.side !== 'self'" class="message-name">{{ message.name }}</div>
                  <div class="message-bubble" :class="{ self: message.side === 'self' }">
                    {{ message.content }}
                  </div>
                  <div class="message-time">{{ message.timeText }}</div>
                </div>

                <div v-if="message.side === 'self'" class="message-avatar self">
                  {{ message.avatarText }}
                </div>
              </div>
            </template>
          </template>
        </div>

        <div class="thread-composer" :class="{ readonly: selectedEntry.kind !== 'chat' }">
          <input
            v-model.trim="composer"
            class="input composer-input"
            :disabled="selectedEntry.kind !== 'chat' || sending"
            :placeholder="composerPlaceholder"
            @keyup.enter="handleSend"
          />

          <div class="composer-tools">
            <span class="tool-dot" />
            <span class="tool-dot" />
            <span class="tool-dot" />
          </div>

          <button
            class="primary-btn composer-send"
            :disabled="selectedEntry.kind !== 'chat' || sending || !composer"
            @click="handleSend"
          >
            {{ sending ? '发送中' : '发送' }}
          </button>
        </div>
      </template>

      <EmptyState
        v-else
        title="请选择一条会话"
        description="左侧列表会展示聊天消息、系统通知和预约提醒。"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { markMessagesRead, pullHistoryMessages, sendChatMessage } from '@/api/chat'
import { fetchNotificationPage, markNotificationRead } from '@/api/notification'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { useAuthStore } from '@/stores/auth'
import { useChatSessionStore } from '@/stores/chatSession'
import { useMessageCenterStore } from '@/stores/messageCenter'
import { formatRequestError } from '@/utils/format'

const router = useRouter()
const authStore = useAuthStore()
const chatSessionStore = useChatSessionStore()
const messageCenterStore = useMessageCenterStore()

const activeFilter = ref('all')
const notifications = ref([])
const notificationLoading = ref(false)
const notificationError = ref('')
const selectedEntryId = ref('')
const selectedMessages = ref([])
const composer = ref('')
const sending = ref(false)
const threadLoading = ref(false)
const threadHint = ref('')
const threadScroller = ref(null)

let selectionLoadId = 0

const notificationTypeMap = {
  BOOKING_REMINDER: 'booking',
  APPOINTMENT_REMINDER: 'booking',
  HOUSE_PRICE_CHANGED: 'system',
  PUBLISHER_NEW_HOUSE: 'system'
}

function minutesAgo(minutes) {
  return new Date(Date.now() - minutes * 60 * 1000).toISOString()
}

const currentUserId = computed(() => Number(authStore.userId || 9001))
const currentUserName = computed(() => authStore.profile?.name || '元气小圆同学')
const loadingSessions = computed(() => chatSessionStore.loading || notificationLoading.value)
const listErrorMessage = computed(() =>
  [chatSessionStore.error, notificationError.value].filter(Boolean).join(' ')
)

function getInitial(text, fallback = '租') {
  const value = String(text || '').trim()
  return value ? value.slice(0, 1) : fallback
}

function padNumber(value) {
  return String(value).padStart(2, '0')
}

function formatListTime(value) {
  const date = new Date(value || '')
  if (Number.isNaN(date.getTime())) {
    return '--'
  }
  const now = new Date()
  const isToday = now.toDateString() === date.toDateString()
  if (isToday) {
    return `${padNumber(date.getHours())}:${padNumber(date.getMinutes())}`
  }
  return `${padNumber(date.getMonth() + 1)}-${padNumber(date.getDate())}`
}

function formatPriceText(value, fallback = '在线沟通') {
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue) || numericValue <= 0) {
    return fallback
  }
  return `¥${numericValue}/月`
}

function normalizeChatEntry(session = {}, source = 'server') {
  const unreadCount = Number(session.unreadCount || 0)
  return {
    id: `chat:${session.sessionId}`,
    kind: 'chat',
    category: 'chat',
    source,
    sessionId: session.sessionId,
    peerId: Number(session.peerId || 0),
    peerName: session.peerName || '房东',
    houseId: Number(session.houseId || 0),
    title: session.peerName || '房东会话',
    subtitle: session.houseTitle || session.houseLabel || '房源沟通中',
    headerSubtitle: session.houseTitle || session.houseLabel || '最近消息',
    preview: session.lastMsgContent || '暂无消息',
    updatedAt: session.updateTime || minutesAgo(0),
    unreadCount,
    avatarText: getInitial(session.peerName, '房'),
    statusText: unreadCount > 0 ? '待回复' : '已回复',
    statusTone: unreadCount > 0 ? 'warning' : 'muted',
    priceText: formatPriceText(session.price),
    linkTarget: Number(session.houseId || 0) > 0 ? `/house/${session.houseId}` : ''
  }
}

function normalizeNotificationEntry(item = {}, source = 'server') {
  const category = notificationTypeMap[item.type] || 'system'
  const unreadCount = Number(item.isRead || 0) === 0 ? 1 : 0
  return {
    id: `notification:${item.id}`,
    kind: 'notification',
    category,
    source,
    notificationId: item.id,
    title: category === 'booking' ? '预约助手' : '系统通知',
    subtitle: item.title || (category === 'booking' ? '预约提醒' : '系统消息'),
    headerSubtitle: category === 'booking' ? '预约日程同步' : '平台消息播报',
    preview: item.content || '暂无通知内容',
    updatedAt: item.createTime || minutesAgo(0),
    unreadCount,
    avatarText: category === 'booking' ? '约' : '系',
    statusText: unreadCount > 0 ? '新消息' : '已读',
    statusTone: unreadCount > 0 ? 'danger' : 'muted',
    priceText: category === 'booking' ? '预约提醒' : '系统通知',
    linkTarget: item.redirectTargetId ? `/house/${item.redirectTargetId}` : ''
  }
}

const allEntries = computed(() => {
  const sessionEntries = chatSessionStore.sessions.map((item) => normalizeChatEntry(item, 'server'))
  const notificationEntries = notifications.value.map((item) => normalizeNotificationEntry(item, 'server'))

  return [...sessionEntries, ...notificationEntries].sort(
    (left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime()
  )
})

const filterItems = computed(() => {
  const entries = allEntries.value
  return [
    { key: 'all', label: '全部消息', icon: '全', count: entries.length },
    {
      key: 'chat',
      label: '房东消息',
      icon: '房',
      count: entries.filter((item) => item.category === 'chat').length
    },
    {
      key: 'system',
      label: '系统通知',
      icon: '系',
      count: entries.filter((item) => item.category === 'system').length
    },
    {
      key: 'booking',
      label: '预约提醒',
      icon: '约',
      count: entries.filter((item) => item.category === 'booking').length
    }
  ]
})

const filteredEntries = computed(() => {
  if (activeFilter.value === 'all') {
    return allEntries.value
  }
  return allEntries.value.filter((item) => item.category === activeFilter.value)
})

const selectedEntry = computed(
  () => filteredEntries.value.find((item) => item.id === selectedEntryId.value) || null
)

const listTitle = computed(() => {
  const current = filterItems.value.find((item) => item.key === activeFilter.value)
  return current?.label || '消息'
})

const composerPlaceholder = computed(() => {
  if (!selectedEntry.value) {
    return '请输入消息...'
  }
  if (selectedEntry.value.kind !== 'chat') {
    return '系统通知不支持回复'
  }
  return '输入消息...'
})

function buildThreadMessage({ id, side, name, content, timeText }) {
  return {
    id,
    side,
    name,
    content,
    timeText,
    avatarText: getInitial(name, side === 'self' ? '我' : '房')
  }
}

function buildNotificationThread(entry) {
  return [
    buildThreadMessage({
      id: `${entry.id}-n1`,
      side: 'peer',
      name: entry.title,
      content: entry.subtitle,
      timeText: formatListTime(entry.updatedAt)
    }),
    buildThreadMessage({
      id: `${entry.id}-n2`,
      side: 'peer',
      name: entry.title,
      content: entry.preview,
      timeText: formatListTime(entry.updatedAt)
    })
  ]
}

function normalizeHistoryMessage(entry, message = {}) {
  const isSelf = String(message.senderId) === String(currentUserId.value)
  return buildThreadMessage({
    id: String(message.id || `${entry.id}-${Math.random()}`),
    side: isSelf ? 'self' : 'peer',
    name: isSelf ? currentUserName.value : entry.peerName || entry.title,
    content: String(message.content || ''),
    timeText: formatListTime(message.createTime || entry.updatedAt)
  })
}

async function scrollThreadToBottom() {
  await nextTick()
  const element = threadScroller.value
  if (!element) {
    return
  }
  element.scrollTop = element.scrollHeight
}

function clearChatUnread(entry) {
  const current = chatSessionStore.sessions.find(
    (item) => String(item.sessionId) === String(entry.sessionId)
  )
  const unreadCount = Number(current?.unreadCount || 0)
  if (!unreadCount) {
    return
  }
  current.unreadCount = 0
  messageCenterStore.decrementChatUnread(unreadCount)
  markMessagesRead({
    sessionId: entry.sessionId,
    upToMessageId: Number.MAX_SAFE_INTEGER
  }).catch(() => {})
}

function markNotificationAsReadLocally(entry) {
  const index = notifications.value.findIndex((item) => String(item.id) === String(entry.notificationId))
  if (index < 0 || Number(notifications.value[index].isRead || 0) === 1) {
    return
  }
  notifications.value[index] = {
    ...notifications.value[index],
    isRead: 1
  }
  messageCenterStore.decrementNotificationUnread()
}

async function markNotificationAsRead(entry) {
  if (entry.kind !== 'notification' || entry.unreadCount === 0 || entry.source !== 'server') {
    return
  }
  markNotificationAsReadLocally(entry)
  try {
    await markNotificationRead(entry.notificationId)
  } catch {
    // Keep local state responsive even when the mark-read call fails.
  }
}

async function loadThread(entry) {
  if (!entry) {
    selectedMessages.value = []
    return
  }

  composer.value = ''
  threadHint.value = ''
  threadLoading.value = true
  const currentLoadId = ++selectionLoadId

  if (entry.kind === 'notification') {
    await markNotificationAsRead(entry)
    if (currentLoadId !== selectionLoadId) {
      return
    }
    selectedMessages.value = buildNotificationThread(entry)
    threadLoading.value = false
    await scrollThreadToBottom()
    return
  }

  clearChatUnread(entry)

  try {
    const result = await pullHistoryMessages({
      sessionId: entry.sessionId,
      limit: 30
    })
    if (currentLoadId !== selectionLoadId) {
      return
    }
    const records = Array.isArray(result?.messages) ? result.messages : []
    if (records.length) {
      selectedMessages.value = records.map((message) => normalizeHistoryMessage(entry, message))
      threadHint.value = ''
    } else {
      selectedMessages.value = []
      threadHint.value = '当前会话暂无历史消息。'
    }
  } catch (error) {
    if (currentLoadId !== selectionLoadId) {
      return
    }
    selectedMessages.value = []
    threadHint.value = formatRequestError(error, '聊天记录暂时不可用。')
  } finally {
    if (currentLoadId === selectionLoadId) {
      threadLoading.value = false
      await scrollThreadToBottom()
    }
  }
}

async function loadNotifications() {
  notificationLoading.value = true
  notificationError.value = ''
  try {
    const page = await fetchNotificationPage({ current: 1, size: 20 })
    notifications.value = Array.isArray(page?.records) ? page.records : []
  } catch (error) {
    notificationError.value = formatRequestError(error, '通知暂时不可用。')
    notifications.value = []
  } finally {
    notificationLoading.value = false
  }
}

async function refreshCurrentFeed() {
  if (activeFilter.value === 'system' || activeFilter.value === 'booking') {
    await loadNotifications()
    return
  }
  if (activeFilter.value === 'all') {
    await Promise.all([
      chatSessionStore.loadSessions({ force: true }),
      loadNotifications()
    ])
    return
  }
  await chatSessionStore.loadSessions({ force: true })
}

async function openEntryTarget() {
  if (!selectedEntry.value?.linkTarget) {
    return
  }
  router.push(selectedEntry.value.linkTarget)
}

async function handleSend() {
  const entry = selectedEntry.value
  const text = composer.value.trim()
  if (!entry || entry.kind !== 'chat' || !text || sending.value) {
    return
  }

  sending.value = true
  composer.value = ''
  const optimisticMessage = buildThreadMessage({
    id: `local-${Date.now()}`,
    side: 'self',
    name: currentUserName.value,
    content: text,
    timeText: formatListTime(new Date().toISOString())
  })

  selectedMessages.value = [...selectedMessages.value, optimisticMessage]
  await scrollThreadToBottom()

  const canUseApi = Number(entry.peerId || 0) > 0 && Number(entry.houseId || 0) > 0
  if (!canUseApi) {
    selectedMessages.value = selectedMessages.value.filter((item) => item.id !== optimisticMessage.id)
    composer.value = text
    threadHint.value = '当前会话缺少必要参数，暂时无法发送消息。'
    sending.value = false
    return
  }

  try {
    const response = await sendChatMessage({
      receiverId: entry.peerId,
      houseId: entry.houseId,
      content: text
    })

    const normalized = normalizeHistoryMessage(entry, response || {
      id: `fallback-${Date.now()}`,
      senderId: currentUserId.value,
      content: text,
      createTime: new Date().toISOString()
    })

    selectedMessages.value = [
      ...selectedMessages.value.filter((item) => item.id !== optimisticMessage.id),
      normalized
    ]
    threadHint.value = ''
    chatSessionStore.loadSessions({ force: true }).catch(() => {})
  } catch (error) {
    selectedMessages.value = selectedMessages.value.filter((item) => item.id !== optimisticMessage.id)
    composer.value = text
    threadHint.value = formatRequestError(error, '发送失败，请稍后重试。')
  } finally {
    sending.value = false
    await scrollThreadToBottom()
  }
}

watch(
  filteredEntries,
  (entries) => {
    if (!entries.length) {
      selectedEntryId.value = ''
      selectedMessages.value = []
      return
    }
    const stillExists = entries.some((item) => item.id === selectedEntryId.value)
    if (!stillExists) {
      selectedEntryId.value = entries[0].id
    }
  },
  { immediate: true }
)

watch(
  selectedEntry,
  (entry) => {
    if (!entry) {
      return
    }
    loadThread(entry)
  },
  { immediate: true }
)

onMounted(async () => {
  messageCenterStore.loadUnreadTotals({ minFreshMs: 5000 }).catch(() => {})
  await Promise.all([
    chatSessionStore.loadSessions({ minFreshMs: 5000 }),
    loadNotifications()
  ])
})
</script>

<style scoped>
.message-desk {
  display: grid;
  grid-template-columns: 220px 320px minmax(0, 1fr);
  gap: 16px;
  min-height: calc(100vh - 180px);
}

.desk-sidebar,
.desk-list,
.desk-thread {
  min-height: 0;
  border-radius: 22px;
  border: 1px solid rgba(226, 232, 240, 0.92);
  box-shadow: 0 18px 45px rgba(148, 163, 184, 0.14);
}

.desk-sidebar {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 22px 18px;
}

.sidebar-header h1,
.list-header h2,
.thread-copy h2 {
  margin: 0;
  color: #1f2937;
}

.sidebar-kicker,
.panel-kicker {
  margin: 0 0 6px;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #9aa3af;
}

.sidebar-groups {
  display: grid;
  gap: 10px;
}

.sidebar-filter {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: 1px solid transparent;
  border-radius: 16px;
  background: #fff;
  padding: 12px 12px;
  text-align: left;
  color: #4b5563;
  transition: all 0.2s ease;
}

.sidebar-filter.active {
  background: #f5f8f2;
  border-color: rgba(126, 154, 98, 0.26);
  color: #2f4b28;
}

.filter-icon {
  width: 28px;
  height: 28px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #f4f4f1;
  font-size: 12px;
  font-weight: 700;
}

.sidebar-filter.active .filter-icon {
  background: #dbe7d0;
}

.filter-label {
  font-size: 14px;
  font-weight: 600;
}

.filter-count {
  font-size: 13px;
  color: #8a8f98;
}

.sidebar-footnote {
  margin: auto 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: #9aa3af;
}

.sidebar-footnote.error {
  color: #c76856;
}

.desk-list,
.desk-thread {
  display: flex;
  flex-direction: column;
  padding: 20px;
}

.list-header,
.thread-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid #edf0eb;
}

.compact-btn {
  padding: 8px 12px;
  border-radius: 12px;
}

.conversation-list {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: auto;
  padding-right: 4px;
}

.conversation-card {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: 12px;
  width: 100%;
  border: 1px solid transparent;
  border-radius: 18px;
  background: #fff;
  padding: 14px;
  text-align: left;
  transition: all 0.2s ease;
}

.conversation-card:hover,
.conversation-card.active {
  background: #fbfbf7;
  border-color: rgba(126, 154, 98, 0.22);
  box-shadow: 0 10px 24px rgba(148, 163, 184, 0.12);
}

.conversation-avatar,
.thread-avatar,
.message-avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #ebe6dc;
  color: #6b4d2f;
  font-size: 18px;
  font-weight: 700;
  flex-shrink: 0;
}

.conversation-avatar.notification,
.thread-avatar.notification {
  background: #f1f4e6;
  color: #58713a;
}

.conversation-main,
.thread-copy {
  min-width: 0;
}

.conversation-top,
.conversation-mid,
.conversation-bottom,
.thread-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.conversation-top h3,
.conversation-mid p,
.conversation-bottom p,
.thread-copy p {
  margin: 0;
}

.conversation-top h3 {
  font-size: 15px;
  color: #242b35;
}

.conversation-time,
.message-time,
.message-name,
.thread-copy p {
  color: #98a1ab;
  font-size: 12px;
}

.conversation-mid {
  margin-top: 6px;
}

.conversation-mid p {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #6b7280;
  font-size: 13px;
}

.conversation-bottom {
  margin-top: 8px;
}

.conversation-bottom p {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #4b5563;
  font-size: 14px;
}

.conversation-badge {
  min-width: 20px;
  height: 20px;
  border-radius: 999px;
  padding: 0 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #ff6f3c;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.status-chip,
.price-pill {
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  white-space: nowrap;
}

.status-chip.warning {
  background: #fff0e8;
  color: #e87538;
}

.status-chip.danger {
  background: #fff1f1;
  color: #df5c51;
}

.status-chip.muted {
  background: #f3f4f6;
  color: #9ca3af;
}

.thread-header {
  padding-bottom: 18px;
}

.thread-identity,
.thread-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.thread-copy h2 {
  font-size: 18px;
}

.thread-copy p {
  margin-top: 6px;
}

.price-pill {
  background: #fff4ec;
  color: #d97745;
}

.thread-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 20px 2px 20px 0;
}

.thread-hint {
  margin: 0 0 14px;
  padding: 10px 14px;
  border-radius: 14px;
  background: #f7f7f3;
  color: #8b949e;
  font-size: 13px;
}

.thread-message-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin-bottom: 16px;
}

.thread-message-row.self {
  justify-content: flex-end;
}

.message-column {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  max-width: min(72%, 520px);
}

.thread-message-row.self .message-column {
  align-items: flex-end;
}

.message-avatar {
  width: 34px;
  height: 34px;
  font-size: 13px;
  background: #efe8df;
}

.message-avatar.self {
  background: #d9c0a8;
  color: #fff;
}

.message-bubble {
  border-radius: 18px;
  background: #f5f1e8;
  padding: 12px 16px;
  color: #4a4f56;
  line-height: 1.7;
  word-break: break-word;
  white-space: pre-wrap;
}

.message-bubble.self {
  background: #edf0df;
  color: #55604f;
}

.thread-composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid #edf0eb;
}

.thread-composer.readonly {
  opacity: 0.72;
}

.composer-input {
  min-width: 0;
  border-radius: 14px;
  border-color: #e5e7eb;
  padding: 12px 14px;
}

.composer-tools {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.tool-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #d1d5db;
}

.composer-send {
  min-width: 74px;
  border-radius: 14px;
  background: #a8be92;
}

@media (max-width: 1180px) {
  .message-desk {
    grid-template-columns: 200px 280px minmax(0, 1fr);
  }
}

@media (max-width: 960px) {
  .message-desk {
    grid-template-columns: 1fr;
  }

  .desk-sidebar,
  .desk-list,
  .desk-thread {
    min-height: auto;
  }

  .thread-header,
  .thread-composer {
    grid-template-columns: 1fr;
  }

  .thread-actions {
    justify-content: flex-start;
  }
}
</style>
