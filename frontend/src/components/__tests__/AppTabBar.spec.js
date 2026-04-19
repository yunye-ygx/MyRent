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

describe('AppTabBar', () => {
  it('uses utility classes for layout so responsive visibility can hide it on desktop', () => {
    const wrapper = mount(AppTabBar)

    expect(wrapper.classes()).toContain('grid')
    expect(wrapper.classes()).toContain('tabbar')
  })
})
