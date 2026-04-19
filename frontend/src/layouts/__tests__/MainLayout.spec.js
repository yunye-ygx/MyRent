import { mount } from '@vue/test-utils'
import MainLayout from '@/layouts/MainLayout.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({
    path: '/home'
  })
}))

describe('MainLayout', () => {
  it('renders the top nav and mobile tab bar without the desktop footer card', () => {
    const wrapper = mount(MainLayout, {
      global: {
        stubs: {
          AppTopNav: {
            template: '<div data-test="top-nav" />'
          },
          AppTabBar: {
            template: '<div data-test="tab-bar" />'
          },
          AppFooter: {
            template: '<div data-test="footer-card" />'
          },
          RouterView: {
            template: '<div data-test="page-view" />'
          }
        }
      }
    })

    expect(wrapper.find('[data-test="top-nav"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="tab-bar"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="footer-card"]').exists()).toBe(false)
  })
})
