import { mount } from '@vue/test-utils'
import MainLayout from '@/layouts/MainLayout.vue'

const push = vi.fn()
const loadUnreadTotals = vi.fn()
const dismissChatToast = vi.fn()
const setCurrentChatSession = vi.fn()
const setMessageDeskPendingTarget = vi.fn()
const handleIncomingChatMessage = vi.fn()
const loadSessions = vi.fn()
const upsertSessionFromMessage = vi.fn()
const setCurrentSessionId = vi.fn()
const userId = 1001
const sockets = []
const routeState = {
  path: '/home',
  name: 'home',
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
    setMessageDeskPendingTarget,
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
    routeState.name = 'home'
    routeState.params = {}
    loadUnreadTotals.mockClear()
    dismissChatToast.mockClear()
    setCurrentChatSession.mockClear()
    setMessageDeskPendingTarget.mockClear()
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
    expect(wrapper.find('.app-frame').exists()).toBe(true)
    expect(wrapper.find('.app-main').exists()).toBe(true)
    expect(setCurrentSessionId).toHaveBeenCalledWith(undefined)
  })

  it('keeps the same shared frame across the top-level navigation routes', () => {
    const routes = [
      { path: '/home', name: 'home' },
      { path: '/houses', name: 'house-list' },
      { path: '/messages', name: 'messages' },
      { path: '/mine', name: 'mine' }
    ]

    routes.forEach((item) => {
      routeState.path = item.path
      routeState.name = item.name

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

      expect(wrapper.find('.app-frame').exists()).toBe(true)
      expect(wrapper.find('.app-main').exists()).toBe(true)
    })
  })

  it('does not duplicate reconciliation on the first websocket open', () => {
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

  it('routes toast clicks into the message center with an explicit target', async () => {
    const wrapper = mount(MainLayout, {
      global: {
        stubs: {
          AppTopNav: true,
          AppTabBar: true,
          OnlineMessageToast: {
            props: ['toast'],
            template: '<button data-test="chat-toast" @click="$emit(\'click\')">{{ toast.senderName }}</button>'
          },
          RouterView: true
        }
      }
    })

    await wrapper.get('[data-test="chat-toast"]').trigger('click')

    expect(dismissChatToast).toHaveBeenCalledWith('toast-1')
    expect(setMessageDeskPendingTarget).toHaveBeenCalledWith({
      kind: 'chat',
      sessionId: '1_9_7',
      peerId: 9,
      peerName: 'Landlord A',
      houseId: 7
    })
    expect(push).toHaveBeenCalledWith('/messages')
  })

  it('refreshes unread totals and session summaries on websocket reconnect, then forwards messages to both stores', () => {
    routeState.path = '/messages'
    routeState.name = 'messages'

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
