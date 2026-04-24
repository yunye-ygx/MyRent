import { mount } from '@vue/test-utils'
import MainLayout from '@/layouts/MainLayout.vue'

const push = vi.fn()
const loadUnreadTotals = vi.fn()
const dismissChatToast = vi.fn()
const setCurrentChatSession = vi.fn()
const handleIncomingChatMessage = vi.fn()
const loadSessions = vi.fn()
const upsertSessionFromMessage = vi.fn()
const setCurrentSessionId = vi.fn()
const userId = 1
const sockets = []
const routeState = {
  path: '/home',
  params: {}
}

class MockWebSocket {
  constructor(url) {
    this.url = url
    this.close = vi.fn()
    sockets.push(this)
  }
}

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    push
  })
}))

vi.mock('@/stores/chatSession', () => ({
  useChatSessionStore: () => ({
    loadSessions,
    upsertSessionFromMessage,
    setCurrentSessionId
  })
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    userId
  })
}))

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    chatToasts: [
      { id: 'toast-1', sessionId: '1_9_7', senderName: 'Landlord A', content: 'hello', peerId: 9, houseId: 7 }
    ],
    loadUnreadTotals,
    dismissChatToast,
    setCurrentChatSession,
    handleIncomingChatMessage
  })
}))

vi.mock('@/utils/storage', () => ({
  getToken: () => 'token-123'
}))

describe('MainLayout', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    routeState.path = '/home'
    routeState.params = {}
    loadUnreadTotals.mockClear()
    dismissChatToast.mockClear()
    setCurrentChatSession.mockClear()
    handleIncomingChatMessage.mockClear()
    loadSessions.mockClear()
    upsertSessionFromMessage.mockClear()
    setCurrentSessionId.mockClear()
    push.mockClear()
    sockets.length = 0
    global.WebSocket = MockWebSocket
  })

  afterEach(() => {
    vi.runOnlyPendingTimers()
    vi.useRealTimers()
  })

  it('renders the top nav, mobile tab bar, and global chat toast stack', () => {
    const wrapper = mount(MainLayout, {
      global: {
        stubs: {
          AppTopNav: {
            template: '<div data-test="top-nav" />'
          },
          AppTabBar: {
            template: '<div data-test="tab-bar" />'
          },
          OnlineMessageToast: {
            props: ['toast'],
            template: '<div data-test="chat-toast">{{ toast.senderName }}</div>'
          },
          RouterView: {
            template: '<div data-test="page-view" />'
          }
        }
      }
    })

    expect(wrapper.find('[data-test="top-nav"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="tab-bar"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Landlord A')
    expect(setCurrentSessionId).toHaveBeenCalledWith(undefined)
  })

  it('does not duplicate reconciliation on the first websocket open', async () => {
    mount(MainLayout, {
      global: {
        stubs: {
          AppTopNav: true,
          AppTabBar: true,
          OnlineMessageToast: true,
          RouterView: true
        }
      }
    })

    expect(sockets).toHaveLength(1)

    sockets[0].onopen()

    expect(loadUnreadTotals).toHaveBeenCalledTimes(1)
    expect(loadSessions).toHaveBeenCalledTimes(1)
    expect(loadSessions).toHaveBeenCalledWith({ minFreshMs: 5000 })
  })

  it('refreshes unread totals and session summaries on websocket reconnect, then forwards messages to both stores', async () => {
    routeState.path = '/messages'

    mount(MainLayout, {
      global: {
        stubs: {
          AppTopNav: true,
          AppTabBar: true,
          OnlineMessageToast: true,
          RouterView: true
        }
      }
    })

    expect(sockets).toHaveLength(1)

    sockets[0].onopen()
    expect(loadUnreadTotals).toHaveBeenCalledTimes(1)
    expect(loadSessions).toHaveBeenCalledWith({ minFreshMs: 5000 })

    loadUnreadTotals.mockClear()
    loadSessions.mockClear()

    sockets[0].onclose()
    vi.advanceTimersByTime(3000)

    expect(sockets).toHaveLength(2)

    sockets[1].onopen()
    expect(loadUnreadTotals).toHaveBeenCalledTimes(1)
    expect(loadSessions).toHaveBeenCalledTimes(1)
    expect(loadSessions).toHaveBeenCalledWith({ force: true })

    const payload = { sessionId: '1_9_7', content: 'hello', senderId: 9 }
    sockets[1].onmessage({ data: JSON.stringify(payload) })

    expect(handleIncomingChatMessage).toHaveBeenCalledWith(payload)
    expect(upsertSessionFromMessage).toHaveBeenCalledWith(payload, userId)
  })
})
