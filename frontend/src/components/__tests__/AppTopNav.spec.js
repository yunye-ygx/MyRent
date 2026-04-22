import { RouterLinkStub, mount } from '@vue/test-utils'
import AppTopNav from '@/components/layout/AppTopNav.vue'

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    totalUnread: 5
  })
}))

describe('AppTopNav', () => {
  it('renders configured nav items, marks the current route, and shows the unread badge', () => {
    const wrapper = mount(AppTopNav, {
      props: {
        items: [
          { label: '首页', to: '/home' },
          { label: '找房', to: '/houses' },
          { label: '消息', to: '/messages' }
        ],
        currentPath: '/houses'
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    })

    expect(wrapper.text()).toContain('我的租房')
    expect(wrapper.text()).toContain('首页')
    expect(wrapper.text()).toContain('找房')
    expect(wrapper.text()).toContain('5')
    expect(wrapper.get('[data-nav="/houses"]').classes()).toContain('is-active')
  })
})
