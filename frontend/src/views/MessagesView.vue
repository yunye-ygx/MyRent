<template>
  <div class="messages-view">
    <section class="intro app-surface">
      <div>
        <p class="eyebrow">Inbox</p>
        <h1 class="title">Messages</h1>
        <p class="description">
          Keep chat sessions and notification inbox in one place so users can move between
          conversation context and follow-up events.
        </p>
      </div>
      <div class="toolbar">
        <button
          data-tab="chat"
          class="ghost-btn"
          :class="{ active: activeTab === 'chat' }"
          @click="activeTab = 'chat'"
        >
          {{ chatTabLabel }}
        </button>
        <button
          data-tab="notifications"
          class="ghost-btn"
          :class="{ active: activeTab === 'notifications' }"
          @click="activeTab = 'notifications'"
        >
          {{ notificationTabLabel }}
        </button>
        <button v-if="activeTab === 'notifications'" class="ghost-btn" @click="markAllRead">
          Mark all read
        </button>
        <button class="ghost-btn" @click="refreshCurrentTab">Refresh</button>
      </div>
    </section>

    <template v-if="activeTab === 'chat'">
      <LoadingState v-if="loading && !sessions.length" text="Loading sessions..." />

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
        title="No chat sessions yet"
        :description="error || 'Start from a house detail page to open a chat.'"
        action-text="Go Home"
        @action="router.push('/home')"
      />
    </template>

    <template v-else>
      <LoadingState v-if="notificationLoading && !notifications.length" text="Loading notifications..." />

      <section v-else-if="notifications.length" class="notification-list">
        <NotificationInboxItem
          v-for="item in notifications"
          :key="item.id"
          :item="item"
          @click="openNotification(item)"
        />
      </section>

      <EmptyState
        v-else
        title="No notifications yet"
        :description="notificationError || 'New house updates and follow notifications will appear here.'"
        action-text="Go Home"
        @action="router.push('/home')"
      />
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchSessionPage } from '@/api/chat'
import { fetchNotificationPage, markAllNotificationsRead, markNotificationRead } from '@/api/notification'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import NotificationInboxItem from '@/components/NotificationInboxItem.vue'
import SessionItem from '@/components/SessionItem.vue'
import { useChatSessionList } from '@/composables/useChatSessionList'
import { useMessageCenterStore } from '@/stores/messageCenter'

const router = useRouter()
const messageCenterStore = useMessageCenterStore()
const activeTab = ref('chat')
const notificationLoading = ref(false)
const notificationError = ref('')
const notifications = ref([])

const { loading, error, sessions, loadSessions } = useChatSessionList(fetchSessionPage)

const chatTabLabel = computed(() => `Chat (${messageCenterStore.chatUnreadTotal})`)
const notificationTabLabel = computed(() => `Notifications (${messageCenterStore.notificationUnreadTotal})`)

async function loadNotifications() {
  notificationLoading.value = true
  notificationError.value = ''
  try {
    const page = await fetchNotificationPage({ current: 1, size: 20 })
    notifications.value = Array.isArray(page?.records) ? page.records : []
  } catch (err) {
    notificationError.value = err?.message || 'Notifications unavailable'
    notifications.value = []
  } finally {
    notificationLoading.value = false
  }
}

async function openNotification(item) {
  if (!item) {
    return
  }

  if (Number(item.isRead || 0) === 0) {
    await markNotificationRead(item.id)
    item.isRead = 1
    messageCenterStore.decrementNotificationUnread()
  }

  router.push(`/house/${item.redirectTargetId}`)
}

async function markAllRead() {
  await markAllNotificationsRead()
  notifications.value = notifications.value.map((item) => ({ ...item, isRead: 1 }))
  messageCenterStore.setNotificationUnreadTotal(0)
}

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

function refreshCurrentTab() {
  if (activeTab.value === 'notifications') {
    loadNotifications()
    return
  }
  loadSessions()
}

onMounted(() => {
  loadSessions()
  loadNotifications()
})
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

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.ghost-btn.active {
  background: var(--color-accent);
  color: var(--color-accent-contrast);
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

.session-list,
.notification-list {
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
