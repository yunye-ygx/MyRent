import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import MessagesView from '@/views/MessagesView.vue'

const pullHistoryMessages = vi.fn()
const markMessagesRead = vi.fn()
const sendChatMessage = vi.fn()

vi.mock('@/api/chat', () => ({
  fetchSessionPage: vi.fn().mockResolvedValue({
    records: [
      {
        sessionId: '1_9_7',
        peerId: 9,
        peerName: 'Landlord Li',
        unreadCount: 2,
        houseId: 7,
        houseTitle: 'River View Studio',
        lastMsgContent: 'Hello, the listing is still available.',
        updateTime: '2026-04-24T10:30:00.000Z'
      }
    ]
  }),
  pullHistoryMessages: (...args) => pullHistoryMessages(...args),
  markMessagesRead: (...args) => markMessagesRead(...args),
  sendChatMessage: (...args) => sendChatMessage(...args)
}))

vi.mock('@/api/notification', () => ({
  fetchNotificationPage: vi.fn().mockResolvedValue({
    records: [
      {
        id: 5,
        type: 'HOUSE_PRICE_CHANGED',
        title: 'Price updated',
        content: 'Monthly rent changed to 5000.',
        redirectTargetId: 7,
        isRead: 0,
        createTime: '2026-04-24T09:00:00.000Z'
      }
    ]
  }),
  markNotificationRead: vi.fn().mockResolvedValue({})
}))

const loadSessions = vi.fn().mockResolvedValue()
const setCurrentSessionId = vi.fn()
const loadUnreadTotals = vi.fn().mockResolvedValue({})
const decrementChatUnread = vi.fn()
const decrementNotificationUnread = vi.fn()
const setCurrentChatSession = vi.fn()
const setMessageDeskSelection = vi.fn()
const setMessageDeskPendingTarget = vi.fn()
const clearMessageDeskPendingTarget = vi.fn()

const chatSessionState = {
  loading: false,
  error: '',
  sessions: [
    {
      sessionId: '1_9_7',
      peerId: 9,
      peerName: 'Landlord Li',
      unreadCount: 2,
      houseId: 7,
      houseTitle: 'River View Studio',
      lastMsgContent: 'Hello, the listing is still available.',
      updateTime: '2026-04-24T10:30:00.000Z'
    }
  ],
  loadSessions,
  setCurrentSessionId
}

const messageCenterState = {
  chatUnreadTotal: 2,
  notificationUnreadTotal: 1,
  totalUnread: 3,
  selectedMessageDeskTarget: null,
  pendingMessageDeskTarget: null,
  loadUnreadTotals,
  decrementChatUnread,
  decrementNotificationUnread,
  setCurrentChatSession,
  setMessageDeskSelection,
  setMessageDeskPendingTarget,
  clearMessageDeskPendingTarget
}

function cloneTarget(target) {
  return target ? { ...target } : null
}

setMessageDeskSelection.mockImplementation((target) => {
  messageCenterState.selectedMessageDeskTarget = cloneTarget(target)
})

setMessageDeskPendingTarget.mockImplementation((target) => {
  messageCenterState.pendingMessageDeskTarget = cloneTarget(target)
})

clearMessageDeskPendingTarget.mockImplementation(() => {
  messageCenterState.pendingMessageDeskTarget = null
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    userId: 1001,
    profile: {
      name: 'Tenant Yuan'
    }
  })
}))

vi.mock('@/stores/chatSession', () => ({
  useChatSessionStore: () => chatSessionState
}))

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => messageCenterState
}))

describe('MessagesView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    pullHistoryMessages.mockReset()
    pullHistoryMessages.mockResolvedValue({
      messages: [
        {
          id: 11,
          senderId: 9,
          content: 'Hello, the listing is still available.',
          createTime: '2026-04-24T10:30:00.000Z'
        }
      ]
    })
    markMessagesRead.mockReset()
    markMessagesRead.mockResolvedValue({})
    sendChatMessage.mockReset()
    sendChatMessage.mockResolvedValue({
      id: 12,
      senderId: 1001,
      content: 'Okay, I can come on Saturday.',
      createTime: '2026-04-24T10:35:00.000Z'
    })
    loadSessions.mockClear()
    setCurrentSessionId.mockClear()
    loadUnreadTotals.mockClear()
    decrementChatUnread.mockClear()
    decrementNotificationUnread.mockClear()
    setCurrentChatSession.mockClear()
    setMessageDeskSelection.mockClear()
    setMessageDeskPendingTarget.mockClear()
    clearMessageDeskPendingTarget.mockClear()
    messageCenterState.selectedMessageDeskTarget = null
    messageCenterState.pendingMessageDeskTarget = null
  })

  async function mountView() {
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

    return { wrapper, router }
  }

  it('keeps the thread pane blank on ordinary entry until the user selects a conversation', async () => {
    const { wrapper } = await mountView()

    expect(loadSessions).toHaveBeenCalledWith({ minFreshMs: 5000 })
    expect(loadUnreadTotals).toHaveBeenCalledWith({ minFreshMs: 5000 })
    expect(wrapper.find('[data-thread-title]').exists()).toBe(false)
    expect(pullHistoryMessages).not.toHaveBeenCalled()

    await wrapper.get('[data-entry-id="chat:1_9_7"]').trigger('click')
    await flushPromises()

    expect(pullHistoryMessages).toHaveBeenCalledWith({
      sessionId: '1_9_7',
      limit: 30
    })
    expect(messageCenterState.selectedMessageDeskTarget).toMatchObject({
      kind: 'chat',
      sessionId: '1_9_7'
    })
    expect(wrapper.get('[data-thread-title]').text()).toContain('Landlord Li')
  })

  it('restores the last selected conversation during the same login session', async () => {
    messageCenterState.selectedMessageDeskTarget = {
      kind: 'chat',
      entryId: 'chat:1_9_7',
      sessionId: '1_9_7',
      peerId: 9,
      peerName: 'Landlord Li',
      houseId: 7,
      houseTitle: 'River View Studio'
    }

    const { wrapper } = await mountView()

    expect(wrapper.get('[data-thread-title]').text()).toContain('Landlord Li')
    expect(pullHistoryMessages).toHaveBeenCalledWith({
      sessionId: '1_9_7',
      limit: 30
    })
  })

  it('opens the targeted conversation when a contextual jump enters the message center', async () => {
    messageCenterState.pendingMessageDeskTarget = {
      kind: 'chat',
      sessionId: '1_9_7',
      peerId: 9,
      peerName: 'Landlord Li',
      houseId: 7,
      houseTitle: 'River View Studio'
    }

    const { wrapper } = await mountView()

    expect(wrapper.get('[data-thread-title]').text()).toContain('Landlord Li')
    expect(clearMessageDeskPendingTarget).toHaveBeenCalledTimes(1)
    expect(messageCenterState.selectedMessageDeskTarget).toMatchObject({
      kind: 'chat',
      sessionId: '1_9_7'
    })
  })
})
