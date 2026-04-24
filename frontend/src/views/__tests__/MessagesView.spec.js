import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import MessagesView from '@/views/MessagesView.vue'

vi.mock('@/api/chat', () => ({
  fetchSessionPage: vi.fn().mockResolvedValue({
    records: []
  })
}))

vi.mock('@/api/notification', () => ({
  fetchNotificationPage: vi.fn().mockResolvedValue({
    records: [
      { id: 5, type: 'HOUSE_PRICE_CHANGED', title: 'Price changed', content: 'Monthly price is now 5000.', redirectTargetId: 7, isRead: 0 }
    ]
  }),
  markNotificationRead: vi.fn().mockResolvedValue({}),
  markAllNotificationsRead: vi.fn().mockResolvedValue({})
}))

const loadSessions = vi.fn().mockResolvedValue()
const loadUnreadTotals = vi.fn()

vi.mock('@/stores/chatSession', () => ({
  useChatSessionStore: () => ({
    loading: false,
    error: '',
    sessions: [
      { sessionId: '1_9_7', peerId: 9, peerName: 'Landlord A', unreadCount: 2, houseId: 7, houseTitle: 'Tianhe One Bed' }
    ],
    loadSessions
  })
}))

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    chatUnreadTotal: 2,
    notificationUnreadTotal: 1,
    loadUnreadTotals,
    decrementNotificationUnread: vi.fn(),
    setNotificationUnreadTotal: vi.fn()
  })
}))

describe('MessagesView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    loadSessions.mockClear()
    loadUnreadTotals.mockClear()
  })

  it('renders session summaries from the shared store and refreshes them via a stable selector', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/messages', component: MessagesView },
        { path: '/chat/:sessionId', component: { template: '<div />' } }
      ]
    })
    router.push('/messages')
    await router.isReady()

    const wrapper = mount(MessagesView, {
      global: { plugins: [router] }
    })

    await flushPromises()

    expect(loadSessions).toHaveBeenCalledTimes(1)
    expect(loadSessions).toHaveBeenCalledWith({ minFreshMs: 5000 })
    expect(loadUnreadTotals).toHaveBeenCalledTimes(1)
    expect(loadUnreadTotals).toHaveBeenCalledWith({ minFreshMs: 5000 })
    expect(wrapper.text()).toContain('Chat (2)')
    expect(wrapper.text()).toContain('Notifications (1)')
    expect(wrapper.text()).toContain('Landlord A')

    await wrapper.get('[data-action="refresh-current-tab"]').trigger('click')
    expect(loadSessions).toHaveBeenCalledTimes(2)

    await wrapper.get('[data-tab="notifications"]').trigger('click')
    expect(wrapper.text()).toContain('Price changed')
  })
})
