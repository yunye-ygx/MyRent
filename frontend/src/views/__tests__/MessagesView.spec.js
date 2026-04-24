import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import MessagesView from '@/views/MessagesView.vue'

vi.mock('@/api/chat', () => ({
  fetchSessionPage: vi.fn().mockResolvedValue({
    records: [
      {
        sessionId: '1_9_7',
        peerId: 9,
        peerName: '房东李女士',
        unreadCount: 2,
        houseId: 7,
        houseTitle: '天河一居室',
        lastMsgContent: '您好，房子还在的。',
        updateTime: '2026-04-24T10:30:00.000Z'
      }
    ]
  }),
  pullHistoryMessages: vi.fn().mockResolvedValue({
    messages: [
      {
        id: 11,
        senderId: 9,
        content: '您好，房子还在的。',
        createTime: '2026-04-24T10:30:00.000Z'
      }
    ]
  }),
  markMessagesRead: vi.fn().mockResolvedValue({}),
  sendChatMessage: vi.fn().mockResolvedValue({
    id: 12,
    senderId: 1001,
    content: '好的，我周六过去。',
    createTime: '2026-04-24T10:35:00.000Z'
  })
}))

vi.mock('@/api/notification', () => ({
  fetchNotificationPage: vi.fn().mockResolvedValue({
    records: [
      {
        id: 5,
        type: 'HOUSE_PRICE_CHANGED',
        title: '价格变动',
        content: '月租已调整为 5000 元。',
        redirectTargetId: 7,
        isRead: 0,
        createTime: '2026-04-24T09:00:00.000Z'
      }
    ]
  }),
  markNotificationRead: vi.fn().mockResolvedValue({}),
  markAllNotificationsRead: vi.fn().mockResolvedValue({})
}))

const loadSessions = vi.fn().mockResolvedValue()
const loadUnreadTotals = vi.fn().mockResolvedValue({})

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    userId: 1001,
    profile: {
      name: '租客小圆'
    }
  })
}))

vi.mock('@/stores/chatSession', () => ({
  useChatSessionStore: () => ({
    loading: false,
    error: '',
    sessions: [
      {
        sessionId: '1_9_7',
        peerId: 9,
        peerName: '房东李女士',
        unreadCount: 2,
        houseId: 7,
        houseTitle: '天河一居室',
        lastMsgContent: '您好，房子还在的。',
        updateTime: '2026-04-24T10:30:00.000Z'
      }
    ],
    loadSessions
  })
}))

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    chatUnreadTotal: 2,
    notificationUnreadTotal: 1,
    totalUnread: 3,
    loadUnreadTotals,
    decrementChatUnread: vi.fn(),
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

  it('renders the message desk, loads shared summaries, and can switch filters', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/messages', component: MessagesView },
        { path: '/house/:id', component: { template: '<div />' } }
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
    expect(wrapper.text()).toContain('全部消息')
    expect(wrapper.text()).toContain('房东李女士')
    expect(wrapper.get('[data-thread-title]').text()).toContain('房东李女士')

    await wrapper.get('[data-filter="system"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('价格变动')
    expect(wrapper.get('[data-thread-title]').text()).toContain('系统通知')
  })
})
