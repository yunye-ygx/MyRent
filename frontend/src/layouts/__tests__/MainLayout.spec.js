import { mount } from '@vue/test-utils'
import MainLayout from '@/layouts/MainLayout.vue'

const routeState = {
  path: '/home',
  name: 'home',
  params: {}
}

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    push: vi.fn()
  })
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    userId: 7
  })
}))

vi.mock('@/stores/chatSession', () => ({
  useChatSessionStore: () => ({
    loadSessions: vi.fn(),
    upsertSessionFromMessage: vi.fn(),
    setCurrentSessionId: vi.fn()
  })
}))

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    chatToasts: [
      { id: 'toast-1', sessionId: '1_9_7', senderName: 'Landlord A', content: 'hello', peerId: 9, houseId: 7 }
    ],
    loadUnreadTotals: vi.fn(),
    dismissChatToast: vi.fn(),
    setCurrentChatSession: vi.fn(),
    handleIncomingChatMessage: vi.fn()
  })
}))

describe('MainLayout', () => {
  beforeEach(() => {
    routeState.path = '/home'
    routeState.name = 'home'
    routeState.params = {}
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
    expect(wrapper.find('.app-container-wide').exists()).toBe(false)
  })

  it('expands the shell width for the mine overview page', () => {
    routeState.path = '/mine'
    routeState.name = 'mine'

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

    expect(wrapper.find('.app-container-wide').exists()).toBe(true)
  })
})
