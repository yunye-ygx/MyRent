import { createPinia, setActivePinia } from 'pinia'
import { useChatSessionStore } from '@/stores/chatSession'

const fetchSessionPage = vi.fn()

vi.mock('@/api/chat', () => ({
  fetchSessionPage: (...args) => fetchSessionPage(...args)
}))

describe('chatSession store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    fetchSessionPage.mockReset()
  })

  it('loads and normalizes session summaries from the page API', async () => {
    fetchSessionPage.mockResolvedValue({
      records: [
        {
          sessionId: '1_9_7',
          peerId: 9,
          peerName: 'Landlord A',
          houseId: 7,
          houseTitle: 'Tianhe One Bed',
          unreadCount: '2'
        }
      ]
    })

    const store = useChatSessionStore()

    await store.loadSessions()

    expect(fetchSessionPage).toHaveBeenCalledWith({ current: 1, size: 50 })
    expect(store.sessions).toEqual([
      expect.objectContaining({
        sessionId: '1_9_7',
        peerName: 'Landlord A',
        houseLabel: 'Tianhe One Bed',
        unreadCount: 2
      })
    ])
  })

  it('reuses a fresh snapshot instead of requesting the page API again immediately', async () => {
    fetchSessionPage.mockResolvedValue({
      records: [
        {
          sessionId: '1_9_7',
          peerId: 9,
          peerName: 'Landlord A',
          houseId: 7,
          houseTitle: 'Tianhe One Bed',
          unreadCount: 2
        }
      ]
    })

    const store = useChatSessionStore()

    await store.loadSessions({ minFreshMs: 5000 })
    await store.loadSessions({ minFreshMs: 5000 })

    expect(fetchSessionPage).toHaveBeenCalledTimes(1)
  })

  it('bypasses the freshness window when a forced reconciliation is requested', async () => {
    fetchSessionPage.mockResolvedValue({
      records: []
    })

    const store = useChatSessionStore()

    await store.loadSessions({ minFreshMs: 5000 })
    await store.loadSessions({ force: true })

    expect(fetchSessionPage).toHaveBeenCalledTimes(2)
  })

  it('moves an existing session to the top and increments unread count for the receiver', () => {
    const store = useChatSessionStore()
    store.sessions = [
      {
        sessionId: '1_3_8',
        peerId: 3,
        peerName: 'Tenant B',
        unreadCount: 0,
        updateTime: '2026-04-24T08:00:00.000Z'
      },
      {
        sessionId: '1_9_7',
        peerId: 9,
        peerName: 'Landlord A',
        unreadCount: 2,
        updateTime: '2026-04-24T09:00:00.000Z',
        lastMsgContent: 'Old message'
      }
    ]

    store.upsertSessionFromMessage(
      {
        sessionId: '1_9_7',
        content: 'Newest message',
        createTime: '2026-04-24T10:00:00.000Z',
        receiverId: 1
      },
      1
    )

    expect(store.sessions[0]).toEqual(
      expect.objectContaining({
        sessionId: '1_9_7',
        lastMsgContent: 'Newest message',
        updateTime: '2026-04-24T10:00:00.000Z',
        unreadCount: 3
      })
    )
  })

  it('does not increment unread count for the currently open session', () => {
    const store = useChatSessionStore()
    store.setCurrentSessionId('1_9_7')
    store.sessions = [
      {
        sessionId: '1_9_7',
        peerId: 9,
        peerName: 'Landlord A',
        unreadCount: 2,
        updateTime: '2026-04-24T09:00:00.000Z',
        lastMsgContent: 'Old message'
      }
    ]

    store.upsertSessionFromMessage(
      {
        sessionId: '1_9_7',
        content: 'Newest message',
        createTime: '2026-04-24T10:00:00.000Z',
        receiverId: 1
      },
      1
    )

    expect(store.sessions[0]).toEqual(
      expect.objectContaining({
        sessionId: '1_9_7',
        lastMsgContent: 'Newest message',
        unreadCount: 2
      })
    )
  })

  it('reloads the page when an incoming message belongs to an unknown session', () => {
    const store = useChatSessionStore()
    const loadSessions = vi.spyOn(store, 'loadSessions').mockResolvedValue()

    store.upsertSessionFromMessage(
      {
        sessionId: '1_9_7',
        content: 'Newest message',
        receiverId: 1
      },
      1
    )

    expect(loadSessions).toHaveBeenCalledTimes(1)
  })

  it('keeps fresher websocket state when an unknown-session reload resolves later', async () => {
    fetchSessionPage.mockResolvedValue({
      records: [
        {
          sessionId: '1_9_7',
          peerId: 9,
          peerName: 'Landlord A',
          unreadCount: 1,
          lastMsgContent: 'Older server message',
          updateTime: '2026-04-24T09:00:00.000Z'
        }
      ]
    })

    const store = useChatSessionStore()
    store.sessions = [
      {
        sessionId: '1_9_7',
        peerId: 9,
        peerName: 'Landlord A',
        unreadCount: 3,
        lastMsgContent: 'Newer live message',
        updateTime: '2026-04-24T10:00:00.000Z'
      }
    ]

    await store.loadSessions()

    expect(store.sessions).toEqual([
      expect.objectContaining({
        sessionId: '1_9_7',
        unreadCount: 3,
        lastMsgContent: 'Newer live message',
        updateTime: '2026-04-24T10:00:00.000Z'
      })
    ])
  })
})
