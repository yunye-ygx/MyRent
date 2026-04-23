import { mount } from '@vue/test-utils'
import MainLayout from '@/layouts/MainLayout.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({
    path: '/home',
    params: {}
  }),
  useRouter: () => ({
    push: vi.fn()
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
  })
})
