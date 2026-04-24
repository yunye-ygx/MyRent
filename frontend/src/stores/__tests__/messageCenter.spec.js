import { createPinia, setActivePinia } from 'pinia'
import { useMessageCenterStore } from '@/stores/messageCenter'

const fetchChatUnreadTotal = vi.fn()
const fetchNotificationUnreadTotal = vi.fn()

vi.mock('@/api/chat', () => ({
  fetchChatUnreadTotal: (...args) => fetchChatUnreadTotal(...args)
}))

vi.mock('@/api/notification', () => ({
  fetchNotificationUnreadTotal: (...args) => fetchNotificationUnreadTotal(...args)
}))

describe('messageCenter store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    fetchChatUnreadTotal.mockReset()
    fetchNotificationUnreadTotal.mockReset()
  })

  it('aggregates chat and notification unread totals', () => {
    const store = useMessageCenterStore()

    store.chatUnreadTotal = 4
    store.notificationUnreadTotal = 3

    expect(store.totalUnread).toBe(7)
  })

  it('suppresses popup when current route already matches the session', () => {
    const store = useMessageCenterStore()

    store.setCurrentChatSession('1_9_7')
    store.pushIncomingChatToast({
      sessionId: '1_9_7',
      senderName: 'Landlord A',
      content: 'hello'
    })

    expect(store.chatToasts).toHaveLength(0)
  })

  it('reuses a fresh unread snapshot instead of requesting totals again immediately', async () => {
    fetchChatUnreadTotal.mockResolvedValue({ total: 3 })
    fetchNotificationUnreadTotal.mockResolvedValue({ total: 2 })

    const store = useMessageCenterStore()

    await store.loadUnreadTotals({ minFreshMs: 5000 })
    await store.loadUnreadTotals({ minFreshMs: 5000 })

    expect(fetchChatUnreadTotal).toHaveBeenCalledTimes(1)
    expect(fetchNotificationUnreadTotal).toHaveBeenCalledTimes(1)
    expect(store.chatUnreadTotal).toBe(3)
    expect(store.notificationUnreadTotal).toBe(2)
  })

  it('bypasses unread freshness when a forced reconciliation is requested', async () => {
    fetchChatUnreadTotal.mockResolvedValue({ total: 4 })
    fetchNotificationUnreadTotal.mockResolvedValue({ total: 1 })

    const store = useMessageCenterStore()

    await store.loadUnreadTotals({ minFreshMs: 5000 })
    await store.loadUnreadTotals({ force: true })

    expect(fetchChatUnreadTotal).toHaveBeenCalledTimes(2)
    expect(fetchNotificationUnreadTotal).toHaveBeenCalledTimes(2)
  })
})
