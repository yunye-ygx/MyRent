import { flushPromises, mount } from '@vue/test-utils'
import ChatView from '@/views/ChatView.vue'
import { markMessagesRead, pullHistoryMessages, pullNewMessages } from '@/api/chat'

const { historyMessages, intersectionObservers, sockets } = vi.hoisted(() => ({
  historyMessages: [
    {
      id: 8,
      sessionId: '1_9_7',
      senderId: 9,
      receiverId: 1001,
      content: 'visible inbound 1'
    },
    {
      id: 9,
      sessionId: '1_9_7',
      senderId: 9,
      receiverId: 1001,
      content: 'visible inbound 2'
    },
    {
      id: 10,
      sessionId: '1_9_7',
      senderId: 1001,
      receiverId: 9,
      content: 'visible outbound'
    },
    {
      id: 11,
      sessionId: '1_9_7',
      senderId: 9,
      receiverId: 1001,
      content: 'hidden inbound'
    }
  ],
  intersectionObservers: [],
  sockets: []
}))

const messageCenterStore = {
  decrementChatUnread: vi.fn(),
  loadUnreadTotals: vi.fn()
}

const routerBack = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: {
      sessionId: '1_9_7'
    },
    query: {
      peerId: '9',
      peerName: 'Landlord A',
      houseId: '7'
    }
  }),
  useRouter: () => ({
    back: routerBack,
    push: vi.fn()
  })
}))

vi.mock('@/api/house', () => ({
  fetchHouseById: vi.fn().mockResolvedValue(null)
}))

vi.mock('@/api/chat', () => ({
  markMessagesRead: vi.fn().mockResolvedValue(2),
  pullHistoryMessages: vi.fn().mockResolvedValue({
    messages: historyMessages,
    nextCursor: null,
    hasMore: false
  }),
  pullNewMessages: vi.fn().mockResolvedValue({
    messages: []
  }),
  sendChatMessage: vi.fn()
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    userId: 1001
  })
}))

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => messageCenterStore
}))

describe('ChatView', () => {
  const originalVisibilityState = document.visibilityState
  beforeEach(() => {
    vi.useFakeTimers()
    routerBack.mockReset()
    messageCenterStore.decrementChatUnread.mockClear()
    messageCenterStore.loadUnreadTotals.mockClear()
    markMessagesRead.mockClear()
    pullHistoryMessages.mockClear()
    pullNewMessages.mockClear()
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'visible'
    })

    global.IntersectionObserver = class {
      constructor(callback) {
        this.callback = callback
        this.observedElements = []
        intersectionObservers.push(this)
      }

      observe(element) {
        this.observedElements.push(element)
      }

      disconnect() {}
    }
    window.IntersectionObserver = global.IntersectionObserver

    global.WebSocket = class {
      constructor() {
        this.close = vi.fn()
        sockets.push(this)
      }
    }
    window.WebSocket = global.WebSocket

    HTMLElement.prototype.scrollTo = vi.fn()
    pullHistoryMessages.mockResolvedValue({
      messages: historyMessages,
      nextCursor: null,
      hasMore: false
    })
  })

  afterEach(() => {
    vi.useRealTimers()
    intersectionObservers.length = 0
    sockets.length = 0
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: originalVisibilityState
    })
  })

  function mountChatView() {
    return mount(ChatView, {
      global: {
        stubs: {
          ChatBubble: {
            props: ['message'],
            template: '<div class="chat-bubble">{{ message.content }}</div>'
          },
          EmptyState: {
            template: '<div />'
          },
          LoadingState: {
            template: '<div />'
          }
        }
      }
    })
  }

  async function triggerVisibility(wrapper, visibleMessageIds) {
    const observer = intersectionObservers.at(-1)
    expect(observer).toBeTruthy()

    const entries = wrapper.findAll('.message-observer-item').map((item) => ({
      target: item.element,
      isIntersecting: visibleMessageIds.includes(Number(item.attributes('data-message-id'))),
      intersectionRatio: visibleMessageIds.includes(Number(item.attributes('data-message-id'))) ? 0.7 : 0
    }))

    observer.callback(entries)
    await vi.advanceTimersByTimeAsync(150)
    await flushPromises()
  }

  it('marks only visible inbound messages as read', async () => {
    const wrapper = mountChatView()

    await flushPromises()

    expect(pullHistoryMessages).toHaveBeenCalled()
    await triggerVisibility(wrapper, [8, 9, 10])

    expect(markMessagesRead).toHaveBeenCalledWith({
      sessionId: '1_9_7',
      upToMessageId: 9
    })
    expect(messageCenterStore.decrementChatUnread).toHaveBeenCalledWith(2)

    wrapper.unmount()
  })

  it('does not mark loaded but non-visible inbound messages as read', async () => {
    const wrapper = mountChatView()

    await flushPromises()
    await triggerVisibility(wrapper, [10])

    expect(markMessagesRead).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('does not advance the watermark across a hidden inbound gap', async () => {
    const wrapper = mountChatView()

    await flushPromises()
    await triggerVisibility(wrapper, [8, 10])

    expect(markMessagesRead).toHaveBeenCalledWith({
      sessionId: '1_9_7',
      upToMessageId: 8
    })

    markMessagesRead.mockClear()
    messageCenterStore.decrementChatUnread.mockClear()

    await triggerVisibility(wrapper, [8, 10, 11])

    expect(markMessagesRead).not.toHaveBeenCalled()
    expect(messageCenterStore.decrementChatUnread).not.toHaveBeenCalled()

    wrapper.unmount()
  })

})
