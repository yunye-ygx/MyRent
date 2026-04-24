import { defineStore } from 'pinia'
import { fetchSessionPage } from '@/api/chat'
import { formatRequestError } from '@/utils/format'

function normalizeSession(session = {}) {
  return {
    ...session,
    peerName: session.peerName || (session.peerId ? `User ${session.peerId}` : ''),
    houseLabel: session.houseLabel || session.houseTitle || '',
    unreadCount: Number(session.unreadCount || 0)
  }
}

function getUpdateTimeValue(updateTime) {
  const value = new Date(updateTime || '').getTime()
  return Number.isNaN(value) ? 0 : value
}

function mergeSessions(serverSessions, currentSessions) {
  const mergedById = new Map()

  for (const session of currentSessions) {
    mergedById.set(String(session.sessionId), normalizeSession(session))
  }

  for (const session of serverSessions) {
    const normalized = normalizeSession(session)
    const sessionId = String(normalized.sessionId)
    const existing = mergedById.get(sessionId)

    if (!existing) {
      mergedById.set(sessionId, normalized)
      continue
    }

    const existingTime = getUpdateTimeValue(existing.updateTime)
    const nextTime = getUpdateTimeValue(normalized.updateTime)
    mergedById.set(
      sessionId,
      existingTime >= nextTime
        ? normalizeSession({ ...normalized, ...existing })
        : normalizeSession({ ...existing, ...normalized })
    )
  }

  return [...mergedById.values()].sort(
    (left, right) => getUpdateTimeValue(right.updateTime) - getUpdateTimeValue(left.updateTime)
  )
}

export const useChatSessionStore = defineStore('chatSession', {
  state: () => ({
    loading: false,
    error: '',
    sessions: [],
    currentSessionId: '',
    hasLoaded: false,
    lastLoadedAt: 0,
    pendingLoad: null
  }),
  actions: {
    setCurrentSessionId(sessionId) {
      this.currentSessionId = String(sessionId || '')
    },
    async loadSessions(options = {}) {
      const { force = false, minFreshMs = 0 } = options
      const loadedRecently = this.hasLoaded && Date.now() - Number(this.lastLoadedAt || 0) < minFreshMs

      if (!force && loadedRecently) {
        return this.sessions
      }

      if (this.pendingLoad) {
        return this.pendingLoad
      }

      this.loading = true
      this.error = ''
      this.pendingLoad = (async () => {
        try {
          const page = await fetchSessionPage({ current: 1, size: 50 })
          const records = Array.isArray(page?.records) ? page.records : []
          this.sessions = mergeSessions(records, this.sessions)
          this.hasLoaded = true
          this.lastLoadedAt = Date.now()
          return this.sessions
        } catch (err) {
          this.error = formatRequestError(err, 'Messages unavailable right now.')
          return this.sessions
        } finally {
          this.loading = false
          this.pendingLoad = null
        }
      })()

      return this.pendingLoad
    },
    upsertSessionFromMessage(message, currentUserId) {
      if (!message?.sessionId) {
        return
      }

      const index = this.sessions.findIndex(
        (item) => String(item.sessionId) === String(message.sessionId)
      )

      if (index < 0) {
        this.loadSessions({ force: true })
        return
      }

      const current = this.sessions[index]
      const isCurrentSession = String(message.sessionId) === this.currentSessionId
      const isUnreadForCurrentUser = String(message.receiverId) === String(currentUserId || '')
      const updated = normalizeSession({
        ...current,
        lastMsgContent: message.content || current.lastMsgContent,
        updateTime: message.createTime || new Date().toISOString(),
        unreadCount: isUnreadForCurrentUser && !isCurrentSession
          ? Number(current.unreadCount || 0) + 1
          : Number(current.unreadCount || 0)
      })

      const nextSessions = [...this.sessions]
      nextSessions.splice(index, 1)
      nextSessions.unshift(updated)
      this.sessions = nextSessions
    }
  }
})
