import { mount } from '@vue/test-utils'
import AppTabBar from '@/components/AppTabBar.vue'

const push = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({
    path: '/ai-chat'
  }),
  useRouter: () => ({
    push
  })
}))

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    totalUnread: 3
  })
}))

describe('AppTabBar', () => {
  beforeEach(() => {
    push.mockReset()
  })

  it('renders the ai tab with dog icon and active state', () => {
    const wrapper = mount(AppTabBar)

    expect(wrapper.classes()).toContain('grid')
    expect(wrapper.text()).toContain('AI 助手')
    expect(wrapper.findAll('.tab-btn')).toHaveLength(5)
    expect(wrapper.find('.icon-roam').exists()).toBe(true)
    expect(wrapper.findAll('.tab-btn')[2].classes()).toContain('active')
  })
})
