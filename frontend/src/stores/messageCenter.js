import { defineStore } from 'pinia'
import { fetchChatUnreadTotal } from '@/api/chat'
import { fetchNotificationUnreadTotal } from '@/api/notification'

export const useMessageCenterStore = defineStore('messageCenter', {
  state: () => ({
    chatUnreadTotal: 0,
    notificationUnreadTotal: 0,
    currentChatSessionId: '',
    chatToasts: []
  }),
  getters: {
    totalUnread(state) {
      return Number(state.chatUnreadTotal || 0) + Number(state.notificationUnreadTotal || 0)
    }
  },
  actions: {
    async loadUnreadTotals() {
      const [chat, notification] = await Promise.all([
        fetchChatUnreadTotal(),
        fetchNotificationUnreadTotal()
      ])
      this.chatUnreadTotal = Number(chat?.total || 0)
      this.notificationUnreadTotal = Number(notification?.total || 0)
    },
    setCurrentChatSession(sessionId) {
      this.currentChatSessionId = String(sessionId || '')
    },
    setChatUnreadTotal(total) {
      this.chatUnreadTotal = Number(total || 0)
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
