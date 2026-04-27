import { defineStore } from 'pinia'
import { fetchChatUnreadTotal } from '@/api/chat'
import { fetchNotificationUnreadTotal } from '@/api/notification'

export const useMessageCenterStore = defineStore('messageCenter', {
  state: () => ({
    chatUnreadTotal: 0,
    notificationUnreadTotal: 0,
    currentChatSessionId: '',
    chatToasts: [],
    selectedMessageDeskTarget: null,
    pendingMessageDeskTarget: null,
    hasLoadedUnreadTotals: false,
    lastLoadedUnreadAt: 0,
    pendingUnreadLoad: null
  }),
  getters: {
    totalUnread(state) {
      return Number(state.chatUnreadTotal || 0) + Number(state.notificationUnreadTotal || 0)
    }
  },
  actions: {
    async loadUnreadTotals(options = {}) {
      const { force = false, minFreshMs = 0 } = options
      const loadedRecently = this.hasLoadedUnreadTotals &&
        Date.now() - Number(this.lastLoadedUnreadAt || 0) < minFreshMs

      if (!force && loadedRecently) {
        return {
          chatUnreadTotal: this.chatUnreadTotal,
          notificationUnreadTotal: this.notificationUnreadTotal
        }
      }

      if (this.pendingUnreadLoad) {
        return this.pendingUnreadLoad
      }

      this.pendingUnreadLoad = (async () => {
        const [chat, notification] = await Promise.all([
          fetchChatUnreadTotal(),
          fetchNotificationUnreadTotal()
        ])
        this.chatUnreadTotal = Number(chat?.total || 0)
        this.notificationUnreadTotal = Number(notification?.total || 0)
        this.hasLoadedUnreadTotals = true
        this.lastLoadedUnreadAt = Date.now()
        return {
          chatUnreadTotal: this.chatUnreadTotal,
          notificationUnreadTotal: this.notificationUnreadTotal
        }
      })()

      try {
        return await this.pendingUnreadLoad
      } finally {
        this.pendingUnreadLoad = null
      }
    },
    setCurrentChatSession(sessionId) {
      this.currentChatSessionId = String(sessionId || '')
    },
    setMessageDeskSelection(target) {
      this.selectedMessageDeskTarget = target ? { ...target } : null
    },
    setMessageDeskPendingTarget(target) {
      this.pendingMessageDeskTarget = target ? { ...target } : null
    },
    clearMessageDeskPendingTarget() {
      this.pendingMessageDeskTarget = null
    },
    setChatUnreadTotal(total) {
      this.chatUnreadTotal = Number(total || 0)
    },
    decrementChatUnread(count = 1) {
      this.chatUnreadTotal = Math.max(0, Number(this.chatUnreadTotal || 0) - Number(count || 0))
    },
    setNotificationUnreadTotal(total) {
      this.notificationUnreadTotal = Number(total || 0)
    },
    decrementNotificationUnread() {
      this.notificationUnreadTotal = Math.max(0, Number(this.notificationUnreadTotal || 0) - 1)
    },
    resetChatToastQueue() {
      this.chatToasts = []
    },
    dismissChatToast(id) {
      this.chatToasts = this.chatToasts.filter((item) => item.id !== id)
    },
    resetState() {
      this.chatUnreadTotal = 0
      this.notificationUnreadTotal = 0
      this.currentChatSessionId = ''
      this.chatToasts = []
      this.selectedMessageDeskTarget = null
      this.pendingMessageDeskTarget = null
      this.hasLoadedUnreadTotals = false
      this.lastLoadedUnreadAt = 0
      this.pendingUnreadLoad = null
    },
    handleIncomingChatMessage(message) {
      if (!message?.sessionId) {
        return
      }

      const isCurrentSession = String(message.sessionId) === this.currentChatSessionId
      if (!isCurrentSession) {
        this.chatUnreadTotal = Number(this.chatUnreadTotal || 0) + 1
      }
      this.pushIncomingChatToast(message)
    },
    pushIncomingChatToast(message) {
      if (!message?.sessionId || String(message.sessionId) === this.currentChatSessionId) {
        return
      }

      this.chatToasts = [
        ...this.chatToasts,
        {
          id: `${message.sessionId}-${message.id || Date.now()}`,
          sessionId: message.sessionId,
          senderName: message.senderName || `User ${message.senderId || ''}`,
          content: String(message.content || '').slice(0, 30),
          peerId: message.senderId,
          houseId: message.houseId
        }
      ]
    }
  }
})
