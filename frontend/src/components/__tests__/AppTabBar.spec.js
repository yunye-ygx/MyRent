import { mount } from '@vue/test-utils'
import AppTabBar from '@/components/AppTabBar.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({
    path: '/home'
  }),
  useRouter: () => ({
    push: vi.fn()
  })
}))

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    totalUnread: 3
  })
}))

describe('AppTabBar', () => {
  it('uses utility classes for layout and shows the unread badge on the messages tab', () => {
    const wrapper = mount(AppTabBar)

    expect(wrapper.classes()).toContain('grid')
    expect(wrapper.classes()).toContain('tabbar')
    expect(wrapper.text()).toContain('3')
  })
})
